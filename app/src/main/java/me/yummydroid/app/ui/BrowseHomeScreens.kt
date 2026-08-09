package me.yummydroid.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.DownloadTaskState
import me.yummydroid.app.InputAction
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.readyListOrEmpty

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
    val isAuthorized = state.auth.profile != null
    val forcedOffline = state.forcedOfflineMode
    val browsePagerSections = remember(isAuthorized, forcedOffline) {
        resolveBrowsePagerSections(isAuthorized, forcedOffline)
    }
    val effectiveHomeSection = resolveEffectiveBrowseSection(state.homeSection, isAuthorized, forcedOffline)
    LaunchedEffect(state.homeSection, isAuthorized, forcedOffline) {
        resolveBrowseSectionCorrection(state.homeSection, isAuthorized, forcedOffline)
            ?.let(onBrowseSectionChange)
    }
    val isCatalog = effectiveHomeSection == BrowseSection.Catalog
    val catalogActionsEnabled = browseCatalogActionsEnabledForSection(
        section = effectiveHomeSection,
        forcedOfflineMode = forcedOffline,
    )
    val isSearching = isCatalog && state.searchQuery.isNotBlank()
    val contentState = if (isSearching) state.searchResults else state.featured
    val pagingState = if (isSearching) state.searchPaging else state.featuredPaging
    val browseScreenDensity = LocalDensity.current
    val inputModeManager = LocalInputModeManager.current
    val browseDpadFocusEnabled = inputModeManager.inputMode != InputMode.Touch
    val isWide = currentWindowSizeDp().width >= 720.dp
    val browseChromePolicy = resolveBrowseChromePolicy(isWide, forcedOffline)
    val scheduleGridState = browseCoordinator.scheduleGridState
    val tvTopChromePinned = browseChromePolicy.pinTopChrome
    val browseTopBarCollapseDistance = BrowseTopBarScrollCollapseDistance
    val browseTopBarCollapseDistancePx = with(browseScreenDensity) {
        browseTopBarCollapseDistance.toPx()
    }
    fun browseTopBarProgressFor(section: BrowseSection): Float {
        if (tvTopChromePinned) return 1f
        return browseCoordinator.topBarVisibilityProgress(
            section = section,
            collapseDistancePx = browseTopBarCollapseDistancePx,
        )
    }
    fun browseTopBarFullyVisibleFor(section: BrowseSection): Boolean {
        return browseTopBarProgressFor(section) > 0.999f
    }
    val browseTopActionsFocusRequester = remember { FocusRequester() }
    val browseSectionTabFocusRequesters = remember(browsePagerSections) {
        browsePagerSections.associateWith { FocusRequester() }
    }
    val browseChromeHazeState = remember { HazeState() }
    var browseBottomChromeBaseMeasuredHeight by remember { mutableStateOf(0.dp) }
    var browseBottomChromeExpandedHeight by remember { mutableStateOf(0.dp) }
    val browseFocusScope = rememberCoroutineScope()
    var scheduleSelectedEpochDay by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
    var browseContentFocusRequestNonce by remember { mutableLongStateOf(0L) }
    var browseFirstFocusRequestNonce by remember { mutableLongStateOf(0L) }
    var browseLayerHasFocus by remember { mutableStateOf(false) }
    val dpadLayerFocusRequestNonce = if (browseDpadFocusEnabled && activeFocusRequestNonce > 0L) {
        activeFocusRequestNonce * 1_000_000L + browseContentFocusRequestNonce
    } else {
        0L
    }
    var searchDialogOpen by remember { mutableStateOf(false) }
    var filtersDialogOpen by remember { mutableStateOf(false) }
    var searchKeyboardBackConsumed by remember { mutableStateOf(false) }
    var searchKeyboardDismissRequest by remember { mutableLongStateOf(0L) }
    var searchInputActionRequest by remember { mutableLongStateOf(0L) }
    var searchInputAction by remember { mutableStateOf<InputAction?>(null) }
    var scheduleCalendarFocusRequestNonce by remember { mutableLongStateOf(0L) }
    var browsePagerTransitionFocusSourcePage by remember { mutableStateOf<Int?>(null) }
    var browsePagerRequestContentFocusOnFinish by remember { mutableStateOf(false) }
    val scheduleZoneId = remember { ZoneId.systemDefault() }
    val phoneScheduleDayGroups = remember(state.schedule, scheduleZoneId, isWide, forcedOffline) {
        if (!isWide && !forcedOffline) {
            state.schedule.readyListOrEmpty().toScheduleDayGroups(scheduleZoneId)
        } else {
            emptyList()
        }
    }
    val phoneScheduleSelectedGroup = remember(phoneScheduleDayGroups, scheduleSelectedEpochDay) {
        phoneScheduleDayGroups.firstOrNull { group -> group.epochDay == scheduleSelectedEpochDay }
            ?: phoneScheduleDayGroups.todayOrClosest()
    }
    val showPhoneScheduleCalendarInBottomChrome = !isWide &&
        !forcedOffline &&
        effectiveHomeSection == BrowseSection.Schedule &&
        phoneScheduleDayGroups.isNotEmpty()
    var suppressContentFocusForSection by remember { mutableStateOf<BrowseSection?>(null) }
    var activeHomeBackToTopHandler by remember { mutableStateOf<HomeBackToTopHandler?>(null) }
    val latestOnRegisterHomeBackToTopHandler by rememberUpdatedState(onRegisterHomeBackToTopHandler)

    fun updateStoredBrowseFocus(section: BrowseSection, index: Int) {
        browseCoordinator.setFocusedIndex(section, index)
    }

    LaunchedEffect(catalogActionsEnabled) {
        if (!catalogActionsEnabled) {
            filtersDialogOpen = false
            searchDialogOpen = false
        }
    }

    LaunchedEffect(searchDialogOpen) {
        if (searchDialogOpen) {
            searchKeyboardBackConsumed = false
            searchInputAction = null
            searchInputActionRequest = 0L
        }
    }

    fun requestCurrentBrowseContentFocus(): Boolean {
        suppressContentFocusForSection = null
        browseContentFocusRequestNonce += 1L
        return true
    }

    fun requestFirstBrowseContentFocus(): Boolean {
        suppressContentFocusForSection = null
        if (effectiveHomeSection == BrowseSection.Downloads) {
            browseContentFocusRequestNonce += 1L
        } else {
            browseFirstFocusRequestNonce += 1L
        }
        return true
    }

    fun recoverFirstBrowseContentFocusIfMissing(): Boolean {
        if (browseLayerHasFocus) return false
        return requestFirstBrowseContentFocus()
    }

    fun requestScheduleCalendarFocus(): Boolean {
        suppressContentFocusForSection = null
        if (showPhoneScheduleCalendarInBottomChrome) {
            scheduleCalendarFocusRequestNonce += 1L
            return true
        }
        browseFocusScope.launch {
            if (scheduleGridState.firstVisibleItemIndex != 0 || scheduleGridState.firstVisibleItemScrollOffset != 0) {
                browseCoordinator.scrollToTop(BrowseSection.Schedule)
            }
            withFrameNanos { }
            scheduleCalendarFocusRequestNonce += 1L
        }
        return true
    }

    fun requestBrowseTopActionsFocus(): Boolean {
        if (
            browseTopBarFullyVisibleFor(effectiveHomeSection) &&
            browseDpadFocusEnabled &&
            browseTopActionsFocusRequester.requestFocusSafely()
        ) {
            return true
        }
        browseFocusScope.launch {
            browseCoordinator.scrollToTop(effectiveHomeSection)
            withFrameNanos { }
            if (browseDpadFocusEnabled) {
                browseTopActionsFocusRequester.requestFocusSafely()
            }
        }
        return true
    }

    fun requestBrowseSectionTabsFocus(
        section: BrowseSection = effectiveHomeSection,
        releasePagerFocusTransition: Boolean = false,
    ): Boolean {
        if (!browseDpadFocusEnabled) return false
        if (forcedOffline) return false
        if (releasePagerFocusTransition) {
            browsePagerTransitionFocusSourcePage = null
            browsePagerRequestContentFocusOnFinish = false
        }
        val requester = browseSectionTabFocusRequesters[section] ?: return false
        if (releasePagerFocusTransition) {
            browseFocusScope.launch {
                withFrameNanos { }
                requester.requestFocusSafely()
            }
            return true
        }
        return requester.requestFocusSafely()
    }

    fun updateHomeBackToTopHandler(section: BrowseSection, handler: HomeBackToTopHandler?) {
        if (handler == null) {
            if (activeHomeBackToTopHandler?.section == section) {
                activeHomeBackToTopHandler = null
            }
        } else {
            activeHomeBackToTopHandler = handler
        }
        latestOnRegisterHomeBackToTopHandler(section, handler)
    }

    val browseModalInputActionHandler by rememberUpdatedState { action: InputAction ->
        when {
            searchDialogOpen -> {
                when (action) {
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
            filtersDialogOpen -> {
                if (action == InputAction.Back) {
                    filtersDialogOpen = false
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }
    DisposableEffect(searchDialogOpen, filtersDialogOpen, onRegisterModalInputActionHandler) {
        if (searchDialogOpen || filtersDialogOpen) {
            onRegisterModalInputActionHandler { action -> browseModalInputActionHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }
    DisposableEffect(onRegisterDpadFocusRecoveryHandler) {
        onRegisterDpadFocusRecoveryHandler(::recoverFirstBrowseContentFocusIfMissing)
        onDispose { onRegisterDpadFocusRecoveryHandler(null) }
    }
    val activeDownloadCount = remember(state.downloadQueue.tasks) {
        state.downloadQueue.tasks.count { task ->
            task.state == DownloadTaskState.Queued ||
                task.state == DownloadTaskState.Running ||
                task.state == DownloadTaskState.Paused
        }
    }
    val catalogFocusFirstRequest = FocusFirstRequest(
        persistentNonce = state.homeFocusResetNonce,
        transientNonce = if (effectiveHomeSection == BrowseSection.Catalog) browseFirstFocusRequestNonce else 0L,
    )
    val scheduleFocusFirstRequest = FocusFirstRequest(
        transientNonce = if (effectiveHomeSection == BrowseSection.Schedule) browseFirstFocusRequestNonce else 0L,
    )
    val historyFocusFirstRequest = FocusFirstRequest(
        transientNonce = if (effectiveHomeSection == BrowseSection.History) browseFirstFocusRequestNonce else 0L,
    )
    val latestOnBrowseSectionChange by rememberUpdatedState(onBrowseSectionChange)
    val latestEffectiveHomeSection by rememberUpdatedState(effectiveHomeSection)
    val browsePagerPage = browsePagerSections.indexOf(effectiveHomeSection).takeIf { it >= 0 } ?: 0
    val useBrowsePager = !forcedOffline && browsePagerSections.size > 1
    val browsePageStateHolder = rememberSaveableStateHolder()
    val browsePagerState = rememberPagerState(
        initialPage = browsePagerPage,
        pageCount = { browsePagerSections.size },
    )
    val browsePagerPositionState = remember(browsePagerState) {
        derivedStateOf {
            browsePagerState.currentPage + browsePagerState.currentPageOffsetFraction
        }
    }
    val browsePagerIsAwayFromTargetState = remember(useBrowsePager, browsePagerPage, browsePagerState) {
        derivedStateOf {
            useBrowsePager &&
                (browsePagerState.currentPage != browsePagerPage ||
                    abs(browsePagerState.currentPageOffsetFraction) > 0.001f)
        }
    }
    val browsePagerIsAwayFromTarget by browsePagerIsAwayFromTargetState
    var browsePageFocusRequestNonce by remember { mutableLongStateOf(0L) }
    var browsePageFocusRequestSection by remember { mutableStateOf(effectiveHomeSection) }
    var keepTabsFocusedForSectionChange by remember { mutableStateOf(false) }
    var pendingTabsFocusSection by remember { mutableStateOf<BrowseSection?>(null) }
    var browsePagerProgrammaticScrollTarget by remember { mutableStateOf<Int?>(null) }
    var browseTopBarProgrammaticTargetProgress by remember { mutableStateOf<Float?>(null) }
    val browseTopBarTargetProgressState = remember(
        effectiveHomeSection,
        browseCoordinator,
        browseTopBarCollapseDistancePx,
        isWide,
        forcedOffline,
    ) {
        derivedStateOf {
            browseTopBarProgressFor(effectiveHomeSection)
        }
    }
    val browseTopBarEffectiveTargetProgressState = remember(
        browseTopBarProgrammaticTargetProgress,
        browseTopBarTargetProgressState,
    ) {
        derivedStateOf {
            browseTopBarProgrammaticTargetProgress
                ?: browseTopBarTargetProgressState.value
        }
    }
    val browseTopBarPagerDriven = useBrowsePager &&
        (
            browsePagerState.isScrollInProgress ||
                browsePagerProgrammaticScrollTarget != null ||
                browsePagerTransitionFocusSourcePage != null
            )
    val browseTopBarVisibilityProgressState = remember(
        browsePagerSections,
        browsePagerState,
        browseCoordinator,
        browseTopBarEffectiveTargetProgressState,
        browseTopBarCollapseDistancePx,
        isWide,
        forcedOffline,
        browseTopBarPagerDriven,
    ) {
        derivedStateOf {
            val effectiveTargetProgress = browseTopBarEffectiveTargetProgressState.value
            if (!browseTopBarPagerDriven || browsePagerSections.isEmpty()) {
                effectiveTargetProgress
            } else {
                val maxPage = browsePagerSections.lastIndex
                val position = (browsePagerState.currentPage + browsePagerState.currentPageOffsetFraction)
                    .coerceIn(0f, maxPage.toFloat())
                val startPage = position.toInt().coerceIn(0, maxPage)
                val endPage = (startPage + 1).coerceAtMost(maxPage)
                val fraction = (position - startPage).coerceIn(0f, 1f)
                val startProgress = browseTopBarProgressFor(browsePagerSections[startPage])
                val endProgress = browseTopBarProgressFor(browsePagerSections[endPage])
                startProgress + (endProgress - startProgress) * fraction
            }
        }
    }
    val browseTopBarDisplayVisibleState = remember(
        browseTopBarEffectiveTargetProgressState,
        browseTopBarVisibilityProgressState,
    ) {
        derivedStateOf {
            browseTopBarEffectiveTargetProgressState.value > 0.001f ||
                browseTopBarVisibilityProgressState.value > 0.001f
        }
    }
    val browseChromeHazeActive = !tvTopChromePinned
    val homeBrowseBackState by remember(
        useBrowsePager,
        effectiveHomeSection,
        browsePagerSections,
        browsePagerState,
        browsePagerPositionState,
        browsePagerIsAwayFromTargetState,
    ) {
        derivedStateOf {
            resolveHomeBrowseBackState(
                useBrowsePager = useBrowsePager,
                effectiveSection = effectiveHomeSection,
                pagerSections = browsePagerSections,
                pagerPosition = browsePagerPositionState.value,
                pagerScrollInProgress = browsePagerState.isScrollInProgress,
                pagerAwayFromTarget = browsePagerIsAwayFromTargetState.value,
            )
        }
    }
    LaunchedEffect(active, homeBrowseBackState) {
        if (active) {
            onHomeBrowseBackStateChange(homeBrowseBackState)
        }
    }
    val browsePagerPosition = browsePagerPositionState.value
    var browseProgrammaticTabTargetPosition by remember { mutableStateOf<Float?>(null) }
    val browseProgrammaticTabPosition by animateFloatAsState(
        targetValue = browseProgrammaticTabTargetPosition ?: browsePagerPage.toFloat(),
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "browseProgrammaticTabPosition",
    )
    val browseTabPosition = resolveBrowseTabPosition(
        active = active,
        useBrowsePager = useBrowsePager,
        pagerPage = browsePagerPage,
        pagerPosition = browsePagerPosition,
        programmaticTabTargetPosition = browseProgrammaticTabTargetPosition,
        programmaticTabPosition = browseProgrammaticTabPosition,
        pagerDriven = browsePagerState.isScrollInProgress ||
            browsePagerProgrammaticScrollTarget != null ||
            browsePagerTransitionFocusSourcePage != null,
        effectiveSectionVisible = effectiveHomeSection in browsePagerSections,
        programmaticScrollPending = browsePagerProgrammaticScrollTarget != null,
    )
    val scheduleBrowsePage = browsePagerSections.indexOf(BrowseSection.Schedule)
    val phoneScheduleCalendarVisualProgress = resolvePhoneScheduleCalendarProgress(
        isWide = isWide,
        forcedOfflineMode = forcedOffline,
        schedulePage = scheduleBrowsePage,
        hasScheduleDays = phoneScheduleDayGroups.isNotEmpty(),
        visualPagerPosition = browseTabPosition ?: browsePagerPage.toFloat(),
    )
    val browseHomeChromeState = BrowseHomeChromeState(
        auth = state.auth,
        activeFilters = if (catalogActionsEnabled) state.filters.activeCount else 0,
        activeSearch = catalogActionsEnabled && isSearching,
        activeFiltersPanel = catalogActionsEnabled && filtersDialogOpen,
        activeSettings = settingsDialogOpen,
        activeDownloads = effectiveHomeSection == BrowseSection.Downloads,
        activeProfile = loginDialogOpen || profileDialogOpen,
        activeDownloadCount = activeDownloadCount,
        forcedOfflineMode = forcedOffline,
        catalogActionsEnabled = catalogActionsEnabled,
        isWide = isWide,
        activeSection = effectiveHomeSection,
        visibleSections = browsePagerSections,
        activeSectionPosition = browseTabPosition,
    )
    val showPhoneScheduleCalendarInBottomChromeVisual =
        showPhoneScheduleCalendarInBottomChrome || phoneScheduleCalendarVisualProgress > 0.001f
    val hasBrowseBottomChrome = browseChromePolicy.showBottomChrome
    val browseBottomChromeBaseHeight = if (!hasBrowseBottomChrome) {
        0.dp
    } else if (browseBottomChromeBaseMeasuredHeight > 0.dp) {
        browseBottomChromeBaseMeasuredHeight
    } else {
        BrowseBottomChromeFallbackProtectedHeight
    }
    val browseBottomChromeExpandedTargetHeight = maxOf(
        browseBottomChromeExpandedHeight,
        browseBottomChromeBaseHeight,
    )
    val browseBottomChromeTargetHeight = if (!hasBrowseBottomChrome) {
        0.dp
    } else if (showPhoneScheduleCalendarInBottomChromeVisual) {
        browseBottomChromeBaseHeight +
            (browseBottomChromeExpandedTargetHeight - browseBottomChromeBaseHeight) *
            phoneScheduleCalendarVisualProgress
    } else {
        browseBottomChromeBaseHeight
    }
    val browseBottomChromeBaseContentPadding = browseBottomChromeBaseHeight
    val browseBottomChromeScheduleContentPadding = browseBottomChromeTargetHeight
    val browseSectionTabsFocusEnabled = browsePagerTransitionFocusSourcePage == null
    var browsePagerWasAligned by remember { mutableStateOf(false) }
    LaunchedEffect(effectiveHomeSection) {
        if (browsePageFocusRequestSection != effectiveHomeSection) {
            browsePageFocusRequestSection = effectiveHomeSection
            if (keepTabsFocusedForSectionChange) {
                val targetFocusSection = pendingTabsFocusSection ?: effectiveHomeSection
                pendingTabsFocusSection = null
                keepTabsFocusedForSectionChange = false
                if (browseDpadFocusEnabled) {
                    withFrameNanos { }
                    requestBrowseSectionTabsFocus(targetFocusSection)
                }
            } else {
                pendingTabsFocusSection = null
                if (
                    browseDpadFocusEnabled &&
                    (
                        !useBrowsePager ||
                            (
                                browsePagerProgrammaticScrollTarget == null &&
                                    browsePagerTransitionFocusSourcePage == null
                                )
                        )
                ) {
                    browsePageFocusRequestNonce += 1L
                }
            }
        }
    }
    val browseFocusRequestNonce = dpadLayerFocusRequestNonce + browsePageFocusRequestNonce
    val browsePagerSettledAtTarget = effectiveHomeSection in browsePagerSections &&
        (!useBrowsePager || (!browsePagerState.isScrollInProgress && !browsePagerIsAwayFromTarget))

    fun browsePagerIsSettledAt(page: Int): Boolean {
        return PagerAlignmentState(
            isScrollInProgress = browsePagerState.isScrollInProgress,
            settledPage = browsePagerState.settledPage,
            currentPage = browsePagerState.currentPage,
            offset = browsePagerState.currentPageOffsetFraction,
        ).isSettledAt(page)
    }

    fun finishProgrammaticBrowsePagerTarget(targetPage: Int) {
        if (browsePagerProgrammaticScrollTarget != targetPage) return
        if (browsePagerPage != targetPage) return
        if (!browsePagerIsSettledAt(targetPage)) return
        val shouldRequestContentFocus = browsePagerTransitionFocusSourcePage != null
        browsePagerProgrammaticScrollTarget = null
        browsePagerTransitionFocusSourcePage = null
        val requestContentFocus = browsePagerRequestContentFocusOnFinish && shouldRequestContentFocus
        browsePagerRequestContentFocusOnFinish = false
        browseTopBarProgrammaticTargetProgress = null
        browseProgrammaticTabTargetPosition = null
        if (requestContentFocus) {
            browsePageFocusRequestNonce += 1L
        }
    }

    LaunchedEffect(active, browsePagerPage, effectiveHomeSection, browsePagerSections) {
        if (!useBrowsePager) {
            browsePagerWasAligned = true
            return@LaunchedEffect
        }
        val targetPage = browsePagerPage
        if (
            effectiveHomeSection in browsePagerSections &&
            (browsePagerState.currentPage != targetPage || browsePagerState.currentPageOffsetFraction != 0f)
        ) {
            if (active && browsePagerWasAligned) {
                if (browsePagerProgrammaticScrollTarget == null) {
                    browsePagerProgrammaticScrollTarget = targetPage
                    browseTopBarProgrammaticTargetProgress = browseTopBarProgressFor(effectiveHomeSection)
                }
                try {
                    browsePagerState.animateScrollToPage(targetPage)
                } finally {
                    finishProgrammaticBrowsePagerTarget(targetPage)
                }
            } else {
                browsePagerState.scrollToPage(targetPage)
                finishProgrammaticBrowsePagerTarget(targetPage)
            }
        } else if (browsePagerProgrammaticScrollTarget == targetPage) {
            finishProgrammaticBrowsePagerTarget(targetPage)
        }
        browsePagerWasAligned = true
    }

    LaunchedEffect(active, browsePagerState, browsePagerPage, effectiveHomeSection, browsePagerSections) {
        if (!useBrowsePager) return@LaunchedEffect
        snapshotFlow {
            PagerAlignmentState(
                isScrollInProgress = browsePagerState.isScrollInProgress,
                settledPage = browsePagerState.settledPage,
                currentPage = browsePagerState.currentPage,
                offset = browsePagerState.currentPageOffsetFraction,
            )
        }
            .distinctUntilChanged()
            .collect { alignment ->
                if (!active) {
                    if (alignment.currentPage != browsePagerPage || abs(alignment.offset) > 0.001f) {
                        browsePagerState.scrollToPage(browsePagerPage)
                    }
                    return@collect
                }
                val programmaticTarget = browsePagerProgrammaticScrollTarget
                if (programmaticTarget != null || browsePagerTransitionFocusSourcePage != null) {
                    if (programmaticTarget != null && browsePagerIsSettledAt(programmaticTarget)) {
                        finishProgrammaticBrowsePagerTarget(programmaticTarget)
                    }
                    return@collect
                }
                if (alignment.isScrollInProgress) return@collect
                if (latestEffectiveHomeSection !in browsePagerSections) return@collect
                val settledSection = browsePagerSections.getOrNull(alignment.settledPage) ?: return@collect
                if (alignment.currentPage != alignment.settledPage || abs(alignment.offset) > 0.001f) {
                    browsePagerState.scrollToPage(alignment.settledPage)
                }
                if (settledSection != latestEffectiveHomeSection) {
                    latestOnBrowseSectionChange(settledSection)
                }
            }
    }
    fun selectBrowseSection(section: BrowseSection, keepTabsFocused: Boolean): Boolean {
        if (section !in browsePagerSections) return false
        if (section == effectiveHomeSection) {
            if (keepTabsFocused && browseDpadFocusEnabled) {
                requestBrowseSectionTabsFocus(section)
            }
            return true
        }
        val keepTabsFocusedForKeyboard = keepTabsFocused && browseDpadFocusEnabled
        val requestContentFocusAfterTransition = !keepTabsFocused && browseDpadFocusEnabled
        keepTabsFocusedForSectionChange = keepTabsFocusedForKeyboard
        if (keepTabsFocusedForKeyboard) {
            pendingTabsFocusSection = section
            suppressContentFocusForSection = section
            requestBrowseSectionTabsFocus(section)
        } else {
            pendingTabsFocusSection = null
            suppressContentFocusForSection = null
        }
        if (useBrowsePager) {
            val targetPage = browsePagerSections.indexOf(section).takeIf { page -> page >= 0 }
            browsePagerProgrammaticScrollTarget = targetPage
            browseProgrammaticTabTargetPosition = targetPage?.toFloat()
            browseTopBarProgrammaticTargetProgress = browseTopBarProgressFor(section)
            browsePagerTransitionFocusSourcePage = if (requestContentFocusAfterTransition) {
                browsePagerPage
            } else {
                null
            }
            browsePagerRequestContentFocusOnFinish = requestContentFocusAfterTransition
        }
        latestOnBrowseSectionChange(section)
        return true
    }

    val onBrowsePagerSectionSelected: (BrowseSection) -> Unit = { section ->
        selectBrowseSection(section, keepTabsFocused = true)
    }
    fun handleBrowsePageHorizontalExit(page: Int, direction: VisualGridDirection): Boolean {
        val targetPage = when (direction) {
            VisualGridDirection.Left -> page - 1
            VisualGridDirection.Right -> page + 1
            VisualGridDirection.Up,
            VisualGridDirection.Down -> return false
        }
        val targetSection = browsePagerSections.getOrNull(targetPage) ?: return false
        return selectBrowseSection(targetSection, keepTabsFocused = false)
    }

    @Composable
    fun BrowseSectionPage(
        pageSection: BrowseSection,
        pageIndex: Int,
        pageCanReceiveFocus: Boolean,
        pageFocusCurrentRequestNonce: Long,
    ) {
        BrowseSectionPageContent(
            pageSection = pageSection,
            pageIndex = pageIndex,
            pageCanReceiveFocus = pageCanReceiveFocus,
            pageFocusCurrentRequestNonce = pageFocusCurrentRequestNonce,
            state = state,
            catalogContentState = contentState,
            catalogPagingState = pagingState,
            browseCoordinator = browseCoordinator,
            catalogFocusFirstRequest = catalogFocusFirstRequest,
            scheduleFocusFirstRequest = scheduleFocusFirstRequest,
            historyFocusFirstRequest = historyFocusFirstRequest,
            sectionTabFocusRequesters = browseSectionTabFocusRequesters,
            catalogContentBottomPadding = browseBottomChromeBaseContentPadding,
            scheduleContentBottomPadding = browseBottomChromeScheduleContentPadding,
            isSearching = isSearching,
            isWide = isWide,
            forcedOfflineMode = forcedOffline,
            tvTopChromePinned = tvTopChromePinned,
            phoneScheduleDayGroups = phoneScheduleDayGroups,
            scheduleSelectedEpochDay = scheduleSelectedEpochDay,
            scheduleCalendarFocusRequestNonce = scheduleCalendarFocusRequestNonce,
            onScheduleSelectedEpochDayChange = { epochDay -> scheduleSelectedEpochDay = epochDay },
            onUpdateHomeBackToTopHandler = ::updateHomeBackToTopHandler,
            onRefresh = onRefresh,
            onLoadMoreAnime = onLoadMoreAnime,
            onHorizontalExit = ::handleBrowsePageHorizontalExit,
            onRequestSectionTabsFocus = { releasePagerFocusTransition ->
                requestBrowseSectionTabsFocus(releasePagerFocusTransition = releasePagerFocusTransition)
            },
            onRequestTopActionsFocus = ::requestBrowseTopActionsFocus,
            onRequestScheduleCalendarFocus = ::requestScheduleCalendarFocus,
            onClearDownloadHistory = onClearDownloadHistory,
            onCancelDownload = onCancelDownload,
            onPauseDownload = onPauseDownload,
            onResumeDownload = onResumeDownload,
            onOpenAnime = onOpenAnime,
        )
    }

    @Composable
    fun BrowseTopBarChrome(
        modifier: Modifier = Modifier,
        collapseWhenHidden: Boolean = true,
    ) {
        BrowseHomeTopBar(
            state = browseHomeChromeState,
            onOpenSearch = {
                if (catalogActionsEnabled) {
                    searchDialogOpen = true
                }
            },
            onOpenFilters = {
                if (catalogActionsEnabled) {
                    filtersDialogOpen = true
                }
            },
            onOpenSettings = onOpenSettings,
            onOpenDownloads = onOpenDownloads,
            onOpenLogin = onOpenLogin,
            onOpenProfile = onOpenProfile,
            onSectionSelected = onBrowsePagerSectionSelected,
            onExitDown = {
                if (isWide && !forcedOffline) {
                    requestBrowseSectionTabsFocus()
                } else {
                    requestCurrentBrowseContentFocus()
                }
            },
            actionsFocusRequester = browseTopActionsFocusRequester,
            sectionTabsFocusRequester = if (isWide && !forcedOffline) {
                browseSectionTabFocusRequesters[effectiveHomeSection]
            } else {
                null
            },
            sectionTabFocusRequesters = browseSectionTabFocusRequesters,
            sectionTabsFocusEnabled = browseSectionTabsFocusEnabled,
            modifier = modifier,
            collapseWhenHidden = collapseWhenHidden,
            visible = browseTopBarDisplayVisibleState.value,
            visibilityProgressProvider = { browseTopBarVisibilityProgressState.value },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { focusState ->
                browseLayerHasFocus = focusState.isFocused || focusState.hasFocus
            }
            .focusGroup(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
        ) {
            if (browseChromePolicy.pinTopChrome) {
                BrowseTopBarChrome(collapseWhenHidden = false)
                if (browseChromePolicy.showTvSectionTabs) {
                    BrowseHomeTvSectionTabs(
                        state = browseHomeChromeState,
                        sectionFocusRequesters = browseSectionTabFocusRequesters,
                        sectionTabsFocusEnabled = browseSectionTabsFocusEnabled,
                        onSectionSelected = onBrowsePagerSectionSelected,
                        onExitUp = ::requestBrowseTopActionsFocus,
                        onExitDown = {
                            if (effectiveHomeSection == BrowseSection.Schedule) {
                                requestScheduleCalendarFocus()
                            } else {
                                requestCurrentBrowseContentFocus()
                            }
                        },
                    )
                }
            } else {
                BrowseTopBarChrome()
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (browseChromeHazeActive) {
                            Modifier.hazeSource(browseChromeHazeState)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                if (effectiveHomeSection == BrowseSection.Downloads) {
                    browsePageStateHolder.SaveableStateProvider(BrowseSection.Downloads) {
                        BrowseSectionPage(
                            pageSection = BrowseSection.Downloads,
                            pageIndex = browsePagerPage,
                            pageCanReceiveFocus = active && browseDpadFocusEnabled,
                            pageFocusCurrentRequestNonce = dpadLayerFocusRequestNonce,
                        )
                    }
                } else if (!useBrowsePager) {
                    val contentFocusSuppressed = effectiveHomeSection == suppressContentFocusForSection
                    browsePageStateHolder.SaveableStateProvider(effectiveHomeSection) {
                        BrowseSectionPage(
                            pageSection = effectiveHomeSection,
                            pageIndex = browsePagerPage,
                            pageCanReceiveFocus = active && browseDpadFocusEnabled && !contentFocusSuppressed,
                            pageFocusCurrentRequestNonce = if (contentFocusSuppressed) {
                                0L
                            } else {
                                browseFocusRequestNonce
                            },
                        )
                    }
                } else {
                    HorizontalPager(
                        state = browsePagerState,
                        beyondViewportPageCount = 1,
                        userScrollEnabled = active,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val pageSection = browsePagerSections.getOrNull(page) ?: BrowseSection.Catalog
                        val contentFocusSuppressed = pageSection == suppressContentFocusForSection
                        val pageCanReceiveFocus = browsePageCanReceiveFocus(
                            active = active,
                            dpadFocusEnabled = browseDpadFocusEnabled,
                            contentFocusSuppressed = contentFocusSuppressed,
                            page = page,
                            targetPage = browsePagerPage,
                            pagerSettledAtTarget = browsePagerSettledAtTarget,
                            programmaticScrollTarget = browsePagerProgrammaticScrollTarget,
                            transitionFocusSourcePage = browsePagerTransitionFocusSourcePage,
                        )
                        val pageFocusCurrentRequestNonce = if (pageCanReceiveFocus && page == browsePagerPage) {
                            browseFocusRequestNonce
                        } else {
                            0L
                        }
                        browsePageStateHolder.SaveableStateProvider(pageSection) {
                            BrowseSectionPage(
                                pageSection = pageSection,
                                pageIndex = page,
                                pageCanReceiveFocus = pageCanReceiveFocus,
                                pageFocusCurrentRequestNonce = pageFocusCurrentRequestNonce,
                            )
                        }
                    }
                }
            }

        }

        if (browseChromePolicy.showBottomChrome) {
            BrowseHomeBottomBar(
                state = browseHomeChromeState,
                onOpenSearch = {
                    if (catalogActionsEnabled) {
                        searchDialogOpen = true
                    }
                },
                onOpenFilters = {
                    if (catalogActionsEnabled) {
                        filtersDialogOpen = true
                    }
                },
                onOpenSettings = onOpenSettings,
                onOpenDownloads = onOpenDownloads,
                onOpenLogin = onOpenLogin,
                onOpenProfile = onOpenProfile,
                onSectionSelected = onBrowsePagerSectionSelected,
                sectionTabsFocusRequester = browseSectionTabFocusRequesters[effectiveHomeSection],
                sectionTabFocusRequesters = browseSectionTabFocusRequesters,
                sectionTabsOnExitUp = if (showPhoneScheduleCalendarInBottomChrome) {
                    ::requestScheduleCalendarFocus
                } else {
                    ::requestCurrentBrowseContentFocus
                },
                sectionTabsFocusEnabled = browseSectionTabsFocusEnabled,
                hazeState = if (browseChromeHazeActive) browseChromeHazeState else null,
                showScheduleCalendar = showPhoneScheduleCalendarInBottomChromeVisual,
                scheduleDayGroups = phoneScheduleDayGroups,
                selectedScheduleEpochDay = phoneScheduleSelectedGroup?.epochDay ?: Long.MIN_VALUE,
                scheduleLocale = state.settings.contentLanguage.uiLocale(),
                scheduleCalendarFocusRequestNonce = scheduleCalendarFocusRequestNonce,
                scheduleCalendarFocusEnabled = browseDpadFocusEnabled,
                scheduleCalendarOnExitUp = ::requestCurrentBrowseContentFocus,
                scheduleCalendarOnExitDown = {
                    requestBrowseSectionTabsFocus(
                        section = BrowseSection.Schedule,
                        releasePagerFocusTransition = true,
                    )
                },
                onScheduleDaySelected = { epochDay ->
                    scheduleSelectedEpochDay = epochDay
                    updateStoredBrowseFocus(BrowseSection.Schedule, 0)
                    browseFocusScope.launch {
                        scheduleGridState.animateScrollToItem(0, 0)
                    }
                },
                scheduleCalendarVisibilityProgress = phoneScheduleCalendarVisualProgress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { size ->
                        val measuredHeight = with(browseScreenDensity) { size.height.toDp() }
                        if (showPhoneScheduleCalendarInBottomChromeVisual) {
                            browseBottomChromeExpandedHeight = maxOf(
                                browseBottomChromeExpandedHeight,
                                measuredHeight,
                            )
                        } else {
                            browseBottomChromeBaseMeasuredHeight = measuredHeight
                        }
                    },
            )
        }

        BrowseCatalogDialogs(
            state = state,
            catalogActionsEnabled = catalogActionsEnabled,
            searchDialogOpen = searchDialogOpen,
            filtersDialogOpen = filtersDialogOpen,
            searchKeyboardDismissRequest = searchKeyboardDismissRequest,
            searchInputAction = searchInputAction,
            searchInputActionRequest = searchInputActionRequest,
            onQueryChange = onQueryChange,
            onSearchSubmitted = onSearchSubmitted,
            onSearchHistorySelected = onSearchHistorySelected,
            onFiltersChange = onFiltersChange,
            onResetFilters = onResetFilters,
            onDismissSearch = { searchDialogOpen = false },
            onDismissFilters = { filtersDialogOpen = false },
            onSearchExitDown = {
                searchDialogOpen = false
                activeHomeBackToTopHandler
                    ?.takeIf { handler -> handler.section == effectiveHomeSection }
                    ?.handleBackToTop(withFocus = true)
            },
        )
    }
}

internal fun browseCatalogActionsEnabledForSection(
    section: BrowseSection,
    forcedOfflineMode: Boolean,
): Boolean {
    return !forcedOfflineMode && section == BrowseSection.Catalog
}

private val BrowseTopBarScrollCollapseDistance = 180.dp
private val BrowseBottomChromeFallbackProtectedHeight = 96.dp
