package me.yummydroid.app.data

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class RepositoryMutationPagingTest {
    @Test
    fun subscriptionPersistenceRequiresSuccessfulResponseAndOriginalAccount() = runBlocking {
        val auth = AuthStorage(InMemoryPlaybackPreferences()).apply { saveSession("a", UserProfile(1, "A", "")) }
        val subscriptions = AccountVideoSubscriptionStorage(InMemoryPlaybackPreferences())
        var accepted = false
        var switchAccount = false
        val video = VideoVariant(id = 11, animeId = 10, player = "CVH", dubbing = "Voice", episode = "1", url = "https://example.test/11", index = 1, durationSeconds = null, views = 0)
        subscriptions.rememberVideos(1, listOf(video.copy(subscribed = true)))
        val repository = YummyAnimeRepository(authStorage = auth, subscriptionState = subscriptions, api = api { request ->
            assertEquals("Bearer a", request.header("Authorization"))
            if (switchAccount) auth.saveSession("b", UserProfile(2, "B", ""))
            200 to "{\"response\":$accepted}"
        })
        assertFalse(repository.unsubscribeVideo(11))
        assertTrue(subscriptions.overlay(1, listOf(video)).single().subscribed)
        accepted = true
        assertTrue(repository.unsubscribeVideo(11))
        assertFalse(subscriptions.overlay(1, listOf(video)).single().subscribed)
        switchAccount = true
        assertTrue(repository.subscribeVideo(11))
        assertFalse(subscriptions.overlay(1, listOf(video)).single().subscribed)
        assertFalse(subscriptions.overlay(2, listOf(video)).single().subscribed)
    }

    @Test
    fun excludedPagesAdvanceByRawRecordsAndCachedPagesApplyFreshMembership() = runBlocking {
        val directory = Files.createTempDirectory("raw-pages").toFile()
        val cache = AnimeContentCacheStorage(directory)
        val auth = AuthStorage(InMemoryPlaybackPreferences()).apply { saveSession("a", UserProfile(1, "A", "")) }
        var excluded = listOf(1, 2, 3)
        val offsets = mutableListOf<Int>()
        val repository = YummyAnimeRepository(authStorage = auth, contentCache = cache, api = api { request ->
            if (request.url.encodedPath.endsWith("/lists/0")) 200 to records(excluded)
            else {
                val offset = request.url.queryParameter("offset")!!.toInt()
                offsets += offset
                200 to records(when (offset) { 0 -> listOf(1, 2); 2 -> listOf(3, 4); else -> listOf(5) })
            }
        })
        try {
            val filters = BrowseFilters(excludedUserMarks = setOf("0"))
            val first = repository.getFeatured(filters, offset = 0, limit = 2)
            assertEquals(listOf(4L), first.value.map { it.id })
            assertEquals(AnimePageCursor(4, true), first.page)
            val second = repository.getFeatured(filters, offset = first.page!!.nextOffset, limit = 2)
            assertEquals(listOf(5L), second.value.map { it.id })
            assertEquals(AnimePageCursor(5, false), second.page)
            excluded = emptyList()
            val fresh = repository.getFeatured(filters, offset = 0, limit = 2)
            assertEquals(listOf(1L, 2L), fresh.value.map { it.id })
            assertEquals(listOf(0, 2, 4), offsets)
        } finally { cache.clear(); directory.deleteRecursively() }
    }

    @Test
    fun acknowledgedWriteWithFailedRefreshEvictsPersonalizedCachesAndKeepsPublicData() = runBlocking {
        val directory = Files.createTempDirectory("mutation-cache").toFile()
        val cache = AnimeContentCacheStorage(directory)
        val other = AnimeContentCacheStorage(directory)
        val auth = AuthStorage(InMemoryPlaybackPreferences()).apply { saveSession("a", UserProfile(1, "A", "")) }
        val repository = YummyAnimeRepository(authStorage = auth, contentCache = cache, api = api { request ->
            if (request.method == "PUT") 200 to "{\"response\":{}}" else 503 to "unavailable"
        })
        try {
            cache.saveSchedule(ContentLanguage.Russian, emptyList())
            cache.saveVideos(ContentLanguage.Russian, 1L, 10L, emptyList())
            assertEquals(emptyList(), other.readVideos(ContentLanguage.Russian, 1L, 10L))
            val generation = cache.generation()
            assertFailsWith<CommittedMutationRefreshException> { repository.setFavorite(10, true) }
            assertNull(cache.readVideos(ContentLanguage.Russian, 1L, 10L))
            assertNull(other.readVideos(ContentLanguage.Russian, 1L, 10L))
            assertEquals(emptyList(), other.readSchedule(ContentLanguage.Russian))
            var published = false
            cache.publishIfCurrent(generation) { published = true }
            assertFalse(published)
            assertTrue(repository.accountContentChanges.value > 0)
        } finally { cache.clear(); directory.deleteRecursively() }
    }

    private fun records(ids: List<Int>): String = "{\"response\":[" + ids.joinToString(",") { "{\"anime_id\":$it,\"title\":\"Anime $it\"}" } + "]}"

    private fun api(respond: (Request) -> Pair<Int, String>) = YummyAnimeApi(OkHttpClient.Builder().addInterceptor { chain ->
        val (code, body) = respond(chain.request())
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
            .body(body.toResponseBody()).build()
    }.build())
}
