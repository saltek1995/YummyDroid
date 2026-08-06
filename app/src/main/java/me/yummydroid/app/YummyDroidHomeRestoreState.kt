package me.yummydroid.app

internal data class HomeRouteRestorePlan(
    val restoredHomeSection: BrowseSection,
    val restoredSearchQuery: String,
    val cachedCatalog: CatalogRouteCache?,
    val canReuseCatalog: Boolean,
    val canReuseSearch: Boolean,
) {
    val shouldLoadCatalog: Boolean
        get() = restoredHomeSection == BrowseSection.Catalog &&
            restoredSearchQuery.isBlank() &&
            !canReuseCatalog &&
            cachedCatalog == null

    val shouldSearchNow: Boolean
        get() = restoredHomeSection == BrowseSection.Catalog &&
            restoredSearchQuery.isNotBlank() &&
            !canReuseSearch
}

internal fun homeRouteRestorePlan(
    entry: NavigationEntry,
    currentState: YummyDroidUiState,
    cachedCatalogForEntry: CatalogRouteCache?,
    preserveHomeSection: Boolean,
): HomeRouteRestorePlan {
    val restoredHomeSection = when {
        preserveHomeSection -> entry.homeSection
        currentState.forcedOfflineMode -> BrowseSection.Downloads
        else -> entry.homeSection
    }
    val restoredSearchQuery = if (restoredHomeSection == BrowseSection.Catalog) entry.searchQuery else ""
    val restoreCatalog = restoredHomeSection == BrowseSection.Catalog && restoredSearchQuery.isBlank()
    val restoreSearch = restoredHomeSection == BrowseSection.Catalog && restoredSearchQuery.isNotBlank()
    val cachedCatalog = cachedCatalogForEntry.takeIf { restoreCatalog }
    val canReuseCatalog = restoreCatalog &&
        currentState.filters == entry.filters &&
        currentState.featured is LoadState.Ready
    val canReuseSearch = restoreSearch &&
        currentState.filters == entry.filters &&
        currentState.searchQuery == restoredSearchQuery &&
        currentState.searchResults is LoadState.Ready
    return HomeRouteRestorePlan(
        restoredHomeSection = restoredHomeSection,
        restoredSearchQuery = restoredSearchQuery,
        cachedCatalog = cachedCatalog,
        canReuseCatalog = canReuseCatalog,
        canReuseSearch = canReuseSearch,
    )
}

internal fun YummyDroidUiState.withRestoredHomeRoute(
    entry: NavigationEntry,
    remainingBackStack: List<NavigationEntry>,
    plan: HomeRouteRestorePlan,
): YummyDroidUiState {
    return copy(
        route = AppRoute.Home,
        navigationBackStack = remainingBackStack,
        homeSection = plan.restoredHomeSection,
        filters = entry.filters,
        searchQuery = plan.restoredSearchQuery,
        searchResults = when {
            plan.restoredHomeSection != BrowseSection.Catalog || plan.restoredSearchQuery.isBlank() -> {
                LoadState.Ready(emptyList())
            }
            plan.canReuseSearch -> searchResults
            else -> LoadState.Loading
        },
        searchPaging = when {
            plan.restoredHomeSection != BrowseSection.Catalog || plan.restoredSearchQuery.isBlank() -> {
                PagingUiState(canLoadMore = false)
            }
            plan.canReuseSearch -> searchPaging
            else -> PagingUiState(canLoadMore = true)
        },
        featured = when {
            plan.canReuseCatalog -> featured
            plan.cachedCatalog != null -> LoadState.Ready(plan.cachedCatalog.animes)
            else -> featured
        },
        featuredPaging = when {
            plan.canReuseCatalog -> featuredPaging
            plan.cachedCatalog != null -> plan.cachedCatalog.paging
            else -> featuredPaging
        },
        forcedOfflineMode = if (forcedOfflineMode) true else plan.cachedCatalog?.forcedOfflineMode ?: false,
        selectedVideoGroup = entry.selectedVideoGroup,
    )
}
