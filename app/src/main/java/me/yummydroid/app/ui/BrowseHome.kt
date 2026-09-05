package me.yummydroid.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.R
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.canExitRootCatalog
import me.yummydroid.app.canHandleRootHomeBackToTop
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.ScheduleAnime
import me.yummydroid.app.readyListOrEmpty
import me.yummydroid.app.ui.components.clearFocusAfterTouch
import me.yummydroid.app.ui.components.liquidGlassBackdrop
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

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
    return listOf(BrowseSection.Catalog, BrowseSection.History, BrowseSection.Schedule)
}

internal fun resolveEffectiveBrowseSection(
    requestedSection: BrowseSection,
    isAuthorized: Boolean,
    forcedOfflineMode: Boolean,
): BrowseSection {
    return when {
        forcedOfflineMode -> BrowseSection.Downloads
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

// BrowsePhoneTopChrome
@Composable
internal fun BrowsePhoneTopChrome(
    state: BrowseHomeChromeState,
    callbacks: BrowseActionCallbacks,
    navigation: BrowseTopSectionNavigation,
    showCompactControls: Boolean,
    visibility: BrowseTopChromeVisibility,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .browseTopBarVisibility(visibility)
            .browseTopBarExitDown(navigation.onExitDown)
            .padding(horizontal = BrowseChromePhoneHorizontalPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Spacer(modifier = Modifier.fillMaxWidth().statusBarsPadding())
        if (state.forcedOfflineMode) BrowsePhoneOfflineIndicator()
        if (showCompactControls) {
            BrowseSectionTabs(
                activeSection = state.activeSection,
                visibleSections = state.visibleSections,
                activeSectionPosition = state.activeSectionPosition,
                onSectionSelected = navigation.onSectionSelected,
                sectionFocusRequesters = navigation.sectionTabFocusRequesters,
                focusEnabled = navigation.sectionTabsFocusEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            val stackActions = currentWindowSizeDp().width < 360.dp
            BrowseChromeActions(
                state = state,
                callbacks = callbacks,
                modifier = Modifier.fillMaxWidth(),
                spreadActions = !stackActions,
                stackActions = stackActions,
                entryFocusRequester = navigation.actionsFocusRequester,
            )
        }
    }
}
@Composable
internal fun AppWordmark(
    modifier: Modifier = Modifier,
    height: Dp,
) {
    Box(
        modifier = modifier.height(height),
        contentAlignment = Alignment.CenterStart,
    ) {
        Image(
            painter = painterResource(R.drawable.app_wordmark),
            contentDescription = "YummyDroid",
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier.fillMaxHeight().width(height * 5.45f),
        )
    }
}

@Composable
private fun BrowsePhoneOfflineIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        OfflineModeChip()
    }
}
// BrowseSectionKeyNavigation
internal fun Modifier.browseSectionKeyNavigation(
    focusEnabled: Boolean,
    focusedSection: () -> BrowseSection?,
    visibleSections: List<BrowseSection>,
    onExitUp: (() -> Boolean)?,
    onExitDown: (() -> Boolean)?,
): Modifier {
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionUp -> onExitUp.consumeSectionExit()
            Key.DirectionDown -> onExitDown.consumeSectionExit()
            Key.DirectionLeft -> isFocusedSectionEdge(
                focusEnabled,
                focusedSection(),
                visibleSections,
                edgeIndex = 0,
            )
            Key.DirectionRight -> isFocusedSectionEdge(
                focusEnabled,
                focusedSection(),
                visibleSections,
                edgeIndex = visibleSections.lastIndex,
            )
            else -> false
        }
    }
}

private fun (() -> Boolean)?.consumeSectionExit(): Boolean {
    if (this == null) return false
    return invoke()
}

