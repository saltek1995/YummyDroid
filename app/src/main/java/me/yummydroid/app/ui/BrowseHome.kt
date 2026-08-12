package me.yummydroid.app.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.DownloadTaskState
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.ScheduleAnime
import me.yummydroid.app.readyListOrEmpty

// BrowseHomeBackStateEffect
@Composable
internal fun BrowseHomeBackStateEffect(
    active: Boolean,
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
    pagerPage: Int,
    usePager: Boolean,
    runtime: BrowsePagerRuntime,
    pagerPosition: Float,
    onHomeBrowseBackStateChange: (HomeBrowseBackState) -> Unit,
) {
    val pagerAwayFromTarget = usePager &&
        (runtime.pagerState.currentPage != pagerPage || abs(runtime.pagerState.currentPageOffsetFraction) > 0.001f)
    val backState = resolveHomeBrowseBackState(
        useBrowsePager = usePager,
        effectiveSection = effectiveSection,
        pagerSections = pagerSections,
        pagerPosition = pagerPosition,
        pagerScrollInProgress = runtime.pagerState.isScrollInProgress,
        pagerAwayFromTarget = pagerAwayFromTarget,
    )
    LaunchedEffect(active, backState) {
        if (active) onHomeBrowseBackStateChange(backState)
    }
}

// BrowseHomeBottomChrome
@Composable
internal fun BoxScope.BrowseHomeBottomChrome(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
) {
    BrowseHomeBottomBar(
        state = state.homeChromeState,
        onOpenSearch = actions.onOpenSearch,
        onOpenFilters = actions.onOpenFilters,
        onOpenSettings = actions.onOpenSettings,
        onOpenDownloads = actions.onOpenDownloads,
        onOpenLogin = actions.onOpenLogin,
        onOpenProfile = actions.onOpenProfile,
        onSectionSelected = actions.onSectionSelected,
        sectionTabsFocusRequester = state.sectionTabFocusRequesters[state.effectiveSection],
        sectionTabFocusRequesters = state.sectionTabFocusRequesters,
        sectionTabsOnExitUp = if (state.showPhoneScheduleCalendar) {
            actions.onRequestScheduleCalendarFocus
        } else {
            actions.onRequestContentFocus
        },
        sectionTabsFocusEnabled = state.sectionTabsFocusEnabled,
        hazeState = if (state.chromeHazeActive) state.chromeHazeState else null,
        showScheduleCalendar = state.showPhoneScheduleCalendarVisual,
        scheduleDayGroups = state.phoneScheduleDayGroups,
        selectedScheduleEpochDay = state.selectedScheduleEpochDay,
        scheduleLocale = state.scheduleLocale,
        scheduleCalendarFocusRequestNonce = state.scheduleCalendarFocusRequestNonce,
        scheduleCalendarFocusEnabled = state.dpadFocusEnabled,
        scheduleCalendarOnExitUp = actions.onRequestContentFocus,
        scheduleCalendarOnExitDown = {
            actions.onRequestSectionTabsFocus(BrowseSection.Schedule, true)
        },
        onScheduleDaySelected = actions.onScheduleDaySelected,
        scheduleCalendarVisibilityProgress = state.scheduleCalendarVisualProgress,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .onSizeChanged { size ->
                actions.onBottomChromeMeasured(
                    size.height,
                    state.showPhoneScheduleCalendarVisual,
                )
            },
    )
}

// BrowseHomeChrome
internal data class BrowseHomeChromeState(
    val auth: AuthUiState,
    val activeFilters: Int,
    val activeSearch: Boolean,
    val activeFiltersPanel: Boolean,
    val activeSettings: Boolean,
    val activeDownloads: Boolean,
    val activeProfile: Boolean,
    val activeDownloadCount: Int,
    val forcedOfflineMode: Boolean,
    val catalogActionsEnabled: Boolean,
    val isWide: Boolean,
    val activeSection: BrowseSection,
    val visibleSections: List<BrowseSection>,
    val activeSectionPosition: Float?,
)

@Composable
internal fun BrowseHomeTopBar(
    state: BrowseHomeChromeState,
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onSectionSelected: (BrowseSection) -> Unit,
    onExitDown: () -> Boolean,
    actionsFocusRequester: FocusRequester,
    sectionTabsFocusRequester: FocusRequester?,
    sectionTabFocusRequesters: Map<BrowseSection, FocusRequester>,
    sectionTabsFocusEnabled: Boolean,
    visible: Boolean,
    visibilityProgressProvider: () -> Float,
    modifier: Modifier = Modifier,
    collapseWhenHidden: Boolean = true,
) {
    BrowseTopBarModern(
        state = state,
        callbacks = BrowseActionCallbacks(
            onOpenSearch = onOpenSearch,
            onOpenFilters = onOpenFilters,
            onOpenSettings = onOpenSettings,
            onOpenDownloads = onOpenDownloads,
            onOpenLogin = onOpenLogin,
            onOpenProfile = onOpenProfile,
        ),
        navigation = BrowseTopSectionNavigation(
            onSectionSelected = onSectionSelected,
            onExitDown = onExitDown,
            actionsFocusRequester = actionsFocusRequester,
            sectionTabsFocusRequester = sectionTabsFocusRequester,
            sectionTabFocusRequesters = sectionTabFocusRequesters,
            sectionTabsFocusEnabled = sectionTabsFocusEnabled,
        ),
        showCompactControls = false,
        visibility = BrowseTopChromeVisibility(
            collapseWhenHidden = collapseWhenHidden,
            visible = visible,
            progress = null,
            progressProvider = visibilityProgressProvider,
        ),
        modifier = modifier,
    )
}

@Composable
internal fun BrowseHomeTvSectionTabs(
    state: BrowseHomeChromeState,
    sectionFocusRequesters: Map<BrowseSection, FocusRequester>,
    sectionTabsFocusEnabled: Boolean,
    onSectionSelected: (BrowseSection) -> Unit,
    onExitUp: () -> Boolean,
    onExitDown: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    BrowseTvSectionIndicatorBar(
        activeSection = state.activeSection,
        visibleSections = state.visibleSections,
        activeSectionPosition = state.activeSectionPosition,
        onSectionSelected = onSectionSelected,
        sectionFocusRequesters = sectionFocusRequesters,
        onExitUp = onExitUp,
        onExitDown = onExitDown,
        drawBackdrop = false,
        backdropVisible = false,
        sectionTabsFocusEnabled = sectionTabsFocusEnabled,
        squareTopCorners = false,
        modifier = modifier,
    )
}

