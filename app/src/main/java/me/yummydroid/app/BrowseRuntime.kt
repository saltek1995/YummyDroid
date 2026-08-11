package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.ScheduleAnime

// AnimePagingState
data class PagingUiState(
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null,
)

internal fun PagingUiState.canRequestAnimePage(reset: Boolean): Boolean {
    return reset || (!isLoadingMore && canLoadMore)
}

internal fun animePageLoadOffset(items: LoadState<List<Anime>>, reset: Boolean): Int {
    return if (reset) 0 else items.readyListOrEmpty().size
}

internal fun animePageLoadingState(reset: Boolean, canLoadMoreOnReset: Boolean = true): PagingUiState {
    return if (reset) {
        PagingUiState(canLoadMore = canLoadMoreOnReset)
    } else {
        PagingUiState(isLoadingMore = true, error = null)
    }
}

internal fun animePageFailureState(
    currentPaging: PagingUiState,
    reset: Boolean,
    error: String,
): PagingUiState {
    return if (reset) {
        PagingUiState(canLoadMore = true)
    } else {
        currentPaging.copy(
            isLoadingMore = false,
            canLoadMore = true,
            error = error,
        )
    }
}

internal data class AnimePageMerge(
    val items: List<Anime>,
    val paging: PagingUiState,
)

internal fun mergeAnimePage(
    existing: List<Anime>,
    incoming: List<Anime>,
    reset: Boolean,
    pageSize: Int,
): AnimePageMerge {
    val base = if (reset) emptyList() else existing
    val merged = (base + incoming).distinctBy { it.id }
    return AnimePageMerge(
        items = merged,
        paging = PagingUiState(
            isLoadingMore = false,
            canLoadMore = incoming.size >= pageSize && merged.size > base.size,
        ),
    )
}

// BrowseContentCoordinator
internal data class ScheduleLoadPlan(
    val showLoading: Boolean,
)