private fun isFocusedSectionEdge(
    focusEnabled: Boolean,
    focusedSection: BrowseSection?,
    visibleSections: List<BrowseSection>,
    edgeIndex: Int,
): Boolean {
    if (!focusEnabled) return false
    val focusedIndex = focusedSection?.let(visibleSections::indexOf) ?: -1
    return focusedIndex == edgeIndex
}
// BrowseSectionTab
private data class BrowseSectionTabStyle(
    val shape: Shape,
    val surfaceColor: Color,
    val contentColor: Color,
)

@Composable
internal fun BrowseSectionTab(
    section: BrowseSection,
    selectedFraction: Float,
    focusEnabled: Boolean,
    squareTopCorners: Boolean,
    focusRequester: FocusRequester?,
    focusedSection: BrowseSection?,
    onFocusedSectionChanged: (BrowseSection?) -> Unit,
    onSectionSelected: (BrowseSection) -> Unit,
    modifier: Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focusEnabled) {
        if (!focusEnabled) {
            focused = false
            if (focusedSection == section) onFocusedSectionChanged(null)
        }
    }
    val focusVisible = focusEnabled &&
        focused &&
        LocalInputModeManager.current.inputMode != InputMode.Touch
    val style = resolveBrowseSectionTabStyle(focusVisible, squareTopCorners)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { focusState ->
                val hasFocus = focusState.isFocused || focusState.hasFocus
                focused = hasFocus
                when {
                    hasFocus -> onFocusedSectionChanged(section)
                    focusedSection == section -> onFocusedSectionChanged(null)
                }
            }
            .focusProperties { canFocus = focusEnabled }
            .clearFocusAfterTouch()
            .clip(style.shape)
            .background(style.surfaceColor, style.shape)
            .clickable(interactionSource = interactionSource, indication = null) {
                onSectionSelected(section)
            },
    ) {
        BrowseSectionTabContent(section, selectedFraction, style.contentColor)
    }
}

@Composable
private fun resolveBrowseSectionTabStyle(
    focusVisible: Boolean,
    squareTopCorners: Boolean,
): BrowseSectionTabStyle {
    val shape = if (squareTopCorners) {
        RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = 7.dp, bottomStart = 7.dp)
    } else {
        RoundedCornerShape(7.dp)
    }
    return BrowseSectionTabStyle(
        shape = shape,
        surfaceColor = if (focusVisible) yummyActionSurfaceColor(focused = true) else yummyActionSurfaceColor(),
        contentColor = if (focusVisible) {
            yummyActionContentColor(focused = true)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.96f)
        },
    )
}

@Composable
private fun BoxScope.BrowseSectionTabContent(
    section: BrowseSection,
    selectedFraction: Float,
    contentColor: Color,
) {
    Text(
        text = section.localizedTitle(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = YummySpacing.xs),
    )
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(3.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = selectedFraction),
                shape = RoundedCornerShape(1.dp),
            ),
    )
}
// BrowseSectionTabs
internal val BrowseSectionTabsHeight = 32.dp

internal fun browseSectionIndicatorFraction(activePosition: Float?, index: Int): Float {
    return activePosition
        ?.let { position -> (1f - abs(position - index)).coerceIn(0f, 1f) }
        ?: 0f
}

