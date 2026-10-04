package me.yummydroid.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.yummydroid.app.data.AnimeSort
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.RepositoryContent
import me.yummydroid.app.data.SearchHistoryStorage
import org.robolectric.RuntimeEnvironment
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowseActionRuntimeTest {
    @Test
    fun queryEditsPreserveFiltersAndSettingsThroughDebouncedSearch() = runBlocking {
        val filters = filteredBrowseState()
        val settings = AppSettings(defaultQuality = PreferredQuality.P720, savedBrowseFilters = filters)
        val state = StateHolder(YummyDroidUiState(filters = filters, settings = settings))
        val capturedRequest = CompletableDeferred<Pair<String, BrowseFilters>>()
        val searchRequests = mutableListOf<Pair<String, BrowseFilters>>()
        var saveBrowseFiltersCalls = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = actionRuntime(scope, state, capturedRequest, searchRequests,
                saveBrowseFilters = { saveBrowseFiltersCalls += 1; settings })

            runtime.updateSearchQuery("m")
            runtime.updateSearchQuery("mo")

            assertEquals("mo" to filters, withTimeout(2_000) { capturedRequest.await() })
            assertEquals(filters, state.value.filters)
            assertEquals(settings, state.value.settings)
            assertEquals(settings.savedBrowseFilters, state.value.settings.savedBrowseFilters)
            assertEquals(0, saveBrowseFiltersCalls)
            assertEquals(listOf("mo" to filters), searchRequests)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun clearingSearchReloadsCatalogWithTheCurrentFilters() = runBlocking {
        val filters = filteredBrowseState()
        val settings = AppSettings(savedBrowseFilters = filters)
        val state = StateHolder(YummyDroidUiState(searchQuery = "old", filters = filters, settings = settings))
        val catalogRequests = mutableListOf<BrowseFilters>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = actionRuntime(scope, state, CompletableDeferred(), mutableListOf(),
                saveBrowseFilters = { error("Search must not save filters") }, catalogRequests = catalogRequests)
            runtime.updateSearchQuery("")
            assertEquals(listOf(filters), catalogRequests)
            assertEquals(filters, state.value.filters)
            assertEquals("", state.value.searchQuery)
        } finally { scope.cancel() }
    }

    @Test
    fun editingAndClearingExistingQueryKeepsFiltersAndCancelsPendingSearch() = runBlocking {
        val filters = filteredBrowseState()
        val settings = AppSettings(defaultQuality = PreferredQuality.P720, savedBrowseFilters = filters)
        val state = StateHolder(YummyDroidUiState(searchQuery = "old", filters = filters, settings = settings))
        val capturedRequest = CompletableDeferred<Pair<String, BrowseFilters>>()
        val searchRequests = mutableListOf<Pair<String, BrowseFilters>>()
        var saveBrowseFiltersCalls = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = actionRuntime(scope, state, capturedRequest, searchRequests,
                saveBrowseFilters = { saveBrowseFiltersCalls += 1; settings })

            runtime.updateSearchQuery("edited")
            runtime.updateSearchQuery("latest")

            assertEquals("latest" to filters, withTimeout(2_000) { capturedRequest.await() })
            runtime.updateSearchQuery("pending")
            runtime.updateSearchQuery("")
            delay(450)

            assertEquals("", state.value.searchQuery)
            assertEquals(filters, state.value.filters)
            assertEquals(settings, state.value.settings)
            assertEquals(0, saveBrowseFiltersCalls)
            assertFalse(state.value.searchPaging.canLoadMore)
            assertEquals(listOf("latest" to filters), searchRequests)
        } finally {
            scope.cancel()
        }
    }

    private fun actionRuntime(
        scope: CoroutineScope,
        state: StateHolder,
        capturedRequest: CompletableDeferred<Pair<String, BrowseFilters>>,
        searchRequests: MutableList<Pair<String, BrowseFilters>>,
        saveBrowseFilters: (BrowseFilters) -> AppSettings,
        catalogRequests: MutableList<BrowseFilters> = mutableListOf(),
    ): BrowseActionRuntime {
        val coordinator = BrowseContentCoordinator(
            scope = scope,
            currentState = { state.value },
            updateState = { transform -> state.value = transform(state.value) },
            fetchCatalog = { filters, _, _ -> catalogRequests += filters; RepositoryContent(emptyList()) },
            searchCatalog = { query, filters, _, _ ->
                searchRequests += query to filters
                capturedRequest.complete(query to filters)
                RepositoryContent(emptyList())
            },
            fetchSchedule = { emptyList() },
            fetchOfflineEntries = { emptyList<OfflineAnimeEntry>() },
            isOfflineConnectivityFailure = { false },
            watchHistoryCoordinator = historyCoordinator(),
            requestCaptchaRetry = { _, _ -> false },
            historyUnavailableMessage = { "History unavailable" },
            monotonicClockMs = { 1_000L },
        )
        return BrowseActionRuntime(
            scope = scope,
            searchHistoryStorage = SearchHistoryStorage(RuntimeEnvironment.getApplication()),
            currentState = { state.value },
            updateState = { transform -> state.value = transform(state.value) },
            browseContentCoordinator = coordinator,
            saveBrowseFilters = saveBrowseFilters,
            offlineUnavailableMessage = { "Offline unavailable" },
            showNotice = {},
        )
    }

    private fun historyCoordinator(): WatchHistoryCoordinator {
        return WatchHistoryCoordinator(
            readProgress = { emptyList() },
            saveProgressIfNewer = {},
            readCachedAnime = { emptyMap() },
            saveCachedAnime = {},
            fetchHistoryPage = { _, _ -> emptyList() },
            uploadProgress = { true },
            fetchAnimeSummary = { error("Unused") },
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun filteredBrowseState(): BrowseFilters = BrowseFilters(
        sort = AnimeSort.Id,
        sortForward = true,
        statuses = setOf("released"),
        genres = setOf("action", "fantasy"),
    )

    private class StateHolder(var value: YummyDroidUiState)
}
