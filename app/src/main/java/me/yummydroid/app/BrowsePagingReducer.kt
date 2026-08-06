package me.yummydroid.app

import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.BrowseFilters

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
