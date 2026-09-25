package me.yummydroid.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.RepositoryContent
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.ScheduleAnime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BrowseContentCoordinatorTest {
    @Test
    fun markChangesPreserveUnfilteredPagesAndPendingRequests() = runBlocking {
        val pending = CompletableDeferred<List<Anime>>()
        val state = StateHolder(YummyDroidUiState(
            searchResults = LoadState.Ready(listOf(anime(8))),
            searchPaging = PagingUiState(nextOffset = 25),
        ))
        val coordinator = coordinator(this, state, fetchCatalog = { _, _, _ -> pending.await() })
        coordinator.loadCatalog()
        yield()
        coordinator.invalidateAnimeMarks()
        pending.complete(listOf(anime(1)))
        yield()
        assertEquals(listOf(1L), state.value.featured.readyListOrEmpty().map { it.id })
        val cache = coordinator.catalogCache(BrowseFilters())
        coordinator.invalidateAnimeMarks()
        assertEquals(cache, coordinator.catalogCache(BrowseFilters()))
        assertEquals(listOf(8L), state.value.searchResults.readyListOrEmpty().map { it.id })
        assertEquals(25, state.value.searchPaging.nextOffset)
    }

    @Test
    fun markChangesInvalidateIncludedAndExcludedMarkFiltersOnly() = runBlocking {
        for (filters in listOf(BrowseFilters(userMarks = setOf("0")), BrowseFilters(excludedUserMarks = setOf("4")))) {
            val history = LoadState.Ready(listOf(anime(9)))
            val state = StateHolder(YummyDroidUiState(filters = filters, historyAnime = history, historyFilters = filters))
            var calls = 0
            val coordinator = coordinator(this, state, fetchCatalog = { _, _, _ -> listOf(anime((++calls).toLong())) })
            coordinator.loadCatalog()
            yield()
            coordinator.invalidateAnimeMarks()
            yield()
            assertEquals(2, calls)
            assertEquals(listOf(2L), state.value.featured.readyListOrEmpty().map { it.id })
            assertEquals(history, state.value.historyAnime)
            assertEquals(1L, state.value.historyBrowseRevision)
        }
    }

    @Test
    fun restoringProfileRestartsCatalogWithoutChangingTabsRegardlessOfFirstResponseTiming() = runBlocking {
        for (firstResponseAlreadyLoaded in listOf(false, true)) {
            val pendingGuestResponse = CompletableDeferred<List<Anime>>()
            val state = StateHolder(YummyDroidUiState())
            var calls = 0
            val coordinator = coordinator(this, state, fetchCatalog = { _, _, _ ->
                if (++calls == 1) {
                    if (firstResponseAlreadyLoaded) listOf(anime(1)) else pendingGuestResponse.await()
                } else listOf(anime(2))
            })
            coordinator.loadCatalog()
            yield()
            val previous = state.value
            val profile = me.yummydroid.app.data.UserProfile(7L, "profile", "")
            state.value = previous.copy(auth = AuthUiState(profile = profile, loading = true))
                .withContentContextTransition(previous)
            // AuthStateRuntime now calls this after applying the cached/server profile.
            coordinator.ensureLoaded(state.value.homeSection)
            yield()
            pendingGuestResponse.complete(listOf(anime(1)))
            yield()
            assertEquals(AppRoute.Home, state.value.route)
            assertEquals(BrowseSection.Catalog, state.value.homeSection)
            assertEquals(listOf(2L), state.value.featured.readyListOrEmpty().map { it.id })
            assertEquals(7L, coordinator.catalogCache(state.value.filters)?.context?.profileId)
            assertEquals(2, calls)

            val cachedState = state.value
            state.value = cachedState.copy(auth = AuthUiState(profile = profile.copy(nickname = "verified")))
                .withContentContextTransition(cachedState)
            coordinator.ensureLoaded(state.value.homeSection)
            yield()
            assertEquals(2, calls) // Server verification must not reload the same account's catalog.
        }
    }

    @Test
    fun guestProfileVerificationKeepsInitialCatalogRequest() = runBlocking {
        val response = CompletableDeferred<List<Anime>>()
        val state = StateHolder(YummyDroidUiState())
        var calls = 0
        val coordinator = coordinator(this, state, fetchCatalog = { _, _, _ -> calls++; response.await() })
        coordinator.loadCatalog()
        yield()
        coordinator.ensureLoaded(state.value.homeSection)
        coordinator.ensureLoaded(state.value.homeSection)
        response.complete(listOf(anime(1)))
        yield()
        assertEquals(1, calls)
        assertEquals(listOf(1L), state.value.featured.readyListOrEmpty().map { it.id })
    }

    @Test
    fun languageChangeRejectsLateCatalogAndScheduleAndResetsLoadedFlags() = runBlocking {
        val catalogResponse = CompletableDeferred<List<Anime>>()
        val scheduleResponse = CompletableDeferred<List<ScheduleAnime>>()
        val state = StateHolder(YummyDroidUiState())
        var catalogCalls = 0
        var scheduleCalls = 0
        val coordinator = coordinator(this, state,
            fetchCatalog = { _, _, _ -> if (++catalogCalls == 1) catalogResponse.await() else listOf(anime(2)) },
            fetchSchedule = { if (++scheduleCalls == 1) scheduleResponse.await() else listOf(scheduleAnime(2)) },
        )
        coordinator.loadCatalog()
        coordinator.loadSchedule()
        yield()
        state.value = state.value.copy(settings = state.value.settings.copy(
            contentLanguage = me.yummydroid.app.data.ContentLanguage.entries.first { it != state.value.settings.contentLanguage },
        ))
        catalogResponse.complete(listOf(anime(1)))
        scheduleResponse.complete(listOf(scheduleAnime(1)))
        yield()
        assertIs<LoadState.Loading>(state.value.featured)
        assertIs<LoadState.Loading>(state.value.schedule)
        assertNull(coordinator.catalogCache(state.value.filters))
        coordinator.ensureLoaded(BrowseSection.Catalog)
        coordinator.ensureLoaded(BrowseSection.Schedule)
        yield()
        assertEquals(listOf(2L), state.value.featured.readyListOrEmpty().map { it.id })
        assertEquals(listOf(2L), state.value.schedule.readyScheduleIds())
        assertEquals(2, catalogCalls)
        assertEquals(2, scheduleCalls)
    }

    @Test
    fun completedCatalogCacheCannotCrossLanguageOrOfflineContexts() = runBlocking {
        val state = StateHolder(YummyDroidUiState())
        var calls = 0
        val coordinator = coordinator(this, state, fetchCatalog = { _, _, _ -> listOf(anime((++calls).toLong())) })
        coordinator.loadCatalog()
        yield()
        assertEquals(listOf(1L), coordinator.catalogCache(state.value.filters)?.animes?.map { it.id })
        state.value = state.value.copy(forcedOfflineMode = true)
        assertNull(coordinator.catalogCache(state.value.filters))
        coordinator.ensureLoaded(BrowseSection.Downloads)
        state.value = state.value.copy(forcedOfflineMode = false)
        coordinator.ensureLoaded(BrowseSection.Catalog)
        yield()
        assertEquals(listOf(2L), state.value.featured.readyListOrEmpty().map { it.id })
    }

    @Test
    fun invalidatedSearchCannotBeReusedOnBackOrMarkFeaturedLoaded() = runBlocking {
        val state = StateHolder(YummyDroidUiState(route = AppRoute.Details(10), searchQuery = "query", searchResults = LoadState.Ready(listOf(anime(1)))))
        var catalogCalls = 0
        val coordinator = coordinator(this, state, fetchCatalog = { _, _, _ -> catalogCalls++; listOf(anime(2)) })
        coordinator.invalidateAccountContent()
        assertIs<LoadState.Loading>(state.value.searchResults)
        state.value = state.value.copy(route = AppRoute.Home)
        coordinator.ensureLoaded(BrowseSection.Catalog)
        yield()
        assertEquals(0, catalogCalls)
        state.value = state.value.copy(searchQuery = "")
        coordinator.ensureLoaded(BrowseSection.Catalog)
        yield()
        assertEquals(1, catalogCalls)
        assertEquals(listOf(2L), state.value.featured.readyListOrEmpty().map { it.id })
    }

    @Test
    fun searchAndAppendOfflineTransitionsExplainInventoryOnce() = runBlocking {
        for (search in listOf(false, true)) {
            val state = StateHolder(YummyDroidUiState(searchQuery = if (search) "query" else "",
                featured = LoadState.Ready(listOf(anime(1)))))
            val notices = mutableListOf<Boolean>()
            var offlineLoads = 0
            val loaded = CompletableDeferred<Unit>()
            val coordinator = coordinator(this, state, offlineFallback = { true },
                fetchOfflineEntries = { offlineLoads++; loaded.complete(Unit); emptyList() }, onOfflineFiltersUnavailable = notices::add)
            if (search) coordinator.search("query") else coordinator.loadCatalog(reset = false)
            yield()
            kotlinx.coroutines.withTimeout(2000) { loaded.await() }
            assertEquals(listOf(true), notices)
            assertEquals(1, offlineLoads)
        }
    }

    @Test
    fun mutationInvalidatesSuspendedResponseAndReloadsWhenReturningHome() = runBlocking {
        val response = CompletableDeferred<List<Anime>>()
        val state = StateHolder(YummyDroidUiState())
        var calls = 0
        val coordinator = coordinator(this, state, fetchCatalog = { _, _, _ ->
            calls += 1
            if (calls == 1) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { response.await() }
            else listOf(anime(2))
        })
        coordinator.loadCatalog()
        yield()
        state.value = state.value.copy(route = AppRoute.Details(10))
        coordinator.invalidateAccountContent()
        response.complete(listOf(anime(1)))
        yield()
        assertNull(coordinator.catalogCache(BrowseFilters()))
        assertIs<LoadState.Loading>(state.value.featured)
        state.value = state.value.copy(route = AppRoute.Home)
        coordinator.ensureLoaded(BrowseSection.Catalog)
        yield()
        assertEquals(listOf(2L), state.value.featured.readyListOrEmpty().map { it.id })
        assertEquals(2, calls)
    }

    @Test
    fun catalogResponsesCannotClearOfflineModeBeforeConnectivityRecovery() = runBlocking {
        val state = StateHolder(YummyDroidUiState())
        var fallback = true
        val coordinator = coordinator(scope = this, state = state, offlineFallback = { fallback })
        coordinator.loadCatalog()
        yield()
        assertEquals(true, state.value.forcedOfflineMode)
        fallback = false
        state.value = state.value.copy(homeSection = BrowseSection.Catalog)
        coordinator.loadCatalog()
        yield()
        assertEquals(true, state.value.forcedOfflineMode)
        state.value = state.value.copy(forcedOfflineMode = false)
        coordinator.loadCatalog()
        yield()
        assertEquals(false, state.value.forcedOfflineMode)
    }

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

    @Test
    fun scheduleLoadPlanPrioritizesForcedLoadingThenQuietRefresh() {
        assertEquals(
            ScheduleLoadPlan(showLoading = true),
            scheduleLoadPlan(
                force = true,
                cacheInitialized = true,
                hasReadySchedule = true,
                loadActive = true,
                refreshDue = false,
            ),
        )
        assertEquals(
            ScheduleLoadPlan(showLoading = true),
            scheduleLoadPlan(
                force = false,
                cacheInitialized = false,
                hasReadySchedule = true,
                loadActive = false,
                refreshDue = false,
            ),
        )
        assertEquals(
            ScheduleLoadPlan(showLoading = true),
            scheduleLoadPlan(
                force = false,
                cacheInitialized = true,
                hasReadySchedule = false,
                loadActive = false,
                refreshDue = false,
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
        offlineFallback: () -> Boolean = { false },
        fetchSchedule: suspend () -> List<ScheduleAnime> = { emptyList() },
        fetchOfflineEntries: suspend () -> List<OfflineAnimeEntry> = { emptyList() },
        isOfflineConnectivityFailure: (Throwable) -> Boolean = { false },
        monotonicClockMs: () -> Long = { 1_000L },
        scheduleRefreshIntervalMs: Long = BROWSE_REMOTE_REFRESH_INTERVAL_MS,
        onOfflineFiltersUnavailable: (Boolean) -> Unit = {},
    ): BrowseContentCoordinator {
        return BrowseContentCoordinator(
            scope = scope,
            currentState = { state.value },
            updateState = { transform -> state.value = transform(state.value) },
            fetchCatalog = { filters, offset, limit -> RepositoryContent(fetchCatalog(filters, offset, limit), offlineFallback()) },
            searchCatalog = { _, _, _, _ -> RepositoryContent(emptyList(), offlineFallback()) },
            fetchSchedule = fetchSchedule,
            fetchOfflineEntries = fetchOfflineEntries,
            isOfflineConnectivityFailure = isOfflineConnectivityFailure,
            watchHistoryCoordinator = historyCoordinator(),
            requestCaptchaRetry = { _, _ -> false },
            historyUnavailableMessage = { "History unavailable" },
            monotonicClockMs = monotonicClockMs,
            scheduleRefreshIntervalMs = scheduleRefreshIntervalMs,
            onOfflineFiltersUnavailable = onOfflineFiltersUnavailable,
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
