package me.yummydroid.app.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
import me.yummydroid.app.ui.theme.yummySurfaceBorder
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
    val browseSwipeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val latestOnBrowseSectionChange by rememberUpdatedState(onBrowseSectionChange)

    fun selectAdjacentBrowseSection(delta: Int) {
        val currentIndex = browsePagerSections.indexOf(effectiveHomeSection)
        if (currentIndex < 0) return
        val nextSection = browsePagerSections.getOrNull(currentIndex + delta) ?: return
        latestOnBrowseSectionChange(nextSection)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        if (browseTopBarVisible) {
            BrowseTopBarModern(
                onOpenSearch = { searchDialogOpen = true },
                onOpenFilters = { filtersDialogOpen = true },
                onOpenSettings = onOpenSettings,
                onOpenDownloads = onOpenDownloads,
                auth = state.auth,
                activeFilters = state.filters.activeCount,
                activeSearch = isSearching,
                activeDownloadCount = activeDownloadCount,
                forcedOfflineMode = state.forcedOfflineMode,
                onOpenLogin = onOpenLogin,
                onOpenProfile = onOpenProfile,
                isWide = isWide,
                activeSection = effectiveHomeSection,
                visibleSections = browsePagerSections,
                onSectionSelected = onBrowseSectionChange,
                onExitDown = ::requestCurrentBrowseContentFocus,
                showCompactControls = false,
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = effectiveHomeSection,
                transitionSpec = {
                    val initialIndex = browsePagerSections.indexOf(initialState).takeIf { it >= 0 } ?: 0
                    val targetIndex = browsePagerSections.indexOf(targetState).takeIf { it >= 0 } ?: initialIndex
                    if (targetIndex >= initialIndex) {
                        slideInHorizontally { width -> width } togetherWith
                            slideOutHorizontally { width -> -width }
                    } else {
                        slideInHorizontally { width -> -width } togetherWith
                            slideOutHorizontally { width -> width }
                    }
                },
                label = "browse-section",
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(browseSwipeThresholdPx, effectiveHomeSection, browsePagerSections) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                totalDrag += dragAmount
                                change.consume()
                            },
                            onDragEnd = {
                                if (abs(totalDrag) >= browseSwipeThresholdPx) {
                                    selectAdjacentBrowseSection(if (totalDrag < 0f) 1 else -1)
                                }
                                totalDrag = 0f
                            },
                            onDragCancel = { totalDrag = 0f },
                        )
                    },
            ) { section ->
                when (section) {
                            BrowseSection.Catalog -> AnimeGridSection(
                                contentState = contentState,
                                pagingState = pagingState,
                                gridState = catalogGridState,
                                cardSize = state.settings.posterCardSize,
                                focusFirstRequest = catalogFocusFirstRequest,
                                focusCurrentRequestNonce = dpadLayerFocusRequestNonce,
                                backToTopSection = BrowseSection.Catalog,
                                onRegisterBackToTopHandler = { handler ->
                                    updateHomeBackToTopHandler(BrowseSection.Catalog, handler)
                                },
                                emptyMessage = if (isSearching) uiText("Ничего не найдено") else uiText("Каталог пуст"),
                                onRetry = onRefresh,
                                onLoadMore = onLoadMoreAnime,
                                onOpenAnime = onOpenAnime,
                            )
                            BrowseSection.Schedule -> ScheduleSection(
                                state = state.schedule,
                                filters = state.filters,
                                catalog = state.filterCatalog.readyDataOrNull() ?: FilterCatalog.Empty,
                                listState = scheduleListState,
                                focusFirstRequest = scheduleFocusFirstRequest,
                                focusCurrentRequestNonce = dpadLayerFocusRequestNonce,
                                onRegisterBackToTopHandler = { handler ->
                                    updateHomeBackToTopHandler(BrowseSection.Schedule, handler)
                                },
                                onRetry = onRefresh,
                                onOpenAnime = onOpenAnime,
                            )
                            BrowseSection.History -> AnimeGridSection(
                                contentState = state.historyAnime,
                                pagingState = PagingUiState(canLoadMore = false),
                                gridState = historyGridState,
                                cardSize = state.settings.posterCardSize,
                                focusFirstRequest = historyFocusFirstRequest,
                                focusCurrentRequestNonce = dpadLayerFocusRequestNonce,
                                backToTopSection = BrowseSection.History,
                                onRegisterBackToTopHandler = { handler ->
                                    updateHomeBackToTopHandler(BrowseSection.History, handler)
                                },
                                emptyMessage = uiText("История пуста"),
                                onRetry = onRefresh,
                                onLoadMore = {},
                                onOpenAnime = onOpenAnime,
                            )
                            BrowseSection.Downloads -> DownloadsSection(
                                state = state,
                                focusCurrentRequestNonce = dpadLayerFocusRequestNonce,
                                onClearHistory = onClearDownloadHistory,
                                onCancelDownload = onCancelDownload,
                                onPauseDownload = onPauseDownload,
                                onResumeDownload = onResumeDownload,
                                onOpenAnime = onOpenAnime,
                            )
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
                onSectionSelected = onBrowseSectionChange,
            )
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
    onOpenAnime: (Long) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val columnsCount = remember(screenWidth, cardSize) {
        cardSize.resolveCatalogColumns(screenWidth)
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
            repeat(2) {
                withFrameNanos { }
                if (requestAnimeItemFocus(index)) return
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
                animes.isEmpty()
            ) {
                return@LaunchedEffect
            }
            val targetIndex = focusedAnimeIndex
                .takeIf { index -> index in animes.indices }
                ?: gridState.firstVisibleItemIndex.coerceIn(0, animes.lastIndex)
            withFrameNanos { }
            updateFocusedAnimeIndex(targetIndex)
            if (!requestAnimeItemFocus(targetIndex)) {
                gridState.scrollToItem(rowStartIndex(targetIndex), 0)
                focusAnimeItemWhenVisible(targetIndex)
            }
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

            fun updateFocusedScheduleIndex(index: Int) {
                focusedScheduleIndex = index
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
                    withFrameNanos { }
                    runCatching { currentItemFocusRequester.requestFocus() }
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
                withFrameNanos { }
                runCatching { currentItemFocusRequester.requestFocus() }
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

            LaunchedEffect(focusCurrentRequestNonce, visibleItems.size, focusedScheduleIndex) {
                if (
                    focusCurrentRequestNonce <= 0L ||
                    visibleItems.isEmpty()
                ) {
                    return@LaunchedEffect
                }
                val firstVisibleScheduleIndex = (listState.firstVisibleItemIndex - 1)
                    .coerceIn(0, visibleItems.lastIndex)
                val targetIndex = focusedScheduleIndex
                    .takeIf { index -> index in visibleItems.indices }
                    ?: firstVisibleScheduleIndex
                val targetListIndex = targetIndex + 1
                val targetIsVisible = listState.layoutInfo.visibleItemsInfo.any { item ->
                    item.index == targetListIndex
                }
                if (!targetIsVisible) {
                    listState.scrollToItem(targetListIndex, 0)
                }
                withFrameNanos { }
                updateFocusedScheduleIndex(targetIndex)
                runCatching { currentItemFocusRequester.requestFocus() }
            }

            if (state.data.isEmpty()) {
                EmptyPane(message = uiText("Расписание пока пустое"), modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
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
            border = yummySurfaceBorder(role),
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