@Composable
internal fun BrowseSectionTabs(
    activeSection: BrowseSection,
    visibleSections: List<BrowseSection>,
    modifier: Modifier = Modifier,
    activeSectionPosition: Float? = null,
    onSectionSelected: (BrowseSection) -> Unit,
    sectionFocusRequesters: Map<BrowseSection, FocusRequester> = emptyMap(),
    onExitUp: (() -> Boolean)? = null,
    onExitDown: (() -> Boolean)? = null,
    squareTopCorners: Boolean = false,
    focusEnabled: Boolean = true,
) {
    val activePosition = activeSectionPosition
        ?: visibleSections.indexOf(activeSection).takeIf { index -> index >= 0 }?.toFloat()
    var focusedSection by remember(visibleSections) { mutableStateOf<BrowseSection?>(null) }
    Row(
        modifier = modifier
            .height(BrowseSectionTabsHeight)
            .browseSectionKeyNavigation(
                focusEnabled = focusEnabled,
                focusedSection = { focusedSection },
                visibleSections = visibleSections,
                onExitUp = onExitUp,
                onExitDown = onExitDown,
            ),
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleSections.forEachIndexed { index, section ->
            BrowseSectionTab(
                section = section,
                selectedFraction = browseSectionIndicatorFraction(activePosition, index),
                focusEnabled = focusEnabled,
                squareTopCorners = squareTopCorners,
                focusRequester = sectionFocusRequesters[section],
                focusedSection = focusedSection,
                onFocusedSectionChanged = { focused -> focusedSection = focused },
                onSectionSelected = onSectionSelected,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}
// BrowseTopChrome
internal val BrowseChromePhoneHorizontalPadding = 16.dp
internal val BrowseChromeWideHorizontalPadding = 24.dp

internal data class BrowseTopSectionNavigation(
    val onSectionSelected: (BrowseSection) -> Unit,
    val onExitDown: (() -> Boolean)?,
    val actionsFocusRequester: FocusRequester?,
    val sectionTabsFocusRequester: FocusRequester?,
    val sectionTabFocusRequesters: Map<BrowseSection, FocusRequester>,
    val sectionTabsFocusEnabled: Boolean,
)

internal data class BrowseTopChromeVisibility(
    val collapseWhenHidden: Boolean,
    val visible: Boolean,
    val progress: Float?,
    val progressProvider: (() -> Float)?,
)

@Composable
internal fun BrowseTopBarModern(
    state: BrowseHomeChromeState,
    callbacks: BrowseActionCallbacks,
    navigation: BrowseTopSectionNavigation,
    showCompactControls: Boolean,
    visibility: BrowseTopChromeVisibility,
    modifier: Modifier = Modifier,
) {
    if (state.isWide) {
        BrowseWideTopChrome(state, callbacks, navigation, visibility, modifier)
    } else {
        BrowsePhoneTopChrome(
            state = state,
            callbacks = callbacks,
            navigation = navigation,
            showCompactControls = showCompactControls,
            visibility = visibility,
            modifier = modifier,
        )
    }
}
// BrowseTopChromeVisibility
@Composable
internal fun Modifier.browseTopBarVisibility(visibility: BrowseTopChromeVisibility): Modifier {
    val animatedProgress = if (visibility.progress == null && visibility.progressProvider == null) {
        val progress by animateFloatAsState(
            targetValue = if (visibility.visible) 1f else 0f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "browseTopBarVisibility",
        )
        progress
    } else {
        null
    }
    fun progress(): Float = (
        visibility.progressProvider?.invoke() ?: visibility.progress ?: animatedProgress ?: 0f
    ).coerceIn(0f, 1f)

    return this
        .then(if (visibility.visible) Modifier else Modifier.focusProperties { canFocus = false })
        .layout { measurable, constraints ->
            val resolvedProgress = progress()
            val placeable = measurable.measure(constraints)
            val height = if (visibility.collapseWhenHidden) {
                (placeable.height * resolvedProgress).roundToInt()
            } else {
                placeable.height
            }
            val offsetY = if (visibility.collapseWhenHidden) {
                height - placeable.height
            } else {
                ((resolvedProgress - 1f) * placeable.height).roundToInt()
            }
            layout(width = placeable.width, height = height) {
                placeable.placeRelative(x = 0, y = offsetY)
            }
        }
        .clipToBounds()
        .graphicsLayer { alpha = if (visibility.collapseWhenHidden) progress() else 1f }
}

internal fun Modifier.browseTopBarExitDown(onExitDown: (() -> Boolean)?): Modifier {
    if (onExitDown == null) return this
    return onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
            onExitDown()
        } else {
            false
        }
    }
}
// BrowseTvChrome
internal val BrowseTvSectionIndicatorHeight = 56.dp
private val BrowseTvSectionIndicatorGlassExtraHeight = 96.dp
private const val BrowseTvSectionIndicatorGlassIntensity = 1.85f
private val BrowseTvSectionIndicatorHorizontalPadding = 24.dp

internal data class BrowseTvBackdropPolicy(
    val enabled: Boolean,
    val visible: Boolean,
    val animateAlpha: Boolean,
    val fallbackAlpha: Float,
)

internal fun browseTvBackdropPolicy(
    drawBackdrop: Boolean,
    backdropVisible: Boolean,
    hasProgress: Boolean,
    hasProgressProvider: Boolean,
): BrowseTvBackdropPolicy {
    val hasDynamicProgress = hasProgress || hasProgressProvider
    val visible = drawBackdrop && (backdropVisible || hasDynamicProgress)
    return BrowseTvBackdropPolicy(
        enabled = drawBackdrop,
        visible = visible,
        animateAlpha = visible && !hasDynamicProgress,
        fallbackAlpha = if (drawBackdrop && backdropVisible) 1f else 0f,
    )
}

@Composable
internal fun BrowseTvSectionIndicatorBar(
    activeSection: BrowseSection,
    visibleSections: List<BrowseSection>,
    modifier: Modifier = Modifier,
    activeSectionPosition: Float? = null,
    onSectionSelected: (BrowseSection) -> Unit,
    sectionFocusRequesters: Map<BrowseSection, FocusRequester> = emptyMap(),
    onExitUp: (() -> Boolean)? = null,
    onExitDown: (() -> Boolean)? = null,
    drawBackdrop: Boolean = true,
    backdropVisible: Boolean = true,
    backdropProgress: Float? = null,
    backdropProgressProvider: (() -> Float)? = null,
    sectionTabsFocusEnabled: Boolean = true,
    squareTopCorners: Boolean = true,
    hazeState: HazeState? = null,
) {
    val backdropPolicy = browseTvBackdropPolicy(
        drawBackdrop = drawBackdrop,
        backdropVisible = backdropVisible,
        hasProgress = backdropProgress != null,
        hasProgressProvider = backdropProgressProvider != null,
    )
    val backdropAlpha = rememberBrowseTvBackdropAlpha(
        policy = backdropPolicy,
        backdropProgress = backdropProgress,
        backdropProgressProvider = backdropProgressProvider,
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(browseTvSectionBarHeight(drawBackdrop)),
    ) {
        BrowseTvSectionBackdrop(
            visible = backdropPolicy.visible,
            alpha = backdropAlpha,
            hazeState = hazeState,
        )
        BrowseTvSectionPointerLayer(visibleSections)
        BrowseSectionTabs(
            activeSection = activeSection,
            visibleSections = visibleSections,
            activeSectionPosition = activeSectionPosition,
            onSectionSelected = onSectionSelected,
            sectionFocusRequesters = sectionFocusRequesters,
            onExitUp = onExitUp,
            onExitDown = onExitDown,
            squareTopCorners = squareTopCorners,
            focusEnabled = sectionTabsFocusEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = BrowseTvSectionIndicatorHorizontalPadding,
                    end = BrowseTvSectionIndicatorHorizontalPadding,
                ),
        )
    }
}

