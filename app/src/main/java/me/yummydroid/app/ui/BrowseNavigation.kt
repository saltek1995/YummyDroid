package me.yummydroid.app.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.canExitRootCatalog
import me.yummydroid.app.canHandleRootHomeBackToTop
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.PosterCardSize

// BrowseCatalogDialogRuntime
internal class BrowseCatalogDialogRuntime {
    var searchDialogOpen by mutableStateOf(false)
    var filtersDialogOpen by mutableStateOf(false)
    var searchKeyboardBackConsumed by mutableStateOf(false)
    var searchKeyboardDismissRequest by mutableLongStateOf(0L)
    var searchInputActionRequest by mutableLongStateOf(0L)
    var searchInputAction by mutableStateOf<InputAction?>(null)

    fun openSearch() {
        filtersDialogOpen = false
        searchDialogOpen = true
    }

    fun openFilters() {
        searchDialogOpen = false
        filtersDialogOpen = true
    }

    fun closeCatalogDialogs() {
        filtersDialogOpen = false
        searchDialogOpen = false
    }

    fun resetSearchInputState() {
        searchKeyboardBackConsumed = false
        searchInputAction = null
        searchInputActionRequest = 0L
    }

    fun handleInputAction(action: InputAction): Boolean {
        return when {
            searchDialogOpen -> handleSearchInputAction(action)
            filtersDialogOpen && action == InputAction.Back -> {
                filtersDialogOpen = false
                true
            }
            else -> false
        }
    }

    private fun handleSearchInputAction(action: InputAction): Boolean {
        return when (action) {
            InputAction.Back -> {
                if (searchKeyboardBackConsumed) {
                    searchDialogOpen = false
                } else {
                    searchKeyboardBackConsumed = true
                    searchKeyboardDismissRequest += 1L
                }
                true
            }
            InputAction.Up,
            InputAction.Down,
            InputAction.Left,
            InputAction.Right,
            InputAction.Confirm -> {
                searchKeyboardBackConsumed = true
                searchInputAction = action
                searchInputActionRequest += 1L
                true
            }
            InputAction.Play,
            InputAction.Pause,
            InputAction.PlayPause,
            InputAction.PreviousEpisode,
            InputAction.NextEpisode -> false
        }
    }
}

