package me.yummydroid.app

// NavigationStateRuntime
internal class NavigationStateRuntime(
    private val currentState: () -> YummyDroidUiState,
    private val publishState: (YummyDroidUiState) -> Unit,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val browseActionRuntime: BrowseActionRuntime,
    private val browseContentCoordinator: BrowseContentCoordinator,
    private val cachedDetailsRoute: (Long) -> DetailsRouteCache?,
    private val cacheCurrentDetailsRouteState: () -> Unit,
    private val refreshPlaybackProgressSnapshot: (Long) -> Unit,
    private val loadAnimeDetails: (Long) -> Unit,
    private val openAnime: (animeId: Long, pushCurrent: Boolean) -> Unit,
    private val playRouteVideo: (AppRoute.Player) -> Unit,
) {
    fun selectBrowseSection(section: BrowseSection) {
        val targetSection = if (currentState().forcedOfflineMode) BrowseSection.Downloads else section
        updateState { state ->
            state.copy(
                route = AppRoute.Home,
                navigationBackStack = state.navigationStackAfterOptionalPush(state.shouldPushHomeMutation()),
                homeSection = targetSection,
                searchQuery = if (targetSection == BrowseSection.Catalog) state.searchQuery else "",
                searchResults = if (targetSection == BrowseSection.Catalog) {
                    state.searchResults
                } else {
                    LoadState.Ready(emptyList())
                },
                searchPaging = if (targetSection == BrowseSection.Catalog) {
                    state.searchPaging
                } else {
                    PagingUiState(canLoadMore = false)
                },
            )
        }
        browseContentCoordinator.ensureLoaded(targetSection)
    }

    fun navigateBack() {
        cacheCurrentDetailsRouteState()
        applyNavigationTransition { state ->
            backNavigationTransition(
                state = state,
                catalogCacheForFilters = browseContentCoordinator::catalogCache,
                detailsCacheForAnime = cachedDetailsRoute,
            )
        }
    }

    fun restoreNavigationEntry(
        entry: NavigationEntry,
        remainingBackStack: List<NavigationEntry>,
        preserveHomeSection: Boolean = false,
    ) {
        applyNavigationTransition { state ->
            restoreNavigationEntryTransition(
                state = state,
                entry = entry,
                remainingBackStack = remainingBackStack,
                cachedCatalogForEntry = browseContentCoordinator.catalogCache(entry.filters),
                cachedDetailsForEntry = (entry.route as? AppRoute.Details)
                    ?.let { route -> cachedDetailsRoute(route.animeId) },
                preserveHomeSection = preserveHomeSection,
            )
        }
    }

    private fun applyNavigationTransition(
        transitionFor: (YummyDroidUiState) -> NavigationTransition,
    ) {
        val transition = transitionFor(currentState())
        if (transition.cancelSearchRequests) {
            browseActionRuntime.cancelSearchRequests()
        }
        publishState(transition.state)
        transition.effects.forEach(::applyNavigationEffect)
    }

    private fun applyNavigationEffect(effect: NavigationEffect) {
        when (effect) {
            NavigationEffect.LoadCatalog -> browseContentCoordinator.loadCatalog(reset = true)
            is NavigationEffect.SearchCatalog -> browseContentCoordinator.search(effect.query, reset = true)
            is NavigationEffect.EnsureBrowseSection -> browseContentCoordinator.ensureLoaded(effect.section)
            is NavigationEffect.RefreshPlaybackProgress -> refreshPlaybackProgressSnapshot(effect.animeId)
            is NavigationEffect.LoadAnimeDetails -> loadAnimeDetails(effect.animeId)
            is NavigationEffect.OpenAnime -> openAnime(effect.animeId, false)
            is NavigationEffect.PlayVideo -> playRouteVideo(effect.route)
        }
    }
}
