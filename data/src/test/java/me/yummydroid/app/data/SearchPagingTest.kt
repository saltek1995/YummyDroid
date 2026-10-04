package me.yummydroid.app.data

import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class SearchPagingTest {
    @Test
    fun queryWhitespaceIsPassedThroughLikeTheWebsiteCatalogDraft() = runBlocking {
        for (query in listOf(" Naruto ", "  ")) {
            val repository = YummyAnimeRepository(api = api { request ->
                assertEquals(query, request.url.queryParameter("q"))
                records(listOf(1))
            })
            assertEquals(listOf(1L), repository.search(query, BrowseFilters()).value.map { it.id })
        }
    }

    @Test
    fun searchPreservesRawQueryServerMatchesAndServerOrderForEverySort() = runBlocking {
        for (sort in catalogSortOptions) {
            for (forward in listOf(false, true)) {
                val offsets = mutableListOf<Int>()
                val query = "Death \"Note\""
                val serverOrder = listOf(2, 99, 1)
                val repository = YummyAnimeRepository(api = api { request ->
                    assertEquals("/anime", request.url.encodedPath) // No detail/title verification requests.
                    assertEquals(query, request.url.queryParameter("q"))
                    assertEquals(sort.apiValue, request.url.queryParameter("sort"))
                    assertEquals(forward.toString(), request.url.queryParameter("sort_forward"))
                    assertEquals("2", request.url.queryParameter("limit"))
                    assertEquals(listOf("42", "63"), request.url.queryParameterValues("genres"))
                    val offset = request.url.queryParameter("offset")!!.toInt()
                    offsets += offset
                    records(serverOrder.drop(offset).take(2))
                })
                val filters = BrowseFilters(sort = sort, sortForward = forward, genres = linkedSetOf("42", "63"))
                val first = repository.search(query, filters, limit = 2)
                assertEquals(listOf(2L, 99L), first.value.map { it.id })
                assertEquals(AnimePageCursor(2, true), first.page)
                assertEquals(listOf(0), offsets) // First page never scans the entire catalog.
                val second = repository.search(query, filters, offset = first.page!!.nextOffset, limit = 2)
                assertEquals(listOf(1L), second.value.map { it.id })
                assertEquals(AnimePageCursor(3, false), second.page)
                assertEquals(listOf(0, 2), offsets)
            }
        }
    }

    @Test
    fun excludedSearchPagesAdvanceByRawRowsAndRetainServerOrder() = runBlocking {
        val auth = AuthStorage(InMemoryPlaybackPreferences()).apply { saveSession("a", UserProfile(1, "A", "")) }
        val offsets = mutableListOf<Int>()
        val repository = YummyAnimeRepository(authStorage = auth, api = api { request ->
            if (request.url.encodedPath.endsWith("/lists/0")) records(listOf(8, 7, 6))
            else {
                val offset = request.url.queryParameter("offset")!!.toInt()
                offsets += offset
                records(listOf(8, 7, 6, 2, 1).drop(offset).take(2))
            }
        })
        val filters = BrowseFilters(excludedUserMarks = setOf("0"))
        val first = repository.search("query", filters, limit = 2)
        assertEquals(listOf(2L), first.value.map { it.id })
        assertEquals(AnimePageCursor(4, true), first.page)
        val next = repository.search("query", filters, offset = first.page!!.nextOffset, limit = 2)
        assertEquals(listOf(1L), next.value.map { it.id })
        assertEquals(AnimePageCursor(5, false), next.page)
        assertEquals(listOf(0, 2, 4), offsets)
    }

    @Test
    fun searchCacheDistinguishesRawQueriesFiltersAccountsAndRefresh() = runBlocking {
        val directory = Files.createTempDirectory("search-parity-cache").toFile()
        try {
            val auth = AuthStorage(InMemoryPlaybackPreferences()).apply { saveSession("a", UserProfile(1, "A", "")) }
            var calls = 0
            val repository = YummyAnimeRepository(authStorage = auth, contentCache = AnimeContentCacheStorage(directory), api = api {
                calls++
                records(listOf(calls))
            })
            val filters = BrowseFilters()
            suspend fun search(query: String = "Death Note", options: BrowseFilters = filters) =
                repository.search(query, options).value.single().id
            assertEquals(1L, search())
            assertEquals(1L, search())
            assertEquals(2L, search("Death  Note"))
            assertEquals(3L, search("\"Death Note\""))
            assertEquals(4L, search(options = filters.copy(sortForward = false)))
            auth.saveSession("b", UserProfile(2, "B", ""))
            assertEquals(5L, search())
            repository.invalidateContentCacheForRefresh()
            assertEquals(6L, search())
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun cancelledPageIsNotCachedAndCanBeRetried() = runBlocking {
        val directory = Files.createTempDirectory("search-cancellation").toFile()
        try {
            var calls = 0
            val repository = YummyAnimeRepository(contentCache = AnimeContentCacheStorage(directory), api = api {
                if (++calls == 1) throw CancellationException("Query changed")
                records(listOf(4))
            })
            assertFailsWith<CancellationException> { repository.search("query", BrowseFilters()) }
            assertEquals(listOf(4L), repository.search("query", BrowseFilters()).value.map { it.id })
            assertEquals(2, calls)
        } finally { directory.deleteRecursively() }
    }

    @Test
    fun historyFetchesAllPagesWithoutChangingQuerySortOrServerOrder() = runBlocking {
        val offsets = mutableListOf<Int>()
        val serverOrder = (101 downTo 1).toList()
        val repository = YummyAnimeRepository(api = api { request ->
            assertEquals("two words", request.url.queryParameter("q"))
            assertEquals("top", request.url.queryParameter("sort"))
            assertEquals("true", request.url.queryParameter("sort_forward"))
            val offset = request.url.queryParameter("offset")!!.toInt()
            offsets += offset
            records(serverOrder.drop(offset).take(100))
        })
        val result = repository.filterHistory((1L..101L).toSet(), "two words", BrowseFilters())
        assertEquals(serverOrder.map(Int::toLong), result.map { it.id })
        assertEquals(listOf(0, 100), offsets)
    }

    @Test
    fun repeatedOrCancelledHistoryPageDoesNotReturnPartialResults() = runBlocking {
        val ids = (1L..101L).toSet()
        val repeated = YummyAnimeRepository(api = api { records((1..100).toList()) })
        assertFailsWith<IllegalStateException> { repeated.filterHistory(ids, "query", BrowseFilters()) }
        val cancelled = YummyAnimeRepository(api = api { request ->
            if (request.url.queryParameter("offset") == "100") throw CancellationException("Cancelled")
            records((1..100).toList())
        })
        assertFailsWith<CancellationException> { cancelled.filterHistory(ids, "query", BrowseFilters()) }
        Unit
    }

    @Test
    fun randomHistoryDoesNotLoseMatchesAcrossIndependentlyShuffledPages() = runBlocking {
        val ids = (1L..101L).toSet()
        val offsets = mutableListOf<Int>()
        val repository = YummyAnimeRepository(api = api { request ->
            assertEquals("id", request.url.queryParameter("sort"))
            assertEquals("false", request.url.queryParameter("sort_forward"))
            val offset = request.url.queryParameter("offset")!!.toInt()
            offsets += offset
            records((101 downTo 1).drop(offset).take(100))
        })
        val result = repository.filterHistory(ids, "query", BrowseFilters(sort = AnimeSort.Random, sortForward = true))
        assertEquals(ids, result.map { it.id }.toSet())
        assertEquals(101, result.size)
        assertEquals(listOf(0, 100), offsets)
    }

    private fun records(ids: List<Int>): String = "{\"response\":[" + ids.joinToString(",") {
        """{"anime_id":$it,"title":"Server match $it","year":${1900 + it},"rating":{"average":${it / 11.0}},"top":{"global":$it}}"""
    } + "]}"

    private fun api(respond: (Request) -> String) = YummyAnimeApi(OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("test")
            .body(respond(chain.request()).toResponseBody()).build()
    }.build())
}