@Composable
internal fun BrowseHomeBottomBar(
    state: BrowseHomeChromeState,
    sectionTabsFocusRequester: FocusRequester?,
    sectionTabFocusRequesters: Map<BrowseSection, FocusRequester>,
    sectionTabsFocusEnabled: Boolean,
    sectionTabsOnExitUp: () -> Boolean,
    hazeState: HazeState?,
    showScheduleCalendar: Boolean,
    scheduleDayGroups: List<ScheduleDayGroup>,
    selectedScheduleEpochDay: Long,
    scheduleLocale: Locale,
    scheduleCalendarFocusRequestNonce: Long,
    scheduleCalendarFocusEnabled: Boolean,
    scheduleCalendarOnExitUp: () -> Boolean,
    scheduleCalendarOnExitDown: () -> Boolean,
    onScheduleDaySelected: (Long) -> Unit,
    scheduleCalendarVisibilityProgress: Float,
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onSectionSelected: (BrowseSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    BrowseBottomBarModern(
        state = state,
        actions = BrowseActionCallbacks(
            onOpenSearch = onOpenSearch,
            onOpenFilters = onOpenFilters,
            onOpenSettings = onOpenSettings,
            onOpenDownloads = onOpenDownloads,
            onOpenLogin = onOpenLogin,
            onOpenProfile = onOpenProfile,
        ),
        sectionNavigation = BrowseBottomSectionNavigation(
            onSectionSelected = onSectionSelected,
            focusRequester = sectionTabsFocusRequester,
            focusRequesters = sectionTabFocusRequesters,
            onExitUp = sectionTabsOnExitUp,
            focusEnabled = sectionTabsFocusEnabled,
        ),
        hazeState = hazeState,
        topProtectedContent = if (showScheduleCalendar) {
            { calendarModifier ->
                ScheduleCalendarBlock(
                    dayGroups = scheduleDayGroups,
                    selectedEpochDay = selectedScheduleEpochDay,
                    locale = scheduleLocale,
                    focusRequestNonce = scheduleCalendarFocusRequestNonce,
                    focusEnabled = scheduleCalendarFocusEnabled,
                    onExitUp = scheduleCalendarOnExitUp,
                    onExitDown = scheduleCalendarOnExitDown,
                    onSelectDay = onScheduleDaySelected,
                    modifier = calendarModifier,
                )
            }
        } else {
            null
        },
        topProtectedVisibilityProgress = scheduleCalendarVisibilityProgress,
        modifier = modifier,
    )
}

@Composable
internal fun BrowseCatalogDialogs(
    state: YummyDroidUiState,
    catalogActionsEnabled: Boolean,
    searchDialogOpen: Boolean,
    filtersDialogOpen: Boolean,
    searchKeyboardDismissRequest: Long,
    searchInputAction: InputAction?,
    searchInputActionRequest: Long,
    onQueryChange: (String) -> Unit,
    onSearchSubmitted: (String) -> Unit,
    onSearchHistorySelected: (String) -> Unit,
    onFiltersChange: (BrowseFilters) -> Unit,
    onResetFilters: () -> Unit,
    onRetryFilterCatalog: () -> Unit,
    onDismissSearch: () -> Unit,
    onDismissFilters: () -> Unit,
    onSearchExitDown: () -> Unit,
) {
    if (catalogActionsEnabled && searchDialogOpen) {
        SearchDialog(
            query = state.searchQuery,
            searchHistory = state.searchHistory,
            keyboardDismissRequest = searchKeyboardDismissRequest,
            remoteInputAction = searchInputAction,
            remoteInputActionRequest = searchInputActionRequest,
            onQueryChange = onQueryChange,
            onSubmitQuery = onSearchSubmitted,
            onHistorySelected = onSearchHistorySelected,
            onDismiss = onDismissSearch,
            onExitDown = onSearchExitDown,
        )
    }
    if (catalogActionsEnabled && filtersDialogOpen) {
        FiltersDialogAccordion(
            filters = state.filters,
            auth = state.auth,
            catalogState = state.filterCatalog,
            offlineEntries = state.offlineEntries.readyListOrEmpty(),
            forcedOfflineMode = state.forcedOfflineMode,
            onApply = onFiltersChange,
            onReset = onResetFilters,
            onRetryCatalog = onRetryFilterCatalog,
            onDismiss = onDismissFilters,
        )
    }
}

// BrowseHomeContent
internal class BrowseHomeVisualRuntime(
    val hazeState: HazeState,
) {
    var bottomChromeBaseMeasuredHeight by mutableStateOf(0.dp)
    var bottomChromeExpandedHeight by mutableStateOf(0.dp)

    fun updateBottomChromeHeight(heightPx: Int, expanded: Boolean, density: Density) {
        val measuredHeight = with(density) { heightPx.toDp() }
        if (expanded) {
            bottomChromeExpandedHeight = maxOf(bottomChromeExpandedHeight, measuredHeight)
        } else {
            bottomChromeBaseMeasuredHeight = measuredHeight
        }
    }
}

@Composable
internal fun rememberBrowseHomeVisualRuntime(): BrowseHomeVisualRuntime {
    return remember { BrowseHomeVisualRuntime(HazeState()) }
}

@Composable
internal fun rememberPhoneScheduleDayGroups(
    schedule: LoadState<List<ScheduleAnime>>,
    isWide: Boolean,
    forcedOfflineMode: Boolean,
): List<ScheduleDayGroup> {
    val zoneId = remember { ZoneId.systemDefault() }
    return remember(schedule, zoneId, isWide, forcedOfflineMode) {
        if (!isWide && !forcedOfflineMode) {
            schedule.readyListOrEmpty().toScheduleDayGroups(zoneId)
        } else {
            emptyList()
        }
    }
}

internal data class BrowseHomeContentModel(
    val state: YummyDroidUiState,
    val browseCoordinator: BrowseRootUiCoordinator,
    val effectiveSection: BrowseSection,
    val pagerSections: List<BrowseSection>,
    val pagerPage: Int,
    val usePager: Boolean,
    val catalogActionsEnabled: Boolean,
    val isSearching: Boolean,
    val isWide: Boolean,
    val forcedOfflineMode: Boolean,
    val dpadFocusEnabled: Boolean,
    val active: Boolean,
    val loginDialogOpen: Boolean,
    val profileDialogOpen: Boolean,
    val settingsDialogOpen: Boolean,
    val density: Density,
    val chromePolicy: BrowseChromePolicy,
    val visualRuntime: BrowseHomeVisualRuntime,
    val phoneScheduleDayGroups: List<ScheduleDayGroup>,
    val scheduleSelectedEpochDay: Long,
    val showPhoneScheduleCalendar: Boolean,
    val dpadLayerFocusRequestNonce: Long,
    val catalogFocusFirstRequest: FocusFirstRequest,
    val scheduleFocusFirstRequest: FocusFirstRequest,
    val historyFocusFirstRequest: FocusFirstRequest,
    val focusBinding: BrowseFocusBinding,
    val focusActions: BrowseFocusActions,
    val pagerRuntime: BrowsePagerRuntime,
    val pagerBinding: BrowsePagerBinding,
    val catalogDialogRuntime: BrowseCatalogDialogRuntime,
)

internal data class BrowseHomeContentActions(
    val onQueryChange: (String) -> Unit,
    val onSearchSubmitted: (String) -> Unit,
    val onSearchHistorySelected: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onRefreshFilterCatalog: () -> Unit,
    val onLoadMoreAnime: () -> Unit,
    val onFiltersChange: (BrowseFilters) -> Unit,
    val onResetFilters: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenDownloads: () -> Unit,
    val onClearDownloadHistory: () -> Unit,
    val onCancelDownload: (Long) -> Unit,
    val onPauseDownload: (Long) -> Unit,
    val onResumeDownload: (Long) -> Unit,
    val onOpenLogin: () -> Unit,
    val onOpenProfile: () -> Unit,
    val onScheduleSelectedEpochDayChange: (Long) -> Unit,
    val onOpenAnime: (Long) -> Unit,
)

@Composable
internal fun BrowseHomeContent(
    model: BrowseHomeContentModel,
    actions: BrowseHomeContentActions,
) {
    val state = model.state
    val activeDownloadCount = rememberActiveDownloadCount(state)
    val scheduleCalendarVisualProgress = resolveScheduleCalendarVisualProgress(model)
    val homeChromeState = BrowseHomeChromeState(
        auth = state.auth,
        activeFilters = if (model.catalogActionsEnabled) state.filters.activeCount else 0,
        activeSearch = model.catalogActionsEnabled && model.isSearching,
        activeFiltersPanel = model.catalogActionsEnabled && model.catalogDialogRuntime.filtersDialogOpen,
        activeSettings = model.settingsDialogOpen,
        activeDownloads = model.effectiveSection == BrowseSection.Downloads,
        activeProfile = model.loginDialogOpen || model.profileDialogOpen,
        activeDownloadCount = activeDownloadCount,
        forcedOfflineMode = model.forcedOfflineMode,
        catalogActionsEnabled = model.catalogActionsEnabled,
        isWide = model.isWide,
        activeSection = model.effectiveSection,
        visibleSections = model.pagerSections,
        activeSectionPosition = model.pagerBinding.tabPosition,
    )
    val showScheduleCalendarVisual =
        model.showPhoneScheduleCalendar || scheduleCalendarVisualProgress > 0.001f
    val bottomChromeBaseHeight = resolveBottomChromeBaseHeight(model)
    val bottomChromeTargetHeight = resolveBottomChromeTargetHeight(
        model = model,
        baseHeight = bottomChromeBaseHeight,
        showScheduleCalendarVisual = showScheduleCalendarVisual,
        scheduleCalendarVisualProgress = scheduleCalendarVisualProgress,
    )
    val selectedScheduleEpochDay = model.phoneScheduleDayGroups
        .firstOrNull { group -> group.epochDay == model.scheduleSelectedEpochDay }
        ?.epochDay
        ?: model.phoneScheduleDayGroups.todayOrClosest()?.epochDay
        ?: Long.MIN_VALUE

    BrowseHomeLayout(
        state = createLayoutState(
            model = model,
            homeChromeState = homeChromeState,
            showScheduleCalendarVisual = showScheduleCalendarVisual,
            selectedScheduleEpochDay = selectedScheduleEpochDay,
            scheduleCalendarVisualProgress = scheduleCalendarVisualProgress,
        ),
        actions = createLayoutActions(
            model = model,
            actions = actions,
            catalogContentBottomPadding = bottomChromeBaseHeight,
            scheduleContentBottomPadding = bottomChromeTargetHeight,
        ),
    )
    BrowseHomeCatalogDialogs(model, actions)
}

@Composable
private fun rememberActiveDownloadCount(state: YummyDroidUiState): Int {
    return remember(state.downloadQueue.tasks) {
        state.downloadQueue.tasks.count { task ->
            task.state == DownloadTaskState.Queued ||
                task.state == DownloadTaskState.Running ||
                task.state == DownloadTaskState.Paused
        }
    }
}

private fun resolveScheduleCalendarVisualProgress(model: BrowseHomeContentModel): Float {
    return resolvePhoneScheduleCalendarProgress(
        isWide = model.isWide,
        forcedOfflineMode = model.forcedOfflineMode,
        schedulePage = model.pagerSections.indexOf(BrowseSection.Schedule),
        hasScheduleDays = model.phoneScheduleDayGroups.isNotEmpty(),
        visualPagerPosition = model.pagerBinding.tabPosition ?: model.pagerPage.toFloat(),
    )
}

private fun resolveBottomChromeBaseHeight(model: BrowseHomeContentModel): Dp {
    if (!model.chromePolicy.showBottomChrome) return 0.dp
    return model.visualRuntime.bottomChromeBaseMeasuredHeight
        .takeIf { height -> height > 0.dp }
        ?: BrowseBottomChromeFallbackProtectedHeight
}

private fun resolveBottomChromeTargetHeight(
    model: BrowseHomeContentModel,
    baseHeight: Dp,
    showScheduleCalendarVisual: Boolean,
    scheduleCalendarVisualProgress: Float,
): Dp {
    if (!model.chromePolicy.showBottomChrome) return 0.dp
    if (!showScheduleCalendarVisual) return baseHeight
    val expandedHeight = maxOf(model.visualRuntime.bottomChromeExpandedHeight, baseHeight)
    return baseHeight + (expandedHeight - baseHeight) * scheduleCalendarVisualProgress
}

private fun createLayoutState(
    model: BrowseHomeContentModel,
    homeChromeState: BrowseHomeChromeState,
    showScheduleCalendarVisual: Boolean,
    selectedScheduleEpochDay: Long,
    scheduleCalendarVisualProgress: Float,
): BrowseHomeLayoutState {
    return BrowseHomeLayoutState(
        active = model.active,
        dpadFocusEnabled = model.dpadFocusEnabled,
        chromePolicy = model.chromePolicy,
        chromeHazeState = model.visualRuntime.hazeState,
        chromeHazeActive = !model.chromePolicy.pinTopChrome,
        homeChromeState = homeChromeState,
        topBarVisible = model.pagerBinding.topBarVisible,
        topBarVisibilityProgressProvider = { model.pagerBinding.topBarVisibilityProgress.value },
        effectiveSection = model.effectiveSection,
        pagerSections = model.pagerSections,
        pagerPage = model.pagerPage,
        usePager = model.usePager,
        pageStateHolder = model.pagerRuntime.pageStateHolder,
        pagerState = model.pagerRuntime.pagerState,
        pagerSettledAtTarget = model.pagerBinding.pagerSettledAtTarget,
        programmaticScrollTarget = model.pagerRuntime.programmaticScrollTarget,
        transitionFocusSourcePage = model.pagerRuntime.transitionFocusSourcePage,
        suppressedContentFocusSection = model.pagerRuntime.suppressedContentFocusSection,
        dpadLayerFocusRequestNonce = model.dpadLayerFocusRequestNonce,
        contentFocusRequestNonce = model.pagerBinding.focusRequestNonce,
        topActionsFocusRequester = model.focusBinding.runtime.topActionsFocusRequester,
        sectionTabFocusRequesters = model.focusBinding.sectionFocusRequesters,
        sectionTabsFocusEnabled = model.pagerRuntime.sectionTabsFocusEnabled,
        isWide = model.isWide,
        forcedOfflineMode = model.forcedOfflineMode,
        showPhoneScheduleCalendar = model.showPhoneScheduleCalendar,
        showPhoneScheduleCalendarVisual = showScheduleCalendarVisual,
        phoneScheduleDayGroups = model.phoneScheduleDayGroups,
        selectedScheduleEpochDay = selectedScheduleEpochDay,
        scheduleLocale = model.state.settings.contentLanguage.uiLocale(),
        scheduleCalendarFocusRequestNonce = model.focusBinding.runtime.scheduleCalendarFocusRequestNonce,
        scheduleCalendarVisualProgress = scheduleCalendarVisualProgress,
    )
}

private fun createLayoutActions(
    model: BrowseHomeContentModel,
    actions: BrowseHomeContentActions,
    catalogContentBottomPadding: Dp,
    scheduleContentBottomPadding: Dp,
): BrowseHomeLayoutActions {
    val focusRuntime = model.focusBinding.runtime
    return BrowseHomeLayoutActions(
        onLayerFocusChanged = { hasFocus -> focusRuntime.layerHasFocus = hasFocus },
        onOpenSearch = {
            if (model.catalogActionsEnabled) model.catalogDialogRuntime.openSearch()
        },
        onOpenFilters = {
            if (model.catalogActionsEnabled) model.catalogDialogRuntime.openFilters()
        },
        onOpenSettings = actions.onOpenSettings,
        onOpenDownloads = actions.onOpenDownloads,
        onOpenLogin = actions.onOpenLogin,
        onOpenProfile = actions.onOpenProfile,
        onSectionSelected = model.pagerBinding.onSectionSelected,
        onRequestTopActionsFocus = model.focusActions.requestTopActionsFocus,
        onRequestSectionTabsFocus = model.focusActions.requestSectionTabsFocus,
        onRequestScheduleCalendarFocus = model.focusActions.requestScheduleCalendarFocus,
        onRequestContentFocus = model.focusActions.requestCurrentContentFocus,
        onScheduleDaySelected = { epochDay ->
            actions.onScheduleSelectedEpochDayChange(epochDay)
            model.browseCoordinator.setFocusedIndex(BrowseSection.Schedule, 0)
            focusRuntime.scrollScheduleToStart(model.browseCoordinator.scheduleGridState)
        },
        onBottomChromeMeasured = { heightPx, expanded ->
            model.visualRuntime.updateBottomChromeHeight(heightPx, expanded, model.density)
        },
        sectionPage = { section, page, canReceiveFocus, focusRequestNonce ->
            BrowseBoundSectionPage(
                model = model,
                actions = actions,
                section = section,
                page = page,
                canReceiveFocus = canReceiveFocus,
                focusRequestNonce = focusRequestNonce,
                catalogContentBottomPadding = catalogContentBottomPadding,
                scheduleContentBottomPadding = scheduleContentBottomPadding,
            )
        },
    )
}

@Composable
private fun BrowseBoundSectionPage(
    model: BrowseHomeContentModel,
    actions: BrowseHomeContentActions,
    section: BrowseSection,
    page: Int,
    canReceiveFocus: Boolean,
    focusRequestNonce: Long,
    catalogContentBottomPadding: Dp,
    scheduleContentBottomPadding: Dp,
) {
    BrowseSectionPageContent(
        model = model,
        actions = actions,
        pageSection = section,
        pageIndex = page,
        pageCanReceiveFocus = canReceiveFocus,
        pageFocusCurrentRequestNonce = focusRequestNonce,
        catalogContentBottomPadding = catalogContentBottomPadding,
        scheduleContentBottomPadding = scheduleContentBottomPadding,
    )
}

@Composable
private fun BrowseHomeCatalogDialogs(
    model: BrowseHomeContentModel,
    actions: BrowseHomeContentActions,
) {
    val dialogRuntime = model.catalogDialogRuntime
    BrowseCatalogDialogs(
        state = model.state,
        catalogActionsEnabled = model.catalogActionsEnabled,
        searchDialogOpen = dialogRuntime.searchDialogOpen,
        filtersDialogOpen = dialogRuntime.filtersDialogOpen,
        searchKeyboardDismissRequest = dialogRuntime.searchKeyboardDismissRequest,
        searchInputAction = dialogRuntime.searchInputAction,
        searchInputActionRequest = dialogRuntime.searchInputActionRequest,
        onQueryChange = actions.onQueryChange,
        onSearchSubmitted = actions.onSearchSubmitted,
        onSearchHistorySelected = actions.onSearchHistorySelected,
        onFiltersChange = actions.onFiltersChange,
        onResetFilters = actions.onResetFilters,
        onRetryFilterCatalog = actions.onRefreshFilterCatalog,
        onDismissSearch = { dialogRuntime.searchDialogOpen = false },
        onDismissFilters = { dialogRuntime.filtersDialogOpen = false },
        onSearchExitDown = {
            dialogRuntime.searchDialogOpen = false
            model.focusBinding.runtime.activeHomeBackToTopHandler
                ?.takeIf { handler -> handler.section == model.effectiveSection }
                ?.handleBackToTop(withFocus = true)
        },
    )
}

private val BrowseBottomChromeFallbackProtectedHeight = 96.dp

// BrowseHomeFocus
internal data class BrowseFocusFirstRequests(
    val catalog: FocusFirstRequest,
    val schedule: FocusFirstRequest,
    val history: FocusFirstRequest,
)

internal fun resolveBrowseFocusFirstRequests(
    section: BrowseSection,
    persistentCatalogNonce: Long,
    transientNonce: Long,
): BrowseFocusFirstRequests {
    return BrowseFocusFirstRequests(
        catalog = FocusFirstRequest(
            persistentNonce = persistentCatalogNonce,
            transientNonce = transientNonce.takeIf { section == BrowseSection.Catalog } ?: 0L,
        ),
        schedule = FocusFirstRequest(
            transientNonce = transientNonce.takeIf { section == BrowseSection.Schedule } ?: 0L,
        ),
        history = FocusFirstRequest(
            transientNonce = transientNonce.takeIf { section == BrowseSection.History } ?: 0L,
        ),
    )
}

internal class BrowseFocusRuntime(
    private val scope: CoroutineScope,
    val topActionsFocusRequester: FocusRequester,
    private val uiControls: UiControlCoordinator,
) {
    var contentFocusRequestNonce by mutableLongStateOf(0L)
    var firstFocusRequestNonce by mutableLongStateOf(0L)
    var layerHasFocus by mutableStateOf(false)
    var scheduleCalendarFocusRequestNonce by mutableLongStateOf(0L)
    var activeHomeBackToTopHandler by mutableStateOf<HomeBackToTopHandler?>(null)

    fun layerFocusRequestNonce(dpadFocusEnabled: Boolean, activeFocusRequestNonce: Long): Long {
        return if (dpadFocusEnabled && activeFocusRequestNonce > 0L) {
            activeFocusRequestNonce * 1_000_000L + contentFocusRequestNonce
        } else {
            0L
        }
    }

    fun requestCurrentContentFocus(pagerRuntime: BrowsePagerRuntime): Boolean {
        uiControls.cancel(UiControlOperation.NavigationLatest)
        pagerRuntime.suppressedContentFocusSection = null
        contentFocusRequestNonce += 1L
        return true
    }

    fun requestFirstContentFocus(
        section: BrowseSection,
        pagerRuntime: BrowsePagerRuntime,
    ): Boolean {
        uiControls.cancel(UiControlOperation.NavigationLatest)
        pagerRuntime.suppressedContentFocusSection = null
        if (section == BrowseSection.Downloads) {
            contentFocusRequestNonce += 1L
        } else {
            firstFocusRequestNonce += 1L
        }
        return true
    }

    fun recoverFirstContentFocus(
        section: BrowseSection,
        pagerRuntime: BrowsePagerRuntime,
    ): Boolean {
        if (layerHasFocus) return false
        return requestFirstContentFocus(section, pagerRuntime)
    }

    fun requestScheduleCalendarFocus(
        showPhoneCalendar: Boolean,
        scheduleGridState: LazyGridState,
        browseCoordinator: BrowseRootUiCoordinator,
        pagerRuntime: BrowsePagerRuntime,
    ): Boolean {
        pagerRuntime.suppressedContentFocusSection = null
        uiControls.cancel(UiControlOperation.NavigationLatest)
        if (showPhoneCalendar) {
            scheduleCalendarFocusRequestNonce += 1L
            return true
        }
        uiControls.launch(scope, this, UiControlOperation.NavigationLatest) {
            if (scheduleGridState.firstVisibleItemIndex != 0 || scheduleGridState.firstVisibleItemScrollOffset != 0) {
                browseCoordinator.scrollToTop(BrowseSection.Schedule)
            }
            withFrameNanos { }
            scheduleCalendarFocusRequestNonce += 1L
        }
        return true
    }

    fun requestTopActionsFocus(
        topBarFullyVisible: Boolean,
        dpadFocusEnabled: Boolean,
        section: BrowseSection,
        browseCoordinator: BrowseRootUiCoordinator,
    ): Boolean {
        uiControls.cancel(UiControlOperation.NavigationLatest)
        if (topBarFullyVisible && dpadFocusEnabled && topActionsFocusRequester.requestFocusSafely()) {
            return true
        }
        uiControls.launch(scope, this, UiControlOperation.NavigationLatest) {
            browseCoordinator.scrollToTop(section)
            withFrameNanos { }
            if (dpadFocusEnabled) {
                topActionsFocusRequester.requestFocusSafely()
            }
        }
        return true
    }

    fun requestSectionTabsFocus(
        section: BrowseSection,
        releasePagerFocusTransition: Boolean,
        dpadFocusEnabled: Boolean,
        forcedOfflineMode: Boolean,
        sectionFocusRequesters: Map<BrowseSection, FocusRequester>,
        pagerRuntime: BrowsePagerRuntime,
    ): Boolean {
        if (!dpadFocusEnabled || forcedOfflineMode) return false
        if (releasePagerFocusTransition) {
            pagerRuntime.releaseFocusTransition()
        }
        val requester = sectionFocusRequesters[section] ?: return false
        uiControls.cancel(UiControlOperation.NavigationLatest)
        if (releasePagerFocusTransition) {
            uiControls.launch(scope, this, UiControlOperation.NavigationLatest) {
                withFrameNanos { }
                requester.requestFocusSafely()
            }
            return true
        }
        return requester.requestFocusSafely()
    }

    fun updateHomeBackToTopHandler(
        section: BrowseSection,
        handler: HomeBackToTopHandler?,
        onRegister: (BrowseSection, HomeBackToTopHandler?) -> Unit,
    ) {
        if (handler == null) {
            if (activeHomeBackToTopHandler?.section == section) {
                activeHomeBackToTopHandler = null
            }
        } else {
            activeHomeBackToTopHandler = handler
        }
        onRegister(section, handler)
    }

    fun scrollScheduleToStart(scheduleGridState: LazyGridState) {
        uiControls.launch(scope, this, UiControlOperation.NavigationLatest) {
            scheduleGridState.animateScrollToItem(0, 0)
        }
    }
}

internal data class BrowseFocusBinding(
    val runtime: BrowseFocusRuntime,
    val sectionFocusRequesters: Map<BrowseSection, FocusRequester>,
)

internal data class BrowseFocusActions(
    val requestCurrentContentFocus: () -> Boolean,
    val requestFirstContentFocus: () -> Boolean,
    val recoverFirstContentFocus: () -> Boolean,
    val requestScheduleCalendarFocus: () -> Boolean,
    val requestTopActionsFocus: () -> Boolean,
    val requestSectionTabsFocus: (BrowseSection, Boolean) -> Boolean,
    val updateHomeBackToTopHandler: (BrowseSection, HomeBackToTopHandler?) -> Unit,
)

internal fun BrowseFocusRuntime.bindActions(
    section: BrowseSection,
    dpadFocusEnabled: Boolean,
    forcedOfflineMode: Boolean,
    showPhoneScheduleCalendar: Boolean,
    scheduleGridState: LazyGridState,
    browseCoordinator: BrowseRootUiCoordinator,
    sectionFocusRequesters: Map<BrowseSection, FocusRequester>,
    pagerRuntime: BrowsePagerRuntime,
    topBarFullyVisible: () -> Boolean,
    onRegisterHomeBackToTopHandler: (BrowseSection, HomeBackToTopHandler?) -> Unit,
): BrowseFocusActions {
    return BrowseFocusActions(
        requestCurrentContentFocus = { requestCurrentContentFocus(pagerRuntime) },
        requestFirstContentFocus = { requestFirstContentFocus(section, pagerRuntime) },
        recoverFirstContentFocus = { recoverFirstContentFocus(section, pagerRuntime) },
        requestScheduleCalendarFocus = {
            requestScheduleCalendarFocus(
                showPhoneScheduleCalendar,
                scheduleGridState,
                browseCoordinator,
                pagerRuntime,
            )
        },
        requestTopActionsFocus = {
            requestTopActionsFocus(
                topBarFullyVisible(),
                dpadFocusEnabled,
                section,
                browseCoordinator,
            )
        },
        requestSectionTabsFocus = { targetSection, releaseTransition ->
            requestSectionTabsFocus(
                targetSection,
                releaseTransition,
                dpadFocusEnabled,
                forcedOfflineMode,
                sectionFocusRequesters,
                pagerRuntime,
            )
        },
        updateHomeBackToTopHandler = { targetSection, handler ->
            updateHomeBackToTopHandler(targetSection, handler, onRegisterHomeBackToTopHandler)
        },
    )
}

@Composable
internal fun rememberBrowseFocusBinding(sections: List<BrowseSection>): BrowseFocusBinding {
    val scope = rememberCoroutineScope()
    val uiControls = LocalUiControlCoordinator.current
    val topActionsFocusRequester = remember { FocusRequester() }
    val runtime = remember(scope, topActionsFocusRequester, uiControls) {
        BrowseFocusRuntime(scope, topActionsFocusRequester, uiControls)
    }
    val sectionFocusRequesters = remember(sections) {
        sections.associateWith { FocusRequester() }
    }
    return remember(runtime, sectionFocusRequesters) {
        BrowseFocusBinding(runtime, sectionFocusRequesters)
    }
}

// BrowseHomeFocusPolicy
internal data class FocusFirstRequest(
    val persistentNonce: Long = 0L,
    val transientNonce: Long = 0L,
)

internal data class PagerAlignmentState(
    val isScrollInProgress: Boolean,
    val settledPage: Int,
    val currentPage: Int,
    val offset: Float,
)

internal fun PagerAlignmentState.isSettledAt(page: Int): Boolean {
    return !isScrollInProgress &&
        settledPage == page &&
        currentPage == page &&
        abs(offset) <= BrowsePagerAlignmentTolerance
}

internal fun browsePageCanReceiveFocus(
    active: Boolean,
    dpadFocusEnabled: Boolean,
    contentFocusSuppressed: Boolean,
    page: Int,
    targetPage: Int,
    pagerSettledAtTarget: Boolean,
    programmaticScrollTarget: Int?,
    transitionFocusSourcePage: Int?,
): Boolean {
    if (focusIsDisabled(active, dpadFocusEnabled, contentFocusSuppressed)) return false
    return page.isSettledFocusTarget(targetPage, pagerSettledAtTarget) ||
        page.isProgrammaticFocusTarget(targetPage, programmaticScrollTarget) ||
        page.isTransitionFocusSource(
            pagerSettledAtTarget = pagerSettledAtTarget,
            programmaticScrollTarget = programmaticScrollTarget,
            transitionFocusSourcePage = transitionFocusSourcePage,
        )
}

private fun focusIsDisabled(
    active: Boolean,
    dpadFocusEnabled: Boolean,
    contentFocusSuppressed: Boolean,
): Boolean = !active || !dpadFocusEnabled || contentFocusSuppressed

private fun Int.isSettledFocusTarget(
    targetPage: Int,
    pagerSettledAtTarget: Boolean,
): Boolean = this == targetPage && pagerSettledAtTarget

private fun Int.isProgrammaticFocusTarget(
    targetPage: Int,
    programmaticScrollTarget: Int?,
): Boolean = programmaticScrollTarget == this && this == targetPage

private fun Int.isTransitionFocusSource(
    pagerSettledAtTarget: Boolean,
    programmaticScrollTarget: Int?,
    transitionFocusSourcePage: Int?,
): Boolean {
    return transitionFocusSourcePage == this &&
        programmaticScrollTarget != null &&
        !pagerSettledAtTarget
}

private const val BrowsePagerAlignmentTolerance = 0.001f

// BrowseHomeLayout
@Composable
internal fun BrowseHomeLayout(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { focusState ->
                actions.onLayerFocusChanged(focusState.isFocused || focusState.hasFocus)
            }
            .focusGroup(),
    ) {
        BrowseHomeContentLayout(state, actions)
        if (state.chromePolicy.showBottomChrome) {
            BrowseHomeBottomChrome(state, actions)
        }
    }
}

