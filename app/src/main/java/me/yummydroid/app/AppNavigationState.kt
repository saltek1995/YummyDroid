package me.yummydroid.app

import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.BrowseFilters

internal fun YummyDroidUiState.navigationEntry(): NavigationEntry {
    return NavigationEntry(
        route = route,
        homeSection = homeSection,
        filters = filters,
        searchQuery = searchQuery,
        selectedVideoGroup = selectedVideoGroup,
    )
}

internal fun YummyDroidUiState.navigationStackAfterOptionalPush(push: Boolean): List<NavigationEntry> {
    return if (push) {
        navigationBackStack.withNavigationEntry(navigationEntry())
    } else {
        navigationBackStack
    }
}

internal fun YummyDroidUiState.navigationStackForDetailsFilter(sourceAnimeId: Long? = null): List<NavigationEntry> {
    val detailsRoute = sourceAnimeId
        ?.takeIf { it > 0L }
        ?.let(AppRoute::Details)
        ?: when (val currentRoute = route) {
            is AppRoute.Details -> currentRoute
            is AppRoute.Player -> AppRoute.Details(currentRoute.video.animeId)
                .takeIf { currentRoute.video.animeId > 0L }
            AppRoute.Home -> details.readyDataOrNull()
                ?.id
                ?.takeIf { it > 0L }
                ?.let(AppRoute::Details)
    } ?: return navigationStackAfterOptionalPush(shouldPushHomeMutation())

    return navigationBackStack.withNavigationEntry(
        NavigationEntry(
            route = detailsRoute,
            homeSection = homeSection,
            filters = filters,
            searchQuery = searchQuery,
            selectedVideoGroup = selectedVideoGroup,
        ),
    )
}

internal fun YummyDroidUiState.shouldPushHomeMutation(): Boolean {
    return route != AppRoute.Home
}

internal fun YummyDroidUiState.withCatalogFilters(
    filters: BrowseFilters,
    settings: AppSettings,
    navigationBackStack: List<NavigationEntry>,
): YummyDroidUiState {
    return copy(
        route = AppRoute.Home,
        navigationBackStack = navigationBackStack,
        homeSection = BrowseSection.Catalog,
        filters = filters,
        settings = settings,
        homeFocusResetNonce = homeFocusResetNonce + 1L,
        searchQuery = "",
        searchResults = LoadState.Ready(emptyList()),
        searchPaging = PagingUiState(canLoadMore = false),
    )
}

internal fun List<NavigationEntry>.withNavigationEntry(entry: NavigationEntry): List<NavigationEntry> {
    return if (lastOrNull() == entry) {
        this
    } else {
        (this + entry).takeLast(MAX_NAVIGATION_STACK)
    }
}