internal class BrowseContentCoordinator(
    private val scope: CoroutineScope,
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val fetchCatalog: suspend (BrowseFilters, Int, Int) -> List<Anime>,
    private val searchCatalog: suspend (String, BrowseFilters, Int, Int) -> List<Anime>,
    private val fetchSchedule: suspend () -> List<ScheduleAnime>,
    private val fetchOfflineEntries: suspend () -> List<OfflineAnimeEntry>,
    private val isOfflineFallbackActive: () -> Boolean,
    private val isOfflineConnectivityFailure: (Throwable) -> Boolean,
    private val watchHistoryCoordinator: WatchHistoryCoordinator,
    private val requestCaptchaRetry: (Throwable, suspend () -> Unit) -> Boolean,
    private val historyUnavailableMessage: () -> String,
    private val monotonicClockMs: () -> Long,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val scheduleRefreshIntervalMs: Long = BROWSE_REMOTE_REFRESH_INTERVAL_MS,
) {
    private var catalogLoadJob: Job? = null
    private var searchLoadJob: Job? = null
    private var scheduleLoadJob: Job? = null
    private var historyLoadJob: Job? = null
    private var offlineLoadJob: Job? = null
    private val catalogPageCache = mutableMapOf<BrowseFilters, CatalogRouteCache>()
    private var catalogCacheInitialized = false
    private var scheduleCacheInitialized = false
    private var scheduleLastRemoteCheckAtMs = 0L

    fun loadCatalog(reset: Boolean = true) {
        val state = currentState()
        val request = animePageRequest(
            items = state.featured,
            paging = state.featuredPaging,
            reset = reset,
        ) ?: return

        if (reset) {
            catalogCacheInitialized = true
            catalogLoadJob?.cancel()
        }
        updateState { it.withCatalogPageLoading(reset = reset, request = request) }
        catalogLoadJob = scope.launch {
            val filters = currentState().filters
            runSuspendCatching { fetchCatalog(filters, request.offset, pageSize) }
                .onSuccess { anime -> applyCatalogSuccess(filters, anime, reset) }
                .onFailure { throwable -> applyCatalogFailure(filters, throwable, reset) }
        }
    }

    fun search(query: String, reset: Boolean = true) {
        val state = currentState()
        val request = animePageRequest(
            items = state.searchResults,
            paging = state.searchPaging,
            reset = reset,
            canLoadMoreOnReset = query.isNotBlank(),
        ) ?: return

        if (reset) searchLoadJob?.cancel()
        updateState { it.withSearchPageLoading(reset = reset, request = request) }
        searchLoadJob = scope.launch {
            val filters = currentState().filters
            runSuspendCatching { searchCatalog(query, filters, request.offset, pageSize) }
                .onSuccess { anime ->
                    val forcedOfflineMode = isOfflineFallbackActive()
                    updateState { state ->
                        reduceSearchPageSuccess(
                            state = state,
                            query = query,
                            requestedFilters = filters,
                            incoming = anime,
                            reset = reset,
                            pageSize = pageSize,
                            forcedOfflineMode = forcedOfflineMode,
                        )
                    }
                }
                .onFailure { throwable ->
                    updateState { state ->
                        reduceSearchPageFailure(
                            state = state,
                            query = query,
                            requestedFilters = filters,
                            reset = reset,
                            error = throwable.userMessage(),
                        )
                    }
                }
        }
    }

    fun loadSchedule(force: Boolean = true) {
        val state = currentState()
        if (state.forcedOfflineMode) {
            scheduleLoadJob?.cancel()
            updateState { it.copy(schedule = LoadState.Ready(emptyList())) }
            return
        }
        val plan = scheduleLoadPlan(
            force = force,
            cacheInitialized = scheduleCacheInitialized,
            hasReadySchedule = state.schedule is LoadState.Ready,
            loadActive = scheduleLoadJob?.isActive == true,
            refreshDue = scheduleRefreshDue(),
        ) ?: return

        scheduleCacheInitialized = true
        scheduleLoadJob?.cancel()
        if (plan.showLoading) updateState { it.copy(schedule = LoadState.Loading) }
        scheduleLoadJob = scope.launch {
            scheduleLastRemoteCheckAtMs = monotonicClockMs()
            runSuspendCatching(fetchSchedule)
                .onSuccess { schedule ->
                    updateState { it.copy(schedule = LoadState.Ready(schedule)) }
                }
                .onFailure { throwable ->
                    updateState { current ->
                        if (!plan.showLoading && current.schedule is LoadState.Ready) {
                            current
                        } else {
                            current.copy(schedule = LoadState.Error(throwable.userMessage()))
                        }
                    }
                }
        }
    }

    fun loadHistory(force: Boolean = true) {
        val state = currentState()
        val plan = watchHistoryCoordinator.beginRefresh(
            force = force,
            hasReadyHistory = state.historyAnime is LoadState.Ready,
            canUseRemote = state.canUseRemoteAccountData(),
            loadActive = historyLoadJob?.isActive == true,
        ) ?: return

        historyLoadJob?.cancel()
        if (plan.showCachedSnapshot) updateState { it.copy(historyAnime = LoadState.Loading) }
        historyLoadJob = scope.launch {
            val resolution = watchHistoryCoordinator.load(
                plan = plan,
                canUseRemote = { currentState().canUseRemoteAccountData() },
                onCachedSnapshot = { anime ->
                    updateState { it.copy(historyAnime = LoadState.Ready(anime)) }
                },
                shouldRetryRemoteFailure = { throwable ->
                    requestCaptchaRetry(throwable) { loadHistory(force = true) }.also { retrying ->
                        if (retrying) updateState { it.copy(historyAnime = LoadState.Loading) }
                    }
                },
            ) ?: return@launch
            updateState { state -> state.withHistoryResolution(resolution, historyUnavailableMessage) }
        }
    }

    fun loadOfflineEntries() {
        offlineLoadJob?.cancel()
        updateState { it.copy(offlineEntries = LoadState.Loading) }
        offlineLoadJob = scope.launch {
            runSuspendCatching(fetchOfflineEntries)
                .onSuccess { entries ->
                    updateState { it.copy(offlineEntries = LoadState.Ready(entries)) }
                }
                .onFailure { throwable ->
                    updateState { it.copy(offlineEntries = LoadState.Error(throwable.userMessage())) }
                }
        }
    }

    fun ensureLoaded(section: BrowseSection) {
        if (currentState().forcedOfflineMode && section != BrowseSection.Downloads) {
            loadOfflineEntries()
            return
        }
        when (section) {
            BrowseSection.Catalog -> if (!catalogCacheInitialized) loadCatalog(reset = true)
            BrowseSection.Schedule -> loadSchedule(force = false)
            BrowseSection.History -> loadHistory(force = false)
            BrowseSection.Downloads -> loadOfflineEntries()
        }
    }

    fun reload() {
        val state = currentState()
        if (state.forcedOfflineMode) {
            updateState(YummyDroidUiState::withOfflineBrowseHome)
            loadOfflineEntries()
            return
        }
        when (state.homeSection) {
            BrowseSection.Catalog -> {
                if (state.searchQuery.isBlank()) loadCatalog(reset = true) else search(state.searchQuery, reset = true)
            }
            BrowseSection.Schedule -> loadSchedule(force = true)
            BrowseSection.History -> loadHistory(force = true)
            BrowseSection.Downloads -> loadOfflineEntries()
        }
    }

    fun loadMore() {
        val state = currentState()
        if (state.route != AppRoute.Home || state.homeSection != BrowseSection.Catalog) return
        if (state.searchQuery.isBlank()) loadCatalog(reset = false) else search(state.searchQuery, reset = false)
    }

    fun cancelSearch() {
        searchLoadJob?.cancel()
    }

    fun clearCaches() {
        catalogLoadJob?.cancel()
        searchLoadJob?.cancel()
        scheduleLoadJob?.cancel()
        historyLoadJob?.cancel()
        offlineLoadJob?.cancel()
        catalogPageCache.clear()
        catalogCacheInitialized = false
        scheduleCacheInitialized = false
        scheduleLastRemoteCheckAtMs = 0L
        watchHistoryCoordinator.resetRefreshState()
    }

    fun catalogCache(filters: BrowseFilters): CatalogRouteCache? = catalogPageCache[filters]

    private fun applyCatalogSuccess(filters: BrowseFilters, anime: List<Anime>, reset: Boolean) {
        val forcedOfflineMode = isOfflineFallbackActive()
        var cacheUpdate: CatalogRouteCache? = null
        updateState { state ->
            val update = reduceCatalogPageSuccess(
                state = state,
                requestedFilters = filters,
                incoming = anime,
                reset = reset,
                pageSize = pageSize,
                forcedOfflineMode = forcedOfflineMode,
            )
            cacheUpdate = update?.cache
            update?.state ?: state
        }
        cacheUpdate?.let { catalogPageCache[filters] = it }
    }

    private fun applyCatalogFailure(filters: BrowseFilters, throwable: Throwable, reset: Boolean) {
        val offlineFailure = isOfflineConnectivityFailure(throwable)
        updateState { state ->
            reduceCatalogPageFailure(
                state = state,
                requestedFilters = filters,
                reset = reset,
                offlineFailure = offlineFailure,
                error = throwable.userMessage(),
            )
        }
        if (offlineFailure) loadOfflineEntries()
    }

    private fun scheduleRefreshDue(): Boolean {
        return scheduleLastRemoteCheckAtMs == 0L ||
            monotonicClockMs() - scheduleLastRemoteCheckAtMs >= scheduleRefreshIntervalMs
    }

    private suspend fun <T> runSuspendCatching(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            Result.failure(throwable)
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 36
    }
}