// BrowseHomeLayoutChrome
@Composable
internal fun BrowseHomeContentLayout(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BrowseHomeTopChrome(state, actions)
        Box(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (state.chromeHazeActive) {
                        Modifier.hazeSource(state.chromeHazeState)
                    } else {
                        Modifier
                    },
                ),
        ) {
            BrowsePageHost(state, actions)
        }
    }
}

@Composable
private fun BrowseHomeTopChrome(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
) {
    if (state.chromePolicy.pinTopChrome) {
        BrowseTopBarChrome(state, actions, collapseWhenHidden = false)
        if (state.chromePolicy.showTvSectionTabs) {
            BrowseHomeTvTabs(state, actions)
        }
    } else {
        BrowseTopBarChrome(state, actions)
    }
}

@Composable
private fun BrowseHomeTvTabs(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
) {
    BrowseHomeTvSectionTabs(
        state = state.homeChromeState,
        sectionFocusRequesters = state.sectionTabFocusRequesters,
        sectionTabsFocusEnabled = state.sectionTabsFocusEnabled,
        onSectionSelected = actions.onSectionSelected,
        onExitUp = actions.onRequestTopActionsFocus,
        onExitDown = {
            if (state.effectiveSection == BrowseSection.Schedule) {
                actions.onRequestScheduleCalendarFocus()
            } else {
                actions.onRequestContentFocus()
            }
        },
    )
}

