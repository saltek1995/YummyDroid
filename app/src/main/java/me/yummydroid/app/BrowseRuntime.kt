package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.ScheduleAnime
import me.yummydroid.app.data.SearchHistoryStorage

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
    private val historyOperations: LatestStateOperationCoordinator = LatestStateOperationCoordinator(),
    private val requestCaptchaRetry: (Throwable, suspend () -> Unit) -> Boolean,
    private val historyUnavailableMessage: () -> String,
    private val monotonicClockMs: () -> Long,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val scheduleRefreshIntervalMs: Long = BROWSE_REMOTE_REFRESH_INTERVAL_MS,
) {
    private val catalogOperations = LatestStateOperationCoordinator()
    private val searchOperations = LatestStateOperationCoordinator()
    private val scheduleOperations = LatestStateOperationCoordinator()
    private val offlineOperations = LatestStateOperationCoordinator()
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
            catalogOperations.cancel()
        }
        updateState { it.withCatalogPageLoading(reset = reset, request = request) }
        catalogOperations.launchLatest(scope) { lease ->
            val filters = currentState().filters
            runSuspendCatching { fetchCatalog(filters, request.offset, pageSize) }
                .onSuccess { anime ->
                    if (lease.isCurrent) applyCatalogSuccess(filters, anime, reset)
                }
                .onFailure { throwable ->
                    if (lease.isCurrent) applyCatalogFailure(filters, throwable, reset)
                }
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

        if (reset) searchOperations.cancel()
        updateState { it.withSearchPageLoading(reset = reset, request = request) }
        searchOperations.launchLatest(scope) { lease ->
            val filters = currentState().filters
            runSuspendCatching { searchCatalog(query, filters, request.offset, pageSize) }
                .onSuccess { anime ->
                    if (!lease.isCurrent) return@onSuccess
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
                    if (!lease.isCurrent) return@onFailure
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
            scheduleOperations.cancel()
            updateState { it.copy(schedule = LoadState.Ready(emptyList())) }
            return
        }
        val plan = scheduleLoadPlan(
            force = force,
            cacheInitialized = scheduleCacheInitialized,
            hasReadySchedule = state.schedule is LoadState.Ready,
            loadActive = scheduleOperations.isActive,
            refreshDue = scheduleRefreshDue(),
        ) ?: return

        scheduleCacheInitialized = true
        if (plan.showLoading) updateState { it.copy(schedule = LoadState.Loading) }
        scheduleOperations.launchLatest(scope) { lease ->
            scheduleLastRemoteCheckAtMs = monotonicClockMs()
            runSuspendCatching(fetchSchedule)
                .onSuccess { schedule ->
                    if (lease.isCurrent) updateState { it.copy(schedule = LoadState.Ready(schedule)) }
                }
                .onFailure { throwable ->
                    if (!lease.isCurrent) return@onFailure
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
            loadActive = historyOperations.isActive,
        ) ?: return

        if (plan.showCachedSnapshot) updateState { it.copy(historyAnime = LoadState.Loading) }
        historyOperations.launchLatest(scope) { lease ->
            val resolution = watchHistoryCoordinator.load(
                plan = plan,
                canUseRemote = { currentState().canUseRemoteAccountData() },
                onCachedSnapshot = { anime ->
                    if (lease.isCurrent) updateState { it.copy(historyAnime = LoadState.Ready(anime)) }
                },
                shouldRetryRemoteFailure = { throwable ->
                    lease.isCurrent && requestCaptchaRetry(throwable) { loadHistory(force = true) }.also { retrying ->
                        if (retrying) updateState { it.copy(historyAnime = LoadState.Loading) }
                    }
                },
            ) ?: return@launchLatest
            if (!lease.isCurrent) return@launchLatest
            updateState { state -> state.withHistoryResolution(resolution, historyUnavailableMessage) }
        }
    }

    fun loadOfflineEntries() {
        updateState { it.copy(offlineEntries = LoadState.Loading) }
        offlineOperations.launchLatest(scope) { lease ->
            runSuspendCatching(fetchOfflineEntries)
                .onSuccess { entries ->
                    if (lease.isCurrent) updateState { it.copy(offlineEntries = LoadState.Ready(entries)) }
                }
                .onFailure { throwable ->
                    if (lease.isCurrent) updateState {
                        it.copy(offlineEntries = LoadState.Error(throwable.userMessage()))
                    }
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
        searchOperations.cancel()
    }

    fun clearCaches() {
        catalogOperations.cancel()
        searchOperations.cancel()
        scheduleOperations.cancel()
        historyOperations.cancel()
        offlineOperations.cancel()
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

// BrowseActionRuntime
internal class BrowseActionRuntime(
    private val scope: CoroutineScope,
    private val searchHistoryStorage: SearchHistoryStorage,
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val browseContentCoordinator: BrowseContentCoordinator,
    private val saveBrowseFilters: (BrowseFilters) -> AppSettings,
    private val offlineUnavailableMessage: () -> String,
    private val showNotice: (String) -> Unit,
) {
    private var searchDebounceJob: Job? = null
    private val searchHistoryOperations = SerialStateOperationCoordinator()

    fun restoreSearchHistory() {
        searchHistoryOperations.launch(scope) {
            val history = withContext(Dispatchers.IO) { searchHistoryStorage.read() }
            updateState { it.copy(searchHistory = history) }
        }
    }

    fun updateSearchQuery(query: String) {
        if (currentState().forcedOfflineMode) {
            showNotice(offlineUnavailableMessage())
            return
        }
        val state = currentState()
        val shouldResetFilters = query.isNotBlank()
        val searchFilters = if (shouldResetFilters) BrowseFilters() else state.filters
        val updatedSettings = if (shouldResetFilters && state.filters != searchFilters) {
            saveBrowseFilters(searchFilters)
        } else {
            state.settings
        }
        updateState { current ->
            current.copy(
                route = AppRoute.Home,
                navigationBackStack = current.navigationStackAfterOptionalPush(current.shouldPushHomeMutation()),
                homeSection = BrowseSection.Catalog,
                filters = searchFilters,
                settings = updatedSettings,
                searchQuery = query,
                searchResults = if (query.isBlank()) LoadState.Ready(emptyList()) else LoadState.Loading,
                searchPaging = PagingUiState(canLoadMore = query.isNotBlank()),
            )
        }

        searchDebounceJob?.cancel()
        browseContentCoordinator.cancelSearch()
        if (query.isBlank()) return

        searchDebounceJob = scope.launchAfterSearchDebounce {
            browseContentCoordinator.search(query, reset = true)
        }
    }

    fun submitSearchQuery(query: String) {
        recordSearchHistory(query)
    }

    fun selectSearchHistoryQuery(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return
        updateSearchQuery(normalizedQuery)
        recordSearchHistory(normalizedQuery)
    }

    fun updateFilters(filters: BrowseFilters) {
        if (currentState().forcedOfflineMode) {
            showNotice(offlineUnavailableMessage())
            return
        }
        applyBrowseFilters(filters)
    }

    fun resetFilters() {
        applyBrowseFilters(BrowseFilters())
    }

    fun cancelSearchRequests() {
        searchDebounceJob?.cancel()
        browseContentCoordinator.cancelSearch()
    }

    fun openLibraryFilter() {
        if (currentState().forcedOfflineMode) {
            showNotice(offlineUnavailableMessage())
            return
        }
        if (currentState().auth.profile == null) return
        val filters = BrowseFilters(userMarks = ALL_USER_MARK_FILTERS)
        val updatedSettings = saveBrowseFilters(filters)
        updateState { state ->
            state.withCatalogFilters(
                filters = filters,
                settings = updatedSettings,
                navigationBackStack = state.navigationStackAfterOptionalPush(state.shouldPushHomeMutation()),
            )
        }
        browseContentCoordinator.loadCatalog(reset = true)
    }

    private fun recordSearchHistory(query: String) {
        if (currentState().forcedOfflineMode) return
        searchHistoryOperations.launch(scope) {
            val history = withContext(Dispatchers.IO) { searchHistoryStorage.add(query) }
            updateState { it.copy(searchHistory = history) }
        }
    }

    private fun applyBrowseFilters(filters: BrowseFilters) {
        val updatedSettings = saveBrowseFilters(filters)
        updateState { state ->
            state.copy(
                filters = filters,
                settings = updatedSettings,
                route = AppRoute.Home,
                navigationBackStack = state.navigationStackAfterOptionalPush(state.shouldPushHomeMutation()),
                homeSection = BrowseSection.Catalog,
                homeFocusResetNonce = state.homeFocusResetNonce + 1L,
            )
        }
        browseContentCoordinator.reload()
    }

    private fun CoroutineScope.launchAfterSearchDebounce(block: suspend () -> Unit): Job {
        return launch {
            delay(SEARCH_DEBOUNCE_MS)
            block()
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
        val ALL_USER_MARK_FILTERS = setOf("0", "1", "2", "3", "4", "5")
    }
}

internal fun scheduleLoadPlan(
    force: Boolean,
    cacheInitialized: Boolean,
    hasReadySchedule: Boolean,
    loadActive: Boolean,
    refreshDue: Boolean,
): ScheduleLoadPlan? {
    if (loadActive && !force) return null
    val showLoading = scheduleLoadingRequired(force, cacheInitialized, hasReadySchedule)
    if (showLoading) return ScheduleLoadPlan(showLoading = true)
    return ScheduleLoadPlan(showLoading = false).takeIf { refreshDue }
}

private fun scheduleLoadingRequired(
    force: Boolean,
    cacheInitialized: Boolean,
    hasReadySchedule: Boolean,
): Boolean = force || !cacheInitialized || !hasReadySchedule

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
