package me.yummydroid.app.data

import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class SearchPagingTest {
    @Test
    fun fullTitleSearchSendsAPhraseAndPagesOnlyItsMatches() = runBlocking {
        var calls = 0
        val repository = YummyAnimeRepository(api = api { request ->
            calls++
            assertEquals("\"Тетрадь смерти\"", request.url.queryParameter("q"))
            """{"response":[
                {"anime_id":1,"title":"Тетрадь смерти"},
                {"anime_id":2,"title":"Тетрадь смерти: Перезапись"}
            ]}"""
        })
        val filters = BrowseFilters(sort = AnimeSort.Id)
        val first = repository.search("Тетрадь смерти", filters, limit = 1)
        assertEquals(listOf(2L), first.value.map { it.id })
        assertEquals(AnimePageCursor(1, true), first.page)
        val second = repository.search("Тетрадь смерти", filters, offset = 1, limit = 1)
        assertEquals(listOf(1L), second.value.map { it.id })
        assertEquals(AnimePageCursor(2, false), second.page)
        assertEquals(1, calls)
    }

    @Test
    fun serverFallbackMatchesAreRemovedBeforePageCursorsAreCalculated() = runBlocking {
        val repository = YummyAnimeRepository(api = api { request ->
            when (request.url.encodedPath) {
                "/anime/3" -> """{"response":{"anime_id":3,"title":"Тетрадь дружбы Нацумэ"}}"""
                "/anime/2" -> """{"response":{"anime_id":2,"title":"Death Note","other_titles":["Тетрадь смерти"]}}"""
                else -> """{"response":[
                    {"anime_id":3,"title":"Тетрадь дружбы Нацумэ"},
                    {"anime_id":2,"title":"Death Note"},
                    {"anime_id":1,"title":"Тетрадь смерти"}
                ]}"""
            }
        })
        val filters = BrowseFilters(sort = AnimeSort.Id)
        val first = repository.search("Тетрадь смерти", filters, limit = 1)
        assertEquals(listOf(2L), first.value.map { it.id })
        assertEquals(AnimePageCursor(1, true), first.page)
        val second = repository.search("Тетрадь смерти", filters, offset = 1, limit = 1)
        assertEquals(listOf(1L), second.value.map { it.id })
        assertEquals(AnimePageCursor(2, false), second.page)
    }

    @Test
    fun everySortOrdersTheWholeSearchBeforePagingAndReusesTheSnapshot() = runBlocking {
        for (sort in AnimeSort.entries) {
            val offsets = mutableListOf<Int>()
            val auth = AuthStorage(InMemoryPlaybackPreferences()).apply { saveSession("token", UserProfile(1, "User", "")) }
            val repository = YummyAnimeRepository(authStorage = auth, api = api { request ->
                assertEquals("Bearer token", request.header("Authorization"))
                assertEquals("query", request.url.queryParameter("q"))
                assertEquals("action", request.url.queryParameter("genres"))
                assertEquals("id", request.url.queryParameter("sort"))
                assertEquals("100", request.url.queryParameter("limit"))
                val offset = request.url.queryParameter("offset")!!.toInt()
                offsets += offset
                // Upstream relevance puts the newer, higher-rated result in batch two.
                records(if (offset == 0) (1..100).toList() else listOf(101))
            })
            val filters = BrowseFilters(sort = sort, genres = setOf("action"))
            val first = repository.search("query", filters, limit = 24)
            if (sort != AnimeSort.Random) {
                val expected = if (sort == AnimeSort.Title) (1L..24L).toList() else (101L downTo 78L).toList()
                assertEquals(expected, first.value.map { it.id }, sort.name)
            }
            val all = first.value.toMutableList()
            var cursor = first.page!!
            while (cursor.canLoadMore) {
                val next = repository.search("query", filters, offset = cursor.nextOffset, limit = 24)
                all += next.value
                cursor = next.page!!
            }
            assertEquals(101, all.size, sort.name)
            assertEquals((1L..101L).toSet(), all.map { it.id }.toSet(), sort.name)
            assertEquals(listOf(0, 100), offsets, sort.name)
        }
    }

    @Test
    fun refreshAndFilterChangesCannotReuseAnOldSnapshot() = runBlocking {
        var calls = 0
        val repository = YummyAnimeRepository(api = api {
            calls++
            records(listOf(1, 2, 3))
        })
        val filters = BrowseFilters()
        repository.search("first", filters, limit = 1)
        repository.search("first", filters, offset = 1, limit = 1)
        assertEquals(1, calls)
        repository.search("first", filters, limit = 1)
        repository.search("second", filters, offset = 1, limit = 1)
        repository.search("second", filters.copy(sort = AnimeSort.Year), offset = 1, limit = 1)
        repository.invalidateContentCacheForRefresh()
        repository.search("second", filters.copy(sort = AnimeSort.Year), offset = 2, limit = 1)
        assertEquals(5, calls)
    }

    @Test
    fun repeatedUpstreamPageFailsWithoutPublishingAPartialSnapshot() = runBlocking {
        val repository = YummyAnimeRepository(api = api { records((1..100).toList()) })
        assertFailsWith<IllegalStateException> { repository.search("query", BrowseFilters()) }
        assertNull(repository.searchSnapshot)
    }

    @Test
    fun cancelledSecondBatchDoesNotPublishPartialResults() = runBlocking {
        val repository = YummyAnimeRepository(api = api { request ->
            if (request.url.queryParameter("offset") == "100") throw CancellationException("Query changed")
            records((1..100).toList())
        })
        assertFailsWith<CancellationException> { repository.search("query", BrowseFilters()) }
        assertNull(repository.searchSnapshot)
    }

    @Test
    fun excludedMarksAreRemovedBeforePagingAndSessionChangesInvalidateResults() = runBlocking {
        val auth = AuthStorage(InMemoryPlaybackPreferences()).apply { saveSession("a", UserProfile(1, "A", "")) }
        var searchCalls = 0
        val repository = YummyAnimeRepository(authStorage = auth, api = api { request ->
            if (request.url.encodedPath.endsWith("/lists/0")) records(listOf(4, 5))
            else { searchCalls++; records((1..5).toList()) }
        })
        val filters = BrowseFilters(sort = AnimeSort.Id, excludedUserMarks = setOf("0"))
        val first = repository.search("query", filters, limit = 2)
        assertEquals(listOf(3L, 2L), first.value.map { it.id })
        assertEquals(AnimePageCursor(2, true), first.page)
        auth.saveSession("a", UserProfile(1, "Renamed", "", unreadNotifications = 5))
        val next = repository.search("query", filters, offset = 2, limit = 2)
        assertEquals(listOf(1L), next.value.map { it.id })
        assertEquals(AnimePageCursor(3, false), next.page)
        assertEquals(1, searchCalls)
        auth.saveSession("b", UserProfile(2, "B", ""))
        repository.search("query", filters, offset = 2, limit = 2)
        assertEquals(2, searchCalls)
    }

    @Test
    fun votesAndTopUseTheirOwnFieldsAndTiesHaveAStableOrder() {
        val records = listOf(
            AnimeDto(animeId = 1, rating = json("{\"average\":9,\"counters\":2}"), top = json("{\"global\":4}")),
            AnimeDto(animeId = 2, rating = json("{\"average\":8,\"counters\":4}"), top = json("{\"global\":2}")),
            AnimeDto(animeId = 3, rating = json("{\"average\":8,\"counters\":4}"), top = json("{\"global\":3}")),
        )
        assertEquals(listOf(1L, 3L, 2L), records.sortedSearchResults(AnimeSort.Rating, Locale.US).map { it.animeId })
        assertEquals(listOf(3L, 2L, 1L), records.sortedSearchResults(AnimeSort.RatingCounters, Locale.US).map { it.animeId })
        assertEquals(listOf(1L, 3L, 2L), records.sortedSearchResults(AnimeSort.Top, Locale.US).map { it.animeId })
    }

    private fun json(value: String) = Json.parseToJsonElement(value).jsonObject

    private fun records(ids: List<Int>): String = "{\"response\":[" + ids.joinToString(",") {
        """{"anime_id":$it,"title":"Anime ${it.toString().padStart(3, '0')}","year":${1900 + it},"rating":{"average":${it / 11.0},"counters":$it},"views":$it,"top":{"global":$it}}"""
    } + "]}"

    private fun api(respond: (Request) -> String) = YummyAnimeApi(OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test")
            .body(respond(chain.request()).toResponseBody()).build()
    }.build())
}
