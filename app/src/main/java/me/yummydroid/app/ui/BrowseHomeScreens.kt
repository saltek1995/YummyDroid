package me.yummydroid.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.ScheduleAnime
import me.yummydroid.app.DownloadTaskState
import me.yummydroid.app.formatScheduleTimestamp
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.readyListOrEmpty
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.YummyAlpha
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor
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
    onOpenAnime: (Long) -> Unit,
) {
    val isAuthorized = state.auth.profile != null
    val browsePagerSections = remember(isAuthorized) { visibleBrowseSections(isAuthorized) }
    val effectiveHomeSection = if (state.homeSection == BrowseSection.History && !isAuthorized) {
        BrowseSection.Catalog
    } else {
        state.homeSection
    }
    LaunchedEffect(state.homeSection, isAuthorized) {
        if (state.homeSection == BrowseSection.History && !isAuthorized) {
            onBrowseSectionChange(BrowseSection.Catalog)
        }
    }
    val isCatalog = effectiveHomeSection == BrowseSection.Catalog
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
    val browseTabPosition = if (effectiveHomeSection in browsePagerSections) {
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

    LaunchedEffect(browsePagerPage, effectiveHomeSection, browsePagerSections) {
        if (effectiveHomeSection in browsePagerSections && browsePagerState.currentPage != browsePagerPage) {
            browsePagerState.animateScrollToPage(browsePagerPage)
        }
    }

    LaunchedEffect(browsePagerState, browsePagerSections) {
        snapshotFlow { browsePagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val section = browsePagerSections.getOrNull(page) ?: return@collect
                if (section != latestEffectiveHomeSection) {
                    latestOnBrowseSectionChange(section)
                }
            }
    }

    @Composable
    fun BrowsePageHost(modifier: Modifier = Modifier) {
        Box(modifier = modifier) {
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
                                emptyMessage = if (isSearching) uiText("Ничего не найдено") else uiText("Каталог пуст"),
                                onRetry = onRefresh,
                                onLoadMore = onLoadMoreAnime,
                                canExitHorizontalDirection = { direction ->
                                    canExitBrowsePageHorizontally(
                                        page = page,
                                        pageCount = browsePagerSections.size,
                                        direction = direction,
                                    )
                                },
                                onOpenAnime = onOpenAnime,
                            )
                            BrowseSection.Schedule -> ScheduleSection(
                                state = state.schedule,
                                filters = state.filters,
                                catalog = state.filterCatalog.readyDataOrNull() ?: FilterCatalog.Empty,
                                listState = scheduleListState,
                                focusFirstRequest = scheduleFocusFirstRequest,
                                focusCurrentRequestNonce = pageFocusCurrentRequestNonce,
                                onRegisterBackToTopHandler = { handler ->
                                    updateHomeBackToTopHandler(BrowseSection.Schedule, handler)
                                },
                                onRetry = onRefresh,
                                canExitHorizontalDirection = { direction ->
                                    canExitBrowsePageHorizontally(
                                        page = page,
                                        pageCount = browsePagerSections.size,
                                        direction = direction,
                                    )
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
                                emptyMessage = uiText("История пуста"),
                                onRetry = onRefresh,
                                onLoadMore = {},
                                canExitHorizontalDirection = { direction ->
                                    canExitBrowsePageHorizontally(
                                        page = page,
                                        pageCount = browsePagerSections.size,
                                        direction = direction,
                                    )
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
    }

    if (isWide) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                            MaterialTheme.colorScheme.background,
                        ),
                    ),
                )
                .padding(22.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            NeonBrowseRail(
                activeSection = effectiveHomeSection,
                catalogCount = contentState.readyListOrEmpty().size,
                scheduleCount = state.schedule.readyListOrEmpty().size,
                historyCount = state.historyAnime.readyListOrEmpty().size,
                activeFilters = state.filters.activeCount,
                activeDownloadCount = activeDownloadCount,
                forcedOfflineMode = state.forcedOfflineMode,
                modifier = Modifier
                    .width(286.dp)
                    .fillMaxHeight(),
            )

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 18.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    NeonBrowseConsoleHeader(
                        profileAvailable = state.auth.profile != null,
                        activeFilters = state.filters.activeCount,
                        activeSearch = isSearching,
                        activeDownloadCount = activeDownloadCount,
                        activeSection = effectiveHomeSection,
                        visibleSections = browsePagerSections,
                        activeSectionPosition = browseTabPosition,
                        onSectionSelected = onBrowseSectionChange,
                        onOpenSearch = { searchDialogOpen = true },
                        onOpenFilters = { filtersDialogOpen = true },
                        onOpenSettings = onOpenSettings,
                        onOpenDownloads = onOpenDownloads,
                        onOpenLogin = onOpenLogin,
                        onOpenProfile = onOpenProfile,
                        onExitDown = ::requestCurrentBrowseContentFocus,
                    )
                    NeonBrowseSectionHeader(
                        activeSection = effectiveHomeSection,
                        contentCount = when (effectiveHomeSection) {
                            BrowseSection.Catalog -> contentState.readyListOrEmpty().size
                            BrowseSection.Schedule -> state.schedule.readyListOrEmpty().size
                            BrowseSection.History -> state.historyAnime.readyListOrEmpty().size
                            BrowseSection.Downloads -> activeDownloadCount
                        },
                    )
                    BrowsePageHost(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.72f)),
                    )
                }
            }
        }
    } else {
        Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.84f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        if (browseTopBarVisible) {
            NeonMobileBrowseHeader(
                activeSection = effectiveHomeSection,
                contentCount = when (effectiveHomeSection) {
                    BrowseSection.Catalog -> contentState.readyListOrEmpty().size
                    BrowseSection.Schedule -> state.schedule.readyListOrEmpty().size
                    BrowseSection.History -> state.historyAnime.readyListOrEmpty().size
                    BrowseSection.Downloads -> activeDownloadCount
                },
                forcedOfflineMode = state.forcedOfflineMode,
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
                                emptyMessage = if (isSearching) uiText("Ничего не найдено") else uiText("Каталог пуст"),
                                onRetry = onRefresh,
                                onLoadMore = onLoadMoreAnime,
                                canExitHorizontalDirection = { direction ->
                                    canExitBrowsePageHorizontally(
                                        page = page,
                                        pageCount = browsePagerSections.size,
                                        direction = direction,
                                    )
                                },
                                onOpenAnime = onOpenAnime,
                            )
                            BrowseSection.Schedule -> ScheduleSection(
                                state = state.schedule,
                                filters = state.filters,
                                catalog = state.filterCatalog.readyDataOrNull() ?: FilterCatalog.Empty,
                                listState = scheduleListState,
                                focusFirstRequest = scheduleFocusFirstRequest,
                                focusCurrentRequestNonce = pageFocusCurrentRequestNonce,
                                onRegisterBackToTopHandler = { handler ->
                                    updateHomeBackToTopHandler(BrowseSection.Schedule, handler)
                                },
                                onRetry = onRefresh,
                                canExitHorizontalDirection = { direction ->
                                    canExitBrowsePageHorizontally(
                                        page = page,
                                        pageCount = browsePagerSections.size,
                                        direction = direction,
                                    )
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
                                emptyMessage = uiText("История пуста"),
                                onRetry = onRefresh,
                                onLoadMore = {},
                                canExitHorizontalDirection = { direction ->
                                    canExitBrowsePageHorizontally(
                                        page = page,
                                        pageCount = browsePagerSections.size,
                                        direction = direction,
                                    )
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

        if (!isWide) {
            BrowseBottomBarModern(
                onOpenSearch = { searchDialogOpen = true },
                onOpenFilters = { filtersDialogOpen = true },
                onOpenSettings = onOpenSettings,
                onOpenDownloads = onOpenDownloads,
                auth = state.auth,
                activeFilters = state.filters.activeCount,
                activeSearch = isSearching,
                activeDownloadCount = activeDownloadCount,
                onOpenLogin = onOpenLogin,
                onOpenProfile = onOpenProfile,
                activeSection = effectiveHomeSection,
                visibleSections = browsePagerSections,
                activeSectionPosition = browseTabPosition,
                onSectionSelected = onBrowseSectionChange,
            )
        }
    }
    }

    if (searchDialogOpen) {
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

    if (filtersDialogOpen) {
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

@Composable
private fun NeonBrowseRail(
    activeSection: BrowseSection,
    catalogCount: Int,
    scheduleCount: Int,
    historyCount: Int,
    activeFilters: Int,
    activeDownloadCount: Int,
    forcedOfflineMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 18.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AppWordmark(
                height = 44.dp,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = activeSection.localizedTitle(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (activeSection) {
                        BrowseSection.Catalog -> uiText("Каталог аниме")
                        BrowseSection.Schedule -> uiText("Выходы серий")
                        BrowseSection.History -> uiText("Продолжить просмотр")
                        BrowseSection.Downloads -> uiText("Офлайн-библиотека")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            NeonRailMetric(title = uiText("Каталог"), value = catalogCount.toString(), active = activeSection == BrowseSection.Catalog)
            NeonRailMetric(title = uiText("Расписание"), value = scheduleCount.toString(), active = activeSection == BrowseSection.Schedule)
            NeonRailMetric(title = uiText("История"), value = historyCount.toString(), active = activeSection == BrowseSection.History)

            if (activeFilters > 0 || activeDownloadCount > 0 || forcedOfflineMode) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (activeFilters > 0) {
                        NeonRailMetric(title = uiText("Фильтры"), value = activeFilters.toString(), active = true)
                    }
                    if (activeDownloadCount > 0) {
                        NeonRailMetric(title = uiText("Загрузки"), value = activeDownloadCount.toString(), active = true)
                    }
                    if (forcedOfflineMode) {
                        OfflineModeChip()
                    }
                }
            }
        }
    }
}

@Composable
private fun NeonRailMetric(
    title: String,
    value: String,
    active: Boolean,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        color = if (active) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NeonMobileBrowseHeader(
    activeSection: BrowseSection,
    contentCount: Int,
    forcedOfflineMode: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.80f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 10.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppWordmark(
                    height = 28.dp,
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    color = YummyColors.neonLime.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = contentCount.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = activeSection.localizedTitle(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (forcedOfflineMode) {
                OfflineModeChip()
            }
        }
    }
}

@Composable
private fun NeonBrowseConsoleHeader(
    profileAvailable: Boolean,
    activeFilters: Int,
    activeSearch: Boolean,
    activeDownloadCount: Int,
    activeSection: BrowseSection,
    visibleSections: List<BrowseSection>,
    activeSectionPosition: Float?,
    onSectionSelected: (BrowseSection) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onExitDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                    onExitDown()
                    true
                } else {
                    false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BrowseSectionTabs(
            activeSection = activeSection,
            visibleSections = visibleSections,
            activeSectionPosition = activeSectionPosition,
            onSectionSelected = onSectionSelected,
            modifier = Modifier.weight(1f),
        )
        ChromeIconButton(
            icon = Icons.Default.Search,
            contentDescription = uiText("Поиск"),
            onClick = onOpenSearch,
            active = activeSearch,
        )
        ChromeIconButton(
            icon = Icons.Default.FilterList,
            contentDescription = uiText("Фильтры"),
            onClick = onOpenFilters,
            active = activeFilters > 0,
            badgeText = activeFilters.takeIf { it > 0 }?.coerceAtMost(9)?.toString(),
        )
        ChromeIconButton(
            icon = Icons.Default.Download,
            contentDescription = uiText("Загрузки"),
            onClick = onOpenDownloads,
            active = activeDownloadCount > 0,
            badgeText = activeDownloadCount.takeIf { it > 0 }?.let { count -> if (count > 9) "9+" else count.toString() },
        )
        ChromeIconButton(
            icon = Icons.Default.Settings,
            contentDescription = uiText("Настройки"),
            onClick = onOpenSettings,
        )
        ChromeIconButton(
            icon = Icons.Default.AccountCircle,
            contentDescription = if (profileAvailable) uiText("Профиль") else uiText("Войти"),
            onClick = if (profileAvailable) onOpenProfile else onOpenLogin,
            active = profileAvailable,
        )
    }
}

@Composable
private fun NeonBrowseSectionHeader(
    activeSection: BrowseSection,
    contentCount: Int,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = activeSection.localizedTitle(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Surface(
                color = YummyColors.neonLime.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.onSecondary,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = contentCount.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private fun canExitBrowsePageHorizontally(
    page: Int,
    pageCount: Int,
    direction: VisualGridDirection,
): Boolean {
    return when (direction) {
        VisualGridDirection.Left -> page > 0
        VisualGridDirection.Right -> page < pageCount - 1
        VisualGridDirection.Up,
        VisualGridDirection.Down -> false
    }
}

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
    canExitHorizontalDirection: (VisualGridDirection) -> Boolean = { false },
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
                VisualGridDirection.Right -> !canExitHorizontalDirection(direction)
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
                focusedItemIndex = focusedAnimeIndex,
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
            contentPadding = PaddingValues(
                start = 30.dp,
                top = 26.dp,
                end = 30.dp,
                bottom = 30.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = YummyAlpha.subtleSurface))
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
    filters: BrowseFilters,
    catalog: FilterCatalog,
    listState: LazyListState,
    focusFirstRequest: FocusFirstRequest,
    focusCurrentRequestNonce: Long,
    onRegisterBackToTopHandler: ((HomeBackToTopHandler?) -> Unit)? = null,
    onRetry: () -> Unit,
    canExitHorizontalDirection: (VisualGridDirection) -> Boolean = { false },
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
            var hidePastItems by rememberSaveable { mutableStateOf(true) }
            val filteredItems = remember(state.data, filters, catalog) {
                state.data.filteredAndSortedSchedule(filters, catalog)
            }
            val upcomingItems = remember(filteredItems) { upcomingScheduleItems(filteredItems) }
            val visibleItems = if (hidePastItems) upcomingItems else filteredItems
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
                    focusedItemIndex = focusedScheduleIndex,
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
                EmptyPane(message = uiText("Расписание пока пустое"), modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val direction = when (event.key) {
                                Key.DirectionLeft -> VisualGridDirection.Left
                                Key.DirectionRight -> VisualGridDirection.Right
                                else -> return@onPreviewKeyEvent false
                            }
                            !canExitHorizontalDirection(direction)
                        },
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        SchedulePastFilterToggle(
                            hidePastItems = hidePastItems,
                            hiddenCount = filteredItems.size - upcomingItems.size,
                            onToggle = { hidePastItems = !hidePastItems },
                        )
                    }

                    if (visibleItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (filteredItems.isEmpty()) {
                                        uiText("По выбранным фильтрам ничего не найдено")
                                    } else {
                                        uiText("Ближайших выходов пока нет")
                                    },
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
internal fun SchedulePastFilterToggle(
    hidePastItems: Boolean,
    hiddenCount: Int,
    onToggle: () -> Unit,
) {
    val role = if (hidePastItems) YummySurfaceRole.ActiveRow else YummySurfaceRole.Row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val shape = RoundedCornerShape(8.dp)
        Surface(
            modifier = Modifier
                .height(36.dp)
                .dpadClickable(shape, onToggle),
            color = yummySurfaceColor(role),
            contentColor = yummySurfaceContentColor(role),
            shape = shape,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(17.dp))
                Text(
                    text = if (hidePastItems) uiText("Прошедшие скрыты") else uiText("Прошедшие показаны"),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (hiddenCount > 0) {
            Text(
                text = if (hidePastItems) "$hiddenCount ${uiText("скрыто")}" else "$hiddenCount ${uiText("прошедших")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .dpadClickable(shape) { onOpenAnime(item.anime.id) },
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterImage(
                url = item.anime.posterUrl,
                contentDescription = item.anime.title,
                modifier = Modifier
                    .width(72.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.anime.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${uiText("Вышло")} ${item.airedEpisodes}" +
                        if (item.totalEpisodes > 0) " ${uiText("из")} ${item.totalEpisodes}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                item.nextEpisodeAtSeconds.takeIf { it > 0L }?.let { next ->
                    Text(
                        text = "${uiText("Следующая")}: ${formatScheduleTimestamp(next)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