internal fun scheduleLoadPlan(
    force: Boolean,
    cacheInitialized: Boolean,
    hasReadySchedule: Boolean,
    loadActive: Boolean,
    refreshDue: Boolean,
): ScheduleLoadPlan? {
    val shouldRefresh = force || !cacheInitialized || !hasReadySchedule || refreshDue
    if (!shouldRefresh || (!force && loadActive)) return null
    return ScheduleLoadPlan(showLoading = force || !cacheInitialized || !hasReadySchedule)
}

private fun YummyDroidUiState.canUseRemoteAccountData(): Boolean {
    return !forcedOfflineMode && auth.profile != null
}

private fun YummyDroidUiState.withHistoryResolution(
    resolution: WatchHistoryResolution,
    unavailableMessage: () -> String,
): YummyDroidUiState {
    return when (resolution) {
        is WatchHistoryResolution.Failed -> copy(
            historyAnime = LoadState.Error(
                resolution.cause.userMessage().ifBlank(unavailableMessage),
            ),
        )
        is WatchHistoryResolution.Ready -> copy(historyAnime = LoadState.Ready(resolution.anime))
    }
}

private fun YummyDroidUiState.withOfflineBrowseHome(): YummyDroidUiState {
    return copy(
        route = AppRoute.Home,
        homeSection = BrowseSection.Downloads,
        searchQuery = "",
        searchResults = LoadState.Ready(emptyList()),
        searchPaging = PagingUiState(canLoadMore = false),
    )
}

// BrowsePagingReducer
internal data class AnimePageRequest(
    val offset: Int,
    val loadingPaging: PagingUiState,
)

internal data class CatalogPageUpdate(
    val state: YummyDroidUiState,
    val cache: CatalogRouteCache,
)

internal fun animePageRequest(
    items: LoadState<List<Anime>>,
    paging: PagingUiState,
    reset: Boolean,
    canLoadMoreOnReset: Boolean = true,
): AnimePageRequest? {
    if (!paging.canRequestAnimePage(reset)) return null
    return AnimePageRequest(
        offset = animePageLoadOffset(items, reset),
        loadingPaging = animePageLoadingState(
            reset = reset,
            canLoadMoreOnReset = canLoadMoreOnReset,
        ),
    )
}

internal fun YummyDroidUiState.withCatalogPageLoading(
    reset: Boolean,
    request: AnimePageRequest,
): YummyDroidUiState {
    return if (reset) {
        copy(
            featured = LoadState.Loading,
            featuredPaging = request.loadingPaging,
        )
    } else {
        copy(featuredPaging = request.loadingPaging)
    }
}