@Composable
private fun BrowseTopBarChrome(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
    collapseWhenHidden: Boolean = true,
) {
    val wideSectionTabsVisible = state.isWide && !state.forcedOfflineMode
    BrowseHomeTopBar(
        state = state.homeChromeState,
        onOpenSearch = actions.onOpenSearch,
        onOpenFilters = actions.onOpenFilters,
        onOpenSettings = actions.onOpenSettings,
        onOpenDownloads = actions.onOpenDownloads,
        onOpenLogin = actions.onOpenLogin,
        onOpenProfile = actions.onOpenProfile,
        onSectionSelected = actions.onSectionSelected,
        onExitDown = {
            if (wideSectionTabsVisible) {
                actions.onRequestSectionTabsFocus(state.effectiveSection, false)
            } else {
                actions.onRequestContentFocus()
            }
        },
        actionsFocusRequester = state.topActionsFocusRequester,
        sectionTabsFocusRequester = if (wideSectionTabsVisible) {
            state.sectionTabFocusRequesters[state.effectiveSection]
        } else {
            null
        },
        sectionTabFocusRequesters = state.sectionTabFocusRequesters,
        sectionTabsFocusEnabled = state.sectionTabsFocusEnabled,
        collapseWhenHidden = collapseWhenHidden,
        visible = state.topBarVisible,
        visibilityProgressProvider = state.topBarVisibilityProgressProvider,
    )
}

