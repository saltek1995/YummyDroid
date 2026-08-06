package me.yummydroid.app.ui
import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
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
import me.yummydroid.app.ui.components.clearFocusAfterTouch
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor
import me.yummydroid.app.ui.theme.YummyColors
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
    val tvTopChromePinned = isWide && !forcedOffline
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
    val browsePagerIsAwayFromTarget = useBrowsePager &&
        (browsePagerState.currentPage != browsePagerPage ||
            abs(browsePagerState.currentPageOffsetFraction) > 0.001f)
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
    var browseProgrammaticTabTargetPosition by remember { mutableStateOf<Float?>(null) }
    val browseProgrammaticTabPosition by animateFloatAsState(
        targetValue = browseProgrammaticTabTargetPosition ?: browsePagerPage.toFloat(),
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "browseProgrammaticTabPosition",
    )
    val browseTabPosition = if (!active) {
        browsePagerPage.toFloat()
    } else if (useBrowsePager && browseProgrammaticTabTargetPosition != null) {
        browseProgrammaticTabPosition
    } else if (
        useBrowsePager &&
        (
            browsePagerState.isScrollInProgress ||
                browsePagerProgrammaticScrollTarget != null ||
                browsePagerTransitionFocusSourcePage != null
            )
    ) {
        browsePagerPosition
    } else if (
        useBrowsePager &&
        effectiveHomeSection in browsePagerSections &&
        browsePagerState.isScrollInProgress
    ) {
        browsePagerPosition
    } else if (effectiveHomeSection in browsePagerSections || browsePagerProgrammaticScrollTarget != null) {
        browsePagerPage.toFloat()
    } else {
        null
    }
    val scheduleBrowsePage = browsePagerSections.indexOf(BrowseSection.Schedule)
    val phoneScheduleCalendarVisualProgress = if (
        !isWide &&
        !forcedOffline &&
        scheduleBrowsePage >= 0 &&
        phoneScheduleDayGroups.isNotEmpty()
    ) {
        val visualPosition = browseTabPosition ?: browsePagerPage.toFloat()
        (1f - abs(visualPosition - scheduleBrowsePage)).coerceIn(0f, 1f)
    } else {
        0f
    }
    val showPhoneScheduleCalendarInBottomChromeVisual =
        showPhoneScheduleCalendarInBottomChrome || phoneScheduleCalendarVisualProgress > 0.001f
    val hasBrowseBottomChrome = !isWide || forcedOffline
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
            contentTopPadding = 0.dp,
            contentBottomPadding = browseBottomChromeBaseContentPadding,
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
                    precomputedDayGroups = if (!isWide && !forcedOffline) phoneScheduleDayGroups else null,
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
                    pinnedTopPadding = if (tvTopChromePinned) BrowseTvScheduleBlockGap else 0.dp,
                    contentBottomPadding = browseBottomChromeScheduleContentPadding,
                    onRegisterBackToTopHandler = { handler ->
                        updateHomeBackToTopHandler(BrowseSection.Schedule, handler)
                    },
                    onRetry = onRefresh,
                    onExitHorizontalDirection = { direction ->
                        handleBrowsePageHorizontalExit(pageIndex, direction)
                    },
                    onExitUp = if (isWide && !forcedOffline) {
                        { requestBrowseSectionTabsFocus(releasePagerFocusTransition = true) }
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
                    contentBottomPadding = browseBottomChromeBaseContentPadding,
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
            visible = browseTopBarDisplayVisibleState.value,
            visibilityProgressProvider = { browseTopBarVisibilityProgressState.value },
        )
    }

    @Composable
    fun BrowseTvPinnedTabsChrome(modifier: Modifier = Modifier) {
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
            drawBackdrop = false,
            backdropVisible = false,
            sectionTabsFocusEnabled = browseSectionTabsFocusEnabled,
            squareTopCorners = false,
            modifier = modifier,
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
            if (tvTopChromePinned) {
                BrowseTopBarChrome(collapseWhenHidden = false)
                BrowseTvPinnedTabsChrome()
            } else if (!isWide || forcedOffline) {
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
                sectionTabsOnExitUp = if (showPhoneScheduleCalendarInBottomChrome) {
                    ::requestScheduleCalendarFocus
                } else {
                    ::requestCurrentBrowseContentFocus
                },
                sectionTabsFocusEnabled = browseSectionTabsFocusEnabled,
                hazeState = if (browseChromeHazeActive) browseChromeHazeState else null,
                topProtectedContent = if (showPhoneScheduleCalendarInBottomChromeVisual) {
                    { calendarModifier ->
                        ScheduleCalendarBlock(
                            dayGroups = phoneScheduleDayGroups,
                            selectedEpochDay = phoneScheduleSelectedGroup?.epochDay ?: Long.MIN_VALUE,
                            locale = state.settings.contentLanguage.uiLocale(),
                            focusRequestNonce = scheduleCalendarFocusRequestNonce,
                            focusEnabled = browseDpadFocusEnabled,
                            onExitUp = ::requestCurrentBrowseContentFocus,
                            onExitDown = {
                                requestBrowseSectionTabsFocus(
                                    section = BrowseSection.Schedule,
                                    releasePagerFocusTransition = true,
                                )
                            },
                            onSelectDay = { epochDay ->
                                scheduleSelectedEpochDay = epochDay
                                updateStoredBrowseFocus(BrowseSection.Schedule, 0)
                                browseFocusScope.launch {
                                    scheduleGridState.animateScrollToItem(0, 0)
                                }
                            },
                            modifier = calendarModifier,
                        )
                    }
                } else {
                    null
                },
                topProtectedVisibilityProgress = phoneScheduleCalendarVisualProgress,
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

private val BrowseTvScheduleBlockGap = 10.dp
private val BrowseTopBarScrollCollapseDistance = 180.dp
private val BrowseGridTopContentPadding = 12.dp
private val BrowseFocusedCardBottomGap = 20.dp
private val BrowseBottomChromeFallbackProtectedHeight = 96.dp
private const val BrowseTouchBounceOverscrollResistance = 0.48f

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
        val touchOverscrollEnabled = LocalInputModeManager.current.inputMode == InputMode.Touch
        val gridHorizontalPadding = browseGridHorizontalContentPadding(maxWidth)
        val focusedGridItemHeightPx = with(density) {
            browseGridItemHeight(
                maxWidth = maxWidth,
                columns = columnsCount,
                horizontalPadding = gridHorizontalPadding,
            ).toPx()
        }
        val focusedGridTopInset = browseGridFocusedCardTopInset(contentTopPadding, maxWidth)
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
            return requester.requestFocusSafely()
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
            focusedItemHeightPx = focusedGridItemHeightPx,
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
            val sourceIndex = currentFocusedAnimeIndex().takeIf { it in animes.indices }
                ?: index.takeIf { it in animes.indices }
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
                gridState.animateScrollToItem(0, 0)
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
            focusController.cancelPendingRequest()
            updateFocusedAnimeIndex(targetIndex)
            focusController.focusItemWhenVisible(targetIndex)
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

        val baseGridBottomContentPadding = if (contentBottomPadding > 0.dp) {
            focusedGridBottomInset
        } else {
            24.dp + BrowseFocusedCardBottomGap
        }
        val gridBottomContentPadding = browseGridFocusedCardBottomPadding(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            columns = columnsCount,
            horizontalPadding = gridHorizontalPadding,
            topInset = focusedGridTopInset,
            bottomInset = focusedGridBottomInset,
            basePadding = baseGridBottomContentPadding,
        )
        BrowseGridScrollLocalProvider(touchOverscrollEnabled = touchOverscrollEnabled) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnsCount),
                state = gridState,
                contentPadding = PaddingValues(
                    start = gridHorizontalPadding,
                    top = BrowseGridTopContentPadding + contentTopPadding,
                    end = gridHorizontalPadding,
                    bottom = gridBottomContentPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(BrowseGridHorizontalGap),
                verticalArrangement = Arrangement.spacedBy(BrowseGridVerticalGap),
                modifier = Modifier
                    .fillMaxSize()
                    .browseTouchBounceOverscroll(
                        enabled = touchOverscrollEnabled,
                        gridState = gridState,
                    )
                    .onPreviewKeyEvent { event ->
                        event.type == KeyEventType.KeyDown &&
                            contentFocusEnabled &&
                            currentFocusedAnimeIndex().let { index ->
                                index in animes.indices && handleAnimeGridDirection(index, event.key)
                            }
                    }
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
    precomputedDayGroups: List<ScheduleDayGroup>? = null,
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
            val touchOverscrollEnabled = LocalInputModeManager.current.inputMode == InputMode.Touch
            val zoneId = remember { ZoneId.systemDefault() }
            val scheduleTimeFormatter = remember(locale) {
                DateTimeFormatter.ofPattern("HH:mm", locale)
            }
            val dayGroups = remember(state.data, zoneId, precomputedDayGroups) {
                precomputedDayGroups ?: state.data.toScheduleDayGroups(zoneId)
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
            val focusedGridTopInset = browseGridFocusedCardTopInset(pinnedTopPadding, maxWidth)
            val focusedGridTopInsetPx = with(density) { focusedGridTopInset.toPx() }
            val focusedGridBottomInset = BrowseFocusedCardBottomGap + contentBottomPadding
            val focusedGridBottomInsetPx = with(density) { focusedGridBottomInset.toPx() }
            var internalCalendarFocusRequestNonce by remember(scheduleDayKey) { mutableLongStateOf(0L) }
            var handledPersistentFocusResetNonce by remember { mutableLongStateOf(0L) }
            var handledTransientFocusResetNonce by remember { mutableLongStateOf(0L) }
            var handledCurrentFocusRequestNonce by remember { mutableLongStateOf(0L) }
            var suppressCalendarFocusAfterBackToTop by remember(scheduleDayKey) { mutableStateOf(false) }
            var scheduleCalendarHasFocus by remember(scheduleDayKey) { mutableStateOf(false) }
            val focusRequestJob = remember(scheduleDayKey, columnsCount) { FocusRequestJobRef() }
            val baseScheduleGridBottomContentPadding = if (contentBottomPadding > 0.dp) {
                focusedGridBottomInset
            } else {
                24.dp + BrowseFocusedCardBottomGap
            }
            val leadingGridItemCount = if (showCalendarInGrid) 1 else 0
            val scheduleGridTopContentPadding = if (showCalendarInGrid) {
                pinnedTopPadding + ScheduleCalendarTopGap
            } else {
                pinnedTopPadding + BrowseGridTopContentPadding
            }
            val scheduleGridVerticalGap = if (showCalendarInGrid) {
                BrowseTvScheduleBlockGap
            } else {
                BrowseChromeItemGap
            }
            val scheduleGridHorizontalPadding = browseGridHorizontalContentPadding(maxWidth)
            val focusedGridItemHeightPx = with(density) {
                browseGridItemHeight(
                    maxWidth = maxWidth,
                    columns = columnsCount,
                    horizontalPadding = scheduleGridHorizontalPadding,
                ).toPx()
            }
            val scheduleGridBottomContentPadding = browseGridFocusedCardBottomPadding(
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                columns = columnsCount,
                horizontalPadding = scheduleGridHorizontalPadding,
                topInset = focusedGridTopInset,
                bottomInset = focusedGridBottomInset,
                basePadding = baseScheduleGridBottomContentPadding,
            )
            fun updateFocusedScheduleIndex(index: Int) {
                if (currentFocusedIndex() != index) {
                    onFocusedIndexChange(index)
                }
            }

            fun requestScheduleItemFocus(index: Int): Boolean {
                val requester = itemFocusRequesters.getOrNull(index) ?: return false
                return requester.requestFocusSafely()
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
                focusedItemHeightPx = focusedGridItemHeightPx,
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
                    if (gridState.firstVisibleItemIndex != 0 || gridState.firstVisibleItemScrollOffset != 0) {
                        gridState.animateScrollToItem(0, 0)
                    }
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
                val sourceIndex = currentFocusedIndex().takeIf { it in visibleItems.indices }
                    ?: index.takeIf { it in visibleItems.indices }
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
                        gridState.animateScrollToItem(0, 0)
                    }
                    return true
                }
                updateFocusedScheduleIndex(0)
                suppressCalendarFocusAfterBackToTop = true
                focusRequestJob.job = focusScope.launch {
                    try {
                        focusController.focusItemWhenVisible(0)
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
                        BrowseGridScrollLocalProvider(touchOverscrollEnabled = touchOverscrollEnabled) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columnsCount),
                                state = gridState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .browseTouchBounceOverscroll(
                                        enabled = touchOverscrollEnabled,
                                        gridState = gridState,
                                    )
                                    .onPreviewKeyEvent { event ->
                                        event.type == KeyEventType.KeyDown &&
                                            !scheduleCalendarHasFocus &&
                                            contentFocusEnabled &&
                                            currentFocusedIndex().let { index ->
                                                index in visibleItems.indices && handleScheduleGridDirection(index, event.key)
                                            }
                                    }
                                    .focusGroup(),
                                contentPadding = PaddingValues(
                                    start = scheduleGridHorizontalPadding,
                                    top = scheduleGridTopContentPadding,
                                    end = scheduleGridHorizontalPadding,
                                    bottom = scheduleGridBottomContentPadding,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(BrowseGridHorizontalGap),
                                verticalArrangement = Arrangement.spacedBy(scheduleGridVerticalGap),
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
                                            onCalendarFocusChanged = { hasFocus ->
                                                scheduleCalendarHasFocus = hasFocus
                                            },
                                            onExitUp = onExitUp,
                                            onExitDown = ::requestScheduleContentFocus,
                                            onSelectDay = { epochDay ->
                                                onSelectedEpochDayChange(epochDay)
                                                updateFocusedScheduleIndex(0)
                                                focusController.cancelPendingRequest()
                                                focusRequestJob.job = focusScope.launch {
                                                    gridState.animateScrollToItem(0, 0)
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
    onCalendarFocusChanged: (Boolean) -> Unit = {},
    onExitUp: () -> Boolean,
    onExitDown: () -> Boolean,
    onSelectDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val calendarListState = rememberLazyListState()
    val calendarScope = rememberCoroutineScope()
    val calendarIsWide = LocalConfiguration.current.screenWidthDp >= 720
    val calendarItemGap = if (calendarIsWide) {
        ScheduleDayTileWideGap
    } else {
        ScheduleDayTilePhoneGap
    }
    val calendarBottomPadding = if (calendarIsWide) {
        ScheduleCalendarWideBottomPadding
    } else {
        ScheduleCalendarPhoneBottomPadding
    }
    val dayKeys = remember(dayGroups) { dayGroups.map { it.epochDay } }
    val dayFocusRequesters = remember(dayKeys) { List(dayKeys.size) { FocusRequester() } }
    val density = LocalDensity.current
    val monthSlotWidth = ScheduleMonthInlineLabelWidth + calendarItemGap
    val monthSlotWidthPx = remember(density, calendarItemGap) {
        with(density) {
            monthSlotWidth.toPx()
        }
    }
    val dayTileWidthPx = remember(density) {
        with(density) { ScheduleDayTileWidth.toPx() }
    }
    val calendarEntries = remember(dayGroups, locale) {
        scheduleCalendarEntries(dayGroups, locale)
    }
    val dayCalendarEntryIndices = remember(dayGroups, calendarEntries) {
        IntArray(dayGroups.size) { -1 }.also { indices ->
            calendarEntries.forEachIndexed { entryIndex, entry ->
                if (entry.dayIndex in indices.indices) {
                    indices[entry.dayIndex] = entryIndex
                }
            }
        }
    }
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

    val calendarMonthOverlay by remember(
        calendarListState,
        calendarEntries,
        dayGroups,
        monthSlotWidthPx,
        dayTileWidthPx,
    ) {
        derivedStateOf {
            resolveScheduleCalendarMonthOverlay(
                dayGroups = dayGroups,
                entries = calendarEntries,
                visibleItems = calendarListState.layoutInfo.visibleItemsInfo.map { item ->
                    VisibleScheduleCalendarItem(
                        index = item.index,
                        offsetPx = item.offset,
                        sizePx = dayTileWidthPx.roundToInt(),
                    )
                },
                fallbackDayIndex = selectedDayIndex(),
                monthSlotWidthPx = monthSlotWidthPx,
                viewportEndPx = calendarListState.layoutInfo.viewportSize.width,
            )
        }
    }
    val calendarContentClipStartPx = remember(calendarMonthOverlay, monthSlotWidthPx) {
        calendarMonthOverlay
            ?.chips
            ?.maxOfOrNull { chip ->
                (chip.offsetPx + monthSlotWidthPx).coerceIn(0f, monthSlotWidthPx)
            }
            ?: 0f
    }

    fun calendarEntryIndexForDay(dayIndex: Int): Int {
        return dayCalendarEntryIndices
            .getOrNull(dayIndex)
            ?.takeIf { index -> index >= 0 }
            ?: dayIndex
    }

    suspend fun scrollCalendarToDayStart(dayIndex: Int) {
        val entryIndex = calendarEntryIndexForDay(dayIndex)
        val entry = calendarEntries.getOrNull(entryIndex)
        val scrollOffset = if (entry?.startsMonth == true) {
            0
        } else {
            -monthSlotWidthPx.roundToInt()
        }
        calendarListState.scrollToItem(entryIndex, scrollOffset)
    }

    suspend fun scrollCalendarToRevealIndex(targetIndex: Int) {
        val layoutInfo = calendarListState.layoutInfo
        val targetFirstIndex = scheduleCalendarEdgeScrollFirstVisibleIndex(
            visibleItems = layoutInfo.visibleItemsInfo.mapNotNull { item ->
                val entry = calendarEntries.getOrNull(item.index) ?: return@mapNotNull null
                val itemOffsetPx = if (entry.startsMonth) {
                    item.offset + monthSlotWidthPx.roundToInt()
                } else {
                    item.offset
                }
                VisibleScheduleCalendarItem(
                    index = entry.dayIndex,
                    offsetPx = itemOffsetPx,
                    sizePx = dayTileWidthPx.roundToInt(),
                )
            },
            viewportStartPx = monthSlotWidthPx.roundToInt(),
            viewportEndPx = layoutInfo.viewportSize.width,
            targetIndex = targetIndex,
        )
        if (targetFirstIndex != null) {
            scrollCalendarToDayStart(targetFirstIndex)
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
                dayFocusRequesters[boundedIndex].requestFocusSafely()
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
        scrollCalendarToDayStart(targetIndex)
        withFrameNanos { }
        dayFocusRequesters[targetIndex].requestFocusSafely()
        handledFocusRequestNonce = focusRequestNonce
    }

    LaunchedEffect(dayKeys) {
        val selectedIndex = dayGroups.indexOfFirst { group -> group.epochDay == selectedEpochDay }
        if (selectedIndex >= 0) {
            scrollCalendarToDayStart(selectedIndex)
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
                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ScheduleCalendarMonthStrip(
                        monthOverlay = calendarMonthOverlay,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .zIndex(1f)
                            .focusProperties { canFocus = false },
                    )
                    CompositionLocalProvider(LocalBringIntoViewSpec provides ScheduleCalendarBringIntoViewSpec) {
                        LazyRow(
                            state = calendarListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .scheduleCalendarStickyMonthMask(calendarContentClipStartPx)
                                .onFocusChanged { focusState ->
                                    onCalendarFocusChanged(focusState.hasFocus)
                                }
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
                                bottom = calendarBottomPadding,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(calendarItemGap),
                        ) {
                            calendarEntries.forEach { entry ->
                                item(
                                    key = entry.key,
                                    contentType = entry.type,
                                ) {
                                    when (entry.type) {
                                        ScheduleCalendarEntryType.MonthDay -> {
                                            val index = entry.dayIndex
                                            val group = dayGroups.getOrNull(index) ?: return@item
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(calendarItemGap),
                                                verticalAlignment = Alignment.Top,
                                            ) {
                                                ScheduleMonthInlineChip(
                                                    title = entry.title,
                                                    modifier = Modifier.focusProperties { canFocus = false },
                                                )
                                                ScheduleDayTile(
                                                    group = group,
                                                    selected = group.epochDay == navigationEpochDay,
                                                    locale = locale,
                                                    focusRequester = dayFocusRequesters[index],
                                                    focusEnabled = focusEnabled,
                                                    onFocusedChanged = onCalendarFocusChanged,
                                                    onExitUp = onExitUp,
                                                    onExitDown = onExitDown,
                                                    onMovePrevious = { moveSelectedDay(-1) },
                                                    onMoveNext = { moveSelectedDay(1) },
                                                    onClick = { selectDayAt(index, moveFocus = false) },
                                                )
                                            }
                                        }

                                        ScheduleCalendarEntryType.Day -> {
                                            val index = entry.dayIndex
                                            val group = dayGroups.getOrNull(index) ?: return@item
                                            ScheduleDayTile(
                                                group = group,
                                                selected = group.epochDay == navigationEpochDay,
                                                locale = locale,
                                                focusRequester = dayFocusRequesters[index],
                                                focusEnabled = focusEnabled,
                                                onFocusedChanged = onCalendarFocusChanged,
                                                onExitUp = onExitUp,
                                                onExitDown = onExitDown,
                                                onMovePrevious = { moveSelectedDay(-1) },
                                                onMoveNext = { moveSelectedDay(1) },
                                                onClick = { selectDayAt(index, moveFocus = false) },
                                            )
                                        }

                                    }
                                }
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

private fun Modifier.scheduleCalendarStickyMonthMask(maskStartPx: Float): Modifier {
    if (maskStartPx <= 0.5f) return this
    return drawWithContent {
        val left = maskStartPx.coerceIn(0f, size.width)
        if (left >= size.width) return@drawWithContent
        clipRect(left = left) {
            this@drawWithContent.drawContent()
        }
    }
}

@Composable
private fun ScheduleCalendarMonthStrip(
    monthOverlay: ScheduleCalendarMonthOverlay?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val resolvedMonthOverlay = monthOverlay ?: return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ScheduleDayTileHeight)
            .clipToBounds(),
    ) {
        resolvedMonthOverlay.chips.forEach { chip ->
            ScheduleMonthInlineChip(
                title = chip.title,
                modifier = Modifier.offset(
                    x = with(density) { chip.offsetPx.toDp() },
                ),
            )
        }
    }
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
        color = yummyActionSurfaceColor(),
        contentColor = yummyActionContentColor(),
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
    onFocusedChanged: (Boolean) -> Unit = {},
    onExitUp: () -> Boolean,
    onExitDown: () -> Boolean,
    onMovePrevious: () -> Boolean,
    onMoveNext: () -> Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    var focused by remember { mutableStateOf(false) }
    val inputModeManager = LocalInputModeManager.current
    val focusVisible = focused && inputModeManager.inputMode != InputMode.Touch
    val dayContentColor = yummyActionContentColor(selected = selected, focused = focusVisible)
    val interactionSource = remember { MutableInteractionSource() }
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
                .onFocusChanged { focusState ->
                    val hasFocus = focusState.isFocused || focusState.hasFocus
                    focused = hasFocus
                    onFocusedChanged(hasFocus)
                }
                .clearFocusAfterTouch()
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> onMovePrevious()
                        Key.DirectionRight -> onMoveNext()
                        Key.DirectionUp -> onExitUp()
                        Key.DirectionDown -> onExitDown()
                        else -> false
                    }
                },
            color = yummyActionSurfaceColor(selected = selected, focused = focusVisible),
            contentColor = dayContentColor,
            border = yummyActionBorder(selected = selected, focused = focusVisible),
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
                        color = if (focusVisible) dayContentColor else if (isWeekend) Color(0xFFFF626B) else dayContentColor,
                    )
                    Text(
                        text = group.date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = dayContentColor,
                    )
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp),
                    shape = YummyRadii.pillShape,
                    color = YummyColors.offline,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
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

private val BrowseGridHorizontalGap = 18.dp
private val BrowseGridVerticalGap = 22.dp
private val ScheduleDayTileWidth = 96.dp
private val ScheduleDayTileHeight = 78.dp
private val ScheduleDayTilePhoneGap = BrowseChromeItemGap
private val ScheduleDayTileWideGap = BrowseChromeItemGap
private val ScheduleCalendarOuterHorizontalPadding = 0.dp
private val ScheduleCalendarHorizontalPadding = 0.dp
private val ScheduleMonthInlineLabelWidth = ScheduleDayTileWidth
private val ScheduleMonthInlineLabelAccentHeight = 2.dp
private val ScheduleCalendarPhoneBottomPadding = 0.dp
private val ScheduleCalendarWideBottomPadding = 0.dp
private val ScheduleCalendarTopGap = 0.dp

private fun browseGridHorizontalContentPadding(maxWidth: Dp): Dp {
    return if (maxWidth >= 720.dp) {
        BrowseChromeWideHorizontalPadding
    } else {
        BrowseChromePhoneHorizontalPadding
    }
}

private fun browseGridItemHeight(
    maxWidth: Dp,
    columns: Int,
    horizontalPadding: Dp,
): Dp {
    if (columns <= 0 || maxWidth <= 0.dp) return 0.dp
    val horizontalGaps = BrowseGridHorizontalGap * (columns - 1).coerceAtLeast(0).toFloat()
    val itemWidth = ((maxWidth - horizontalPadding * 2f - horizontalGaps) / columns.toFloat())
        .coerceAtLeast(0.dp)
    return itemWidth / AnimeCardPosterAspectRatio
}

private fun browseGridFocusedCardTopInset(
    contentTopPadding: Dp,
    maxWidth: Dp,
): Dp {
    if (contentTopPadding <= 0.dp) return 0.dp
    return if (maxWidth >= 720.dp) {
        BrowseTvSectionIndicatorHeight + BrowseFocusedCardBottomGap
    } else {
        contentTopPadding
    }
}

private fun browseGridFocusedCardBottomPadding(
    maxWidth: Dp,
    maxHeight: Dp,
    columns: Int,
    horizontalPadding: Dp,
    topInset: Dp,
    bottomInset: Dp,
    basePadding: Dp,
): Dp {
    if (columns <= 0 || maxWidth <= 0.dp || maxHeight <= 0.dp) return basePadding
    val itemHeight = browseGridItemHeight(
        maxWidth = maxWidth,
        columns = columns,
        horizontalPadding = horizontalPadding,
    )
    val safeHeight = (maxHeight - topInset - bottomInset).coerceAtLeast(0.dp)
    if (itemHeight <= 0.dp || safeHeight <= 0.dp) return basePadding

    val targetCenter = topInset + safeHeight / 2f
    val requiredPadding = maxHeight - targetCenter - itemHeight / 2f
    return maxOf(basePadding, requiredPadding.coerceAtLeast(0.dp))
}

@Composable
private fun Modifier.browseTouchBounceOverscroll(
    enabled: Boolean,
    gridState: LazyGridState,
): Modifier {
    if (!enabled) return this

    val scope = rememberCoroutineScope()
    val offsetPx = remember { mutableFloatStateOf(0f) }
    val reboundJobRef = remember { arrayOfNulls<Job>(1) }
    val reboundSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
    }

    fun cancelRebound() {
        reboundJobRef[0]?.cancel()
        reboundJobRef[0] = null
    }

    fun startRebound() {
        val start = offsetPx.floatValue
        if (abs(start) <= 0.5f) {
            offsetPx.floatValue = 0f
            return
        }
        cancelRebound()
        reboundJobRef[0] = scope.launch {
            val animatable = Animatable(start)
            animatable.animateTo(0f, reboundSpec) {
                offsetPx.floatValue = value
            }
            offsetPx.floatValue = 0f
        }
    }

    fun consumePull(deltaY: Float): Float {
        if (deltaY == 0f) return 0f
        val current = offsetPx.floatValue
        val pullingPastTop = deltaY > 0f && !gridState.canScrollBackward
        val pullingPastBottom = deltaY < 0f && !gridState.canScrollForward
        if (!pullingPastTop && !pullingPastBottom) return 0f

        cancelRebound()
        offsetPx.floatValue = current + deltaY * BrowseTouchBounceOverscrollResistance
        return deltaY
    }

    fun consumeReturn(deltaY: Float): Float {
        val current = offsetPx.floatValue
        if (current == 0f || deltaY == 0f) return 0f
        val returnsFromTop = current > 0f && deltaY < 0f
        val returnsFromBottom = current < 0f && deltaY > 0f
        if (!returnsFromTop && !returnsFromBottom) return 0f

        cancelRebound()
        val proposed = current + deltaY
        val consumed = when {
            current > 0f && proposed < 0f -> -current
            current < 0f && proposed > 0f -> -current
            else -> deltaY
        }
        offsetPx.floatValue = current + consumed
        return consumed
    }

    val connection = remember(gridState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val consumedY = consumeReturn(available.y)
                return if (consumedY != 0f) Offset(x = 0f, y = consumedY) else Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val consumedY = consumePull(available.y)
                return if (consumedY != 0f) Offset(x = 0f, y = consumedY) else Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetPx.floatValue == 0f) return Velocity.Zero
                startRebound()
                return Velocity(x = 0f, y = available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (offsetPx.floatValue == 0f) return Velocity.Zero
                startRebound()
                return Velocity(x = 0f, y = available.y)
            }
        }
    }

    return this
        .nestedScroll(connection)
        .graphicsLayer {
            translationY = offsetPx.floatValue
        }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BrowseGridScrollLocalProvider(
    touchOverscrollEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (touchOverscrollEnabled) {
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides BrowseGridNoopBringIntoViewSpec,
            content = content,
        )
    } else {
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides BrowseGridNoopBringIntoViewSpec,
            LocalOverscrollFactory provides null,
            content = content,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
private val BrowseGridNoopBringIntoViewSpec = object : BringIntoViewSpec {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(durationMillis = 0)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

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
