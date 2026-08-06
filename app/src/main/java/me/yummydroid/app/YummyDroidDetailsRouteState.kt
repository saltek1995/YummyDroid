package me.yummydroid.app

import me.yummydroid.app.data.BrowseFilters

internal fun DetailsRouteCache.validProgressVideoGroup(): String? {
    val progressGroupKey = playbackProgress?.groupKey?.takeIf { it.isNotBlank() } ?: return null
    return progressGroupKey.takeIf { groupKey ->
        videos.readyListOrEmpty().any { it.groupKey == groupKey }
    }
}

internal fun YummyDroidUiState.withDetailsRouteCache(
    route: AppRoute.Details,
    navigationBackStack: List<NavigationEntry>,
    cachedRoute: DetailsRouteCache,
    homeSection: BrowseSection = this.homeSection,
    filters: BrowseFilters = this.filters,
    searchQuery: String = this.searchQuery,
): YummyDroidUiState {
    return copy(
        route = route,
        navigationBackStack = navigationBackStack,
        homeSection = homeSection,
        filters = filters,
        searchQuery = searchQuery,
        details = cachedRoute.details,
        videos = cachedRoute.videos,
        detailsExtras = cachedRoute.detailsExtras,
        animeMark = cachedRoute.animeMark,
        forcedOfflineMode = cachedRoute.forcedOfflineMode,
        selectedVideoGroup = cachedRoute.validProgressVideoGroup() ?: cachedRoute.selectedVideoGroup,
        playbackProgress = cachedRoute.playbackProgress,
        playbackHistory = cachedRoute.playbackHistory,
    )
}