// BrowseHomeLayoutModel
internal data class BrowseHomeLayoutState(
    val active: Boolean,
    val dpadFocusEnabled: Boolean,
    val chromePolicy: BrowseChromePolicy,
    val chromeHazeState: HazeState,
    val chromeHazeActive: Boolean,
    val homeChromeState: BrowseHomeChromeState,
    val topBarVisible: Boolean,
    val topBarVisibilityProgressProvider: () -> Float,
    val effectiveSection: BrowseSection,
    val pagerSections: List<BrowseSection>,
    val pagerPage: Int,
    val usePager: Boolean,
    val pageStateHolder: SaveableStateHolder,
    val pagerState: PagerState,
    val pagerSettledAtTarget: Boolean,
    val programmaticScrollTarget: Int?,
    val transitionFocusSourcePage: Int?,
    val suppressedContentFocusSection: BrowseSection?,
    val dpadLayerFocusRequestNonce: Long,
    val contentFocusRequestNonce: Long,
    val topActionsFocusRequester: FocusRequester,
    val sectionTabFocusRequesters: Map<BrowseSection, FocusRequester>,
    val sectionTabsFocusEnabled: Boolean,
    val isWide: Boolean,
    val forcedOfflineMode: Boolean,
    val showPhoneScheduleCalendar: Boolean,
    val showPhoneScheduleCalendarVisual: Boolean,
    val phoneScheduleDayGroups: List<ScheduleDayGroup>,
    val selectedScheduleEpochDay: Long,
    val scheduleLocale: Locale,
    val scheduleCalendarFocusRequestNonce: Long,
    val scheduleCalendarVisualProgress: Float,
)