@Composable
private fun rememberBrowseTvBackdropAlpha(
    policy: BrowseTvBackdropPolicy,
    backdropProgress: Float?,
    backdropProgressProvider: (() -> Float)?,
): () -> Float {
    val animatedAlpha = if (policy.animateAlpha) {
        val value by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "browseTabsBackdropAlpha",
        )
        value
    } else {
        null
    }
    return {
        resolveBrowseTvBackdropAlpha(
            policy = policy,
            backdropProgress = backdropProgress,
            backdropProgressProvider = backdropProgressProvider,
            animatedAlpha = animatedAlpha,
        )
    }
}

internal fun resolveBrowseTvBackdropAlpha(
    policy: BrowseTvBackdropPolicy,
    backdropProgress: Float?,
    backdropProgressProvider: (() -> Float)?,
    animatedAlpha: Float?,
): Float {
    if (!policy.enabled) return 0f
    return (backdropProgressProvider?.invoke() ?: backdropProgress ?: animatedAlpha ?: policy.fallbackAlpha)
        .coerceIn(0f, 1f)
}

private fun browseTvSectionBarHeight(drawBackdrop: Boolean): Dp {
    return if (drawBackdrop) {
        BrowseTvSectionIndicatorHeight + BrowseTvSectionIndicatorGlassExtraHeight
    } else {
        BrowseSectionTabsHeight
    }
}

