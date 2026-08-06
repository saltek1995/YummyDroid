package me.yummydroid.app

import me.yummydroid.app.data.BrowseFilters

internal sealed interface NavigationEffect {
    data object LoadCatalog : NavigationEffect

    data class SearchCatalog(val query: String) : NavigationEffect

    data class EnsureBrowseSection(val section: BrowseSection) : NavigationEffect

    data class RefreshPlaybackProgress(val animeId: Long) : NavigationEffect

    data class LoadAnimeDetails(val animeId: Long) : NavigationEffect

    data class OpenAnime(val animeId: Long) : NavigationEffect

    data class PlayVideo(val route: AppRoute.Player) : NavigationEffect
}

internal data class NavigationTransition(
    val state: YummyDroidUiState,
    val cancelSearchRequests: Boolean = false,
    val effects: List<NavigationEffect> = emptyList(),
)

internal fun backNavigationTransition(
    state: YummyDroidUiState,
    catalogCacheForFilters: (BrowseFilters) -> CatalogRouteCache?,
    detailsCacheForAnime: (Long) -> DetailsRouteCache?,
): NavigationTransition {
    val previous = state.navigationBackStack.lastOrNull()
    if (previous != null) {
        return restoreNavigationEntryTransition(
            state = state,
            entry = previous,
            remainingBackStack = state.navigationBackStack.dropLast(1),
            cachedCatalogForEntry = catalogCacheForFilters(previous.filters),
            cachedDetailsForEntry = (previous.route as? AppRoute.Details)
                ?.animeId
                ?.let(detailsCacheForAnime),
        )
    }
    return rootBackNavigationTransition(state)
}

internal fun restoreNavigationEntryTransition(
    state: YummyDroidUiState,
    entry: NavigationEntry,
    remainingBackStack: List<NavigationEntry>,
    cachedCatalogForEntry: CatalogRouteCache?,
    cachedDetailsForEntry: DetailsRouteCache?,
    preserveHomeSection: Boolean = false,
): NavigationTransition {
    return when (val route = entry.route) {
        AppRoute.Home -> restoreHomeEntry(
            state = state,
            entry = entry,
            remainingBackStack = remainingBackStack,
            cachedCatalogForEntry = cachedCatalogForEntry,
            preserveHomeSection = preserveHomeSection,
        )

        is AppRoute.Details -> restoreDetailsEntry(
            state = state,
            entry = entry,
            route = route,
            remainingBackStack = remainingBackStack,
            cachedDetailsForEntry = cachedDetailsForEntry,
            preserveHomeSection = preserveHomeSection,
        )

        is AppRoute.Player -> restorePlayerEntry(
            state = state,
            entry = entry,
            route = route,
            remainingBackStack = remainingBackStack,
            preserveHomeSection = preserveHomeSection,
        )
    }
}

private fun rootBackNavigationTransition(state: YummyDroidUiState): NavigationTransition {
    return when (val route = state.route) {
        AppRoute.Home -> rootHomeBackTransition(state)
        is AppRoute.Details -> NavigationTransition(
            state = state.copy(
                route = AppRoute.Home,
                homeSection = state.restoredHomeSection(state.homeSection, preserveHomeSection = false),
            ),
        )

        is AppRoute.Player -> NavigationTransition(
            state = state,
            effects = listOf(NavigationEffect.OpenAnime(route.video.animeId)),
        )
    }
}

private fun rootHomeBackTransition(state: YummyDroidUiState): NavigationTransition {
    if (state.searchQuery.isNotBlank()) {
        return NavigationTransition(
            state = state.withClearedSearch(),
            cancelSearchRequests = true,
            effects = listOf(NavigationEffect.EnsureBrowseSection(BrowseSection.Catalog)),
        )
    }
    if (state.homeSection == BrowseSection.Downloads && state.forcedOfflineMode) {
        return NavigationTransition(state = state)
    }
    if (state.homeSection == BrowseSection.Catalog) {
        return NavigationTransition(state = state)
    }
    return NavigationTransition(
        state = state.withClearedSearch(homeSection = BrowseSection.Catalog),
        effects = listOf(NavigationEffect.EnsureBrowseSection(BrowseSection.Catalog)),
    )
}

private fun restoreHomeEntry(
    state: YummyDroidUiState,
    entry: NavigationEntry,
    remainingBackStack: List<NavigationEntry>,
    cachedCatalogForEntry: CatalogRouteCache?,
    preserveHomeSection: Boolean,
): NavigationTransition {
    val restorePlan = homeRouteRestorePlan(
        entry = entry,
        currentState = state,
        cachedCatalogForEntry = cachedCatalogForEntry,
        preserveHomeSection = preserveHomeSection,
    )
    return NavigationTransition(
        state = state.withRestoredHomeRoute(
            entry = entry,
            remainingBackStack = remainingBackStack,
            plan = restorePlan,
        ),
        cancelSearchRequests = true,
        effects = if (preserveHomeSection) emptyList() else restorePlan.followUpEffects(),
    )
}

