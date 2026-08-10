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
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.PosterCardSize

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
        sectionTabFocusRequester = model.focusBinding.sectionFocusRequesters[BrowseSection.Catalog],
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
        sectionTabFocusRequester = model.focusBinding.sectionFocusRequesters[BrowseSection.History],
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