@Composable
private fun BrowseTvSectionBackdrop(
    visible: Boolean,
    alpha: () -> Float,
    hazeState: HazeState?,
) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha() }
            .liquidGlassBackdrop(
                shape = RoundedCornerShape(0.dp),
                intensity = BrowseTvSectionIndicatorGlassIntensity,
                hazeState = hazeState,
                topFadeFraction = 0f,
                bottomFadeFraction = 0.56f,
            ),
    )
}

@Composable
private fun BrowseTvSectionPointerLayer(visibleSections: List<BrowseSection>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BrowseSectionTabsHeight),
        verticalAlignment = Alignment.Top,
    ) {
        BrowseTvSectionPointerEdge("tv-tabs-left-edge")
        visibleSections.forEachIndexed { index, _ ->
            Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
            if (index < visibleSections.lastIndex) {
                Spacer(
                    modifier = Modifier
                        .width(YummySpacing.md)
                        .fillMaxHeight()
                        .consumeUnhandledPointerInput("tv-tabs-gap-$index"),
                )
            }
        }
        BrowseTvSectionPointerEdge("tv-tabs-right-edge")
    }
}

@Composable
private fun BrowseTvSectionPointerEdge(key: String) {
    Spacer(
        modifier = Modifier
            .width(BrowseTvSectionIndicatorHorizontalPadding)
            .fillMaxHeight()
            .consumeUnhandledPointerInput(key),
    )
}
// BrowseWideTopChrome
@Composable
internal fun BrowseWideTopChrome(
    state: BrowseHomeChromeState,
    callbacks: BrowseActionCallbacks,
    navigation: BrowseTopSectionNavigation,
    visibility: BrowseTopChromeVisibility,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .browseTopBarVisibility(visibility)
            .browseTopBarExitDown(navigation.onExitDown)
            .statusBarsPadding()
            .padding(horizontal = BrowseChromeWideHorizontalPadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppWordmark(modifier = Modifier.weight(1f), height = 52.dp)
            if (state.forcedOfflineMode) OfflineModeChip()
            BrowseChromeActions(
                state = state,
                callbacks = callbacks,
                entryFocusRequester = navigation.actionsFocusRequester,
                downFocusRequester = navigation.sectionTabsFocusRequester,
                consumeUpWhenNoRequester = true,
                consumeHorizontalEdgesWhenNoRequester = true,
            )
        }
    }
}

// BrowseBottomChrome
internal data class BrowseBottomSectionNavigation(
    val onSectionSelected: (BrowseSection) -> Unit,
    val focusRequester: FocusRequester?,
    val focusRequesters: Map<BrowseSection, FocusRequester>,
    val onExitUp: (() -> Boolean)?,
    val focusEnabled: Boolean,
)

@Composable
internal fun BrowseBottomBarModern(
    state: BrowseHomeChromeState,
    actions: BrowseActionCallbacks,
    sectionNavigation: BrowseBottomSectionNavigation,
    hazeState: HazeState?,
    topProtectedContent: (@Composable (Modifier) -> Unit)?,
    topProtectedVisibilityProgress: Float?,
    modifier: Modifier = Modifier,
) {
    BrowseBottomChromeLayout(
        state = state,
        actions = actions,
        sectionNavigation = sectionNavigation,
        showSectionTabs = !state.forcedOfflineMode,
        hazeState = hazeState,
        topProtectedContent = topProtectedContent,
        topProtectedVisibilityProgress = topProtectedVisibilityProgress,
        modifier = modifier,
    )
}
// BrowseBottomChromeGeometry
internal class BrowseBottomChromeGeometryState {
    internal var barTopRootY by mutableFloatStateOf(0f)
    internal var barHeightPx by mutableIntStateOf(0)
    internal var baseControlsHeightPx by mutableIntStateOf(0)
    internal var pointerBlockStartY by mutableStateOf<Float?>(null)
}
private fun BrowseBottomChromeGeometryState.clearPointerBlockStart() {
    pointerBlockStartY = null
}

