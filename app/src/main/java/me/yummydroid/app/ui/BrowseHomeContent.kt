package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import java.time.ZoneId
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.DownloadTaskState
import me.yummydroid.app.LoadState
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.ScheduleAnime
import me.yummydroid.app.readyListOrEmpty

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
    val activeDownloadCount = remember(state.downloadQueue.tasks) {
        state.downloadQueue.tasks.count { task ->
            task.state == DownloadTaskState.Queued ||
                task.state == DownloadTaskState.Running ||
                task.state == DownloadTaskState.Paused
        }
    }
    val schedulePage = model.pagerSections.indexOf(BrowseSection.Schedule)
    val scheduleCalendarVisualProgress = resolvePhoneScheduleCalendarProgress(
        isWide = model.isWide,
        forcedOfflineMode = model.forcedOfflineMode,
        schedulePage = schedulePage,
        hasScheduleDays = model.phoneScheduleDayGroups.isNotEmpty(),
        visualPagerPosition = model.pagerBinding.tabPosition ?: model.pagerPage.toFloat(),
    )
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
            if (model.catalogActionsEnabled) model.catalogDialogRuntime.searchDialogOpen = true
        },
        onOpenFilters = {
            if (model.catalogActionsEnabled) model.catalogDialogRuntime.filtersDialogOpen = true
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
    val catalogContentState = if (model.isSearching) model.state.searchResults else model.state.featured
    val catalogPagingState = if (model.isSearching) model.state.searchPaging else model.state.featuredPaging
    BrowseSectionPageContent(
        pageSection = section,
        pageIndex = page,
        pageCanReceiveFocus = canReceiveFocus,
        pageFocusCurrentRequestNonce = focusRequestNonce,
        state = model.state,
        catalogContentState = catalogContentState,
        catalogPagingState = catalogPagingState,
        browseCoordinator = model.browseCoordinator,
        catalogFocusFirstRequest = model.catalogFocusFirstRequest,
        scheduleFocusFirstRequest = model.scheduleFocusFirstRequest,
        historyFocusFirstRequest = model.historyFocusFirstRequest,
        sectionTabFocusRequesters = model.focusBinding.sectionFocusRequesters,
        catalogContentBottomPadding = catalogContentBottomPadding,
        scheduleContentBottomPadding = scheduleContentBottomPadding,
        isSearching = model.isSearching,
        isWide = model.isWide,
        forcedOfflineMode = model.forcedOfflineMode,
        tvTopChromePinned = model.chromePolicy.pinTopChrome,
        phoneScheduleDayGroups = model.phoneScheduleDayGroups,
        scheduleSelectedEpochDay = model.scheduleSelectedEpochDay,
        scheduleCalendarFocusRequestNonce = model.focusBinding.runtime.scheduleCalendarFocusRequestNonce,
        onScheduleSelectedEpochDayChange = actions.onScheduleSelectedEpochDayChange,
        onUpdateHomeBackToTopHandler = model.focusActions.updateHomeBackToTopHandler,
        onRefresh = actions.onRefresh,
        onLoadMoreAnime = actions.onLoadMoreAnime,
        onHorizontalExit = model.pagerBinding.onHorizontalExit,
        onRequestSectionTabsFocus = { releaseTransition ->
            model.focusActions.requestSectionTabsFocus(model.effectiveSection, releaseTransition)
        },
        onRequestTopActionsFocus = model.focusActions.requestTopActionsFocus,
        onRequestScheduleCalendarFocus = model.focusActions.requestScheduleCalendarFocus,
        onClearDownloadHistory = actions.onClearDownloadHistory,
        onCancelDownload = actions.onCancelDownload,
        onPauseDownload = actions.onPauseDownload,
        onResumeDownload = actions.onResumeDownload,
        onOpenAnime = actions.onOpenAnime,
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