private fun restoreDetailsEntry(
    state: YummyDroidUiState,
    entry: NavigationEntry,
    route: AppRoute.Details,
    remainingBackStack: List<NavigationEntry>,
    cachedDetailsForEntry: DetailsRouteCache?,
    preserveHomeSection: Boolean,
): NavigationTransition {
    val restoredHomeSection = state.restoredHomeSection(entry.homeSection, preserveHomeSection)
    if (cachedDetailsForEntry != null) {
        return NavigationTransition(
            state = state.withDetailsRouteCache(
                route = route,
                navigationBackStack = remainingBackStack,
                cachedRoute = cachedDetailsForEntry,
                homeSection = restoredHomeSection,
                filters = entry.filters,
                searchQuery = entry.searchQuery,
            ),
            effects = listOf(NavigationEffect.RefreshPlaybackProgress(route.animeId)),
        )
    }
    return NavigationTransition(
        state = state.withLoadingDetailsRoute(
            route = route,
            entry = entry,
            remainingBackStack = remainingBackStack,
            restoredHomeSection = restoredHomeSection,
        ),
        effects = listOf(NavigationEffect.LoadAnimeDetails(route.animeId)),
    )
}

private fun restorePlayerEntry(
    state: YummyDroidUiState,
    entry: NavigationEntry,
    route: AppRoute.Player,
    remainingBackStack: List<NavigationEntry>,
    preserveHomeSection: Boolean,
): NavigationTransition {
    return NavigationTransition(
        state = state.copy(
            route = route,
            navigationBackStack = remainingBackStack,
            homeSection = state.restoredHomeSection(entry.homeSection, preserveHomeSection),
            filters = entry.filters,
            searchQuery = entry.searchQuery,
            selectedVideoGroup = entry.selectedVideoGroup,
        ),
        effects = listOf(NavigationEffect.PlayVideo(route)),
    )
}

private fun YummyDroidUiState.withClearedSearch(
    homeSection: BrowseSection = this.homeSection,
): YummyDroidUiState {
    return copy(
        homeSection = homeSection,
        searchQuery = "",
        searchResults = LoadState.Ready(emptyList()),
        searchPaging = PagingUiState(canLoadMore = false),
    )
}

private fun YummyDroidUiState.withLoadingDetailsRoute(
    route: AppRoute.Details,
    entry: NavigationEntry,
    remainingBackStack: List<NavigationEntry>,
    restoredHomeSection: BrowseSection,
): YummyDroidUiState {
    return copy(
        route = route,
        navigationBackStack = remainingBackStack,
        homeSection = restoredHomeSection,
        filters = entry.filters,
        searchQuery = entry.searchQuery,
        selectedVideoGroup = entry.selectedVideoGroup,
        details = LoadState.Loading,
        videos = LoadState.Loading,
        detailsExtras = LoadState.Loading,
        animeMark = LoadState.Loading,
        playbackProgress = playbackProgress?.takeIf { progress -> progress.animeId == route.animeId },
        playbackHistory = playbackHistory.takeIf { history ->
            history.any { progress -> progress.animeId == route.animeId }
        }.orEmpty(),
    )
}

private fun YummyDroidUiState.restoredHomeSection(
    requested: BrowseSection,
    preserveHomeSection: Boolean,
): BrowseSection {
    return if (!preserveHomeSection && forcedOfflineMode && requested != BrowseSection.Downloads) {
        BrowseSection.Downloads
    } else {
        requested
    }
}

private fun HomeRouteRestorePlan.followUpEffects(): List<NavigationEffect> {
    return when (restoredHomeSection) {
        BrowseSection.Catalog -> when {
            shouldLoadCatalog -> listOf(NavigationEffect.LoadCatalog)
            shouldSearchNow -> listOf(NavigationEffect.SearchCatalog(restoredSearchQuery))
            else -> emptyList()
        }

        BrowseSection.Schedule -> listOf(NavigationEffect.EnsureBrowseSection(BrowseSection.Schedule))
        BrowseSection.History -> listOf(NavigationEffect.EnsureBrowseSection(BrowseSection.History))
        BrowseSection.Downloads -> listOf(NavigationEffect.EnsureBrowseSection(BrowseSection.Downloads))
    }
}