@Composable
internal fun rememberBrowseCatalogDialogRuntime(
    catalogActionsEnabled: Boolean,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
): BrowseCatalogDialogRuntime {
    val runtime = remember { BrowseCatalogDialogRuntime() }
    LaunchedEffect(catalogActionsEnabled) {
        if (!catalogActionsEnabled) runtime.closeCatalogDialogs()
    }
    LaunchedEffect(runtime.searchDialogOpen) {
        if (runtime.searchDialogOpen) runtime.resetSearchInputState()
    }
    val modalInputActionHandler by rememberUpdatedState { action: InputAction ->
        runtime.handleInputAction(action)
    }
    DisposableEffect(
        runtime.searchDialogOpen,
        runtime.filtersDialogOpen,
        onRegisterModalInputActionHandler,
    ) {
        if (runtime.searchDialogOpen || runtime.filtersDialogOpen) {
            onRegisterModalInputActionHandler { action -> modalInputActionHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }
    return runtime
}

// BrowseFocusRequestJob
internal class FocusRequestJobRef(
    private val uiControls: UiControlCoordinator,
    private val awaitFrame: suspend () -> Unit = { withFrameNanos { } },
) {
    private var pendingIndex: Int? = null

    fun cancel() {
        pendingIndex = null
        uiControls.cancel(this, UiControlOperation.NavigationSerial)
    }

    fun requestFocusWhenReady(
        index: Int,
        focusScope: CoroutineScope,
        requestItemFocus: (Int) -> Boolean,
    ) {
        pendingIndex = index
        uiControls.launch(focusScope, this, UiControlOperation.NavigationSerial) {
            while (pendingIndex != null) {
                val target = pendingIndex ?: break
                if (focusTargetWhilePending(target, requestItemFocus) && pendingIndex == target) {
                    pendingIndex = null
                }
            }
        }
    }

    private suspend fun focusTargetWhilePending(
        target: Int,
        requestItemFocus: (Int) -> Boolean,
    ): Boolean {
        repeat(8) {
            awaitFrame()
            if (pendingIndex != target) return false
            if (requestItemFocus(target)) return true
        }
        return true
    }
}

// BrowseFocusStore
internal class BrowseFocusStore {
    private var catalogFocusedIndex: Int = -1
    private var historyFocusedIndex: Int = -1
    private var scheduleFocusedIndex: Int = 0

    fun focusedIndex(section: BrowseSection): Int = when (section) {
        BrowseSection.Catalog -> catalogFocusedIndex
        BrowseSection.Schedule -> scheduleFocusedIndex
        BrowseSection.History -> historyFocusedIndex
        BrowseSection.Downloads -> -1
    }

    fun setFocusedIndex(section: BrowseSection, index: Int) {
        when (section) {
            BrowseSection.Catalog -> catalogFocusedIndex = index
            BrowseSection.Schedule -> scheduleFocusedIndex = index
            BrowseSection.History -> historyFocusedIndex = index
            BrowseSection.Downloads -> Unit
        }
    }
}

// BrowseRootTopBarPolicy
internal fun browseRootTopBarVisibilityProgress(
    section: BrowseSection,
    collapseDistancePx: Float,
    leadingScrollAnchorItems: Int,
    catalogGridState: LazyGridState,
    scheduleGridState: LazyGridState,
    historyGridState: LazyGridState,
): Float = when (section) {
    BrowseSection.Catalog -> catalogGridState.topBarScrollProgress(collapseDistancePx)
    BrowseSection.Schedule -> scheduleGridState.topBarScrollProgress(collapseDistancePx, leadingScrollAnchorItems)
    BrowseSection.History -> historyGridState.topBarScrollProgress(collapseDistancePx)
    BrowseSection.Downloads -> 1f
}

private fun LazyGridState.topBarScrollProgress(
    collapseDistancePx: Float,
    leadingScrollAnchorItems: Int = 0,
): Float = browseTopBarVisibilityProgress(
    firstVisibleItemIndex = firstVisibleItemIndex,
    firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
    canScrollBackward = canScrollBackward,
    collapseDistancePx = collapseDistancePx,
    leadingScrollAnchorItems = leadingScrollAnchorItems,
)

internal fun browseTopBarVisibilityProgress(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    canScrollBackward: Boolean,
    collapseDistancePx: Float,
    leadingScrollAnchorItems: Int = 0,
): Float {
    if (collapseDistancePx <= 0f) return if (!canScrollBackward) 1f else 0f
    val anchorIndex = leadingScrollAnchorItems.coerceAtLeast(0)
    val consumedPx = when {
        firstVisibleItemIndex < anchorIndex -> 0f
        firstVisibleItemIndex == anchorIndex -> firstVisibleItemScrollOffset.toFloat()
        else -> collapseDistancePx
    }
    return (1f - consumedPx / collapseDistancePx).coerceIn(0f, 1f)
}

internal fun LazyGridState.canHandleBrowseRootBackToTop(section: BrowseSection): Boolean {
    return canScrollBackward ||
        canHandleRootHomeBackToTop(
            isRootHome = true,
            homeSection = section,
            firstVisibleItemIndex = firstVisibleItemIndex,
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
        )
}

// BrowseRootUiCoordinator
@Composable
internal fun rememberBrowseRootUiCoordinator(
    catalogGridState: LazyGridState,
    scheduleGridState: LazyGridState,
    historyGridState: LazyGridState,
): BrowseRootUiCoordinator {
    val focusStore = remember { BrowseFocusStore() }
    return remember(catalogGridState, scheduleGridState, historyGridState, focusStore) {
        BrowseRootUiCoordinator(catalogGridState, scheduleGridState, historyGridState, focusStore)
    }
}

internal class BrowseRootUiCoordinator(
    val catalogGridState: LazyGridState,
    val scheduleGridState: LazyGridState,
    val historyGridState: LazyGridState,
    private val focusStore: BrowseFocusStore,
) {
    fun gridState(section: BrowseSection): LazyGridState? = when (section) {
        BrowseSection.Catalog -> catalogGridState
        BrowseSection.Schedule -> scheduleGridState
        BrowseSection.History -> historyGridState
        BrowseSection.Downloads -> null
    }

    fun topBarVisible(section: BrowseSection, leadingScrollAnchorItems: Int = 0): Boolean {
        return topBarVisibilityProgress(section, 1f, leadingScrollAnchorItems) > 0.999f
    }

    fun topBarVisibilityProgress(
        section: BrowseSection,
        collapseDistancePx: Float,
        leadingScrollAnchorItems: Int = 0,
    ): Float = browseRootTopBarVisibilityProgress(
        section = section,
        collapseDistancePx = collapseDistancePx,
        leadingScrollAnchorItems = leadingScrollAnchorItems,
        catalogGridState = catalogGridState,
        scheduleGridState = scheduleGridState,
        historyGridState = historyGridState,
    )

    fun canScrollToTop(section: BrowseSection): Boolean =
        gridState(section)?.canHandleBrowseRootBackToTop(section) == true

    suspend fun scrollToTop(section: BrowseSection) {
        gridState(section)?.animateScrollToItem(0, 0)
    }

    fun canExitAppFromBack(section: BrowseSection, settledAtSection: Boolean): Boolean {
        if (!settledAtSection || catalogGridState.canScrollBackward) return false
        return canExitRootCatalog(
            isRootHome = true,
            homeSection = section,
            firstVisibleItemIndex = catalogGridState.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = catalogGridState.firstVisibleItemScrollOffset,
            browsePagerSettledAtStateSection = settledAtSection,
        )
    }

    fun focusedIndex(section: BrowseSection): Int = focusStore.focusedIndex(section)

    fun setFocusedIndex(section: BrowseSection, index: Int) = focusStore.setFocusedIndex(section, index)
}

// BrowseScreenContentBinding
internal fun createBrowseHomeContentModel(
    state: YummyDroidUiState,
    config: BrowseScreenRuntimeConfig,
    environment: BrowseScreenEnvironment,
    navigation: BrowseScreenNavigation,
    focusBinding: BrowseFocusBinding,
    pagerRuntime: BrowsePagerRuntime,
    visualRuntime: BrowseHomeVisualRuntime,
    catalogDialogRuntime: BrowseCatalogDialogRuntime,
    phoneSchedule: BrowsePhoneScheduleRuntime,
    scheduleSelectedEpochDay: Long,
    dpadLayerFocusRequestNonce: Long,
): BrowseHomeContentModel = BrowseHomeContentModel(
    state = state,
    browseCoordinator = config.browseCoordinator,
    effectiveSection = environment.effectiveSection,
    pagerSections = environment.pagerSections,
    pagerPage = environment.pagerPage,
    usePager = environment.usePager,
    catalogActionsEnabled = environment.catalogActionsEnabled,
    isSearching = environment.isSearching,
    isWide = environment.isWide,
    forcedOfflineMode = environment.forcedOfflineMode,
    dpadFocusEnabled = environment.dpadFocusEnabled,
    active = config.active,
    loginDialogOpen = config.loginDialogOpen,
    profileDialogOpen = config.profileDialogOpen,
    settingsDialogOpen = config.settingsDialogOpen,
    density = environment.density,
    chromePolicy = environment.chromePolicy,
    visualRuntime = visualRuntime,
    phoneScheduleDayGroups = phoneSchedule.dayGroups,
    scheduleSelectedEpochDay = scheduleSelectedEpochDay,
    showPhoneScheduleCalendar = phoneSchedule.showInBottomChrome,
    dpadLayerFocusRequestNonce = dpadLayerFocusRequestNonce,
    catalogFocusFirstRequest = navigation.focusFirstRequests.catalog,
    scheduleFocusFirstRequest = navigation.focusFirstRequests.schedule,
    historyFocusFirstRequest = navigation.focusFirstRequests.history,
    focusBinding = focusBinding,
    focusActions = navigation.focusActions,
    pagerRuntime = pagerRuntime,
    pagerBinding = navigation.pagerBinding,
    catalogDialogRuntime = catalogDialogRuntime,
)

internal fun BrowseScreenRuntimeActions.toBrowseHomeContentActions(
    onScheduleSelectedEpochDayChange: (Long) -> Unit,
): BrowseHomeContentActions = BrowseHomeContentActions(
    onQueryChange = onQueryChange,
    onSearchSubmitted = onSearchSubmitted,
    onSearchHistorySelected = onSearchHistorySelected,
    onRefresh = onRefresh,
    onRefreshFilterCatalog = onRefreshFilterCatalog,
    onLoadMoreAnime = onLoadMoreAnime,
    onFiltersChange = onFiltersChange,
    onResetFilters = onResetFilters,
    onOpenSettings = onOpenSettings,
    onOpenDownloads = onOpenDownloads,
    onClearDownloadHistory = onClearDownloadHistory,
    onCancelDownload = onCancelDownload,
    onPauseDownload = onPauseDownload,
    onResumeDownload = onResumeDownload,
    onOpenLogin = onOpenLogin,
    onOpenProfile = onOpenProfile,
    onScheduleSelectedEpochDayChange = onScheduleSelectedEpochDayChange,
    onOpenAnime = onOpenAnime,
)

// BrowseScreenEnvironment
internal data class BrowseScreenEnvironment(
    val effectiveSection: BrowseSection,
    val pagerSections: List<BrowseSection>,
    val pagerPage: Int,
    val usePager: Boolean,
    val catalogActionsEnabled: Boolean,
    val isSearching: Boolean,
    val density: Density,
    val dpadFocusEnabled: Boolean,
    val isWide: Boolean,
    val forcedOfflineMode: Boolean,
    val chromePolicy: BrowseChromePolicy,
    val topBarCollapseDistancePx: Float,
) {
    fun topBarFullyVisible(
        browseCoordinator: BrowseRootUiCoordinator,
        section: BrowseSection,
    ): Boolean {
        if (chromePolicy.pinTopChrome) return true
        return browseCoordinator.topBarVisibilityProgress(
            section = section,
            collapseDistancePx = topBarCollapseDistancePx,
        ) > 0.999f
    }
}

@Composable
internal fun rememberBrowseScreenEnvironment(
    state: YummyDroidUiState,
    browseCoordinator: BrowseRootUiCoordinator,
    onBrowseSectionChange: (BrowseSection) -> Unit,
): BrowseScreenEnvironment {
    val isAuthorized = state.auth.profile != null
    val forcedOffline = state.forcedOfflineMode
    val pagerSections = remember(isAuthorized, forcedOffline) {
        resolveBrowsePagerSections(isAuthorized, forcedOffline)
    }
    val effectiveSection = resolveEffectiveBrowseSection(state.homeSection, isAuthorized, forcedOffline)
    LaunchedEffect(state.homeSection, isAuthorized, forcedOffline) {
        resolveBrowseSectionCorrection(state.homeSection, isAuthorized, forcedOffline)
            ?.let(onBrowseSectionChange)
    }
    val density = LocalDensity.current
    val inputModeManager = LocalInputModeManager.current
    val isWide = currentResponsiveWindowSizeDp().width >= 720.dp
    val pagerPage = pagerSections.indexOf(effectiveSection).takeIf { it >= 0 } ?: 0
    return BrowseScreenEnvironment(
        effectiveSection = effectiveSection,
        pagerSections = pagerSections,
        pagerPage = pagerPage,
        usePager = !forcedOffline && pagerSections.size > 1,
        catalogActionsEnabled = browseCatalogActionsEnabledForSection(effectiveSection, forcedOffline),
        isSearching = effectiveSection == BrowseSection.Catalog && state.searchQuery.isNotBlank(),
        density = density,
        dpadFocusEnabled = inputModeManager.inputMode != InputMode.Touch,
        isWide = isWide,
        forcedOfflineMode = forcedOffline,
        chromePolicy = resolveBrowseChromePolicy(isWide, forcedOffline),
        topBarCollapseDistancePx = with(density) { BrowseTopBarScrollCollapseDistance.toPx() },
    )
}

private val BrowseTopBarScrollCollapseDistance = 180.dp

// BrowseScreenFacade
@Composable
internal fun BrowseScreen(
    state: YummyDroidUiState,
    browseCoordinator: BrowseRootUiCoordinator,
    activeFocusRequestNonce: Long,
    onRegisterHomeBackToTopHandler: (BrowseSection, HomeBackToTopHandler?) -> Unit,
    onHomeBrowseBackStateChange: (HomeBrowseBackState) -> Unit = {},
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    onRegisterDpadFocusRecoveryHandler: ((() -> Boolean)?) -> Unit = {},
    onQueryChange: (String) -> Unit,
    onSearchSubmitted: (String) -> Unit,
    onSearchHistorySelected: (String) -> Unit,
    onRefresh: () -> Unit,
    onRefreshFilterCatalog: () -> Unit,
    onLoadMoreAnime: () -> Unit,
    onBrowseSectionChange: (BrowseSection) -> Unit,
    onFiltersChange: (BrowseFilters) -> Unit,
    onResetFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    onClearDownloadHistory: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onResumeDownload: (Long) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    loginDialogOpen: Boolean = false,
    profileDialogOpen: Boolean = false,
    settingsDialogOpen: Boolean = false,
    active: Boolean = true,
    onOpenAnime: (Long) -> Unit,
) {
    BrowseScreenRuntime(
        state = state,
        config = BrowseScreenRuntimeConfig(
            browseCoordinator = browseCoordinator,
            activeFocusRequestNonce = activeFocusRequestNonce,
            onRegisterHomeBackToTopHandler = onRegisterHomeBackToTopHandler,
            onHomeBrowseBackStateChange = onHomeBrowseBackStateChange,
            onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
            onRegisterDpadFocusRecoveryHandler = onRegisterDpadFocusRecoveryHandler,
            loginDialogOpen = loginDialogOpen,
            profileDialogOpen = profileDialogOpen,
            settingsDialogOpen = settingsDialogOpen,
            active = active,
        ),
        actions = BrowseScreenRuntimeActions(
            onQueryChange = onQueryChange,
            onSearchSubmitted = onSearchSubmitted,
            onSearchHistorySelected = onSearchHistorySelected,
            onRefresh = onRefresh,
            onRefreshFilterCatalog = onRefreshFilterCatalog,
            onLoadMoreAnime = onLoadMoreAnime,
            onBrowseSectionChange = onBrowseSectionChange,
            onFiltersChange = onFiltersChange,
            onResetFilters = onResetFilters,
            onOpenSettings = onOpenSettings,
            onOpenDownloads = onOpenDownloads,
            onClearDownloadHistory = onClearDownloadHistory,
            onCancelDownload = onCancelDownload,
            onPauseDownload = onPauseDownload,
            onResumeDownload = onResumeDownload,
            onOpenLogin = onOpenLogin,
            onOpenProfile = onOpenProfile,
            onOpenAnime = onOpenAnime,
        ),
    )
}

// BrowseScreenNavigationRuntime
internal data class BrowseScreenNavigation(
    val focusActions: BrowseFocusActions,
    val focusFirstRequests: BrowseFocusFirstRequests,
    val pagerBinding: BrowsePagerBinding,
)

internal data class BrowsePhoneScheduleRuntime(
    val dayGroups: List<ScheduleDayGroup>,
    val showInBottomChrome: Boolean,
)

@Composable
internal fun rememberBrowsePhoneScheduleRuntime(
    state: YummyDroidUiState,
    environment: BrowseScreenEnvironment,
): BrowsePhoneScheduleRuntime {
    val dayGroups = rememberPhoneScheduleDayGroups(
        state.schedule,
        environment.isWide,
        environment.forcedOfflineMode,
    )
    return BrowsePhoneScheduleRuntime(
        dayGroups = dayGroups,
        showInBottomChrome = !environment.isWide &&
            !environment.forcedOfflineMode &&
            environment.effectiveSection == BrowseSection.Schedule &&
            dayGroups.isNotEmpty(),
    )
}

@Composable
internal fun rememberBrowseScreenNavigation(
    state: YummyDroidUiState,
    environment: BrowseScreenEnvironment,
    config: BrowseScreenRuntimeConfig,
    focusBinding: BrowseFocusBinding,
    pagerRuntime: BrowsePagerRuntime,
    dpadLayerFocusRequestNonce: Long,
    phoneSchedule: BrowsePhoneScheduleRuntime,
    onBrowseSectionChange: (BrowseSection) -> Unit,
): BrowseScreenNavigation {
    val focusRuntime = focusBinding.runtime
    val focusActions = focusRuntime.bindActions(
        section = environment.effectiveSection,
        dpadFocusEnabled = environment.dpadFocusEnabled,
        forcedOfflineMode = environment.forcedOfflineMode,
        showPhoneScheduleCalendar = phoneSchedule.showInBottomChrome,
        scheduleGridState = config.browseCoordinator.scheduleGridState,
        browseCoordinator = config.browseCoordinator,
        sectionFocusRequesters = focusBinding.sectionFocusRequesters,
        pagerRuntime = pagerRuntime,
        topBarFullyVisible = {
            environment.topBarFullyVisible(config.browseCoordinator, environment.effectiveSection)
        },
        onRegisterHomeBackToTopHandler = config.onRegisterHomeBackToTopHandler,
    )
    DisposableEffect(config.onRegisterDpadFocusRecoveryHandler) {
        config.onRegisterDpadFocusRecoveryHandler(focusActions.recoverFirstContentFocus)
        onDispose { config.onRegisterDpadFocusRecoveryHandler(null) }
    }
    val focusFirstRequests = resolveBrowseFocusFirstRequests(
        section = environment.effectiveSection,
        persistentCatalogNonce = state.homeFocusResetNonce,
        transientNonce = focusRuntime.firstFocusRequestNonce,
    )
    val pagerBinding = rememberBrowsePagerBinding(
        active = config.active,
        effectiveSection = environment.effectiveSection,
        pagerSections = environment.pagerSections,
        usePager = environment.usePager,
        dpadFocusEnabled = environment.dpadFocusEnabled,
        dpadLayerFocusRequestNonce = dpadLayerFocusRequestNonce,
        isWide = environment.isWide,
        forcedOfflineMode = environment.forcedOfflineMode,
        browseCoordinator = config.browseCoordinator,
        topBarCollapseDistancePx = environment.topBarCollapseDistancePx,
        runtime = pagerRuntime,
        onBrowseSectionChange = onBrowseSectionChange,
        onHomeBrowseBackStateChange = config.onHomeBrowseBackStateChange,
        onRequestSectionTabsFocus = focusActions.requestSectionTabsFocus,
    )
    return BrowseScreenNavigation(focusActions, focusFirstRequests, pagerBinding)
}

// BrowseSectionPages
@Composable
internal fun BrowseSectionPageContent(
    model: BrowseHomeContentModel,
    actions: BrowseHomeContentActions,
    pageSection: BrowseSection,
    pageIndex: Int,
    pageCanReceiveFocus: Boolean,
    pageFocusCurrentRequestNonce: Long,
    catalogContentBottomPadding: Dp,
    scheduleContentBottomPadding: Dp,
) {
    val catalogContentState = if (model.isSearching) model.state.searchResults else model.state.featured
    val catalogPagingState = if (model.isSearching) model.state.searchPaging else model.state.featuredPaging
    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusProperties { canFocus = pageCanReceiveFocus }
            .focusGroup(),
    ) {
        when (pageSection) {
            BrowseSection.Catalog -> BrowseCatalogSectionPage(
                model = model,
                actions = actions,
                pageIndex = pageIndex,
                pageCanReceiveFocus = pageCanReceiveFocus,
                pageFocusCurrentRequestNonce = pageFocusCurrentRequestNonce,
                contentState = catalogContentState,
                pagingState = catalogPagingState,
                contentBottomPadding = catalogContentBottomPadding,
            )
            BrowseSection.Schedule -> BrowseScheduleSectionPage(
                model = model,
                actions = actions,
                pageIndex = pageIndex,
                pageCanReceiveFocus = pageCanReceiveFocus,
                pageFocusCurrentRequestNonce = pageFocusCurrentRequestNonce,
                contentBottomPadding = scheduleContentBottomPadding,
            )
            BrowseSection.History -> BrowseHistorySectionPage(
                model = model,
                actions = actions,
                pageIndex = pageIndex,
                pageCanReceiveFocus = pageCanReceiveFocus,
                pageFocusCurrentRequestNonce = pageFocusCurrentRequestNonce,
                contentBottomPadding = catalogContentBottomPadding,
            )
            BrowseSection.Downloads -> DownloadsSection(
                state = model.state,
                focusCurrentRequestNonce = pageFocusCurrentRequestNonce,
                contentBottomPadding = catalogContentBottomPadding,
                onClearHistory = actions.onClearDownloadHistory,
                onCancelDownload = actions.onCancelDownload,
                onPauseDownload = actions.onPauseDownload,
                onResumeDownload = actions.onResumeDownload,
                onOpenAnime = actions.onOpenAnime,
                onRetry = actions.onRefresh,
            )
        }
    }
}

@Composable
private fun BrowseCatalogSectionPage(
    model: BrowseHomeContentModel,
    actions: BrowseHomeContentActions,
    pageIndex: Int,
    pageCanReceiveFocus: Boolean,
    pageFocusCurrentRequestNonce: Long,
    contentState: LoadState<List<Anime>>,
    pagingState: PagingUiState,
    contentBottomPadding: Dp,
) {
    val browseCoordinator = model.browseCoordinator
    BrowseAnimeGridPage(
        section = BrowseSection.Catalog,
        contentState = contentState,
        pagingState = pagingState,
        gridState = browseCoordinator.catalogGridState,
        cardSize = model.state.settings.posterCardSize,
        contentBottomPadding = contentBottomPadding,
        focusFirstRequest = model.catalogFocusFirstRequest,
        pageIndex = pageIndex,
        pageCanReceiveFocus = pageCanReceiveFocus,
        pageFocusCurrentRequestNonce = pageFocusCurrentRequestNonce,
        currentFocusedIndex = { browseCoordinator.focusedIndex(BrowseSection.Catalog) },
        onFocusedIndexChange = { browseCoordinator.setFocusedIndex(BrowseSection.Catalog, it) },
        onRegisterBackToTopHandler = { model.focusActions.updateHomeBackToTopHandler(BrowseSection.Catalog, it) },
        emptyMessage = if (model.isSearching) uiText(UiStringKey.NothingFound) else uiText(UiStringKey.CatalogIsEmpty),
        onRetry = actions.onRefresh,
        onLoadMore = actions.onLoadMoreAnime,
        onHorizontalExit = model.pagerBinding.onHorizontalExit,
        onRequestSectionTabsFocus = model.sectionTabsFocusRequester(),
        onRequestTopActionsFocus = model.focusActions.requestTopActionsFocus,
        sectionTabFocusRequester = model.focusBinding.sectionFocusRequesters[BrowseSection.Catalog],
        isWide = model.isWide,
        forcedOfflineMode = model.forcedOfflineMode,
        onOpenAnime = actions.onOpenAnime,
    )
}

@Composable
private fun BrowseScheduleSectionPage(
    model: BrowseHomeContentModel,
    actions: BrowseHomeContentActions,
    pageIndex: Int,
    pageCanReceiveFocus: Boolean,
    pageFocusCurrentRequestNonce: Long,
    contentBottomPadding: Dp,
) {
    val browseCoordinator = model.browseCoordinator
    val tvChromeVisible = model.isWide && !model.forcedOfflineMode
    ScheduleSection(
        state = model.state.schedule,
        precomputedDayGroups = if (!model.isWide && !model.forcedOfflineMode) model.phoneScheduleDayGroups else null,
        gridState = browseCoordinator.scheduleGridState,
        cardSize = model.state.settings.posterCardSize,
        locale = model.state.settings.contentLanguage.uiLocale(),
        focusFirstRequest = model.scheduleFocusFirstRequest,
        focusCurrentRequestNonce = pageFocusCurrentRequestNonce,
        calendarFocusRequestNonce = model.focusBinding.runtime.scheduleCalendarFocusRequestNonce,
        contentFocusEnabled = pageCanReceiveFocus,
        showCalendarInGrid = tvChromeVisible,
        selectedEpochDay = model.scheduleSelectedEpochDay,
        onSelectedEpochDayChange = actions.onScheduleSelectedEpochDayChange,
        currentFocusedIndex = { browseCoordinator.focusedIndex(BrowseSection.Schedule) },
        onFocusedIndexChange = { browseCoordinator.setFocusedIndex(BrowseSection.Schedule, it) },
        pinnedTopPadding = if (model.chromePolicy.pinTopChrome) BrowseTvScheduleBlockGap else 0.dp,
        contentBottomPadding = contentBottomPadding,
        onRegisterBackToTopHandler = { model.focusActions.updateHomeBackToTopHandler(BrowseSection.Schedule, it) },
        onRetry = actions.onRefresh,
        onExitHorizontalDirection = { model.pagerBinding.onHorizontalExit(pageIndex, it) },
        onExitUp = if (tvChromeVisible) {
            { model.sectionTabsFocusRequester()(true) }
        } else {
            model.focusActions.requestTopActionsFocus
        },
        onExitDown = if (tvChromeVisible) {
            { false }
        } else {
            model.focusActions.requestScheduleCalendarFocus
        },
        onOpenAnime = actions.onOpenAnime,
    )
}

@Composable
private fun BrowseHistorySectionPage(
    model: BrowseHomeContentModel,
    actions: BrowseHomeContentActions,
    pageIndex: Int,
    pageCanReceiveFocus: Boolean,
    pageFocusCurrentRequestNonce: Long,
    contentBottomPadding: Dp,
) {
    val browseCoordinator = model.browseCoordinator
    BrowseAnimeGridPage(
        section = BrowseSection.History,
        contentState = model.state.historyAnime,
        pagingState = PagingUiState(canLoadMore = false),
        gridState = browseCoordinator.historyGridState,
        cardSize = model.state.settings.posterCardSize,
        contentBottomPadding = contentBottomPadding,
        focusFirstRequest = model.historyFocusFirstRequest,
        pageIndex = pageIndex,
        pageCanReceiveFocus = pageCanReceiveFocus,
        pageFocusCurrentRequestNonce = pageFocusCurrentRequestNonce,
        currentFocusedIndex = { browseCoordinator.focusedIndex(BrowseSection.History) },
        onFocusedIndexChange = { browseCoordinator.setFocusedIndex(BrowseSection.History, it) },
        onRegisterBackToTopHandler = { model.focusActions.updateHomeBackToTopHandler(BrowseSection.History, it) },
        emptyMessage = uiText(UiStringKey.HistoryIsEmpty),
        onRetry = actions.onRefresh,
        onLoadMore = {},
        onHorizontalExit = model.pagerBinding.onHorizontalExit,
        onRequestSectionTabsFocus = model.sectionTabsFocusRequester(),
        onRequestTopActionsFocus = model.focusActions.requestTopActionsFocus,
        sectionTabFocusRequester = model.focusBinding.sectionFocusRequesters[BrowseSection.History],
        isWide = model.isWide,
        forcedOfflineMode = model.forcedOfflineMode,
        onOpenAnime = actions.onOpenAnime,
    )
}

private fun BrowseHomeContentModel.sectionTabsFocusRequester(): (Boolean) -> Boolean = { releaseTransition ->
    focusActions.requestSectionTabsFocus(effectiveSection, releaseTransition)
}

@Composable
private fun BrowseAnimeGridPage(
    section: BrowseSection,
    contentState: LoadState<List<Anime>>,
    pagingState: PagingUiState,
    gridState: LazyGridState,
    cardSize: PosterCardSize,
    contentBottomPadding: Dp,
    focusFirstRequest: FocusFirstRequest,
    pageIndex: Int,
    pageCanReceiveFocus: Boolean,
    pageFocusCurrentRequestNonce: Long,
    currentFocusedIndex: () -> Int,
    onFocusedIndexChange: (Int) -> Unit,
    onRegisterBackToTopHandler: (HomeBackToTopHandler?) -> Unit,
    emptyMessage: String,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onHorizontalExit: (Int, VisualGridDirection) -> Boolean,
    onRequestSectionTabsFocus: (releasePagerFocusTransition: Boolean) -> Boolean,
    onRequestTopActionsFocus: () -> Boolean,
    sectionTabFocusRequester: FocusRequester?,
    isWide: Boolean,
    forcedOfflineMode: Boolean,
    onOpenAnime: (Long) -> Unit,
) {
    val tvChromeVisible = isWide && !forcedOfflineMode
    AnimeGridSection(
        contentState = contentState,
        pagingState = pagingState,
        gridState = gridState,
        cardSize = cardSize,
        contentTopPadding = 0.dp,
        contentBottomPadding = contentBottomPadding,
        focusFirstRequest = focusFirstRequest,
        focusCurrentRequestNonce = pageFocusCurrentRequestNonce,
        contentFocusEnabled = pageCanReceiveFocus,
        currentFocusedIndex = currentFocusedIndex,
        onFocusedIndexChange = onFocusedIndexChange,
        backToTopSection = section,
        onRegisterBackToTopHandler = onRegisterBackToTopHandler,
        emptyMessage = emptyMessage,
        onRetry = onRetry,
        onLoadMore = onLoadMore,
        onExitHorizontalDirection = { direction -> onHorizontalExit(pageIndex, direction) },
        onExitUp = if (tvChromeVisible) {
            { onRequestSectionTabsFocus(false) }
        } else {
            onRequestTopActionsFocus
        },
        exitUpFocusRequester = sectionTabFocusRequester.takeIf { tvChromeVisible },
        onExitDown = if (tvChromeVisible) {
            { false }
        } else {
            { onRequestSectionTabsFocus(false) }
        },
        onOpenAnime = onOpenAnime,
    )
}
