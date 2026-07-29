package me.yummydroid.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.content.res.Configuration
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.canHandleRootHomeBackToTop
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.ScheduleAnime
import me.yummydroid.app.DownloadTaskState
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.readyListOrEmpty
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.YummyDroidUiState

@Composable
internal fun BrowseScreen(
    state: YummyDroidUiState,
    catalogGridState: LazyGridState,
    scheduleListState: LazyListState,
    historyGridState: LazyGridState,
    activeFocusRequestNonce: Long,
    onRegisterHomeBackToTopHandler: (BrowseSection, HomeBackToTopHandler?) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    onQueryChange: (String) -> Unit,
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
    val isWide = configuration.screenWidthDp >= 720
    var browseContentFocusRequestNonce by remember { mutableLongStateOf(0L) }
    val dpadLayerFocusRequestNonce = if (activeFocusRequestNonce > 0L) {
        activeFocusRequestNonce * 1_000_000L + browseContentFocusRequestNonce
    } else {
        0L
    }
    val browseTopBarVisible = !isWide || when (effectiveHomeSection) {
        BrowseSection.Catalog -> catalogGridState.firstVisibleItemIndex == 0 &&
            catalogGridState.firstVisibleItemScrollOffset == 0
        BrowseSection.Schedule -> scheduleListState.firstVisibleItemIndex == 0 &&
            scheduleListState.firstVisibleItemScrollOffset == 0
        BrowseSection.History -> historyGridState.firstVisibleItemIndex == 0 &&
            historyGridState.firstVisibleItemScrollOffset == 0
        BrowseSection.Downloads -> true
    }
    var searchDialogOpen by remember { mutableStateOf(false) }
    var filtersDialogOpen by remember { mutableStateOf(false) }
    var activeHomeBackToTopHandler by remember { mutableStateOf<HomeBackToTopHandler?>(null) }
    val latestOnRegisterHomeBackToTopHandler by rememberUpdatedState(onRegisterHomeBackToTopHandler)

    LaunchedEffect(catalogActionsEnabled) {
        if (!catalogActionsEnabled) {
            filtersDialogOpen = false
            searchDialogOpen = false
        }
    }

    fun requestCurrentBrowseContentFocus() {
        browseContentFocusRequestNonce += 1L
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
        if (action != InputAction.Back) {
            false
        } else {
            when {
                filtersDialogOpen -> {
                    filtersDialogOpen = false
                    true
                }
                searchDialogOpen -> {
                    searchDialogOpen = false
                    true
                }
                else -> false
            }
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
    val activeDownloadCount = state.downloadQueue.tasks.count { task ->
        task.state == DownloadTaskState.Queued ||
            task.state == DownloadTaskState.Running ||
            task.state == DownloadTaskState.Paused
    }
    val catalogFocusFirstRequest = FocusFirstRequest(
        persistentNonce = state.homeFocusResetNonce,
    )
    val scheduleFocusFirstRequest = FocusFirstRequest()
    val historyFocusFirstRequest = FocusFirstRequest()
    val latestOnBrowseSectionChange by rememberUpdatedState(onBrowseSectionChange)
    val latestEffectiveHomeSection by rememberUpdatedState(effectiveHomeSection)
    val browsePagerPage = browsePagerSections.indexOf(effectiveHomeSection).takeIf { it >= 0 } ?: 0
    val browsePagerState = rememberPagerState(
        initialPage = browsePagerPage,
        pageCount = { browsePagerSections.size },
    )
    val browsePagerScope = rememberCoroutineScope()
    val browseTabPosition = if (!active || !browsePagerState.isScrollInProgress) {
        browsePagerPage.toFloat()
    } else if (effectiveHomeSection in browsePagerSections) {
        browsePagerState.currentPage + browsePagerState.currentPageOffsetFraction
    } else {
        null
    }
    var browsePageFocusRequestNonce by remember { mutableLongStateOf(0L) }
    var browsePageFocusRequestSection by remember { mutableStateOf(effectiveHomeSection) }
    LaunchedEffect(effectiveHomeSection) {
        if (browsePageFocusRequestSection != effectiveHomeSection) {
            browsePageFocusRequestSection = effectiveHomeSection
            browsePageFocusRequestNonce += 1L
        }
    }
    val browseFocusRequestNonce = if (dpadLayerFocusRequestNonce > 0L) {
        dpadLayerFocusRequestNonce + browsePageFocusRequestNonce
    } else {
        0L
    }

    LaunchedEffect(active, browsePagerPage, effectiveHomeSection, browsePagerSections) {
        if (
            effectiveHomeSection in browsePagerSections &&
            (browsePagerState.currentPage != browsePagerPage || browsePagerState.currentPageOffsetFraction != 0f)
        ) {
            browsePagerState.scrollToPage(browsePagerPage)
        }
    }

    LaunchedEffect(active, browsePagerState, browsePagerPage, effectiveHomeSection, browsePagerSections) {
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
    val onBrowsePagerSectionSelected: (BrowseSection) -> Unit = { section ->
        val page = browsePagerSections.indexOf(section)
        if (page < 0) {
            latestOnBrowseSectionChange(section)
        } else {
            browsePagerScope.launch {
                if (browsePagerState.currentPage != page || browsePagerState.currentPageOffsetFraction != 0f) {
                    browsePagerState.scrollToPage(page)
                }
                latestOnBrowseSectionChange(section)
            }
        }
    }
    fun handleBrowsePageHorizontalExit(page: Int, direction: VisualGridDirection): Boolean {
        val targetPage = when (direction) {
            VisualGridDirection.Left -> page - 1
            VisualGridDirection.Right -> page + 1
            VisualGridDirection.Up,
            VisualGridDirection.Down -> return false
        }
        browsePagerSections.getOrNull(targetPage)?.let { targetSection ->
            onBrowsePagerSectionSelected(targetSection)
        }
        return true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (browseTopBarVisible) {
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
                onExitDown = ::requestCurrentBrowseContentFocus,
                showCompactControls = false,
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (effectiveHomeSection == BrowseSection.Downloads) {
                DownloadsSection(
                    state = state,
                    focusCurrentRequestNonce = dpadLayerFocusRequestNonce,
                    onClearHistory = onClearDownloadHistory,
                    onCancelDownload = onCancelDownload,
                    onPauseDownload = onPauseDownload,
                    onResumeDownload = onResumeDownload,
                    onOpenAnime = onOpenAnime,
                )
            } else {
                HorizontalPager(
                    state = browsePagerState,
                    beyondViewportPageCount = browsePagerSections.size,
                    userScrollEnabled = active,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val pageSection = browsePagerSections.getOrNull(page) ?: BrowseSection.Catalog
                    val pageIsActive = page == browsePagerPage
                    val pageFocusCurrentRequestNonce = if (page == browsePagerPage) {
                        browseFocusRequestNonce
                    } else {
                        0L
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .focusProperties { canFocus = pageIsActive }
                            .focusGroup(),
                    ) {
                        when (pageSection) {
                            BrowseSection.Catalog -> AnimeGridSection(
                                contentState = contentState,
                                pagingState = pagingState,
                                gridState = catalogGridState,
                                cardSize = state.settings.posterCardSize,
                                focusFirstRequest = catalogFocusFirstRequest,
                                focusCurrentRequestNonce = pageFocusCurrentRequestNonce,
                                backToTopSection = BrowseSection.Catalog,
                                onRegisterBackToTopHandler = { handler ->
                                    updateHomeBackToTopHandler(BrowseSection.Catalog, handler)
                                },
                                emptyMessage = if (isSearching) uiText(UiStringKey.NothingFound) else uiText(UiStringKey.CatalogIsEmpty),
                                onRetry = onRefresh,
                                onLoadMore = onLoadMoreAnime,
                                onExitHorizontalDirection = { direction ->
                                    handleBrowsePageHorizontalExit(page, direction)
                                },
                                onOpenAnime = onOpenAnime,
                            )
                            BrowseSection.Schedule -> ScheduleSection(
                                state = state.schedule,
                                listState = scheduleListState,
                                focusFirstRequest = scheduleFocusFirstRequest,
                                focusCurrentRequestNonce = pageFocusCurrentRequestNonce,
                                onRegisterBackToTopHandler = { handler ->
                                    updateHomeBackToTopHandler(BrowseSection.Schedule, handler)
                                },
                                onRetry = onRefresh,
                                onExitHorizontalDirection = { direction ->
                                    handleBrowsePageHorizontalExit(page, direction)
                                },
                                onOpenAnime = onOpenAnime,
                            )
                            BrowseSection.History -> AnimeGridSection(
                                contentState = state.historyAnime,
                                pagingState = PagingUiState(canLoadMore = false),
                                gridState = historyGridState,
                                cardSize = state.settings.posterCardSize,
                                focusFirstRequest = historyFocusFirstRequest,
                                focusCurrentRequestNonce = pageFocusCurrentRequestNonce,
                                backToTopSection = BrowseSection.History,
                                onRegisterBackToTopHandler = { handler ->
                                    updateHomeBackToTopHandler(BrowseSection.History, handler)
                                },
                                emptyMessage = uiText(UiStringKey.HistoryIsEmpty),
                                onRetry = onRefresh,
                                onLoadMore = {},
                                onExitHorizontalDirection = { direction ->
                                    handleBrowsePageHorizontalExit(page, direction)
                                },
                                onOpenAnime = onOpenAnime,
                            )
                            BrowseSection.Downloads -> DownloadsSection(
                                state = state,
                                focusCurrentRequestNonce = pageFocusCurrentRequestNonce,
                                onClearHistory = onClearDownloadHistory,
                                onCancelDownload = onCancelDownload,
                                onPauseDownload = onPauseDownload,
                                onResumeDownload = onResumeDownload,
                                onOpenAnime = onOpenAnime,
                            )
                        }
                    }
                }
            }
        }

        if (isWide && !forcedOffline) {
            BrowseTvSectionIndicatorBar(
                activeSection = effectiveHomeSection,
                visibleSections = browsePagerSections,
                activeSectionPosition = browseTabPosition,
                onSectionSelected = onBrowsePagerSectionSelected,
            )
        } else {
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
            )
        }
    }

    if (catalogActionsEnabled && searchDialogOpen) {
        SearchDialog(
            query = state.searchQuery,
            onQueryChange = onQueryChange,
            onDismiss = { searchDialogOpen = false },
            onExitDown = {
                searchDialogOpen = false
                activeHomeBackToTopHandler
                    ?.takeIf { handler -> handler.section == effectiveHomeSection }
                    ?.handleBackToTop()
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

internal fun browseCatalogActionsEnabledForSection(
    section: BrowseSection,
    forcedOfflineMode: Boolean,
): Boolean {
    return !forcedOfflineMode && section == BrowseSection.Catalog
}

private data class PagerAlignmentState(
    val isScrollInProgress: Boolean,
    val settledPage: Int,
    val currentPage: Int,
    val offset: Float,
)

@Composable
internal fun AnimeGridSection(
    contentState: LoadState<List<Anime>>,
    pagingState: PagingUiState,
    gridState: LazyGridState,
    cardSize: PosterCardSize,
    focusFirstRequest: FocusFirstRequest,
    focusCurrentRequestNonce: Long,
    backToTopSection: BrowseSection,
    onRegisterBackToTopHandler: ((HomeBackToTopHandler?) -> Unit)? = null,
    emptyMessage: String,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onExitHorizontalDirection: (VisualGridDirection) -> Boolean = { true },
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
        val itemFocusRequesters = remember(backToTopSection, animes.size, columnsCount) {
            List(animes.size) { FocusRequester() }
        }
        var focusedAnimeIndex by rememberSaveable(backToTopSection, columnsCount) { mutableIntStateOf(-1) }
        var handledPersistentFocusResetNonce by remember(backToTopSection) { mutableLongStateOf(0L) }
        var handledCurrentFocusRequestNonce by remember(backToTopSection) { mutableLongStateOf(0L) }
        var focusRequestJob by remember(backToTopSection, columnsCount) { mutableStateOf<Job?>(null) }

        fun updateFocusedAnimeIndex(index: Int) {
            focusedAnimeIndex = index
        }

        fun rowStartIndex(index: Int): Int {
            return if (columnsCount > 0) (index / columnsCount) * columnsCount else index
        }

        fun requestAnimeItemFocus(index: Int): Boolean {
            val requester = itemFocusRequesters.getOrNull(index) ?: return false
            return runCatching { requester.requestFocus() }.getOrDefault(false)
        }

        suspend fun focusAnimeItemWhenVisible(index: Int) {
            withTimeoutOrNull(1_000L) {
                snapshotFlow {
                    gridState.layoutInfo.visibleItemsInfo.any { item -> item.index == index }
                }
                    .filter { isVisible -> isVisible }
                    .first()
            }
            repeat(6) {
                withFrameNanos { }
                if (requestAnimeItemFocus(index)) return
            }
        }

        fun moveAnimeFocusTo(index: Int): Boolean {
            if (index !in animes.indices) return false
            focusRequestJob?.cancel()
            updateFocusedAnimeIndex(index)
            if (requestAnimeItemFocus(index)) {
                focusRequestJob = null
                return true
            }
            focusRequestJob = focusScope.launch {
                gridState.scrollToItem(rowStartIndex(index), 0)
                focusAnimeItemWhenVisible(index)
            }
            return true
        }

        fun visibleAnimeFocusBounds(): List<VisualFocusBounds> {
            return gridState.layoutInfo.visibleItemsInfo.mapNotNull { item ->
                val itemIndex = item.index
                if (itemIndex !in animes.indices) return@mapNotNull null
                VisualFocusBounds(
                    index = itemIndex,
                    left = item.offset.x.toFloat(),
                    top = item.offset.y.toFloat(),
                    right = (item.offset.x + item.size.width).toFloat(),
                    bottom = (item.offset.y + item.size.height).toFloat(),
                )
            }
        }

        fun handleAnimeGridDirection(index: Int, key: Key): Boolean {
            if (columnsCount <= 0 || index !in animes.indices) return false
            val direction = when (key) {
                Key.DirectionLeft -> VisualGridDirection.Left
                Key.DirectionRight -> VisualGridDirection.Right
                Key.DirectionUp -> VisualGridDirection.Up
                Key.DirectionDown -> VisualGridDirection.Down
                else -> return false
            }
            val sourceIndex = focusedAnimeIndex.takeIf { it in animes.indices } ?: index
            val visualTarget = visualFocusDirectionalTarget(
                bounds = visibleAnimeFocusBounds(),
                sourceIndex = sourceIndex,
                direction = direction,
            )
            if (visualTarget != null) {
                return moveAnimeFocusTo(visualTarget)
            }
            val target = visualGridMoveTarget(
                index = sourceIndex,
                total = animes.size,
                columns = columnsCount,
                direction = direction,
            )
            if (target != null) {
                return moveAnimeFocusTo(target)
            }
            if (direction == VisualGridDirection.Down && pagingState.canLoadMore && !pagingState.isLoadingMore) {
                onLoadMore()
            }
            return when (direction) {
                VisualGridDirection.Left,
                VisualGridDirection.Right -> onExitHorizontalDirection(direction)
                VisualGridDirection.Down -> true
                VisualGridDirection.Up -> false
            }
        }

        fun canHandleBackToTop(): Boolean {
            return canHandleRootHomeBackToTop(
                isRootHome = true,
                homeSection = backToTopSection,
                firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
            )
        }

        fun handleBackToTop(): Boolean {
            if (!canHandleBackToTop() || animes.isEmpty()) return false
            focusRequestJob?.cancel()
            updateFocusedAnimeIndex(0)
            focusRequestJob = focusScope.launch {
                gridState.scrollToItem(0, 0)
                focusAnimeItemWhenVisible(0)
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
            if (!shouldHandlePersistent) return@LaunchedEffect
            val targetIndex = 0
            val targetRowStart = rowStartIndex(targetIndex)
            focusRequestJob?.cancel()
            focusRequestJob = null
            updateFocusedAnimeIndex(targetIndex)
            gridState.scrollToItem(targetRowStart, 0)
            focusAnimeItemWhenVisible(targetIndex)
            gridState.scrollToItem(targetRowStart, 0)
            if (shouldHandlePersistent) {
                handledPersistentFocusResetNonce = focusFirstRequest.persistentNonce
            }
        }

        LaunchedEffect(focusCurrentRequestNonce, animes.size, columnsCount) {
            if (
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
            val targetIndex = focusedAnimeIndex
                .takeIf { index -> index in visibleIndexes }
                ?: visibleIndexes.minOrNull()
                ?: gridState.firstVisibleItemIndex.coerceIn(0, animes.lastIndex)
            updateFocusedAnimeIndex(targetIndex)
            if (!requestAnimeItemFocus(targetIndex)) {
                focusAnimeItemWhenVisible(targetIndex)
            }
            handledCurrentFocusRequestNonce = focusCurrentRequestNonce
        }

        LaunchedEffect(animes.size) {
            if (animes.isEmpty()) {
                updateFocusedAnimeIndex(-1)
            } else if (focusedAnimeIndex > animes.lastIndex) {
                updateFocusedAnimeIndex(animes.lastIndex)
            }
        }

        LaunchedEffect(
            focusedAnimeIndex,
            animes.size,
            columnsCount,
            pagingState.canLoadMore,
            pagingState.isLoadingMore,
            pagingState.error,
        ) {
            if (
                focusedAnimeIndex < 0 ||
                columnsCount <= 0 ||
                !pagingState.canLoadMore ||
                pagingState.isLoadingMore ||
                pagingState.error != null
            ) {
                return@LaunchedEffect
            }
            val focusedRow = focusedAnimeIndex / columnsCount
            val lastLoadedRow = animes.lastIndex.coerceAtLeast(0) / columnsCount
            if (lastLoadedRow - focusedRow < 2) {
                onLoadMore()
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columnsCount),
            state = gridState,
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier
                .fillMaxSize()
                .focusGroup(),
        ) {
            itemsIndexed(animes, key = { index, anime -> "anime-grid:$index:${anime.id}:${anime.title}" }) { index, anime ->
                var itemHasFocus by remember { mutableStateOf(false) }
                AnimeCard(
                    anime = anime,
                    onClick = { onOpenAnime(anime.id) },
                    focused = itemHasFocus,
                    modifier = Modifier
                        .focusRequester(itemFocusRequesters[index])
                        .onPreviewKeyEvent { event ->
                            event.type == KeyEventType.KeyDown &&
                                handleAnimeGridDirection(index, event.key)
                        }
                        .onFocusChanged { focusState ->
                            if (focusState.hasFocus) {
                                itemHasFocus = true
                                updateFocusedAnimeIndex(index)
                            } else {
                                itemHasFocus = false
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

@Composable
internal fun ScheduleSection(
    state: LoadState<List<ScheduleAnime>>,
    listState: LazyListState,
    focusFirstRequest: FocusFirstRequest,
    focusCurrentRequestNonce: Long,
    onRegisterBackToTopHandler: ((HomeBackToTopHandler?) -> Unit)? = null,
    onRetry: () -> Unit,
    onExitHorizontalDirection: (VisualGridDirection) -> Boolean = { true },
    onOpenAnime: (Long) -> Unit,
) {
    when (state) {
        LoadState.Loading -> LoadingPane(Modifier.fillMaxSize())
        is LoadState.Error -> ErrorPane(
            message = state.message,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
        )
        is LoadState.Ready -> {
            val zoneId = remember { ZoneId.systemDefault() }
            val dayGroups = remember(state.data, zoneId) {
                state.data.toScheduleDayGroups(zoneId)
            }
            var selectedScheduleDay by rememberSaveable { mutableLongStateOf(Long.MIN_VALUE) }
            val selectedGroup = remember(dayGroups, selectedScheduleDay) {
                dayGroups.firstOrNull { group -> group.epochDay == selectedScheduleDay }
                    ?: dayGroups.todayOrClosest()
            }
            val visibleItems = selectedGroup?.items.orEmpty()
            val focusScope = rememberCoroutineScope()
            val currentItemFocusRequester = remember { FocusRequester() }
            var focusedScheduleIndex by rememberSaveable { mutableIntStateOf(0) }
            var handledPersistentFocusResetNonce by remember { mutableLongStateOf(0L) }
            var handledCurrentFocusRequestNonce by remember { mutableLongStateOf(0L) }

            fun updateFocusedScheduleIndex(index: Int) {
                focusedScheduleIndex = index
            }

            suspend fun focusScheduleItemWhenVisible(listIndex: Int) {
                withTimeoutOrNull(1_000L) {
                    snapshotFlow {
                        listState.layoutInfo.visibleItemsInfo.any { item -> item.index == listIndex }
                    }
                        .filter { isVisible -> isVisible }
                        .first()
                }
                repeat(6) {
                    withFrameNanos { }
                    if (runCatching { currentItemFocusRequester.requestFocus() }.getOrDefault(false)) return
                }
            }

            fun canHandleBackToTop(): Boolean {
                return canHandleRootHomeBackToTop(
                    isRootHome = true,
                    homeSection = BrowseSection.Schedule,
                    firstVisibleItemIndex = listState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
                )
            }

            fun handleBackToTop(): Boolean {
                if (!canHandleBackToTop() || visibleItems.isEmpty()) return false
                updateFocusedScheduleIndex(0)
                focusScope.launch {
                    listState.scrollToItem(0, 0)
                    focusScheduleItemWhenVisible(1)
                }
                return true
            }

            LaunchedEffect(dayGroups.map { it.epochDay }) {
                if (dayGroups.isEmpty()) {
                    selectedScheduleDay = Long.MIN_VALUE
                    updateFocusedScheduleIndex(-1)
                    return@LaunchedEffect
                }
                if (dayGroups.none { group -> group.epochDay == selectedScheduleDay }) {
                    selectedScheduleDay = dayGroups.todayOrClosest()?.epochDay ?: dayGroups.first().epochDay
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
                if (!shouldHandlePersistent) {
                    return@LaunchedEffect
                }
                listState.scrollToItem(0)
                updateFocusedScheduleIndex(0)
                focusScheduleItemWhenVisible(1)
                if (shouldHandlePersistent) {
                    handledPersistentFocusResetNonce = focusFirstRequest.persistentNonce
                }
            }

            LaunchedEffect(visibleItems.size) {
                updateFocusedScheduleIndex(
                    when {
                    visibleItems.isEmpty() -> -1
                    focusedScheduleIndex < 0 -> 0
                    focusedScheduleIndex !in visibleItems.indices -> visibleItems.lastIndex
                    else -> focusedScheduleIndex
                    },
                )
            }

            LaunchedEffect(focusCurrentRequestNonce, visibleItems.size) {
                if (
                    focusCurrentRequestNonce <= 0L ||
                    focusCurrentRequestNonce == handledCurrentFocusRequestNonce ||
                    visibleItems.isEmpty()
                ) {
                    return@LaunchedEffect
                }
                withFrameNanos { }
                val visibleListIndexes = listState.layoutInfo.visibleItemsInfo
                    .asSequence()
                    .map { item -> item.index }
                    .filter { listIndex -> listIndex > 0 }
                    .toList()
                val focusedListIndex = focusedScheduleIndex
                    .takeIf { index -> index in visibleItems.indices }
                    ?.plus(1)
                val targetListIndex = focusedListIndex
                    .takeIf { listIndex -> listIndex in visibleListIndexes }
                    ?: visibleListIndexes.minOrNull()
                    ?: listState.firstVisibleItemIndex.coerceAtLeast(1)
                val targetIndex = (targetListIndex - 1).coerceIn(0, visibleItems.lastIndex)
                updateFocusedScheduleIndex(targetIndex)
                focusScheduleItemWhenVisible(targetListIndex)
                handledCurrentFocusRequestNonce = focusCurrentRequestNonce
            }

            if (state.data.isEmpty()) {
                EmptyPane(message = uiText(UiStringKey.ScheduleIsEmpty), modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            val direction = when (event.key) {
                                Key.DirectionLeft -> VisualGridDirection.Left
                                Key.DirectionRight -> VisualGridDirection.Right
                                else -> return@onKeyEvent false
                            }
                            onExitHorizontalDirection(direction)
                        },
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "schedule-calendar") {
                        ScheduleCalendarBlock(
                            dayGroups = dayGroups,
                            selectedEpochDay = selectedGroup?.epochDay ?: Long.MIN_VALUE,
                            onSelectDay = { epochDay ->
                                selectedScheduleDay = epochDay
                                updateFocusedScheduleIndex(0)
                                focusScope.launch {
                                    listState.animateScrollToItem(0, 0)
                                }
                            },
                        )
                    }

                    if (dayGroups.isEmpty() || visibleItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = uiText(UiStringKey.NoUpcomingReleasesYet),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    lazyItemsIndexed(
                        visibleItems,
                        key = { index, item -> "schedule:$index:${item.anime.id}:${item.nextEpisodeAtSeconds}" },
                    ) { index, item ->
                        ScheduleRow(
                            item = item,
                            onOpenAnime = onOpenAnime,
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .then(
                                    if (index == focusedScheduleIndex) {
                                        Modifier.focusRequester(currentItemFocusRequester)
                                    } else {
                                        Modifier
                                    },
                                )
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

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ScheduleCalendarBlock(
    dayGroups: List<ScheduleDayGroup>,
    selectedEpochDay: Long,
    onSelectDay: (Long) -> Unit,
) {
    val calendarListState = rememberLazyListState()
    val calendarScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val dayKeys = remember(dayGroups) { dayGroups.map { it.epochDay } }
    val dayFocusRequesters = remember(dayKeys) { List(dayKeys.size) { FocusRequester() } }
    val calendarSnapFlingBehavior = rememberSnapFlingBehavior(calendarListState, SnapPosition.Start)
    var navigationEpochDay by remember(dayKeys) { mutableLongStateOf(selectedEpochDay) }
    val calendarHorizontalPaddingPx = with(density) {
        ScheduleCalendarHorizontalPadding.toPx().roundToInt()
    }
    val monthLabelFallbackWidthPx = with(density) { ScheduleMonthLabelReservedWidth.toPx().roundToInt() }
    val monthDividerWidthPx = with(density) { ScheduleMonthDividerWidth.toPx().roundToInt() }
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
            calendarListState.animateScrollToItem(targetFirstIndex, 0)
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

    LaunchedEffect(dayKeys) {
        val selectedIndex = dayGroups.indexOfFirst { group -> group.epochDay == selectedEpochDay }
        if (selectedIndex >= 0) {
            calendarListState.scrollToItem(selectedIndex, 0)
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .nestedScroll(calendarPagerBoundary),
        color = yummySurfaceColor(YummySurfaceRole.Panel).copy(alpha = 0.92f),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Panel),
    ) {
        Column {
            if (dayGroups.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    CompositionLocalProvider(LocalBringIntoViewSpec provides ScheduleCalendarBringIntoViewSpec) {
                        LazyRow(
                            state = calendarListState,
                            modifier = Modifier
                                .focusGroup()
                                .onPreviewKeyEvent { event ->
                                    val delta = when (event.key) {
                                        Key.DirectionLeft -> -1
                                        Key.DirectionRight -> 1
                                        else -> return@onPreviewKeyEvent false
                                    }
                                    if (event.type == KeyEventType.KeyDown) {
                                        moveSelectedDay(delta)
                                    } else {
                                        true
                                    }
                                },
                            contentPadding = PaddingValues(
                                start = ScheduleCalendarHorizontalPadding,
                                top = ScheduleMonthLabelHeight + ScheduleMonthLabelSpacing,
                                end = ScheduleCalendarHorizontalPadding,
                                bottom = 14.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(ScheduleDayTileGap),
                            flingBehavior = calendarSnapFlingBehavior,
                        ) {
                            lazyItemsIndexed(
                                dayGroups,
                                key = { _, group -> "schedule-day:${group.epochDay}" },
                            ) { index, group ->
                                ScheduleDayTile(
                                    group = group,
                                    selected = group.epochDay == navigationEpochDay,
                                    focusRequester = dayFocusRequesters[index],
                                    onClick = { selectDayAt(index, moveFocus = false) },
                                )
                            }
                        }
                    }
                    ScheduleCalendarMonthOverlay(
                        listState = calendarListState,
                        dayGroups = dayGroups,
                        fallbackIndex = selectedDayIndex(),
                        horizontalPaddingPx = calendarHorizontalPaddingPx,
                        fallbackWidthPx = monthLabelFallbackWidthPx,
                        dividerWidthPx = monthDividerWidthPx,
                        modifier = Modifier.matchParentSize(),
                    )
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

@Composable
private fun ScheduleCalendarMonthOverlay(
    listState: LazyListState,
    dayGroups: List<ScheduleDayGroup>,
    fallbackIndex: Int,
    horizontalPaddingPx: Int,
    fallbackWidthPx: Int,
    dividerWidthPx: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val labelColor = MaterialTheme.colorScheme.onSurface
    val labelTextSizePx = with(density) { MaterialTheme.typography.labelLarge.fontSize.toPx() }
    val labelHeightPx = with(density) { ScheduleMonthLabelHeight.toPx() }
    val labelSpacingPx = with(density) { ScheduleMonthLabelSpacing.toPx() }
    val dayTileHeightPx = with(density) { ScheduleDayTileHeight.toPx() }
    Canvas(modifier = modifier) {
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo.map { item ->
            VisibleScheduleCalendarItem(
                index = item.index,
                offsetPx = item.offset,
                sizePx = item.size,
            )
        }
        val monthLabels = buildScheduleCalendarMonthLabels(
            dayGroups = dayGroups,
            visibleItems = visibleItems,
            fallbackIndex = fallbackIndex,
            fallbackWidthPx = fallbackWidthPx,
            dividerWidthPx = dividerWidthPx,
        )
        val dividerTopPx = labelHeightPx + labelSpacingPx
        monthLabels.boundaryDividers.forEach { offsetPx ->
            drawRoundRect(
                color = Color(0xFF3CCE7B).copy(alpha = 0.72f),
                topLeft = Offset(
                    x = (horizontalPaddingPx + offsetPx).toFloat(),
                    y = dividerTopPx,
                ),
                size = Size(
                    width = dividerWidthPx.toFloat(),
                    height = dayTileHeightPx,
                ),
                cornerRadius = CornerRadius(
                    x = dividerWidthPx / 2f,
                    y = dividerWidthPx / 2f,
                ),
            )
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textSize = labelTextSizePx
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val baseline = (labelHeightPx - textPaint.ascent() - textPaint.descent()) / 2f
        monthLabels.segments.forEach { segment ->
            if (segment.title.isBlank() || segment.widthPx <= 0) return@forEach
            val left = (horizontalPaddingPx + segment.offsetPx).toFloat()
            val right = left + segment.widthPx
            drawContext.canvas.nativeCanvas.apply {
                save()
                clipRect(left, 0f, right, labelHeightPx)
                drawText(segment.title, left, baseline, textPaint)
                restore()
            }
        }
    }
}

private data class ScheduleCalendarMonthLabels(
    val segments: List<ScheduleCalendarMonthSegment> = emptyList(),
    val boundaryDividers: List<Int> = emptyList(),
)

private data class ScheduleCalendarMonthSegment(
    val title: String,
    val offsetPx: Int,
    val widthPx: Int,
)

internal data class VisibleScheduleCalendarItem(
    val index: Int,
    val offsetPx: Int,
    val sizePx: Int,
)

private data class VisibleScheduleDayItem(
    val index: Int,
    val group: ScheduleDayGroup,
    val offsetPx: Int,
    val sizePx: Int,
)

private data class VisibleScheduleMonthRun(
    val year: Int,
    val monthValue: Int,
    val title: String,
    val startOffsetPx: Int,
    val endOffsetPx: Int,
)

private fun buildScheduleCalendarMonthLabels(
    dayGroups: List<ScheduleDayGroup>,
    visibleItems: List<VisibleScheduleCalendarItem>,
    fallbackIndex: Int,
    fallbackWidthPx: Int,
    dividerWidthPx: Int,
): ScheduleCalendarMonthLabels {
    if (dayGroups.isEmpty()) return ScheduleCalendarMonthLabels()
    val visibleDays = visibleItems
        .mapNotNull { item ->
            dayGroups.getOrNull(item.index)?.let { group ->
                VisibleScheduleDayItem(
                    index = item.index,
                    group = group,
                    offsetPx = item.offsetPx,
                    sizePx = item.sizePx,
                )
            }
        }
        .sortedBy { day -> day.offsetPx }
    val pinnedIndex = visibleDays.firstOrNull()?.index
        ?: fallbackIndex.coerceIn(dayGroups.indices)
    if (visibleDays.isEmpty()) {
        return ScheduleCalendarMonthLabels(
            segments = listOf(
                ScheduleCalendarMonthSegment(
                    title = dayGroups[pinnedIndex].scheduleMonthTitle(),
                    offsetPx = 0,
                    widthPx = fallbackWidthPx,
                ),
            ),
        )
    }

    val runs = mutableListOf<VisibleScheduleMonthRun>()
    visibleDays.forEach { day ->
        val last = runs.lastOrNull()
        val monthChanged = last == null ||
            last.year != day.group.date.year ||
            last.monthValue != day.group.date.monthValue
        if (last == null || monthChanged) {
            runs += VisibleScheduleMonthRun(
                year = day.group.date.year,
                monthValue = day.group.date.monthValue,
                title = day.group.scheduleMonthTitle(),
                startOffsetPx = day.offsetPx.coerceAtLeast(0),
                endOffsetPx = day.offsetPx + day.sizePx,
            )
        } else {
            runs[runs.lastIndex] = last.copy(endOffsetPx = day.offsetPx + day.sizePx)
        }
    }

    val segments = runs.mapIndexed { index, run ->
        val nextStartOffset = runs.getOrNull(index + 1)?.startOffsetPx
        val endOffset = if (nextStartOffset == null) {
            run.endOffsetPx
        } else {
            minOf(run.endOffsetPx, nextStartOffset)
        }
        ScheduleCalendarMonthSegment(
            title = run.title,
            offsetPx = run.startOffsetPx,
            widthPx = (endOffset - run.startOffsetPx).coerceAtLeast(1),
        )
    }
    val dividers = visibleDays
        .zipWithNext()
        .mapNotNull { day ->
            val previous = day.first
            val current = day.second
            val previousDate = previous.group.date
            val currentDate = current.group.date
            val monthChanged = previousDate.year != currentDate.year ||
                previousDate.monthValue != currentDate.monthValue
            if (previous.index + 1 == current.index && monthChanged) {
                scheduleCalendarMonthDividerOffsetPx(
                    previousOffsetPx = previous.offsetPx,
                    previousSizePx = previous.sizePx,
                    boundaryOffsetPx = current.offsetPx,
                    dividerWidthPx = dividerWidthPx,
                )
            } else {
                null
            }
        }
        .distinct()
    return ScheduleCalendarMonthLabels(
        segments = segments,
        boundaryDividers = dividers,
    )
}

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

internal fun scheduleCalendarMonthDividerOffsetPx(
    previousOffsetPx: Int,
    previousSizePx: Int,
    boundaryOffsetPx: Int,
    dividerWidthPx: Int,
): Int? {
    val gapStartPx = previousOffsetPx + previousSizePx
    val gapWidthPx = boundaryOffsetPx - gapStartPx
    if (gapWidthPx <= dividerWidthPx) return null
    return gapStartPx + ((gapWidthPx - dividerWidthPx) / 2)
}

@Composable
private fun ScheduleDayTile(
    group: ScheduleDayGroup,
    selected: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val dayOfWeek = group.date.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, scheduleLocale)
        .replace(".", "")
        .replaceFirstChar { char -> char.uppercase(scheduleLocale) }
    val isWeekend = group.date.dayOfWeek.value >= 6
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(ScheduleDayTileWidth),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(ScheduleDayTileHeight)
                .focusRequester(focusRequester)
                .dpadClickable(shape, onClick),
            color = if (selected) Color.White.copy(alpha = 0.22f) else Color(0xFF202023).copy(alpha = 0.92f),
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
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.72f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                            .background(Color(0xFF3CCE7B)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleReleaseRow(
    item: ScheduleAnime,
    onOpenAnime: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val compact = LocalConfiguration.current.screenWidthDp < 560
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .dpadClickable(shape) { onOpenAnime(item.anime.id) },
        color = Color(0xFF252527).copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 84.dp else 92.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterImage(
                url = item.anime.posterUrl,
                contentDescription = item.anime.title,
                modifier = Modifier
                    .padding(start = 10.dp, end = 12.dp)
                    .size(if (compact) 58.dp else 70.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = item.anime.title,
                    style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (compact) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = scheduleReleasedText(item),
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = item.formatScheduleTime(),
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = if (compact) 12.dp else 18.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(5.dp)
                    .background(Color(0xFF3CCE7B)),
            )
        }
    }
}

@Composable
internal fun ScheduleRow(
    item: ScheduleAnime,
    onOpenAnime: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScheduleReleaseRow(item = item, onOpenAnime = onOpenAnime, modifier = modifier)
}

private val ScheduleDayTileWidth = 96.dp
private val ScheduleDayTileHeight = 78.dp
private val ScheduleDayTileGap = 10.dp
private val ScheduleCalendarHorizontalPadding = 12.dp
private val ScheduleMonthLabelHeight = 24.dp
private val ScheduleMonthLabelSpacing = 8.dp
private val ScheduleMonthLabelReservedWidth = 112.dp
private val ScheduleMonthDividerWidth = 3.dp
@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
private val ScheduleCalendarBringIntoViewSpec = object : BringIntoViewSpec {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(durationMillis = 340)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}
private val scheduleLocale: Locale = Locale.forLanguageTag("ru-RU")
private val scheduleTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", scheduleLocale)

private fun ScheduleDayGroup.scheduleMonthTitle(): String {
    return date.month.getDisplayName(TextStyle.FULL_STANDALONE, scheduleLocale).uppercase(scheduleLocale)
}

private data class ScheduleDayGroup(
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

private fun ScheduleAnime.formatScheduleTime(): String {
    val timestamp = scheduleDisplayTimestampSeconds() ?: return "--:--"
    return Instant.ofEpochSecond(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(scheduleTimeFormatter)
}

@Composable
private fun scheduleReleasedText(item: ScheduleAnime): String {
    return "${item.airedEpisodes} ${uiText(UiStringKey.EpisodeIsAlreadyOut)}"
}