internal fun YummyDroidUiState.withSearchPageLoading(
    reset: Boolean,
    request: AnimePageRequest,
): YummyDroidUiState {
    return if (reset) {
        copy(
            searchResults = LoadState.Loading,
            searchPaging = request.loadingPaging,
        )
    } else {
        copy(searchPaging = request.loadingPaging)
    }
}

internal fun reduceCatalogPageSuccess(
    state: YummyDroidUiState,
    requestedFilters: BrowseFilters,
    incoming: List<Anime>,
    reset: Boolean,
    pageSize: Int,
    forcedOfflineMode: Boolean,
): CatalogPageUpdate? {
    if (!state.acceptsCatalogPage(requestedFilters, allowInactiveCatalog = forcedOfflineMode)) return null

    val page = mergeAnimePage(
        existing = state.featured.readyListOrEmpty(),
        incoming = incoming,
        reset = reset,
        pageSize = pageSize,
    )
    val cache = CatalogRouteCache(
        animes = page.items,
        paging = page.paging,
        forcedOfflineMode = forcedOfflineMode,
    )
    return CatalogPageUpdate(
        state = state.copy(
            featured = LoadState.Ready(page.items),
            forcedOfflineMode = forcedOfflineMode,
            homeSection = if (forcedOfflineMode) BrowseSection.Downloads else state.homeSection,
            searchQuery = if (forcedOfflineMode) "" else state.searchQuery,
            searchResults = if (forcedOfflineMode) LoadState.Ready(emptyList()) else state.searchResults,
            searchPaging = if (forcedOfflineMode) PagingUiState(canLoadMore = false) else state.searchPaging,
            featuredPaging = page.paging,
        ),
        cache = cache,
    )
}

internal fun reduceCatalogPageFailure(
    state: YummyDroidUiState,
    requestedFilters: BrowseFilters,
    reset: Boolean,
    offlineFailure: Boolean,
    error: String,
): YummyDroidUiState {
    if (!state.acceptsCatalogPage(requestedFilters, allowInactiveCatalog = offlineFailure)) return state
    if (reset && offlineFailure) {
        return state.copy(
            featured = LoadState.Ready(emptyList()),
            forcedOfflineMode = true,
            homeSection = BrowseSection.Downloads,
            searchQuery = "",
            searchResults = LoadState.Ready(emptyList()),
            searchPaging = PagingUiState(canLoadMore = false),
            featuredPaging = PagingUiState(canLoadMore = false),
        )
    }
    if (reset) {
        return state.copy(
            featured = LoadState.Error(error),
            forcedOfflineMode = false,
            featuredPaging = animePageFailureState(
                currentPaging = state.featuredPaging,
                reset = true,
                error = error,
            ),
        )
    }
    return state.copy(
        featuredPaging = animePageFailureState(
            currentPaging = state.featuredPaging,
            reset = false,
            error = error,
        ),
    )
}

internal fun reduceSearchPageSuccess(
    state: YummyDroidUiState,
    query: String,
    requestedFilters: BrowseFilters,
    incoming: List<Anime>,
    reset: Boolean,
    pageSize: Int,
    forcedOfflineMode: Boolean,
): YummyDroidUiState {
    if (!state.acceptsSearchPage(query, requestedFilters)) return state
    val page = mergeAnimePage(
        existing = state.searchResults.readyListOrEmpty(),
        incoming = incoming,
        reset = reset,
        pageSize = pageSize,
    )
    return state.copy(
        searchResults = LoadState.Ready(page.items),
        forcedOfflineMode = forcedOfflineMode,
        searchPaging = page.paging,
    )
}

internal fun reduceSearchPageFailure(
    state: YummyDroidUiState,
    query: String,
    requestedFilters: BrowseFilters,
    reset: Boolean,
    error: String,
): YummyDroidUiState {
    if (!state.acceptsSearchPage(query, requestedFilters)) return state
    return if (reset) {
        state.copy(
            searchResults = LoadState.Error(error),
            forcedOfflineMode = false,
            searchPaging = animePageFailureState(
                currentPaging = state.searchPaging,
                reset = true,
                error = error,
            ),
        )
    } else {
        state.copy(
            searchPaging = animePageFailureState(
                currentPaging = state.searchPaging,
                reset = false,
                error = error,
            ),
        )
    }
}

private fun YummyDroidUiState.acceptsCatalogPage(
    requestedFilters: BrowseFilters,
    allowInactiveCatalog: Boolean,
): Boolean {
    if (route != AppRoute.Home) return false
    if (filters != requestedFilters) return false
    if (searchQuery.isNotBlank()) return false
    return homeSection == BrowseSection.Catalog || allowInactiveCatalog
}

private fun YummyDroidUiState.acceptsSearchPage(
    query: String,
    requestedFilters: BrowseFilters,
): Boolean {
    return searchQuery == query && filters == requestedFilters
}
