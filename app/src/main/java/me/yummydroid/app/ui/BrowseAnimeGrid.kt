package me.yummydroid.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.LoadState
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.PosterCardSize

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
        val responsiveWidth = currentResponsiveWindowSizeDp().width
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
        val gridHorizontalPadding = browseGridHorizontalContentPadding(responsiveWidth)
        val focusedGridItemHeightPx = with(density) {
            browseGridItemHeight(
                maxWidth = maxWidth,
                columns = columnsCount,
                horizontalPadding = gridHorizontalPadding,
            ).toPx()
        }
        val focusedGridTopInset = browseGridFocusedCardTopInset(contentTopPadding, responsiveWidth)
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
        var retainedFocusedIndexOnOpen by remember(backToTopSection) { mutableIntStateOf(-1) }
        val focusRequestJob = remember(backToTopSection, columnsCount) { FocusRequestJobRef() }

        fun currentFocusedAnimeIndex(): Int = currentFocusedIndex()

        fun focusUpdateBlocked(): Boolean = browseGridFocusUpdateBlocked(
            retainedIndexOnOpen = retainedFocusedIndexOnOpen,
            contentFocusEnabled = contentFocusEnabled,
            requestNonce = focusCurrentRequestNonce,
            handledRequestNonce = handledCurrentFocusRequestNonce,
        )

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

        val focusController = browseGridFocusController(
            gridState = gridState,
            itemFocusRequesters = itemFocusRequesters,
            columns = columnsCount,
            leadingGridItemCount = 0,
            currentFocusedIndex = ::currentFocusedAnimeIndex,
            updateFocusedIndex = ::updateFocusedAnimeIndex,
            protectedTopPx = focusedGridTopInsetPx,
            protectedBottomPx = focusedGridBottomInsetPx,
            focusedItemHeightPx = focusedGridItemHeightPx,
            focusScope = focusScope,
            focusRequestJob = focusRequestJob,
        )

        fun handleAnimeGridDirection(index: Int, key: Key): Boolean {
            return handleVisualGridNavigationKey(
                key = key,
                itemCount = animes.size,
                columns = columnsCount,
                currentFocusedIndex = currentFocusedAnimeIndex(),
                fallbackIndex = index,
                moveFocusTo = focusController::moveFocusTo,
                onEdgeExit = { direction ->
                    if (direction == VisualGridDirection.Up && exitUpFocusRequester != null) {
                        false
                    } else {
                        if (direction == VisualGridDirection.Down && pagingState.canLoadMore && !pagingState.isLoadingMore) {
                            onLoadMore()
                        }
                        when (direction) {
                            VisualGridDirection.Left,
                            VisualGridDirection.Right -> onExitHorizontalDirection(direction)
                            VisualGridDirection.Down -> onExitDown()
                            VisualGridDirection.Up -> onExitUp()
                        }
                    }
                },
            )
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
            val retainedIndex = preferredBrowseGridRestoreIndex(
                retainedIndexOnOpen = retainedFocusedIndexOnOpen,
                currentFocusedIndex = currentFocusedAnimeIndex(),
                itemCount = animes.size,
            )
            withFrameNanos { }
            val visibleIndexes = gridState.layoutInfo.visibleItemsInfo
                .asSequence()
                .map { item -> item.index }
                .filter { index -> index in animes.indices }
                .toList()
            val targetIndex = retainedIndex
                ?: visibleIndexes.minOrNull()
                ?: gridState.firstVisibleItemIndex.coerceIn(0, animes.lastIndex)
            updateFocusedAnimeIndex(targetIndex)
            focusController.focusItemWhenVisible(targetIndex)
            handledCurrentFocusRequestNonce = focusCurrentRequestNonce
            retainedFocusedIndexOnOpen = -1
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
                        onClick = {
                            updateFocusedAnimeIndex(index)
                            retainedFocusedIndexOnOpen = index
                            onOpenAnime(anime.id)
                        },
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
                                if (focusState.hasFocus && !focusUpdateBlocked()) {
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

internal fun browseGridFocusUpdateBlocked(
    retainedIndexOnOpen: Int,
    contentFocusEnabled: Boolean,
    requestNonce: Long,
    handledRequestNonce: Long,
): Boolean {
    return retainedIndexOnOpen >= 0 ||
        (contentFocusEnabled && requestNonce > 0L && requestNonce != handledRequestNonce)
}

internal fun preferredBrowseGridRestoreIndex(
    retainedIndexOnOpen: Int,
    currentFocusedIndex: Int,
    itemCount: Int,
): Int? {
    return retainedIndexOnOpen.takeIf { it in 0 until itemCount }
        ?: currentFocusedIndex.takeIf { it in 0 until itemCount }
}