private fun BrowseBottomChromeGeometryState.pointerBlockHeight(
    density: Density,
    fallbackStart: Dp,
): Dp = with(density) {
    val start = pointerBlockStartY ?: fallbackStart.toPx()
    (barHeightPx - start).coerceAtLeast(0f).toDp()
}

private fun BrowseBottomChromeGeometryState.baseControlsContentHeight(
    density: Density,
    contentTopPadding: Dp,
): Dp {
    val controlsHeight = if (baseControlsHeightPx > 0) {
        with(density) { baseControlsHeightPx.toDp() }
    } else {
        contentTopPadding + BrowseBottomBaseControlsFallbackHeight
    }
    return (controlsHeight - contentTopPadding).coerceAtLeast(0.dp)
}

private fun Modifier.trackBrowseBottomBar(geometry: BrowseBottomChromeGeometryState): Modifier = this
    .onSizeChanged { size -> geometry.barHeightPx = size.height }
    .onGloballyPositioned { coordinates ->
        geometry.barTopRootY = coordinates.positionInRoot().y
    }

private fun Modifier.trackBrowseBaseControls(geometry: BrowseBottomChromeGeometryState): Modifier {
    return onSizeChanged { size -> geometry.baseControlsHeightPx = size.height }
}

private fun Modifier.browsePointerBlockStartAnchor(geometry: BrowseBottomChromeGeometryState): Modifier {
    return onGloballyPositioned { coordinates ->
        geometry.pointerBlockStartY = (coordinates.positionInRoot().y - geometry.barTopRootY).coerceAtLeast(0f)
    }
}
// BrowseBottomChromeLayout
private val BrowseBottomBarGlassTopFadeHeight = 32.dp
internal val BrowseBottomChromeInteractiveTopPadding = BrowseBottomBarGlassTopFadeHeight + 10.dp
internal val BrowseChromeItemGap = 8.dp
internal val BrowseBottomBaseControlsFallbackHeight = 96.dp
private val BrowseBottomCalendarToTabsGap = BrowseChromeItemGap

@Composable
internal fun BrowseBottomChromeLayout(
    state: BrowseHomeChromeState,
    actions: BrowseActionCallbacks,
    sectionNavigation: BrowseBottomSectionNavigation,
    showSectionTabs: Boolean,
    hazeState: HazeState?,
    topProtectedContent: (@Composable (Modifier) -> Unit)?,
    topProtectedVisibilityProgress: Float?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val geometry = remember { BrowseBottomChromeGeometryState() }
    val actionFocusRequester = remember { FocusRequester() }
    val protectedContent = rememberBrowseBottomProtectedContentState(
        content = topProtectedContent,
        visibilityProgress = topProtectedVisibilityProgress,
    )
    LaunchedEffect(protectedContent.active, showSectionTabs) {
        geometry.clearPointerBlockStart()
    }
    val pointerBlockHeight = geometry.pointerBlockHeight(
        density = density,
        fallbackStart = BrowseBottomChromeInteractiveTopPadding,
    )
    val baseContentHeight = geometry.baseControlsContentHeight(
        density = density,
        contentTopPadding = BrowseBottomChromeInteractiveTopPadding,
    )

    val baseBackdropHeight = baseContentHeight + BrowseBottomChromeInteractiveTopPadding
    val trackedModifier = modifier.fillMaxWidth().trackBrowseBottomBar(geometry)
    Box(modifier = trackedModifier) {
        BrowseBottomChromeBackdrop(
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(baseBackdropHeight),
        )
        BrowseBottomPointerBlock(
            height = pointerBlockHeight,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        protectedContent.content?.let { content ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = baseContentHeight,
                    )
                    .browseBottomTopProtectedVisibility(protectedContent.progress)
                    .browsePointerBlockStartAnchor(geometry),
            ) {
                BrowseBottomChromeBackdrop(
                    hazeState = hazeState,
                    modifier = Modifier.matchParentSize(),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(bottom = BrowseBottomCalendarToTabsGap),
                ) {
                    content(Modifier.fillMaxWidth())
                }
            }
        }
        BrowseBottomControls(
            state = state,
            actions = actions,
            sectionNavigation = sectionNavigation,
            showSectionTabs = showSectionTabs,
            protectedSlotActive = protectedContent.active,
            actionFocusRequester = actionFocusRequester,
            geometry = geometry,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun BrowseBottomChromeBackdrop(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.liquidGlassBackdrop(
                shape = RoundedCornerShape(0.dp),
                intensity = 1.12f,
                hazeState = hazeState,
                topFadeFraction = 0.36f,
            ),
    )
}

@Composable
private fun BrowseBottomPointerBlock(
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    if (height <= 0.dp) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .consumeUnhandledPointerInput(height),
    )
}
// BrowseBottomControls
private val BrowseBottomChromeItemGap = BrowseChromeItemGap