internal data class BrowseHomeLayoutActions(
    val onLayerFocusChanged: (Boolean) -> Unit,
    val onOpenSearch: () -> Unit,
    val onOpenFilters: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenDownloads: () -> Unit,
    val onOpenLogin: () -> Unit,
    val onOpenProfile: () -> Unit,
    val onSectionSelected: (BrowseSection) -> Unit,
    val onRequestTopActionsFocus: () -> Boolean,
    val onRequestSectionTabsFocus: (BrowseSection, Boolean) -> Boolean,
    val onRequestScheduleCalendarFocus: () -> Boolean,
    val onRequestContentFocus: () -> Boolean,
    val onScheduleDaySelected: (Long) -> Unit,
    val onBottomChromeMeasured: (heightPx: Int, expanded: Boolean) -> Unit,
    val sectionPage: @Composable (
        section: BrowseSection,
        page: Int,
        canReceiveFocus: Boolean,
        focusRequestNonce: Long,
    ) -> Unit,
)

// BrowseHomePageHost
@Composable
internal fun BrowsePageHost(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
) {
    when {
        state.effectiveSection == BrowseSection.Downloads -> {
            state.pageStateHolder.SaveableStateProvider(BrowseSection.Downloads) {
                actions.sectionPage(
                    BrowseSection.Downloads,
                    state.pagerPage,
                    state.active && state.dpadFocusEnabled,
                    state.dpadLayerFocusRequestNonce,
                )
            }
        }
        !state.usePager -> BrowseSinglePage(state, actions)
        else -> BrowseHorizontalPager(state, actions)
    }
}

