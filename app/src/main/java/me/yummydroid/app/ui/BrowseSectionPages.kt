package me.yummydroid.app.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.LoadState
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.PosterCardSize

@Composable
internal fun BrowseSectionPageContent(
    pageSection: BrowseSection,
    pageIndex: Int,
    pageCanReceiveFocus: Boolean,
    pageFocusCurrentRequestNonce: Long,
    state: YummyDroidUiState,
    catalogContentState: LoadState<List<Anime>>,
    catalogPagingState: PagingUiState,
    browseCoordinator: BrowseRootUiCoordinator,
    catalogFocusFirstRequest: FocusFirstRequest,
    scheduleFocusFirstRequest: FocusFirstRequest,
    historyFocusFirstRequest: FocusFirstRequest,
    sectionTabFocusRequesters: Map<BrowseSection, FocusRequester>,
    catalogContentBottomPadding: Dp,
    scheduleContentBottomPadding: Dp,
    isSearching: Boolean,
    isWide: Boolean,
    forcedOfflineMode: Boolean,
    tvTopChromePinned: Boolean,
    phoneScheduleDayGroups: List<ScheduleDayGroup>,
    scheduleSelectedEpochDay: Long,
    scheduleCalendarFocusRequestNonce: Long,
    onScheduleSelectedEpochDayChange: (Long) -> Unit,
    onUpdateHomeBackToTopHandler: (BrowseSection, HomeBackToTopHandler?) -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreAnime: () -> Unit,
    onHorizontalExit: (Int, VisualGridDirection) -> Boolean,
    onRequestSectionTabsFocus: (releasePagerFocusTransition: Boolean) -> Boolean,
    onRequestTopActionsFocus: () -> Boolean,
    onRequestScheduleCalendarFocus: () -> Boolean,
    onClearDownloadHistory: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onResumeDownload: (Long) -> Unit,
    onOpenAnime: (Long) -> Unit,
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
                contentState = catalogContentState,
                pagingState = catalogPagingState,
                gridState = browseCoordinator.catalogGridState,
                cardSize = state.settings.posterCardSize,
                contentBottomPadding = catalogContentBottomPadding,
                focusFirstRequest = catalogFocusFirstRequest,
                pageIndex = pageIndex,
                pageCanReceiveFocus = pageCanReceiveFocus,
                pageFocusCurrentRequestNonce = pageFocusCurrentRequestNonce,
                currentFocusedIndex = { browseCoordinator.focusedIndex(BrowseSection.Catalog) },
                onFocusedIndexChange = { index -> browseCoordinator.setFocusedIndex(BrowseSection.Catalog, index) },
                onRegisterBackToTopHandler = { handler ->
                    onUpdateHomeBackToTopHandler(BrowseSection.Catalog, handler)
                },
                emptyMessage = if (isSearching) uiText(UiStringKey.NothingFound) else uiText(UiStringKey.CatalogIsEmpty),
                onRetry = onRefresh,
                onLoadMore = onLoadMoreAnime,
                onHorizontalExit = onHorizontalExit,
                onRequestSectionTabsFocus = onRequestSectionTabsFocus,
                onRequestTopActionsFocus = onRequestTopActionsFocus,
                sectionTabFocusRequester = sectionTabFocusRequesters[BrowseSection.Catalog],
                isWide = isWide,
                forcedOfflineMode = forcedOfflineMode,
                onOpenAnime = onOpenAnime,
            )
            BrowseSection.Schedule -> ScheduleSection(
                state = state.schedule,
                precomputedDayGroups = if (!isWide && !forcedOfflineMode) phoneScheduleDayGroups else null,
                gridState = browseCoordinator.scheduleGridState,
                cardSize = state.settings.posterCardSize,
                locale = state.settings.contentLanguage.uiLocale(),
                focusFirstRequest = scheduleFocusFirstRequest,
                focusCurrentRequestNonce = pageFocusCurrentRequestNonce,
                calendarFocusRequestNonce = scheduleCalendarFocusRequestNonce,
                contentFocusEnabled = pageCanReceiveFocus,
                showCalendarInGrid = isWide && !forcedOfflineMode,
                selectedEpochDay = scheduleSelectedEpochDay,
                onSelectedEpochDayChange = onScheduleSelectedEpochDayChange,
                currentFocusedIndex = { browseCoordinator.focusedIndex(BrowseSection.Schedule) },
                onFocusedIndexChange = { index -> browseCoordinator.setFocusedIndex(BrowseSection.Schedule, index) },
                pinnedTopPadding = if (tvTopChromePinned) BrowseTvScheduleBlockGap else 0.dp,
                contentBottomPadding = scheduleContentBottomPadding,
                onRegisterBackToTopHandler = { handler ->
                    onUpdateHomeBackToTopHandler(BrowseSection.Schedule, handler)
                },
                onRetry = onRefresh,
                onExitHorizontalDirection = { direction -> onHorizontalExit(pageIndex, direction) },
                onExitUp = if (isWide && !forcedOfflineMode) {
                    { onRequestSectionTabsFocus(true) }
                } else {
                    onRequestTopActionsFocus
                },
                onExitDown = if (isWide && !forcedOfflineMode) {
                    { false }
                } else {
                    onRequestScheduleCalendarFocus
                },
                onOpenAnime = onOpenAnime,
            )
            BrowseSection.History -> BrowseAnimeGridPage(
                section = BrowseSection.History,
                contentState = state.historyAnime,
                pagingState = PagingUiState(canLoadMore = false),
                gridState = browseCoordinator.historyGridState,
                cardSize = state.settings.posterCardSize,
                contentBottomPadding = catalogContentBottomPadding,
                focusFirstRequest = historyFocusFirstRequest,
                pageIndex = pageIndex,
                pageCanReceiveFocus = pageCanReceiveFocus,
                pageFocusCurrentRequestNonce = pageFocusCurrentRequestNonce,
                currentFocusedIndex = { browseCoordinator.focusedIndex(BrowseSection.History) },
                onFocusedIndexChange = { index -> browseCoordinator.setFocusedIndex(BrowseSection.History, index) },
                onRegisterBackToTopHandler = { handler ->
                    onUpdateHomeBackToTopHandler(BrowseSection.History, handler)
                },
                emptyMessage = uiText(UiStringKey.HistoryIsEmpty),
                onRetry = onRefresh,
                onLoadMore = {},
                onHorizontalExit = onHorizontalExit,
                onRequestSectionTabsFocus = onRequestSectionTabsFocus,
                onRequestTopActionsFocus = onRequestTopActionsFocus,
                sectionTabFocusRequester = sectionTabFocusRequesters[BrowseSection.History],
                isWide = isWide,
                forcedOfflineMode = forcedOfflineMode,
                onOpenAnime = onOpenAnime,
            )
            BrowseSection.Downloads -> DownloadsSection(
                state = state,
                focusCurrentRequestNonce = pageFocusCurrentRequestNonce,
                contentBottomPadding = catalogContentBottomPadding,
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