@Composable
internal fun BrowseBottomControls(
    state: BrowseHomeChromeState,
    actions: BrowseActionCallbacks,
    sectionNavigation: BrowseBottomSectionNavigation,
    showSectionTabs: Boolean,
    protectedSlotActive: Boolean,
    actionFocusRequester: FocusRequester,
    geometry: BrowseBottomChromeGeometryState,
    modifier: Modifier = Modifier,
) {
    val trackedModifier = modifier.fillMaxWidth().trackBrowseBaseControls(geometry)
    Column(
        modifier = trackedModifier
            .padding(
                start = 16.dp,
                top = BrowseBottomChromeInteractiveTopPadding,
                end = 16.dp,
                bottom = BrowseBottomChromeItemGap,
            ),
    ) {
        if (showSectionTabs) {
            BrowseBottomSectionTabs(
                state = state,
                navigation = sectionNavigation,
                protectedSlotActive = protectedSlotActive,
                actionFocusRequester = actionFocusRequester,
                geometry = geometry,
            )
            Spacer(modifier = Modifier.height(BrowseBottomChromeItemGap))
        }
        BrowseBottomActions(
            state = state,
            actions = actions,
            sectionNavigation = sectionNavigation,
            showSectionTabs = showSectionTabs,
            protectedSlotActive = protectedSlotActive,
            actionFocusRequester = actionFocusRequester,
            geometry = geometry,
        )
    }
}

@Composable
private fun BrowseBottomSectionTabs(
    state: BrowseHomeChromeState,
    navigation: BrowseBottomSectionNavigation,
    protectedSlotActive: Boolean,
    actionFocusRequester: FocusRequester,
    geometry: BrowseBottomChromeGeometryState,
) {
    val focusRequesters = navigation.focusRequesters.ifEmpty {
        navigation.focusRequester
            ?.let { requester -> mapOf(state.activeSection to requester) }
            .orEmpty()
    }
    BrowseSectionTabs(
        activeSection = state.activeSection,
        visibleSections = state.visibleSections,
        activeSectionPosition = state.activeSectionPosition,
        onSectionSelected = navigation.onSectionSelected,
        sectionFocusRequesters = focusRequesters,
        onExitUp = navigation.onExitUp,
        onExitDown = { actionFocusRequester.requestFocusSafely() },
        focusEnabled = navigation.focusEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (protectedSlotActive) Modifier else with(geometry) {
                    Modifier.browsePointerBlockStartAnchor(geometry)
                },
            ),
    )
}

