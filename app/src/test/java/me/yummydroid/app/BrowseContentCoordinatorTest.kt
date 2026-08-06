package me.yummydroid.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.ScheduleAnime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BrowseContentCoordinatorTest {
    @Test
    fun catalogSuccessUpdatesStateAndRouteCacheFromOneResult() = runBlocking {
        val state = StateHolder(
            YummyDroidUiState(featured = LoadState.Ready(emptyList())),
        )
        val requests = mutableListOf<Triple<BrowseFilters, Int, Int>>()
        val coordinator = coordinator(
            scope = this,
            state = state,
            fetchCatalog = { filters, offset, limit ->
                requests += Triple(filters, offset, limit)
                listOf(anime(1))
            },
        )

        coordinator.loadCatalog()
        yield()

        assertEquals(listOf(1L), state.value.featured.readyListOrEmpty().map(Anime::id))
        assertEquals(listOf(Triple(BrowseFilters(), 0, 36)), requests)
        assertEquals(
            state.value.featured.readyListOrEmpty(),
            coordinator.catalogCache(BrowseFilters())?.animes,
        )
    }

    @Test
    fun catalogResultIsIgnoredAfterRequestFiltersBecomeStale() = runBlocking {
        val requestedFilters = BrowseFilters(fromYear = 2026)
        val changedFilters = BrowseFilters(fromYear = 2025)
        val state = StateHolder(
            YummyDroidUiState(
                featured = LoadState.Ready(emptyList()),
                filters = requestedFilters,
            ),
        )
        val coordinator = coordinator(
            scope = this,
            state = state,
            fetchCatalog = { _, _, _ ->
                state.value = state.value.copy(filters = changedFilters)
                listOf(anime(1))
            },
        )

        coordinator.loadCatalog()
        yield()

        assertEquals(changedFilters, state.value.filters)
        assertEquals(emptyList(), state.value.featured.readyListOrEmpty())
        assertNull(coordinator.catalogCache(requestedFilters))
    }

    @Test
    fun offlineCatalogFailureMovesBrowseToDownloadsAndLoadsOfflineEntries() = runBlocking {
        val state = StateHolder(
            YummyDroidUiState(featured = LoadState.Ready(emptyList())),
        )
        var offlineLoads = 0
        val coordinator = coordinator(
            scope = this,
            state = state,
            fetchCatalog = { _, _, _ -> throw IllegalStateException("offline") },
            fetchOfflineEntries = {
                offlineLoads += 1
                emptyList()
            },
            isOfflineConnectivityFailure = { true },
        )

        coordinator.loadCatalog()
        yield()
        yield()

        assertEquals(true, state.value.forcedOfflineMode)
        assertEquals(BrowseSection.Downloads, state.value.homeSection)
        assertIs<LoadState.Ready<List<OfflineAnimeEntry>>>(state.value.offlineEntries)
        assertEquals(1, offlineLoads)
    }

    @Test
    fun scheduleUsesOneRefreshClockAndKeepsReadyDataDuringTimedRefresh() = runBlocking {
        var nowMs = 1_000L
        var requests = 0
        val state = StateHolder(YummyDroidUiState())
        val coordinator = coordinator(
            scope = this,
            state = state,
            fetchSchedule = {
                requests += 1
                listOf(scheduleAnime(requests.toLong()))
            },
            monotonicClockMs = { nowMs },
            scheduleRefreshIntervalMs = 60_000L,
        )

        coordinator.loadSchedule(force = false)
        yield()
        coordinator.loadSchedule(force = false)
        yield()

        assertEquals(1, requests)
        assertEquals(listOf(1L), state.value.schedule.readyScheduleIds())

        nowMs += 60_001L
        coordinator.loadSchedule(force = false)
        assertIs<LoadState.Ready<List<ScheduleAnime>>>(state.value.schedule)
        yield()

        assertEquals(2, requests)
        assertEquals(listOf(2L), state.value.schedule.readyScheduleIds())
    }

    @Test
    fun newerCatalogResetCancelsOlderRequestWithoutLateOverwrite() = runBlocking {
        val firstResult = CompletableDeferred<List<Anime>>()
        var requests = 0
        val state = StateHolder(
            YummyDroidUiState(featured = LoadState.Ready(emptyList())),
        )
        val coordinator = coordinator(
            scope = this,
            state = state,
            fetchCatalog = { _, _, _ ->
                requests += 1
                if (requests == 1) firstResult.await() else listOf(anime(2))
            },
        )

        coordinator.loadCatalog()
        yield()
        coordinator.loadCatalog()
        yield()
        firstResult.complete(listOf(anime(1)))
        yield()

        assertEquals(2, requests)
        assertEquals(listOf(2L), state.value.featured.readyListOrEmpty().map(Anime::id))
    }

    @Test
    fun scheduleLoadPlanRejectsFreshCacheAndCompetingBackgroundLoad() {
        assertNull(
            scheduleLoadPlan(
                force = false,
                cacheInitialized = true,
                hasReadySchedule = true,
                loadActive = false,
                refreshDue = false,
            ),
        )
        assertNull(
            scheduleLoadPlan(
                force = false,
                cacheInitialized = false,
                hasReadySchedule = false,
                loadActive = true,
                refreshDue = true,
            ),
        )
        assertEquals(
            ScheduleLoadPlan(showLoading = false),
            scheduleLoadPlan(
                force = false,
                cacheInitialized = true,
                hasReadySchedule = true,
                loadActive = false,
                refreshDue = true,
            ),
        )
    }

    private fun coordinator(
        scope: CoroutineScope,
        state: StateHolder,
        fetchCatalog: suspend (BrowseFilters, Int, Int) -> List<Anime> = { _, _, _ -> emptyList() },
        fetchSchedule: suspend () -> List<ScheduleAnime> = { emptyList() },
        fetchOfflineEntries: suspend () -> List<OfflineAnimeEntry> = { emptyList() },
        isOfflineConnectivityFailure: (Throwable) -> Boolean = { false },
        monotonicClockMs: () -> Long = { 1_000L },
        scheduleRefreshIntervalMs: Long = BROWSE_REMOTE_REFRESH_INTERVAL_MS,
    ): BrowseContentCoordinator {
        return BrowseContentCoordinator(
            scope = scope,
            currentState = { state.value },
            updateState = { transform -> state.value = transform(state.value) },
            fetchCatalog = fetchCatalog,
            searchCatalog = { _, _, _, _ -> emptyList() },
            fetchSchedule = fetchSchedule,
            fetchOfflineEntries = fetchOfflineEntries,
            isOfflineFallbackActive = { false },
            isOfflineConnectivityFailure = isOfflineConnectivityFailure,
            watchHistoryCoordinator = historyCoordinator(),
            requestCaptchaRetry = { _, _ -> false },
            historyUnavailableMessage = { "History unavailable" },
            monotonicClockMs = monotonicClockMs,
            scheduleRefreshIntervalMs = scheduleRefreshIntervalMs,
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
            fetchAnimeSummary = { anime(it) },
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun anime(id: Long): Anime {
        return Anime(
            id = id,
            title = "Anime $id",
            description = "",
            posterUrl = "",
            animeUrl = "",
            year = 2026,
            rating = null,
            views = 0,
            status = "",
            type = "",
            genres = emptyList(),
            blockedIn = emptyList(),
        )
    }

    private fun scheduleAnime(id: Long): ScheduleAnime {
        return ScheduleAnime(
            anime = anime(id),
            airedEpisodes = 1,
            totalEpisodes = 1,
            previousEpisodeAtSeconds = 0,
            nextEpisodeAtSeconds = 0,
        )
    }

    private fun LoadState<List<ScheduleAnime>>.readyScheduleIds(): List<Long> {
        return (this as LoadState.Ready).data.map { it.anime.id }
    }

    private class StateHolder(var value: YummyDroidUiState)
}
