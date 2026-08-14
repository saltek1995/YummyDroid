package me.yummydroid.app

import android.view.KeyEvent
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.SiteNotification

// AppBackAction
internal enum class AppBackAction {
    CloseModal,
    HidePlayerControls,
    NavigateBack,
    ScrollRootHomeToTop,
    ReturnRootHomeToCatalog,
    ExitApp,
    Ignore,
}

// AppBackActionResolver
internal fun resolveAppBackAction(
    hasModal: Boolean,
    canHidePlayerControls: Boolean,
    canNavigateBack: Boolean,
    canScrollRootHomeToTop: Boolean,
    canReturnRootHomeToCatalog: Boolean = false,
    canExitApp: Boolean = false,
): AppBackAction {
    return when {
        hasModal -> AppBackAction.CloseModal
        canHidePlayerControls -> AppBackAction.HidePlayerControls
        canNavigateBack -> AppBackAction.NavigateBack
        canScrollRootHomeToTop -> AppBackAction.ScrollRootHomeToTop
        canReturnRootHomeToCatalog -> AppBackAction.ReturnRootHomeToCatalog
        canExitApp -> AppBackAction.ExitApp
        else -> AppBackAction.Ignore
    }
}

// AppNavigationReducer
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
    val retainedProgress = playbackProgress?.takeIf { progress -> progress.animeId == route.animeId }
    val retainedHistory = playbackHistory.takeIf { history ->
        history.any { progress -> progress.animeId == route.animeId }
    }.orEmpty()
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
        playbackProgress = retainedProgress,
        playbackHistory = retainedHistory,
        playbackHistoryLoading = shouldAwaitPlaybackHistoryForDetails(
            animeId = route.animeId,
            isAuthenticated = auth.profile != null,
            forcedOfflineMode = forcedOfflineMode,
            playbackProgress = retainedProgress,
            playbackHistory = retainedHistory,
        ),
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

// AppNavigationState
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

// InputAction
enum class InputAction {
    Up,
    Down,
    Left,
    Right,
    Confirm,
    Play,
    Pause,
    PlayPause,
    PreviousEpisode,
    NextEpisode,
    Back,
}

data class InputActionEvent(
    val action: InputAction,
    val repeatCount: Int = 0,
    val followsPointerInput: Boolean = false,
    val focusRecovery: Boolean = false,
) {
    val isRepeated: Boolean
        get() = repeatCount > 0
}

// InputActionMapping
internal fun inputActionForKeyCode(keyCode: Int): InputAction? {
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> InputAction.Up
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> InputAction.Down
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
        KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> InputAction.Left
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT,
        KeyEvent.KEYCODE_NAVIGATE_NEXT -> InputAction.Right
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
        KeyEvent.KEYCODE_CHANNEL_DOWN,
        KeyEvent.KEYCODE_BUTTON_L1 -> InputAction.PreviousEpisode
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
        KeyEvent.KEYCODE_CHANNEL_UP,
        KeyEvent.KEYCODE_BUTTON_R1 -> InputAction.NextEpisode
        KeyEvent.KEYCODE_MEDIA_PLAY -> InputAction.Play
        KeyEvent.KEYCODE_MEDIA_PAUSE -> InputAction.Pause
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK -> InputAction.PlayPause
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_NAVIGATE_IN -> InputAction.Confirm
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_NAVIGATE_OUT,
        KeyEvent.KEYCODE_BUTTON_B -> InputAction.Back
        else -> null
    }
}

// RootCatalogExitPolicy
internal fun canExitRootCatalog(
    isRootHome: Boolean,
    homeSection: BrowseSection,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    browsePagerSettledAtStateSection: Boolean = true,
): Boolean {
    if (!browsePagerSettledAtStateSection) return false
    if (!isRootHome || homeSection != BrowseSection.Catalog) return false
    return firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
}

// RootHomeBackToTopPolicy
internal fun canHandleRootHomeBackToTop(
    isRootHome: Boolean,
    homeSection: BrowseSection,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Boolean {
    if (!isRootHome || homeSection == BrowseSection.Downloads) return false
    return firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0
}

// RootHomeCatalogReturnPolicy
internal fun canReturnRootHomeToCatalog(
    isRootHome: Boolean,
    homeSection: BrowseSection,
    visualHomeSection: BrowseSection = homeSection,
): Boolean {
    return isRootHome &&
        (
            homeSection == BrowseSection.Schedule ||
                homeSection == BrowseSection.History ||
                visualHomeSection == BrowseSection.Schedule ||
                visualHomeSection == BrowseSection.History
        )
}

// SiteNotificationNavigation
internal fun SiteNotification.animeIdForOpen(): Long? {
    val fromUrl = Regex("""-(\d+)(?:[/#?]|$)""")
        .find(clickUrl)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
    return fromUrl ?: objectId.takeIf { it > 0L }
}