@Composable
private fun BrowseBottomActions(
    state: BrowseHomeChromeState,
    actions: BrowseActionCallbacks,
    sectionNavigation: BrowseBottomSectionNavigation,
    showSectionTabs: Boolean,
    protectedSlotActive: Boolean,
    actionFocusRequester: FocusRequester,
    geometry: BrowseBottomChromeGeometryState,
) {
    val stackActions = currentWindowSizeDp().width < 360.dp
    BrowseChromeActions(
        state = state,
        callbacks = actions,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (showSectionTabs || protectedSlotActive) Modifier else with(geometry) {
                    Modifier.browsePointerBlockStartAnchor(geometry)
                },
            ),
        spreadActions = !stackActions,
        stackActions = stackActions,
        entryFocusRequester = actionFocusRequester,
        upFocusRequester = sectionNavigation.focusRequester,
        consumeDownWhenNoRequester = true,
        consumeHorizontalEdgesWhenNoRequester = true,
        reverseActionOrder = true,
        fillActionWidth = true,
    )
}
// BrowseBottomProtectedContent
internal data class BrowseBottomProtectedContentState(
    val content: (@Composable (Modifier) -> Unit)?,
    val progress: Float,
) {
    val active: Boolean
        get() = content != null && progress > 0.001f
}

@Composable
internal fun rememberBrowseBottomProtectedContentState(
    content: (@Composable (Modifier) -> Unit)?,
    visibilityProgress: Float?,
): BrowseBottomProtectedContentState {
    var retainedContent by remember {
        mutableStateOf<(@Composable (Modifier) -> Unit)?>(null)
    }
    val animatedProgress = if (visibilityProgress == null) {
        val progress by animateFloatAsState(
            targetValue = if (content != null) 1f else 0f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "browseBottomTopProtectedProgress",
            finishedListener = { value ->
                if (value <= 0.001f) retainedContent = null
            },
        )
        progress
    } else {
        0f
    }
    val resolvedProgress = visibilityProgress?.coerceIn(0f, 1f) ?: animatedProgress

    SideEffect {
        if (content != null) retainedContent = content
    }
    LaunchedEffect(content, resolvedProgress) {
        if (content == null && resolvedProgress <= 0.001f) retainedContent = null
    }
    return BrowseBottomProtectedContentState(
        content = content ?: retainedContent,
        progress = resolvedProgress,
    )
}

internal fun Modifier.browseBottomTopProtectedVisibility(progress: Float): Modifier {
    val resolvedProgress = progress.coerceIn(0f, 1f)
    return this
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val height = (placeable.height * resolvedProgress).roundToInt()
            layout(width = placeable.width, height = height) {
                placeable.placeRelative(x = 0, y = height - placeable.height)
            }
        }
        .clipToBounds()
        .graphicsLayer { alpha = resolvedProgress }
}

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
    val focusableContentSections = browseFocusableContentSections(state, environment.isSearching)
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
        focusableContentSections = focusableContentSections,
        onBrowseSectionChange = onBrowseSectionChange,
        onHomeBrowseBackStateChange = config.onHomeBrowseBackStateChange,
        onRequestSectionTabsFocus = focusActions.requestSectionTabsFocus,
    )
    return BrowseScreenNavigation(focusActions, focusFirstRequests, pagerBinding)
}

internal fun browseFocusableContentSections(
    state: YummyDroidUiState,
    isSearching: Boolean,
): Set<BrowseSection> {
    val catalog = if (isSearching) state.searchResults else state.featured
    return buildSet {
        if (catalog.hasFocusableListContent()) add(BrowseSection.Catalog)
        if (state.historyAnime.hasFocusableListContent()) add(BrowseSection.History)
        if (state.schedule.hasFocusableListContent()) add(BrowseSection.Schedule)
        add(BrowseSection.Downloads)
    }
}

private fun LoadState<List<*>>.hasFocusableListContent(): Boolean = when (this) {
    LoadState.Loading -> false
    is LoadState.Error -> true
    is LoadState.Ready -> data.isNotEmpty()
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
        onExitDown = if (tvChromeVisible) {
            { false }
        } else {
            { onRequestSectionTabsFocus(false) }
        },
        onOpenAnime = onOpenAnime,
    )
}
