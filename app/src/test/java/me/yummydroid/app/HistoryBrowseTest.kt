package me.yummydroid.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class HistoryBrowseTest {
    @Test
    fun defaultHistoryPreservesOrderWithoutStartingFilter() = runBlocking {
        val history = listOf(watchHistoryAnime(3), watchHistoryAnime(1), watchHistoryAnime(2))
        val state = MutableStateFlow(
            YummyDroidUiState(
                historyAnime = LoadState.Ready(history),
                searchQuery = "catalog query",
                filters = BrowseFilters(fromYear = 2020),
            ),
        )
        var calls = 0
        val observer = observe(state) { _, _, _ -> calls++; emptyList() }

        yield()

        assertEquals(listOf(3L, 1L, 2L), state.value.visibleHistoryAnime.readyIds())
        assertEquals(0, calls)
        observer.cancelAndJoin()
    }

    @Test
    fun queryAndFiltersUseOnlyHistoryInputsAndLeaveCatalogStateUntouched() = runBlocking {
        val history = listOf(watchHistoryAnime(3), watchHistoryAnime(1), watchHistoryAnime(2))
        val catalogFilters = BrowseFilters(fromYear = 2020)
        val historyFilters = BrowseFilters(fromYear = 2025)
        val catalogResults = LoadState.Ready(listOf(watchHistoryAnime(99)))
        val state = MutableStateFlow(
            YummyDroidUiState(
                historyAnime = LoadState.Ready(history),
                searchQuery = "catalog query",
                filters = catalogFilters,
                searchResults = catalogResults,
            ),
        )
        var request: Triple<Set<Long>, String, BrowseFilters>? = null
        val observer = observe(state) { ids, query, filters ->
            request = Triple(ids, query, filters)
            listOf(watchHistoryAnime(2))
        }

        state.update { it.copy(historySearchQuery = "history query", historyFilters = historyFilters) }
        awaitHistoryResult(state)

        assertEquals(Triple(setOf(3L, 1L, 2L), "history query", historyFilters), request)
        assertEquals("catalog query", state.value.searchQuery)
        assertEquals(catalogFilters, state.value.filters)
        assertEquals(catalogResults, state.value.searchResults)
        assertEquals(listOf(2L), state.value.visibleHistoryAnime.readyIds())
        observer.cancelAndJoin()
    }

    @Test
    fun staleDelayedResponseCannotPublishAfterResetAndResetReturnsOriginalHistory() = runBlocking {
        val history = listOf(watchHistoryAnime(3), watchHistoryAnime(1))
        val state = MutableStateFlow(YummyDroidUiState(historyAnime = LoadState.Ready(history)))
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<List<me.yummydroid.app.data.Anime>>()
        val observer = observe(state) { _, _, _ ->
            started.complete(Unit)
            withContext(NonCancellable) { response.await() }
        }

        state.update { it.copy(historySearchQuery = "old") }
        started.await()
        assertIs<LoadState.Loading>(state.value.visibleHistoryAnime)
        state.update { it.copy(historySearchQuery = "") }
        response.complete(listOf(watchHistoryAnime(9)))
        yield()

        assertNull(state.value.historyBrowseResult)
        assertEquals(listOf(3L, 1L), state.value.visibleHistoryAnime.readyIds())
        observer.cancelAndJoin()
    }

    @Test
    fun accountContextChangeCannotShowOldFilteredResult() = runBlocking {
        val state = MutableStateFlow(
            YummyDroidUiState(
                historyAnime = LoadState.Ready(listOf(watchHistoryAnime(1))),
                auth = AuthUiState(profile = UserProfile(1L, "First", "")),
            ),
        )
        val started = CompletableDeferred<Unit>()
        val firstResponse = CompletableDeferred<List<me.yummydroid.app.data.Anime>>()
        val secondResponse = CompletableDeferred<List<me.yummydroid.app.data.Anime>>()
        var calls = 0
        val observer = observe(state) { _, _, _ ->
            if (++calls == 1) {
                started.complete(Unit)
                withContext(NonCancellable) { firstResponse.await() }
            } else {
                secondResponse.await()
            }
        }

        state.update { it.copy(historySearchQuery = "query") }
        started.await()
        state.update { it.copy(auth = AuthUiState(profile = UserProfile(2L, "Second", ""))) }
        firstResponse.complete(listOf(watchHistoryAnime(9)))
        yield()

        assertNull(state.value.historyBrowseResult)
        assertIs<LoadState.Loading>(state.value.visibleHistoryAnime)
        observer.cancelAndJoin()
    }

    private fun CoroutineScope.observe(
        state: MutableStateFlow<YummyDroidUiState>,
        filter: suspend (Set<Long>, String, BrowseFilters) -> List<me.yummydroid.app.data.Anime>,
    ) = launch {
        observeHistoryBrowse(state, { transform -> state.update(transform) }, filter)
    }

    private suspend fun awaitHistoryResult(state: MutableStateFlow<YummyDroidUiState>) {
        withTimeout(2_000) { state.first { it.historyBrowseResult != null } }
    }

    private fun LoadState<List<me.yummydroid.app.data.Anime>>.readyIds(): List<Long> {
        return (this as LoadState.Ready).data.map { it.id }
    }
}