@Composable
private fun BrowseSinglePage(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
) {
    val focusSuppressed = state.effectiveSection == state.suppressedContentFocusSection
    state.pageStateHolder.SaveableStateProvider(state.effectiveSection) {
        actions.sectionPage(
            state.effectiveSection,
            state.pagerPage,
            state.active && state.dpadFocusEnabled && !focusSuppressed,
            if (focusSuppressed) 0L else state.contentFocusRequestNonce,
        )
    }
}

@Composable
private fun BrowseHorizontalPager(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
) {
    HorizontalPager(
        state = state.pagerState,
        beyondViewportPageCount = 1,
        userScrollEnabled = state.active,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        BrowsePagerPage(state, actions, page)
    }
}

@Composable
private fun BrowsePagerPage(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
    page: Int,
) {
    val section = state.pagerSections.getOrNull(page) ?: BrowseSection.Catalog
    val canReceiveFocus = browsePageCanReceiveFocus(
        active = state.active,
        dpadFocusEnabled = state.dpadFocusEnabled,
        contentFocusSuppressed = section == state.suppressedContentFocusSection,
        page = page,
        targetPage = state.pagerPage,
        pagerSettledAtTarget = state.pagerSettledAtTarget,
        programmaticScrollTarget = state.programmaticScrollTarget,
        transitionFocusSourcePage = state.transitionFocusSourcePage,
    )
    val focusRequestNonce = if (canReceiveFocus && page == state.pagerPage) {
        state.contentFocusRequestNonce
    } else {
        0L
    }
    state.pageStateHolder.SaveableStateProvider(section) {
        actions.sectionPage(section, page, canReceiveFocus, focusRequestNonce)
    }
}

// BrowseHomePolicy
internal data class BrowseChromePolicy(
    val pinTopChrome: Boolean,
    val showTvSectionTabs: Boolean,
    val showBottomChrome: Boolean,
)

internal fun resolveBrowseChromePolicy(
    isWide: Boolean,
    forcedOfflineMode: Boolean,
): BrowseChromePolicy {
    return BrowseChromePolicy(
        pinTopChrome = isWide,
        showTvSectionTabs = isWide && !forcedOfflineMode,
        showBottomChrome = !isWide,
    )
}

internal fun resolveBrowsePagerSections(
    isAuthorized: Boolean,
    forcedOfflineMode: Boolean,
): List<BrowseSection> {
    if (forcedOfflineMode) return listOf(BrowseSection.Downloads)
    return if (isAuthorized) {
        listOf(BrowseSection.Catalog, BrowseSection.History, BrowseSection.Schedule)
    } else {
        listOf(BrowseSection.Catalog, BrowseSection.Schedule)
    }
}

internal fun resolveEffectiveBrowseSection(
    requestedSection: BrowseSection,
    isAuthorized: Boolean,
    forcedOfflineMode: Boolean,
): BrowseSection {
    return when {
        forcedOfflineMode -> BrowseSection.Downloads
        requestedSection == BrowseSection.History && !isAuthorized -> BrowseSection.Catalog
        else -> requestedSection
    }
}

