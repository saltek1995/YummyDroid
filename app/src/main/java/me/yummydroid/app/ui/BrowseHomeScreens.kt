package me.yummydroid.app.ui
import android.content.res.Configuration
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.ScheduleAnime
import me.yummydroid.app.DownloadTaskState
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.readyListOrEmpty
import me.yummydroid.app.ui.components.animatedFocusBorder
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.yummySurfaceContentColor
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.YummyDroidUiState

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
        if (forcedOffline) listOf(BrowseSection.Downloads) else visibleBrowseSections(isAuthorized)
    }
    val effectiveHomeSection = when {
        forcedOffline -> BrowseSection.Downloads
        state.homeSection == BrowseSection.History && !isAuthorized -> BrowseSection.Catalog
        else -> state.homeSection
    }
    LaunchedEffect(state.homeSection, isAuthorized, forcedOffline) {
        when {
            forcedOffline && state.homeSection != BrowseSection.Downloads -> {
                onBrowseSectionChange(BrowseSection.Downloads)
            }
            state.homeSection == BrowseSection.History && !isAuthorized -> {
                onBrowseSectionChange(BrowseSection.Catalog)
            }
        }
    }
    val isCatalog = effectiveHomeSection == BrowseSection.Catalog
    val catalogActionsEnabled = browseCatalogActionsEnabledForSection(
        section = effectiveHomeSection,
        forcedOfflineMode = forcedOffline,
    )
    val isSearching = isCatalog && state.searchQuery.isNotBlank()
    val contentState = if (isSearching) state.searchResults else state.featured
    val pagingState = if (isSearching) state.searchPaging else state.featuredPaging
    val configuration = LocalConfiguration.current
    val browseScreenDensity = LocalDensity.current
    val inputModeManager = LocalInputModeManager.current
    val browseDpadFocusEnabled = inputModeManager.inputMode != InputMode.Touch
    val isWide = configuration.screenWidthDp >= 720
    val catalogGridState = browseCoordinator.catalogGridState
    val scheduleGridState = browseCoordinator.scheduleGridState
    val historyGridState = browseCoordinator.historyGridState
    val browseTopActionsFocusRequester = remember { FocusRequester() }
    val browseSectionTabFocusRequesters = remember(browsePagerSections) {
        browsePagerSections.associateWith { FocusRequester() }
    }
    val browseChromeHazeState = remember { HazeState() }
    var browseBottomChromeHeight by remember { mutableStateOf(0.dp) }
    val browseBottomChromeContentPadding = if (!isWide || forcedOffline) {
        if (browseBottomChromeHeight > 0.dp) {
            (browseBottomChromeHeight - BrowseBottomChromeInteractiveTopPadding).coerceAtLeast(0.dp)
        } else {
            BrowseBottomChromeFallbackProtectedHeight
        }
    } else {
        0.dp
    }
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
    val scheduleZoneId = remember { ZoneId.systemDefault() }
    val phoneScheduleDayGroups = remember(state.schedule, scheduleZoneId) {
        state.schedule.readyListOrEmpty().toScheduleDayGroups(scheduleZoneId)
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
            browseCoordinator.scrollToTop(BrowseSection.Schedule)
            withFrameNanos { }
            scheduleCalendarFocusRequestNonce += 1L
        }
        return true
    }

    fun requestBrowseTopActionsFocus(): Boolean {
        if (
            browseCoordinator.topBarVisible(effectiveHomeSection) &&
            browseDpadFocusEnabled &&
            runCatching { browseTopActionsFocusRequester.requestFocus() }.getOrDefault(false)
        ) {
            return true
        }
        browseFocusScope.launch {
            browseCoordinator.scrollToTop(effectiveHomeSection)
            withFrameNanos { }
            if (browseDpadFocusEnabled) {
                runCatching { browseTopActionsFocusRequester.requestFocus() }
            }
        }
        return true
    }

    fun requestBrowseSectionTabsFocus(section: BrowseSection = effectiveHomeSection): Boolean {
        if (!browseDpadFocusEnabled) return false
        if (forcedOffline) return false
        val requester = browseSectionTabFocusRequesters[section] ?: return false
        return runCatching { requester.requestFocus() }.getOrDefault(false)
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
    val activeDownloadCount = state.downloadQueue.tasks.count { task ->
        task.state == DownloadTaskState.Queued ||
            task.state == DownloadTaskState.Running ||
            task.state == DownloadTaskState.Paused
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
    val browsePagerIsAwayFromTarget = useBrowsePager &&
        (browsePagerState.currentPage != browsePagerPage ||
            abs(browsePagerState.currentPageOffsetFraction) > 0.001f)
    var browsePageFocusRequestNonce by remember { mutableLongStateOf(0L) }
    var browsePageFocusRequestSection by remember { mutableStateOf(effectiveHomeSection) }
    var keepTabsFocusedForSectionChange by remember { mutableStateOf(false) }
    var pendingTabsFocusSection by remember { mutableStateOf<BrowseSection?>(null) }
    var browsePagerProgrammaticScrollTarget by remember { mutableStateOf<Int?>(null) }
    var browsePagerTransitionFocusSourcePage by remember { mutableStateOf<Int?>(null) }
    var browsePagerRequestContentFocusOnFinish by remember { mutableStateOf(false) }
    var browseTopBarProgrammaticTargetVisible by remember { mutableStateOf<Boolean?>(null) }
    val browseTopBarTargetVisible by remember(
        effectiveHomeSection,
        browseCoordinator,
    ) {
        derivedStateOf {
            browseCoordinator.topBarVisible(effectiveHomeSection)
        }
    }
    val browseTopBarEffectiveTargetVisible = browseTopBarProgrammaticTargetVisible
        ?: browseTopBarTargetVisible
    val browseTopBarGestureDriven = useBrowsePager &&
        browsePagerProgrammaticScrollTarget == null &&
        browsePagerTransitionFocusSourcePage == null &&
        browsePagerState.isScrollInProgress
    val browseTopBarRawVisibilityProgress by remember(
        browsePagerSections,
        browsePagerState,
        browseCoordinator,
        browseTopBarEffectiveTargetVisible,
        browseTopBarGestureDriven,
    ) {
        derivedStateOf {
            if (!browseTopBarGestureDriven || browsePagerSections.isEmpty()) {
                if (browseTopBarEffectiveTargetVisible) 1f else 0f
            } else {
                val maxPage = browsePagerSections.lastIndex
                val position = (browsePagerState.currentPage + browsePagerState.currentPageOffsetFraction)
                    .coerceIn(0f, maxPage.toFloat())
                val startPage = position.toInt().coerceIn(0, maxPage)
                val endPage = (startPage + 1).coerceAtMost(maxPage)
                val fraction = (position - startPage).coerceIn(0f, 1f)
                val startProgress = if (browseCoordinator.topBarVisible(browsePagerSections[startPage])) 1f else 0f
                val endProgress = if (browseCoordinator.topBarVisible(browsePagerSections[endPage])) 1f else 0f
                startProgress + (endProgress - startProgress) * fraction
            }
        }
    }
    val animatedBrowseTopBarVisibilityProgress by animateFloatAsState(
        targetValue = browseTopBarRawVisibilityProgress,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "browseTopBarSharedVisibility",
    )
    val browseTopBarVisibilityProgress = if (browseTopBarGestureDriven) {
        browseTopBarRawVisibilityProgress
    } else {
        animatedBrowseTopBarVisibilityProgress
    }
    val browseTopBarDisplayVisible = browseTopBarEffectiveTargetVisible ||
        browseTopBarVisibilityProgress > 0.001f
    val browseTvGlassProgress = ((0.14f - browseTopBarVisibilityProgress) / 0.14f)
        .coerceIn(0f, 1f)
    val homeBrowseBackState = remember(
        effectiveHomeSection,
        browsePagerSections,
        browsePagerPage,
        browsePagerState.currentPage,
        browsePagerState.currentPageOffsetFraction,
        browsePagerState.isScrollInProgress,
        browsePagerIsAwayFromTarget,
    ) {
        if (!useBrowsePager || effectiveHomeSection == BrowseSection.Downloads || browsePagerSections.isEmpty()) {
            HomeBrowseBackState(effectiveHomeSection, settledAtStateSection = true)
        } else {
            val visiblePage = (browsePagerState.currentPage + browsePagerState.currentPageOffsetFraction)
                .roundToInt()
                .coerceIn(0, browsePagerSections.lastIndex)
            HomeBrowseBackState(
                visualSection = browsePagerSections.getOrNull(visiblePage) ?: effectiveHomeSection,
                settledAtStateSection = !browsePagerState.isScrollInProgress && !browsePagerIsAwayFromTarget,
            )
        }
    }
    LaunchedEffect(active, homeBrowseBackState) {
        if (active) {
            onHomeBrowseBackStateChange(homeBrowseBackState)
        }
    }
    val browsePagerPosition = browsePagerState.currentPage + browsePagerState.currentPageOffsetFraction
    val browseTabPosition = if (!active) {
        browsePagerPage.toFloat()
    } else if (
        useBrowsePager &&
        effectiveHomeSection in browsePagerSections &&
        browsePagerState.isScrollInProgress
    ) {
        browsePagerPosition
    } else if (browsePagerProgrammaticScrollTarget != null) {
        browsePagerPosition
    } else if (effectiveHomeSection in browsePagerSections || browsePagerProgrammaticScrollTarget != null) {
        browsePagerPage.toFloat()
    } else {
        null
    }
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
                if (browseDpadFocusEnabled) {
                    browsePageFocusRequestNonce += 1L
                }
            }
        }
    }
    val browseFocusRequestNonce = dpadLayerFocusRequestNonce + browsePageFocusRequestNonce
    val browsePagerSettledAtTarget = effectiveHomeSection in browsePagerSections &&
        (!useBrowsePager || (!browsePagerState.isScrollInProgress && !browsePagerIsAwayFromTarget))

    fun browsePagerIsSettledAt(page: Int): Boolean {
        return !browsePagerState.isScrollInProgress &&
            browsePagerState.settledPage == page &&
            browsePagerState.currentPage == page &&
            abs(browsePagerState.currentPageOffsetFraction) <= 0.001f
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
        browseTopBarProgrammaticTargetVisible = null
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
                    browseTopBarProgrammaticTargetVisible =
                        browseCoordinator.topBarVisible(effectiveHomeSection)
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
            browsePagerProgrammaticScrollTarget = browsePagerSections.indexOf(section).takeIf { page -> page >= 0 }
            browseTopBarProgrammaticTargetVisible = browseCoordinator.topBarVisible(section)
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
    fun BrowseAnimeGridPage(
        section: BrowseSection,
        contentState: LoadState<List<Anime>>,
        pagingState: PagingUiState,
        gridState: LazyGridState,
        focusFirstRequest: FocusFirstRequest,
        pageIndex: Int,
        pageCanReceiveFocus: Boolean,
        pageFocusCurrentRequestNonce: Long,
        emptyMessage: String,
        onLoadMore: () -> Unit,
    ) {
        AnimeGridSection(
            contentState = contentState,
            pagingState = pagingState,
            gridState = gridState,
            cardSize = state.settings.posterCardSize,
            contentTopPadding = if (isWide && !forcedOffline) {
                BrowseTvPinnedTabsContentTopPadding
            } else {
                0.dp
            },
            contentBottomPadding = browseBottomChromeContentPadding,
            focusFirstRequest = focusFirstRequest,
            focusCurrentRequestNonce = pageFocusCurrentRequestNonce,
            contentFocusEnabled = pageCanReceiveFocus,
            currentFocusedIndex = { browseCoordinator.focusedIndex(section) },
            onFocusedIndexChange = { index -> updateStoredBrowseFocus(section, index) },
            backToTopSection = section,
            onRegisterBackToTopHandler = { handler ->
                updateHomeBackToTopHandler(section, handler)
            },
            emptyMessage = emptyMessage,
            onRetry = onRefresh,
            onLoadMore = onLoadMore,
            onExitHorizontalDirection = { direction ->
                handleBrowsePageHorizontalExit(pageIndex, direction)
            },
            onExitUp = if (isWide && !forcedOffline) {
                { requestBrowseSectionTabsFocus() }
            } else {
                ::requestBrowseTopActionsFocus
            },
            exitUpFocusRequester = if (isWide && !forcedOffline) {
                browseSectionTabFocusRequesters[section]
            } else {
                null
            },
            onExitDown = if (isWide && !forcedOffline) {
                { false }
            } else {
                { requestBrowseSectionTabsFocus() }
            },
            onOpenAnime = onOpenAnime,
        )
    }

    @Composable
    fun BrowseSectionPage(
        pageSection: BrowseSection,
        pageIndex: Int,
        pageCanReceiveFocus: Boolean,
        pageFocusCurrentRequestNonce: Long,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = pageCanReceiveFocus }
                .focusGroup(),
        ) {
            when (pageSection) {
                BrowseSection.Catalog -> BrowseAnimeGridPage(
                    section = BrowseSection.Catalog,
                    contentState = contentState,
                    pagingState = pagingState,
                    gridState = catalogGridState,
                    focusFirstRequest = catalogFocusFirstRequest,
                    pageIndex = pageIndex,
                    pageCanReceiveFocus = pageCanReceiveFocus,
                    pageFocusCurrentRequestNonce = pageFocusCurrentRequestNonce,
                    emptyMessage = if (isSearching) uiText(UiStringKey.NothingFound) else uiText(UiStringKey.CatalogIsEmpty),
                    onLoadMore = onLoadMoreAnime,
                )
                BrowseSection.Schedule -> ScheduleSection(
                    state = state.schedule,
                    gridState = scheduleGridState,
                    cardSize = state.settings.posterCardSize,
                    locale = state.settings.contentLanguage.uiLocale(),
                    focusFirstRequest = scheduleFocusFirstRequest,
                    focusCurrentRequestNonce = pageFocusCurrentRequestNonce,
                    calendarFocusRequestNonce = scheduleCalendarFocusRequestNonce,
                    contentFocusEnabled = pageCanReceiveFocus,
                    showCalendarInGrid = isWide && !forcedOffline,
                    selectedEpochDay = scheduleSelectedEpochDay,
                    onSelectedEpochDayChange = { epochDay -> scheduleSelectedEpochDay = epochDay },
                    currentFocusedIndex = { browseCoordinator.focusedIndex(BrowseSection.Schedule) },
                    onFocusedIndexChange = { index -> updateStoredBrowseFocus(BrowseSection.Schedule, index) },
                    pinnedTopPadding = if (isWide && !forcedOffline) {
                        BrowseTvScheduleTabsContentTopPadding
                    } else {
                        0.dp
                    },
                    contentBottomPadding = browseBottomChromeContentPadding,
                    onRegisterBackToTopHandler = { handler ->
                        updateHomeBackToTopHandler(BrowseSection.Schedule, handler)
                    },
                    onRetry = onRefresh,
                    onExitHorizontalDirection = { direction ->
                        handleBrowsePageHorizontalExit(pageIndex, direction)
                    },
                    onExitUp = if (isWide && !forcedOffline) {
                        { requestBrowseSectionTabsFocus() }
                    } else {
                        ::requestBrowseTopActionsFocus
                    },
                    onExitDown = if (isWide && !forcedOffline) {
                        { false }
                    } else {
                        ::requestScheduleCalendarFocus
                    },
                    onOpenAnime = onOpenAnime,
                )
                BrowseSection.History -> BrowseAnimeGridPage(
                    section = BrowseSection.History,
                    contentState = state.historyAnime,
                    pagingState = PagingUiState(canLoadMore = false),
                    gridState = historyGridState,
                    focusFirstRequest = historyFocusFirstRequest,
                    pageIndex = pageIndex,
                    pageCanReceiveFocus = pageCanReceiveFocus,
                    pageFocusCurrentRequestNonce = pageFocusCurrentRequestNonce,
                    emptyMessage = uiText(UiStringKey.HistoryIsEmpty),
                    onLoadMore = {},
                )
                BrowseSection.Downloads -> DownloadsSection(
                    state = state,
                    focusCurrentRequestNonce = pageFocusCurrentRequestNonce,
                    contentBottomPadding = browseBottomChromeContentPadding,
                    onClearHistory = onClearDownloadHistory,
                    onCancelDownload = onCancelDownload,
                    onPauseDownload = onPauseDownload,
                    onResumeDownload = onResumeDownload,
                    onOpenAnime = onOpenAnime,
                )
            }
        }
    }

    @Composable
    fun BrowseTopBarChrome(
        modifier: Modifier = Modifier,
        collapseWhenHidden: Boolean = true,
    ) {
        BrowseTopBarModern(
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
            auth = state.auth,
            activeFilters = if (catalogActionsEnabled) state.filters.activeCount else 0,
            activeSearch = catalogActionsEnabled && isSearching,
            activeFiltersPanel = catalogActionsEnabled && filtersDialogOpen,
            activeSettings = settingsDialogOpen,
            activeDownloads = effectiveHomeSection == BrowseSection.Downloads,
            activeProfile = loginDialogOpen || profileDialogOpen,
            activeDownloadCount = activeDownloadCount,
            forcedOfflineMode = state.forcedOfflineMode,
            searchEnabled = catalogActionsEnabled,
            filtersEnabled = catalogActionsEnabled,
            onOpenLogin = onOpenLogin,
            onOpenProfile = onOpenProfile,
            isWide = isWide,
            activeSection = effectiveHomeSection,
            visibleSections = browsePagerSections,
            activeSectionPosition = browseTabPosition,
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
            showCompactControls = false,
            modifier = modifier,
            collapseWhenHidden = collapseWhenHidden,
            visible = browseTopBarDisplayVisible,
            visibilityProgress = browseTopBarVisibilityProgress,
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
                .fillMaxSize()
                .hazeSource(browseChromeHazeState),
        ) {
            BrowseTopBarChrome()

            Box(modifier = Modifier.weight(1f)) {
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
                        val keepCurrentCardFocusedDuringPagerTransition =
                            browsePagerTransitionFocusSourcePage == page &&
                                browsePagerProgrammaticScrollTarget != null &&
                                !browsePagerSettledAtTarget
                        val canFocusProgrammaticTargetDuringPagerTransition =
                            browsePagerProgrammaticScrollTarget == page &&
                                page == browsePagerPage
                        val pageCanReceiveFocus = active &&
                            browseDpadFocusEnabled &&
                            !contentFocusSuppressed &&
                            (
                                page == browsePagerPage && browsePagerSettledAtTarget ||
                                    canFocusProgrammaticTargetDuringPagerTransition ||
                                    keepCurrentCardFocusedDuringPagerTransition
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

                if (isWide && !forcedOffline) {
                    BrowseTvSectionIndicatorBar(
                        activeSection = effectiveHomeSection,
                        visibleSections = browsePagerSections,
                        activeSectionPosition = browseTabPosition,
                        onSectionSelected = onBrowsePagerSectionSelected,
                        sectionFocusRequesters = browseSectionTabFocusRequesters,
                        onExitUp = ::requestBrowseTopActionsFocus,
                        onExitDown = {
                            if (effectiveHomeSection == BrowseSection.Schedule) {
                                requestScheduleCalendarFocus()
                            } else {
                                requestCurrentBrowseContentFocus()
                            }
                        },
                        hazeState = browseChromeHazeState,
                        backdropVisible = true,
                        backdropProgress = browseTvGlassProgress,
                        sectionTabsFocusEnabled = browseSectionTabsFocusEnabled,
                        squareTopCorners = browseTopBarVisibilityProgress <= 0.01f,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(1f),
                    )
                }
            }

        }

        if (!isWide || forcedOffline) {
            BrowseBottomBarModern(
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
                auth = state.auth,
                activeFilters = if (catalogActionsEnabled) state.filters.activeCount else 0,
                activeSearch = catalogActionsEnabled && isSearching,
                activeFiltersPanel = catalogActionsEnabled && filtersDialogOpen,
                activeSettings = settingsDialogOpen,
                activeDownloads = effectiveHomeSection == BrowseSection.Downloads,
                activeProfile = loginDialogOpen || profileDialogOpen,
                activeDownloadCount = activeDownloadCount,
                searchEnabled = catalogActionsEnabled,
                filtersEnabled = catalogActionsEnabled,
                onOpenLogin = onOpenLogin,
                onOpenProfile = onOpenProfile,
                activeSection = effectiveHomeSection,
                visibleSections = browsePagerSections,
                activeSectionPosition = browseTabPosition,
                onSectionSelected = onBrowsePagerSectionSelected,
                showSectionTabs = !forcedOffline,
                sectionTabsFocusRequester = browseSectionTabFocusRequesters[effectiveHomeSection],
                sectionTabFocusRequesters = browseSectionTabFocusRequesters,
                sectionTabsOnExitUp = ::requestCurrentBrowseContentFocus,
                sectionTabsFocusEnabled = browseSectionTabsFocusEnabled,
                hazeState = browseChromeHazeState,
                topProtectedContent = if (showPhoneScheduleCalendarInBottomChrome) {
                    { calendarModifier ->
                        ScheduleCalendarBlock(
                            dayGroups = phoneScheduleDayGroups,
                            selectedEpochDay = phoneScheduleSelectedGroup?.epochDay ?: Long.MIN_VALUE,
                            locale = state.settings.contentLanguage.uiLocale(),
                            focusRequestNonce = scheduleCalendarFocusRequestNonce,
                            focusEnabled = browseDpadFocusEnabled,
                            onExitUp = ::requestCurrentBrowseContentFocus,
                            onExitDown = { requestBrowseSectionTabsFocus(BrowseSection.Schedule) },
                            onSelectDay = { epochDay ->
                                scheduleSelectedEpochDay = epochDay
                                updateStoredBrowseFocus(BrowseSection.Schedule, 0)
                                browseFocusScope.launch {
                                    scheduleGridState.scrollToItem(0, 0)
                                }
                            },
                            modifier = calendarModifier,
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { size ->
                        browseBottomChromeHeight = with(browseScreenDensity) { size.height.toDp() }
                    },
            )
        }

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
                onDismiss = { searchDialogOpen = false },
                onExitDown = {
                    searchDialogOpen = false
                    activeHomeBackToTopHandler
                        ?.takeIf { handler -> handler.section == effectiveHomeSection }
                        ?.handleBackToTop(withFocus = true)
                },
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
                onDismiss = { filtersDialogOpen = false },
            )
        }
    }
}

internal fun browseCatalogActionsEnabledForSection(
    section: BrowseSection,
    forcedOfflineMode: Boolean,
): Boolean {
    return !forcedOfflineMode && section == BrowseSection.Catalog
}

private val BrowseTvPinnedTabsContentTopPadding = BrowseTvSectionIndicatorHeight - 24.dp
private val BrowseTvScheduleTabsContentTopPadding = BrowseTvSectionIndicatorHeight
private val BrowseFocusedCardBottomGap = 20.dp
private val BrowseBottomChromeFallbackProtectedHeight = 96.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberBrowseGridBringIntoViewSpec(topInset: Dp, bottomInset: Dp): BringIntoViewSpec {
    val density = LocalDensity.current
    val protectedTopPx = with(density) { topInset.toPx() }
    val protectedBottomPx = with(density) { bottomInset.toPx() }
    return remember(protectedTopPx, protectedBottomPx) {
        EdgeGridBringIntoViewSpec(
            protectedTopPx = protectedTopPx,
            protectedBottomPx = protectedBottomPx,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
private class EdgeGridBringIntoViewSpec(
    private val protectedTopPx: Float,
    private val protectedBottomPx: Float,
) : BringIntoViewSpec {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (containerSize <= 0f || size <= 0f) return 0f
        val safeTop = protectedTopPx.coerceIn(0f, containerSize)
        val safeBottom = (containerSize - protectedBottomPx.coerceAtLeast(0f)).coerceIn(safeTop, containerSize)
        val safeHeight = (safeBottom - safeTop).coerceAtLeast(0f)
        if (size > safeHeight) {
            return offset - safeTop
        }
        return when {
            offset < safeTop -> offset - safeTop
            offset + size > safeBottom -> offset + size - safeBottom
            else -> 0f
        }
    }
}

private data class PagerAlignmentState(
    val isScrollInProgress: Boolean,
    val settledPage: Int,
    val currentPage: Int,
    val offset: Float,
)

internal data class HomeBrowseBackState(
    val visualSection: BrowseSection,
    val settledAtStateSection: Boolean,
)

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun AnimeGridSection(
    contentState: LoadState<List<Anime>>,
    pagingState: PagingUiState,
    gridState: LazyGridState,
    cardSize: PosterCardSize,
    contentTopPadding: Dp = 0.dp,
    contentBottomPadding: Dp = 0.dp,
    focusFirstRequest: FocusFirstRequest,
    focusCurrentRequestNonce: Long,
    contentFocusEnabled: Boolean = true,
    currentFocusedIndex: () -> Int,
    onFocusedIndexChange: (Int) -> Unit,
    backToTopSection: BrowseSection,
    onRegisterBackToTopHandler: ((HomeBackToTopHandler?) -> Unit)? = null,
    emptyMessage: String,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onExitHorizontalDirection: (VisualGridDirection) -> Boolean = { true },
    onExitUp: () -> Boolean = { false },
    exitUpFocusRequester: FocusRequester? = null,
    onExitDown: () -> Boolean = { false },
    onOpenAnime: (Long) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columnsCount = remember(maxWidth, cardSize) {
            cardSize.resolveCatalogColumns(maxWidth.value.roundToInt())
        }
        AnimeListStateContent(
            state = contentState,
            onRetry = onRetry,
            emptyMessage = emptyMessage,
        ) { animes ->
        val focusScope = rememberCoroutineScope()
        val density = LocalDensity.current
        val focusedGridTopInset = if (contentTopPadding > 0.dp) {
            24.dp + contentTopPadding
        } else {
            0.dp
        }
        val focusedGridTopInsetPx = with(density) { focusedGridTopInset.toPx() }
        val focusedGridBottomInset = BrowseFocusedCardBottomGap + contentBottomPadding
        val focusedGridBottomInsetPx = with(density) { focusedGridBottomInset.toPx() }
        val itemFocusRequesters = remember(backToTopSection, animes.size, columnsCount) {
            List(animes.size) { FocusRequester() }
        }
        val lastLoadMoreRequestSize = remember(backToTopSection) { intArrayOf(-1) }
        var handledPersistentFocusResetNonce by remember(backToTopSection) { mutableLongStateOf(0L) }
        var handledTransientFocusResetNonce by remember(backToTopSection) { mutableLongStateOf(0L) }
        var handledCurrentFocusRequestNonce by remember(backToTopSection) { mutableLongStateOf(0L) }
        val focusRequestJob = remember(backToTopSection, columnsCount) { FocusRequestJobRef() }

        fun currentFocusedAnimeIndex(): Int = currentFocusedIndex()

        fun maybeLoadMoreNear(index: Int) {
            if (
                index < 0 ||
                columnsCount <= 0 ||
                !pagingState.canLoadMore ||
                pagingState.isLoadingMore ||
                pagingState.error != null ||
                lastLoadMoreRequestSize[0] == animes.size
            ) {
                return
            }
            val focusedRow = index / columnsCount
            val lastLoadedRow = animes.lastIndex.coerceAtLeast(0) / columnsCount
            if (lastLoadedRow - focusedRow < 2) {
                lastLoadMoreRequestSize[0] = animes.size
                onLoadMore()
            }
        }

        fun updateFocusedAnimeIndex(index: Int) {
            if (currentFocusedAnimeIndex() != index) {
                onFocusedIndexChange(index)
            }
            maybeLoadMoreNear(index)
        }

        fun requestAnimeItemFocus(index: Int): Boolean {
            val requester = itemFocusRequesters.getOrNull(index) ?: return false
            return runCatching { requester.requestFocus() }.getOrDefault(false)
        }

        val focusController = BrowseGridFocusController(
            gridState = gridState,
            itemCount = animes.size,
            columns = columnsCount,
            leadingGridItemCount = 0,
            currentFocusedIndex = ::currentFocusedAnimeIndex,
            updateFocusedIndex = ::updateFocusedAnimeIndex,
            requestItemFocus = ::requestAnimeItemFocus,
            protectedTopPx = focusedGridTopInsetPx,
            protectedBottomPx = focusedGridBottomInsetPx,
            focusScope = focusScope,
            focusRequestJob = focusRequestJob,
        )

        fun handleAnimeGridDirection(index: Int, key: Key): Boolean {
            if (columnsCount <= 0 || index !in animes.indices) return false
            val direction = when (key) {
                Key.DirectionLeft -> VisualGridDirection.Left
                Key.DirectionRight -> VisualGridDirection.Right
                Key.DirectionUp -> VisualGridDirection.Up
                Key.DirectionDown -> VisualGridDirection.Down
                else -> return false
            }
            val sourceIndex = index.takeIf { it in animes.indices }
                ?: currentFocusedAnimeIndex().takeIf { it in animes.indices }
                ?: return false
            val target = visualGridMoveTarget(
                index = sourceIndex,
                total = animes.size,
                columns = columnsCount,
                direction = direction,
            )
            if (target != null) {
                return focusController.moveFocusTo(target)
            }
            if (direction == VisualGridDirection.Up && exitUpFocusRequester != null) {
                return false
            }
            if (direction == VisualGridDirection.Down && pagingState.canLoadMore && !pagingState.isLoadingMore) {
                onLoadMore()
            }
            return when (direction) {
                VisualGridDirection.Left,
                VisualGridDirection.Right -> onExitHorizontalDirection(direction)
                VisualGridDirection.Down -> onExitDown()
                VisualGridDirection.Up -> onExitUp()
            }
        }

        fun canHandleBackToTop(): Boolean {
            return gridState.canHandleBrowseRootBackToTop(backToTopSection)
        }

        fun handleBackToTop(withFocus: Boolean): Boolean {
            if (!canHandleBackToTop()) return false
            focusController.cancelPendingRequest()
            if (withFocus && animes.isNotEmpty()) {
                return focusController.moveFocusTo(0)
            }
            focusScope.launch {
                gridState.scrollToItem(0, 0)
            }
            return true
        }

        DisposableEffect(animes.size, columnsCount, onRegisterBackToTopHandler) {
            val register = onRegisterBackToTopHandler
            if (register != null && animes.isNotEmpty() && columnsCount > 0) {
                register(
                        HomeBackToTopHandler(
                            section = backToTopSection,
                            canHandle = ::canHandleBackToTop,
                            handle = ::handleBackToTop,
                    ),
                )
            } else {
                register?.invoke(null)
            }
            onDispose { register?.invoke(null) }
        }

        LaunchedEffect(focusFirstRequest, animes.size, columnsCount) {
            if (animes.isEmpty()) return@LaunchedEffect
            val shouldHandlePersistent = focusFirstRequest.persistentNonce > 0L &&
                focusFirstRequest.persistentNonce != handledPersistentFocusResetNonce
            val shouldHandleTransient = focusFirstRequest.transientNonce > 0L &&
                focusFirstRequest.transientNonce != handledTransientFocusResetNonce
            if (!shouldHandlePersistent && !shouldHandleTransient) return@LaunchedEffect
            val targetIndex = 0
            val targetRowStart = focusController.rowStartIndex(targetIndex)
            focusController.cancelPendingRequest()
            updateFocusedAnimeIndex(targetIndex)
            gridState.scrollToItem(targetRowStart, 0)
            focusController.focusItemAfterLayout(targetIndex)
            gridState.scrollToItem(targetRowStart, 0)
            if (shouldHandlePersistent) {
                handledPersistentFocusResetNonce = focusFirstRequest.persistentNonce
            }
            if (shouldHandleTransient) {
                handledTransientFocusResetNonce = focusFirstRequest.transientNonce
            }
        }

        LaunchedEffect(focusCurrentRequestNonce, animes.size, columnsCount) {
            if (
                !contentFocusEnabled ||
                focusCurrentRequestNonce <= 0L ||
                focusCurrentRequestNonce == handledCurrentFocusRequestNonce ||
                animes.isEmpty()
            ) {
                return@LaunchedEffect
            }
            withFrameNanos { }
            val visibleIndexes = gridState.layoutInfo.visibleItemsInfo
                .asSequence()
                .map { item -> item.index }
                .filter { index -> index in animes.indices }
                .toList()
            val targetIndex = currentFocusedAnimeIndex()
                .takeIf { index -> index in animes.indices }
                ?: visibleIndexes.minOrNull()
                ?: gridState.firstVisibleItemIndex.coerceIn(0, animes.lastIndex)
            updateFocusedAnimeIndex(targetIndex)
            focusController.focusItemWhenVisible(targetIndex)
            handledCurrentFocusRequestNonce = focusCurrentRequestNonce
        }

        LaunchedEffect(animes.size) {
            if (animes.isEmpty()) {
                updateFocusedAnimeIndex(-1)
            } else if (currentFocusedAnimeIndex() > animes.lastIndex) {
                updateFocusedAnimeIndex(animes.lastIndex)
            }
        }

        LaunchedEffect(
            animes.size,
            columnsCount,
            pagingState.canLoadMore,
            pagingState.isLoadingMore,
            pagingState.error,
        ) {
            if (!pagingState.isLoadingMore) {
                lastLoadMoreRequestSize[0] = -1
            }
            maybeLoadMoreNear(currentFocusedAnimeIndex())
        }

        val browseGridBringIntoViewSpec = rememberBrowseGridBringIntoViewSpec(
            topInset = focusedGridTopInset,
            bottomInset = focusedGridBottomInset,
        )
        val gridBottomContentPadding = if (contentBottomPadding > 0.dp) {
            focusedGridBottomInset
        } else {
            24.dp + BrowseFocusedCardBottomGap
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides browseGridBringIntoViewSpec) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnsCount),
                state = gridState,
                contentPadding = PaddingValues(
                    start = 24.dp,
                    top = 24.dp + contentTopPadding,
                    end = 24.dp,
                    bottom = gridBottomContentPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .focusGroup(),
            ) {
                itemsIndexed(
                    items = animes,
                    key = { _, anime -> anime.id },
                    contentType = { _, _ -> "anime-card" },
                ) { index, anime ->
                    AnimeCard(
                        anime = anime,
                        onClick = { onOpenAnime(anime.id) },
                        modifier = Modifier
                            .focusProperties { canFocus = contentFocusEnabled }
                            .focusRequester(itemFocusRequesters[index])
                            .then(
                                if (exitUpFocusRequester != null && index < columnsCount) {
                                    Modifier.focusProperties { up = exitUpFocusRequester }
                                } else {
                                    Modifier
                                },
                            )
                            .onPreviewKeyEvent { event ->
                                event.type == KeyEventType.KeyDown &&
                                    handleAnimeGridDirection(index, event.key)
                            }
                            .onFocusChanged { focusState ->
                                if (focusState.hasFocus) {
                                    updateFocusedAnimeIndex(index)
                                }
                            },
                    )
                }

                if (pagingState.isLoadingMore || pagingState.canLoadMore || pagingState.error != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        PagingGridFooter(
                            paging = pagingState,
                            onLoadMore = onLoadMore,
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ScheduleSection(
    state: LoadState<List<ScheduleAnime>>,
    gridState: LazyGridState,
    cardSize: PosterCardSize,
    locale: Locale,
    focusFirstRequest: FocusFirstRequest,
    focusCurrentRequestNonce: Long,
    calendarFocusRequestNonce: Long = 0L,
    contentFocusEnabled: Boolean = true,
    showCalendarInGrid: Boolean = true,
    selectedEpochDay: Long,
    onSelectedEpochDayChange: (Long) -> Unit,
    currentFocusedIndex: () -> Int,
    onFocusedIndexChange: (Int) -> Unit,
    pinnedTopPadding: Dp = 0.dp,
    contentBottomPadding: Dp = 0.dp,
    onRegisterBackToTopHandler: ((HomeBackToTopHandler?) -> Unit)? = null,
    onRetry: () -> Unit,
    onExitHorizontalDirection: (VisualGridDirection) -> Boolean = { true },
    onExitUp: () -> Boolean = { false },
    onExitDown: () -> Boolean = { false },
    onOpenAnime: (Long) -> Unit,
) {
    when (state) {
        LoadState.Loading -> LoadingPane(Modifier.fillMaxSize())
        is LoadState.Error -> ErrorPane(
            message = state.message,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
        )
        is LoadState.Ready -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val columnsCount = remember(maxWidth, cardSize) {
                cardSize.resolveCatalogColumns(maxWidth.value.roundToInt())
            }
            val density = LocalDensity.current
            val zoneId = remember { ZoneId.systemDefault() }
            val scheduleTimeFormatter = remember(locale) {
                DateTimeFormatter.ofPattern("HH:mm", locale)
            }
            val dayGroups = remember(state.data, zoneId) {
                state.data.toScheduleDayGroups(zoneId)
            }
            val dayGroupKeys = remember(dayGroups) { dayGroups.map { group -> group.epochDay } }
            val selectedScheduleDay = selectedEpochDay
            val selectedGroup = remember(dayGroups, selectedScheduleDay) {
                dayGroups.firstOrNull { group -> group.epochDay == selectedScheduleDay }
                    ?: dayGroups.todayOrClosest()
            }
            val visibleItems = selectedGroup?.items.orEmpty()
            val focusScope = rememberCoroutineScope()
            val scheduleDayKey = selectedGroup?.epochDay ?: Long.MIN_VALUE
            val itemFocusRequesters = remember(scheduleDayKey, visibleItems.size, columnsCount) {
                List(visibleItems.size) { FocusRequester() }
            }
            val focusedGridTopInset = if (pinnedTopPadding > 0.dp) {
                pinnedTopPadding + ScheduleFocusedCardTopGap
            } else {
                0.dp
            }
            val focusedGridTopInsetPx = with(density) { focusedGridTopInset.toPx() }
            val focusedGridBottomInset = BrowseFocusedCardBottomGap + contentBottomPadding
            val focusedGridBottomInsetPx = with(density) { focusedGridBottomInset.toPx() }
            var internalCalendarFocusRequestNonce by remember(scheduleDayKey) { mutableLongStateOf(0L) }
            var handledPersistentFocusResetNonce by remember { mutableLongStateOf(0L) }
            var handledTransientFocusResetNonce by remember { mutableLongStateOf(0L) }
            var handledCurrentFocusRequestNonce by remember { mutableLongStateOf(0L) }
            var suppressCalendarFocusAfterBackToTop by remember(scheduleDayKey) { mutableStateOf(false) }
            val focusRequestJob = remember(scheduleDayKey, columnsCount) { FocusRequestJobRef() }
            val scheduleGridBringIntoViewSpec = rememberBrowseGridBringIntoViewSpec(
                topInset = focusedGridTopInset,
                bottomInset = focusedGridBottomInset,
            )
            val scheduleGridBottomContentPadding = if (contentBottomPadding > 0.dp) {
                focusedGridBottomInset
            } else {
                24.dp + BrowseFocusedCardBottomGap
            }
            val leadingGridItemCount = if (showCalendarInGrid) 1 else 0
            val scheduleGridTopContentPadding = if (showCalendarInGrid) {
                pinnedTopPadding + ScheduleCalendarTopGap
            } else {
                pinnedTopPadding + 24.dp
            }

            fun updateFocusedScheduleIndex(index: Int) {
                if (currentFocusedIndex() != index) {
                    onFocusedIndexChange(index)
                }
            }

            fun requestScheduleItemFocus(index: Int): Boolean {
                val requester = itemFocusRequesters.getOrNull(index) ?: return false
                return runCatching { requester.requestFocus() }.getOrDefault(false)
            }

            val focusController = BrowseGridFocusController(
                gridState = gridState,
                itemCount = visibleItems.size,
                columns = columnsCount,
                leadingGridItemCount = leadingGridItemCount,
                currentFocusedIndex = currentFocusedIndex,
                updateFocusedIndex = ::updateFocusedScheduleIndex,
                requestItemFocus = ::requestScheduleItemFocus,
                protectedTopPx = focusedGridTopInsetPx,
                protectedBottomPx = focusedGridBottomInsetPx,
                focusScope = focusScope,
                focusRequestJob = focusRequestJob,
            )

            fun requestScheduleCalendarFocus(): Boolean {
                if (!showCalendarInGrid) {
                    return onExitUp()
                }
                suppressCalendarFocusAfterBackToTop = false
                focusController.cancelPendingRequest()
                focusRequestJob.job = focusScope.launch {
                    gridState.scrollToItem(0, 0)
                    withFrameNanos { }
                    internalCalendarFocusRequestNonce += 1L
                }
                return true
            }

            fun requestScheduleContentFocus(): Boolean {
                suppressCalendarFocusAfterBackToTop = false
                if (visibleItems.isEmpty()) return false
                return focusController.moveFocusTo(0)
            }

            fun handleScheduleGridDirection(index: Int, key: Key): Boolean {
                if (columnsCount <= 0 || index !in visibleItems.indices) return false
                val direction = when (key) {
                    Key.DirectionLeft -> VisualGridDirection.Left
                    Key.DirectionRight -> VisualGridDirection.Right
                    Key.DirectionUp -> VisualGridDirection.Up
                    Key.DirectionDown -> VisualGridDirection.Down
                    else -> return false
                }
                val sourceIndex = index.takeIf { it in visibleItems.indices }
                    ?: currentFocusedIndex().takeIf { it in visibleItems.indices }
                    ?: return false
                val target = visualGridMoveTarget(
                    index = sourceIndex,
                    total = visibleItems.size,
                    columns = columnsCount,
                    direction = direction,
                )
                if (target != null) {
                    return focusController.moveFocusTo(target)
                }
                return when (direction) {
                    VisualGridDirection.Left,
                    VisualGridDirection.Right -> onExitHorizontalDirection(direction)
                    VisualGridDirection.Up -> requestScheduleCalendarFocus()
                    VisualGridDirection.Down -> onExitDown()
                }
            }

            fun canHandleBackToTop(): Boolean {
                return gridState.canHandleBrowseRootBackToTop(BrowseSection.Schedule)
            }

            fun handleBackToTop(withFocus: Boolean): Boolean {
                if (!canHandleBackToTop()) return false
                focusController.cancelPendingRequest()
                if (!withFocus || visibleItems.isEmpty()) {
                    focusRequestJob.job = focusScope.launch {
                        gridState.scrollToItem(0, 0)
                    }
                    return true
                }
                updateFocusedScheduleIndex(0)
                suppressCalendarFocusAfterBackToTop = true
                focusRequestJob.job = focusScope.launch {
                    try {
                        gridState.scrollToItem(0, 0)
                        withFrameNanos { }
                        focusController.focusItemAfterLayout(0)
                    } finally {
                        suppressCalendarFocusAfterBackToTop = false
                    }
                }
                return true
            }

            LaunchedEffect(dayGroupKeys) {
                if (dayGroups.isEmpty()) {
                    onSelectedEpochDayChange(Long.MIN_VALUE)
                    updateFocusedScheduleIndex(-1)
                    return@LaunchedEffect
                }
                if (dayGroups.none { group -> group.epochDay == selectedScheduleDay }) {
                    onSelectedEpochDayChange(dayGroups.todayOrClosest()?.epochDay ?: dayGroups.first().epochDay)
                    updateFocusedScheduleIndex(0)
                }
            }

            DisposableEffect(visibleItems.size, onRegisterBackToTopHandler) {
                val register = onRegisterBackToTopHandler
                if (register != null && visibleItems.isNotEmpty()) {
                    register(
                        HomeBackToTopHandler(
                            section = BrowseSection.Schedule,
                            canHandle = ::canHandleBackToTop,
                            handle = ::handleBackToTop,
                        ),
                    )
                } else {
                    register?.invoke(null)
                }
                onDispose { register?.invoke(null) }
            }

            LaunchedEffect(focusFirstRequest, visibleItems.size) {
                if (visibleItems.isEmpty()) return@LaunchedEffect
                val shouldHandlePersistent = focusFirstRequest.persistentNonce > 0L &&
                    focusFirstRequest.persistentNonce != handledPersistentFocusResetNonce
                val shouldHandleTransient = focusFirstRequest.transientNonce > 0L &&
                    focusFirstRequest.transientNonce != handledTransientFocusResetNonce
                if (!shouldHandlePersistent && !shouldHandleTransient) {
                    return@LaunchedEffect
                }
                focusController.cancelPendingRequest()
                gridState.scrollToItem(0, 0)
                updateFocusedScheduleIndex(0)
                focusController.focusItemWhenVisible(0)
                if (shouldHandlePersistent) {
                    handledPersistentFocusResetNonce = focusFirstRequest.persistentNonce
                }
                if (shouldHandleTransient) {
                    handledTransientFocusResetNonce = focusFirstRequest.transientNonce
                }
            }

            LaunchedEffect(visibleItems.size) {
                updateFocusedScheduleIndex(
                    when {
                    visibleItems.isEmpty() -> -1
                    currentFocusedIndex() < 0 -> 0
                    currentFocusedIndex() !in visibleItems.indices -> visibleItems.lastIndex
                    else -> currentFocusedIndex()
                    },
                )
            }

            LaunchedEffect(focusCurrentRequestNonce, visibleItems.size) {
                if (
                    !contentFocusEnabled ||
                    focusCurrentRequestNonce <= 0L ||
                    focusCurrentRequestNonce == handledCurrentFocusRequestNonce ||
                    visibleItems.isEmpty()
                ) {
                    return@LaunchedEffect
                }
                withFrameNanos { }
                val focusedGridIndex = currentFocusedIndex()
                    .takeIf { index -> index in visibleItems.indices }
                val targetGridIndex = focusedGridIndex
                    ?: (gridState.firstVisibleItemIndex - leadingGridItemCount).coerceIn(0, visibleItems.lastIndex)
                val targetIndex = targetGridIndex.coerceIn(0, visibleItems.lastIndex)
                updateFocusedScheduleIndex(targetIndex)
                focusController.focusItemWhenVisible(targetIndex)
                handledCurrentFocusRequestNonce = focusCurrentRequestNonce
            }

            if (state.data.isEmpty()) {
                EmptyPane(message = uiText(UiStringKey.ScheduleIsEmpty), modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (dayGroups.isEmpty() || visibleItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = pinnedTopPadding),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = uiText(UiStringKey.NoUpcomingReleasesYet),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides scheduleGridBringIntoViewSpec) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columnsCount),
                                state = gridState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .focusGroup(),
                                contentPadding = PaddingValues(
                                    start = 24.dp,
                                    top = scheduleGridTopContentPadding,
                                    end = 24.dp,
                                    bottom = scheduleGridBottomContentPadding,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(18.dp),
                                verticalArrangement = Arrangement.spacedBy(22.dp),
                            ) {
                                if (showCalendarInGrid) {
                                    item(
                                        key = "schedule-calendar",
                                        span = { GridItemSpan(maxLineSpan) },
                                        contentType = "schedule-calendar",
                                    ) {
                                        ScheduleCalendarBlock(
                                            dayGroups = dayGroups,
                                            selectedEpochDay = selectedGroup?.epochDay ?: Long.MIN_VALUE,
                                            locale = locale,
                                            focusRequestNonce = calendarFocusRequestNonce * 1_000_000L +
                                                internalCalendarFocusRequestNonce,
                                            focusEnabled = contentFocusEnabled && !suppressCalendarFocusAfterBackToTop,
                                            onExitUp = onExitUp,
                                            onExitDown = ::requestScheduleContentFocus,
                                            onSelectDay = { epochDay ->
                                                onSelectedEpochDayChange(epochDay)
                                                updateFocusedScheduleIndex(0)
                                                focusController.cancelPendingRequest()
                                                focusRequestJob.job = focusScope.launch {
                                                    gridState.scrollToItem(0, 0)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }

                                itemsIndexed(
                                    items = visibleItems,
                                    key = { _, item -> item.anime.id },
                                    contentType = { _, _ -> "schedule-card" },
                                ) { index, item ->
                                    ScheduleRow(
                                        item = item,
                                        timeFormatter = scheduleTimeFormatter,
                                        onOpenAnime = onOpenAnime,
                                        modifier = Modifier
                                            .focusProperties { canFocus = contentFocusEnabled }
                                            .focusRequester(itemFocusRequesters[index])
                                            .onPreviewKeyEvent { event ->
                                                event.type == KeyEventType.KeyDown &&
                                                    handleScheduleGridDirection(index, event.key)
                                            }
                                            .onFocusChanged { focusState ->
                                                if (focusState.hasFocus) {
                                                    updateFocusedScheduleIndex(index)
                                                }
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ScheduleCalendarBlock(
    dayGroups: List<ScheduleDayGroup>,
    selectedEpochDay: Long,
    locale: Locale,
    focusRequestNonce: Long = 0L,
    focusEnabled: Boolean = true,
    onExitUp: () -> Boolean,
    onExitDown: () -> Boolean,
    onSelectDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val calendarListState = rememberLazyListState()
    val calendarScope = rememberCoroutineScope()
    val dayKeys = remember(dayGroups) { dayGroups.map { it.epochDay } }
    val dayFocusRequesters = remember(dayKeys) { List(dayKeys.size) { FocusRequester() } }
    val calendarSnapFlingBehavior = rememberSnapFlingBehavior(calendarListState, SnapPosition.Start)
    var navigationEpochDay by remember(dayKeys) { mutableLongStateOf(selectedEpochDay) }
    var handledFocusRequestNonce by remember { mutableLongStateOf(0L) }
    LaunchedEffect(selectedEpochDay) {
        if (selectedEpochDay != Long.MIN_VALUE && navigationEpochDay != selectedEpochDay) {
            navigationEpochDay = selectedEpochDay
        }
    }
    val calendarPagerBoundary = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                return if (available.x != 0f) {
                    Offset(x = available.x, y = 0f)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return if (available.x != 0f) {
                    Velocity(x = available.x, y = 0f)
                } else {
                    Velocity.Zero
                }
            }
        }
    }
    fun selectedDayIndex(): Int {
        return dayGroups.indexOfFirst { group -> group.epochDay == navigationEpochDay }
            .takeIf { index -> index >= 0 }
            ?: dayGroups.indexOfFirst { group -> group.epochDay == selectedEpochDay }
                .takeIf { index -> index >= 0 }
            ?: 0
    }

    suspend fun scrollCalendarToRevealIndex(targetIndex: Int) {
        val layoutInfo = calendarListState.layoutInfo
        val targetFirstIndex = scheduleCalendarEdgeScrollFirstVisibleIndex(
            visibleItems = layoutInfo.visibleItemsInfo.map { item ->
                VisibleScheduleCalendarItem(
                    index = item.index,
                    offsetPx = item.offset,
                    sizePx = item.size,
                )
            },
            viewportStartPx = 0,
            viewportEndPx = layoutInfo.viewportSize.width,
            targetIndex = targetIndex,
        )
        if (targetFirstIndex != null) {
            calendarListState.scrollToItem(targetFirstIndex, 0)
        }
    }

    fun selectDayAt(targetIndex: Int, moveFocus: Boolean): Boolean {
        if (dayGroups.isEmpty()) return true
        val boundedIndex = targetIndex.coerceIn(dayGroups.indices)
        val targetDay = dayGroups[boundedIndex].epochDay
        if (navigationEpochDay != targetDay) {
            navigationEpochDay = targetDay
        }
        if (targetDay != selectedEpochDay) {
            onSelectDay(targetDay)
        }
        calendarScope.launch {
            scrollCalendarToRevealIndex(boundedIndex)
            if (moveFocus) {
                withFrameNanos { }
                runCatching { dayFocusRequesters[boundedIndex].requestFocus() }
            }
        }
        return true
    }

    fun moveSelectedDay(delta: Int): Boolean {
        return selectDayAt(selectedDayIndex() + delta, moveFocus = true)
    }

    LaunchedEffect(focusRequestNonce, dayKeys) {
        if (
            !focusEnabled ||
            focusRequestNonce <= 0L ||
            focusRequestNonce == handledFocusRequestNonce ||
            dayGroups.isEmpty()
        ) {
            return@LaunchedEffect
        }
        val targetIndex = selectedDayIndex().coerceIn(dayGroups.indices)
        calendarListState.scrollToItem(targetIndex, 0)
        withFrameNanos { }
        runCatching { dayFocusRequesters[targetIndex].requestFocus() }
        handledFocusRequestNonce = focusRequestNonce
    }

    LaunchedEffect(dayKeys) {
        val selectedIndex = dayGroups.indexOfFirst { group -> group.epochDay == selectedEpochDay }
        if (selectedIndex >= 0) {
            calendarListState.scrollToItem(selectedIndex, 0)
        }
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScheduleCalendarOuterHorizontalPadding)
            .nestedScroll(calendarPagerBoundary),
        color = Color.Transparent,
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Panel),
    ) {
        Column {
            if (dayGroups.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ScheduleDayTileGap),
                    verticalAlignment = Alignment.Top,
                ) {
                    ScheduleCalendarMonthStrip(
                        listState = calendarListState,
                        dayGroups = dayGroups,
                        fallbackIndex = selectedDayIndex(),
                        locale = locale,
                        modifier = Modifier.focusProperties { canFocus = false },
                    )
                    CompositionLocalProvider(LocalBringIntoViewSpec provides ScheduleCalendarBringIntoViewSpec) {
                        LazyRow(
                            state = calendarListState,
                            modifier = Modifier
                                .weight(1f)
                                .focusGroup()
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    val delta = when (event.key) {
                                        Key.DirectionLeft -> -1
                                        Key.DirectionRight -> 1
                                        Key.DirectionUp -> return@onPreviewKeyEvent onExitUp()
                                        Key.DirectionDown -> return@onPreviewKeyEvent onExitDown()
                                        else -> return@onPreviewKeyEvent false
                                    }
                                    moveSelectedDay(delta)
                                },
                            contentPadding = PaddingValues(
                                start = 0.dp,
                                top = 0.dp,
                                end = ScheduleCalendarHorizontalPadding,
                                bottom = 10.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(ScheduleDayTileGap),
                            flingBehavior = calendarSnapFlingBehavior,
                        ) {
                            lazyItemsIndexed(
                                dayGroups,
                                key = { _, group -> group.epochDay },
                                contentType = { _, _ -> "schedule-calendar-day" },
                            ) { index, group ->
                                ScheduleDayTile(
                                    group = group,
                                    selected = group.epochDay == navigationEpochDay,
                                    locale = locale,
                                    focusRequester = dayFocusRequesters[index],
                                    focusEnabled = focusEnabled,
                                    onExitDown = onExitDown,
                                    onClick = { selectDayAt(index, moveFocus = false) },
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = uiText(UiStringKey.NoUpcomingReleasesYet),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

internal data class VisibleScheduleCalendarItem(
    val index: Int,
    val offsetPx: Int,
    val sizePx: Int,
)

internal fun scheduleCalendarFullyVisibleItems(
    visibleItems: List<VisibleScheduleCalendarItem>,
    viewportStartPx: Int,
    viewportEndPx: Int,
): List<VisibleScheduleCalendarItem> {
    return visibleItems
        .filter { item ->
            item.offsetPx >= viewportStartPx &&
                item.offsetPx + item.sizePx <= viewportEndPx
        }
        .sortedBy { item -> item.index }
}

internal fun scheduleCalendarEdgeScrollFirstVisibleIndex(
    visibleItems: List<VisibleScheduleCalendarItem>,
    viewportStartPx: Int,
    viewportEndPx: Int,
    targetIndex: Int,
): Int? {
    val visible = visibleItems.sortedBy { item -> item.index }
    if (visible.isEmpty()) return null

    val fullyVisible = scheduleCalendarFullyVisibleItems(
        visibleItems = visible,
        viewportStartPx = viewportStartPx,
        viewportEndPx = viewportEndPx,
    )
    val capacity = fullyVisible.size.takeIf { count -> count > 0 } ?: visible.size
    val first = fullyVisible.firstOrNull() ?: visible.first()
    val last = fullyVisible.lastOrNull() ?: visible.last()
    val target = visible.firstOrNull { item -> item.index == targetIndex }

    return when {
        target != null && target.offsetPx >= viewportStartPx && target.offsetPx + target.sizePx <= viewportEndPx -> null
        targetIndex <= first.index -> targetIndex.coerceAtLeast(0)
        targetIndex >= last.index -> (targetIndex - capacity + 1).coerceAtLeast(0)
        target != null && target.offsetPx < viewportStartPx -> targetIndex.coerceAtLeast(0)
        target != null && target.offsetPx + target.sizePx > viewportEndPx -> {
            (targetIndex - capacity + 1).coerceAtLeast(0)
        }
        else -> null
    }
}

internal data class ScheduleCalendarPinnedMonth(
    val title: String,
    val nextTitle: String?,
    val currentOffsetFraction: Float,
)

internal fun resolveScheduleCalendarPinnedMonth(
    dayGroups: List<ScheduleDayGroup>,
    visibleItems: List<VisibleScheduleCalendarItem>,
    fallbackIndex: Int,
    locale: Locale,
    chipWidthPx: Float,
    chipGapPx: Float,
): ScheduleCalendarPinnedMonth? {
    if (dayGroups.isEmpty()) return null
    val visibleDays = visibleItems
        .filter { item -> item.index in dayGroups.indices && item.offsetPx + item.sizePx > 0 }
        .sortedBy { item -> item.offsetPx }
    val currentIndex = visibleDays.firstOrNull()?.index
        ?: fallbackIndex.coerceIn(dayGroups.indices)
    val currentGroup = dayGroups[currentIndex]
    val nextMonthDay = visibleDays.firstOrNull { item ->
        val group = dayGroups[item.index]
        !group.sameScheduleMonth(currentGroup) && item.offsetPx >= 0
    }
    val pushDistancePx = chipWidthPx + chipGapPx
    val currentOffsetFraction = if (pushDistancePx > 0f) {
        nextMonthDay
            ?.let { item -> ((item.offsetPx - pushDistancePx) / pushDistancePx).coerceIn(-1f, 0f) }
            ?: 0f
    } else {
        0f
    }
    return ScheduleCalendarPinnedMonth(
        title = currentGroup.scheduleMonthTitle(locale),
        nextTitle = nextMonthDay?.let { item -> dayGroups[item.index].scheduleMonthTitle(locale) },
        currentOffsetFraction = currentOffsetFraction,
    )
}

@Composable
private fun ScheduleCalendarMonthStrip(
    listState: LazyListState,
    dayGroups: List<ScheduleDayGroup>,
    fallbackIndex: Int,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val chipWidthPx = with(density) { ScheduleMonthInlineLabelWidth.toPx() }
    val chipGapPx = with(density) { ScheduleDayTileGap.toPx() }
    val pinnedMonth by remember(dayGroups, fallbackIndex, locale, chipWidthPx, chipGapPx) {
        derivedStateOf {
            resolveScheduleCalendarPinnedMonth(
                dayGroups = dayGroups,
                visibleItems = listState.layoutInfo.visibleItemsInfo.map { item ->
                    VisibleScheduleCalendarItem(
                        index = item.index,
                        offsetPx = item.offset,
                        sizePx = item.size,
                    )
                },
                fallbackIndex = fallbackIndex,
                locale = locale,
                chipWidthPx = chipWidthPx,
                chipGapPx = chipGapPx,
            )
        }
    }
    val resolvedPinnedMonth = pinnedMonth ?: return
    Box(
        modifier = modifier
            .width(ScheduleMonthInlineLabelWidth)
            .height(ScheduleDayTileHeight)
            .clipToBounds(),
    ) {
        ScheduleMonthInlineChip(
            title = resolvedPinnedMonth.title,
            modifier = Modifier.offset(
                x = ScheduleMonthInlineLabelWidth * resolvedPinnedMonth.currentOffsetFraction,
            ),
        )
        resolvedPinnedMonth.nextTitle?.let { nextTitle ->
            ScheduleMonthInlineChip(
                title = nextTitle,
                modifier = Modifier.offset(
                    x = ScheduleMonthInlineLabelWidth * (1f + resolvedPinnedMonth.currentOffsetFraction),
                ),
            )
        }
    }
}

private fun ScheduleDayGroup.sameScheduleMonth(other: ScheduleDayGroup): Boolean {
    return date.year == other.date.year && date.monthValue == other.date.monthValue
}

@Composable
private fun ScheduleMonthInlineChip(
    title: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(ScheduleMonthInlineLabelWidth)
            .height(ScheduleDayTileHeight),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth(0.72f)
                    .height(ScheduleMonthInlineLabelAccentHeight)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = YummyRadii.pillShape,
                    ),
            )
        }
    }
}

@Composable
private fun ScheduleDayTile(
    group: ScheduleDayGroup,
    selected: Boolean,
    locale: Locale,
    focusRequester: FocusRequester,
    focusEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    onExitDown: () -> Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    var focused by remember { mutableStateOf(false) }
    val dayOfWeek = remember(group.date, locale) {
        group.date.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
            .replace(".", "")
            .replaceFirstChar { char -> char.uppercase(locale) }
    }
    val isWeekend = remember(group.date) { group.date.dayOfWeek.value >= 6 }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(ScheduleDayTileWidth),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(ScheduleDayTileHeight)
                .focusProperties { canFocus = focusEnabled }
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionDown &&
                        onExitDown()
                }
                .onFocusChanged { focusState ->
                    focused = focusState.isFocused || focusState.hasFocus
                }
                .dpadClickable(shape, onClick)
                .animatedFocusBorder(active = focused),
            color = if (selected || focused) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = shape,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = dayOfWeek,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Black,
                        color = if (isWeekend) Color(0xFFFF626B) else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = group.date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp),
                    shape = YummyRadii.pillShape,
                    color = Color(0xFF3CCE7B),
                    contentColor = Color.White,
                ) {
                    Text(
                        text = group.items.size.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ScheduleRow(
    item: ScheduleAnime,
    timeFormatter: DateTimeFormatter,
    onOpenAnime: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val episodeIsAlreadyOutText = uiText(UiStringKey.EpisodeIsAlreadyOut)
    val metaText = remember(item.airedEpisodes, episodeIsAlreadyOutText) {
        "${item.airedEpisodes} $episodeIsAlreadyOutText"
    }
    val scheduleTime = remember(item.nextEpisodeAtSeconds, item.previousEpisodeAtSeconds, timeFormatter) {
        item.formatScheduleTime(timeFormatter)
    }
    AnimeCard(
        anime = item.anime,
        onClick = { onOpenAnime(item.anime.id) },
        metaText = metaText,
        topEndContent = {
            ScheduleTimeBadge(time = scheduleTime)
        },
        modifier = modifier,
    )
}

@Composable
private fun ScheduleTimeBadge(time: String) {
    Surface(
        shape = YummyRadii.smallShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.94f),
        contentColor = Color(0xFF211200),
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

private val ScheduleDayTileWidth = 96.dp
private val ScheduleDayTileHeight = 78.dp
private val ScheduleDayTileGap = 10.dp
private val ScheduleCalendarOuterHorizontalPadding = 0.dp
private val ScheduleCalendarHorizontalPadding = 0.dp
private val ScheduleMonthInlineLabelWidth = 92.dp
private val ScheduleMonthInlineLabelAccentHeight = 2.dp
private val ScheduleCalendarTopGap = 0.dp
private val ScheduleFocusedCardTopGap = 18.dp
@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
private val ScheduleCalendarBringIntoViewSpec = object : BringIntoViewSpec {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(
        durationMillis = 420,
        easing = FastOutSlowInEasing,
    )

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}
private fun ScheduleDayGroup.scheduleMonthTitle(locale: Locale): String {
    return date.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).uppercase(locale)
}

internal data class ScheduleDayGroup(
    val date: LocalDate,
    val epochDay: Long,
    val items: List<ScheduleAnime>,
)

private data class ScheduleTimedItem(
    val item: ScheduleAnime,
    val timestampSeconds: Long,
)

private fun List<ScheduleAnime>.toScheduleDayGroups(zoneId: ZoneId): List<ScheduleDayGroup> {
    return asSequence()
        .mapNotNull { item ->
            item.scheduleDisplayTimestampSeconds()?.let { timestamp ->
                ScheduleTimedItem(item = item, timestampSeconds = timestamp)
            }
        }
        .groupBy { timedItem ->
            Instant.ofEpochSecond(timedItem.timestampSeconds).atZone(zoneId).toLocalDate()
        }
        .map { (date, items) ->
            ScheduleDayGroup(
                date = date,
                epochDay = date.toEpochDay(),
                items = items
                    .sortedWith(compareBy<ScheduleTimedItem> { it.timestampSeconds }.thenBy { it.item.anime.title })
                    .map { it.item },
            )
        }
        .sortedBy { it.epochDay }
}

private fun List<ScheduleDayGroup>.todayOrClosest(): ScheduleDayGroup? {
    if (isEmpty()) return null
    val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
    return firstOrNull { group -> group.epochDay == today }
        ?: firstOrNull { group -> group.epochDay > today }
        ?: last()
}

private fun ScheduleAnime.scheduleDisplayTimestampSeconds(): Long? {
    return when {
        nextEpisodeAtSeconds > 0L -> nextEpisodeAtSeconds
        previousEpisodeAtSeconds > 0L -> previousEpisodeAtSeconds
        else -> null
    }
}

private fun ScheduleAnime.formatScheduleTime(formatter: DateTimeFormatter): String {
    val timestamp = scheduleDisplayTimestampSeconds() ?: return "--:--"
    return Instant.ofEpochSecond(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