internal fun resolveBrowseSectionCorrection(
    requestedSection: BrowseSection,
    isAuthorized: Boolean,
    forcedOfflineMode: Boolean,
): BrowseSection? {
    return resolveEffectiveBrowseSection(requestedSection, isAuthorized, forcedOfflineMode)
        .takeUnless { section -> section == requestedSection }
}

internal fun resolveBrowseTabPosition(
    active: Boolean,
    useBrowsePager: Boolean,
    pagerPage: Int,
    pagerPosition: Float,
    programmaticTabTargetPosition: Float?,
    programmaticTabPosition: Float,
    pagerDriven: Boolean,
    effectiveSectionVisible: Boolean,
    programmaticScrollPending: Boolean,
): Float? {
    return when {
        !active -> pagerPage.toFloat()
        useBrowsePager && programmaticTabTargetPosition != null -> programmaticTabPosition
        useBrowsePager && pagerDriven -> pagerPosition
        effectiveSectionVisible || programmaticScrollPending -> pagerPage.toFloat()
        else -> null
    }
}

internal fun resolveHomeBrowseBackState(
    useBrowsePager: Boolean,
    effectiveSection: BrowseSection,
    pagerSections: List<BrowseSection>,
    pagerPosition: Float,
    pagerScrollInProgress: Boolean,
    pagerAwayFromTarget: Boolean,
): HomeBrowseBackState {
    if (!useBrowsePager || effectiveSection == BrowseSection.Downloads || pagerSections.isEmpty()) {
        return HomeBrowseBackState(effectiveSection, settledAtStateSection = true)
    }
    val visiblePage = pagerPosition.roundToInt().coerceIn(0, pagerSections.lastIndex)
    return HomeBrowseBackState(
        visualSection = pagerSections[visiblePage],
        settledAtStateSection = !pagerScrollInProgress && !pagerAwayFromTarget,
    )
}

internal fun resolvePhoneScheduleCalendarProgress(
    isWide: Boolean,
    forcedOfflineMode: Boolean,
    schedulePage: Int,
    hasScheduleDays: Boolean,
    visualPagerPosition: Float,
): Float {
    val calendarUnavailable = isWide || forcedOfflineMode
    val scheduleUnavailable = schedulePage < 0 || !hasScheduleDays
    if (calendarUnavailable || scheduleUnavailable) return 0f
    return (1f - abs(visualPagerPosition - schedulePage)).coerceIn(0f, 1f)
}

internal data class HomeBrowseBackState(
    val visualSection: BrowseSection,
    val settledAtStateSection: Boolean,
)

// BrowseHomeScreenRuntime
internal data class BrowseScreenRuntimeConfig(
    val browseCoordinator: BrowseRootUiCoordinator,
    val activeFocusRequestNonce: Long,
    val onRegisterHomeBackToTopHandler: (BrowseSection, HomeBackToTopHandler?) -> Unit,
    val onHomeBrowseBackStateChange: (HomeBrowseBackState) -> Unit,
    val onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    val onRegisterDpadFocusRecoveryHandler: ((() -> Boolean)?) -> Unit,
    val loginDialogOpen: Boolean,
    val profileDialogOpen: Boolean,
    val settingsDialogOpen: Boolean,
    val active: Boolean,
)

internal data class BrowseScreenRuntimeActions(
    val onQueryChange: (String) -> Unit,
    val onSearchSubmitted: (String) -> Unit,
    val onSearchHistorySelected: (String) -> Unit,
    val onRefresh: () -> Unit,
    val onRefreshFilterCatalog: () -> Unit,
    val onLoadMoreAnime: () -> Unit,
    val onBrowseSectionChange: (BrowseSection) -> Unit,
    val onFiltersChange: (BrowseFilters) -> Unit,
    val onResetFilters: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenDownloads: () -> Unit,
    val onClearDownloadHistory: () -> Unit,
    val onCancelDownload: (Long) -> Unit,
    val onPauseDownload: (Long) -> Unit,
    val onResumeDownload: (Long) -> Unit,
    val onOpenLogin: () -> Unit,
    val onOpenProfile: () -> Unit,
    val onOpenAnime: (Long) -> Unit,
)

@Composable
internal fun BrowseScreenRuntime(
    state: YummyDroidUiState,
    config: BrowseScreenRuntimeConfig,
    actions: BrowseScreenRuntimeActions,
) {
    val environment = rememberBrowseScreenEnvironment(
        state = state,
        browseCoordinator = config.browseCoordinator,
        onBrowseSectionChange = actions.onBrowseSectionChange,
    )
    val focusBinding = rememberBrowseFocusBinding(environment.pagerSections)
    val pagerRuntime = rememberBrowsePagerRuntime(
        initialPage = environment.pagerPage,
        initialSection = environment.effectiveSection,
        pageCount = { environment.pagerSections.size },
    )
    val visualRuntime = rememberBrowseHomeVisualRuntime()
    var scheduleSelectedEpochDay by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
    val dpadLayerFocusRequestNonce = focusBinding.runtime.layerFocusRequestNonce(
        environment.dpadFocusEnabled,
        config.activeFocusRequestNonce,
    )
    val catalogDialogRuntime = rememberBrowseCatalogDialogRuntime(
        config.active && environment.catalogActionsEnabled,
        config.onRegisterModalInputActionHandler,
    )
    val phoneSchedule = rememberBrowsePhoneScheduleRuntime(state, environment)
    val navigation = rememberBrowseScreenNavigation(
        state = state,
        environment = environment,
        config = config,
        focusBinding = focusBinding,
        pagerRuntime = pagerRuntime,
        dpadLayerFocusRequestNonce = dpadLayerFocusRequestNonce,
        phoneSchedule = phoneSchedule,
        onBrowseSectionChange = actions.onBrowseSectionChange,
    )

    BrowseHomeContent(
        model = createBrowseHomeContentModel(
            state = state,
            config = config,
            environment = environment,
            navigation = navigation,
            focusBinding = focusBinding,
            pagerRuntime = pagerRuntime,
            visualRuntime = visualRuntime,
            catalogDialogRuntime = catalogDialogRuntime,
            phoneSchedule = phoneSchedule,
            scheduleSelectedEpochDay = scheduleSelectedEpochDay,
            dpadLayerFocusRequestNonce = dpadLayerFocusRequestNonce,
        ),
        actions = actions.toBrowseHomeContentActions { epochDay ->
            scheduleSelectedEpochDay = epochDay
        },
    )
}

internal fun browseCatalogActionsEnabledForSection(
    section: BrowseSection,
    forcedOfflineMode: Boolean,
): Boolean = !forcedOfflineMode && section == BrowseSection.Catalog
