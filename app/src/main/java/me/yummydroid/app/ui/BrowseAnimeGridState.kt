package me.yummydroid.app.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.LoadState
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.PosterCardSize

internal data class AnimeGridParams(
    val contentState: LoadState<List<Anime>>,
    val pagingState: PagingUiState,
    val gridState: LazyGridState,
    val cardSize: PosterCardSize,
    val contentTopPadding: Dp,
    val contentBottomPadding: Dp,
    val focusFirstRequest: FocusFirstRequest,
    val focusCurrentRequestNonce: Long,
    val contentFocusEnabled: Boolean,
    val currentFocusedIndex: () -> Int,
    val onFocusedIndexChange: (Int) -> Unit,
    val backToTopSection: BrowseSection,
    val onRegisterBackToTopHandler: ((HomeBackToTopHandler?) -> Unit)?,
    val emptyMessage: String,
    val onRetry: () -> Unit,
    val onLoadMore: () -> Unit,
    val onExitHorizontalDirection: (VisualGridDirection) -> Boolean,
    val onExitUp: () -> Boolean,
    val exitUpFocusRequester: FocusRequester?,
    val onExitDown: () -> Boolean,
    val onOpenAnime: (Long) -> Unit,
)

internal data class AnimeGridLayout(
    val columnsCount: Int,
    val touchOverscrollEnabled: Boolean,
    val horizontalPadding: Dp,
    val topContentPadding: Dp,
    val bottomContentPadding: Dp,
    val itemFocusRequesters: List<FocusRequester>,
    val focusedTopInsetPx: Float,
    val focusedBottomInsetPx: Float,
    val focusedItemHeightPx: Float,
)

@Composable
internal fun rememberAnimeGridLayout(
    params: AnimeGridParams,
    itemCount: Int,
    maxWidth: Dp,
    maxHeight: Dp,
): AnimeGridLayout {
    val responsiveWidth = currentResponsiveWindowSizeDp().width
    val columnsCount = remember(maxWidth, params.cardSize) {
        params.cardSize.resolveCatalogColumns(maxWidth.value.roundToInt())
    }
    val density = LocalDensity.current
    val horizontalPadding = browseGridHorizontalContentPadding(responsiveWidth)
    val focusedTopInset = browseGridFocusedCardTopInset(params.contentTopPadding, responsiveWidth)
    val focusedBottomInset = BrowseFocusedCardBottomGap + params.contentBottomPadding
    val baseBottomPadding = if (params.contentBottomPadding > 0.dp) {
        focusedBottomInset
    } else {
        24.dp + BrowseFocusedCardBottomGap
    }
    val itemFocusRequesters = remember(params.backToTopSection, itemCount, columnsCount) {
        List(itemCount) { FocusRequester() }
    }
    return AnimeGridLayout(
        columnsCount = columnsCount,
        touchOverscrollEnabled = LocalInputModeManager.current.inputMode == InputMode.Touch,
        horizontalPadding = horizontalPadding,
        topContentPadding = BrowseGridTopContentPadding + params.contentTopPadding,
        bottomContentPadding = browseGridFocusedCardBottomPadding(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            columns = columnsCount,
            horizontalPadding = horizontalPadding,
            topInset = focusedTopInset,
            bottomInset = focusedBottomInset,
            basePadding = baseBottomPadding,
        ),
        itemFocusRequesters = itemFocusRequesters,
        focusedTopInsetPx = with(density) { focusedTopInset.toPx() },
        focusedBottomInsetPx = with(density) { focusedBottomInset.toPx() },
        focusedItemHeightPx = with(density) {
            browseGridItemHeight(
                maxWidth = maxWidth,
                columns = columnsCount,
                horizontalPadding = horizontalPadding,
            ).toPx()
        },
    )
}

internal class AnimeGridActions(
    private val params: AnimeGridParams,
    private val animes: List<Anime>,
    private val layout: AnimeGridLayout,
    private val focusController: BrowseGridFocusController,
    private val focusScope: CoroutineScope,
    private val maybeLoadMore: (Int) -> Unit,
    private val updateFocused: (Int) -> Unit,
) {
    fun maybeLoadMoreNear(index: Int) {
        maybeLoadMore(index)
    }

    fun updateFocusedIndex(index: Int) {
        updateFocused(index)
    }

    fun handleGridDirection(index: Int, key: Key): Boolean {
        return handleVisualGridNavigationKey(
            key = key,
            itemCount = animes.size,
            columns = layout.columnsCount,
            currentFocusedIndex = params.currentFocusedIndex(),
            fallbackIndex = index,
            moveFocusTo = focusController::moveFocusTo,
            onEdgeExit = ::handleGridEdgeExit,
        )
    }

    private fun handleGridEdgeExit(direction: VisualGridDirection): Boolean {
        if (direction == VisualGridDirection.Up && params.exitUpFocusRequester != null) return false
        if (direction == VisualGridDirection.Down && params.pagingState.canLoadMore && !params.pagingState.isLoadingMore) {
            params.onLoadMore()
        }
        return when (direction) {
            VisualGridDirection.Left,
            VisualGridDirection.Right -> params.onExitHorizontalDirection(direction)
            VisualGridDirection.Down -> params.onExitDown()
            VisualGridDirection.Up -> params.onExitUp()
        }
    }

    fun canHandleBackToTop(): Boolean {
        return params.gridState.canHandleBrowseRootBackToTop(params.backToTopSection)
    }

    fun handleBackToTop(withFocus: Boolean): Boolean {
        if (!canHandleBackToTop()) return false
        focusController.cancelPendingRequest()
        if (withFocus && animes.isNotEmpty()) return focusController.moveFocusTo(0)
        focusScope.launch { params.gridState.animateScrollToItem(0, 0) }
        return true
    }
}

internal fun shouldLoadMoreNearBrowseIndex(
    index: Int,
    itemCount: Int,
    columnsCount: Int,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    hasError: Boolean,
    lastRequestItemCount: Int,
): Boolean {
    if (index < 0 || columnsCount <= 0 || itemCount <= 0) return false
    if (!canLoadMore || isLoadingMore || hasError || lastRequestItemCount == itemCount) return false
    val focusedRow = index / columnsCount
    val lastLoadedRow = (itemCount - 1) / columnsCount
    return lastLoadedRow - focusedRow < 2
}

internal fun boundedAnimeFocusedIndexUpdate(itemCount: Int, currentIndex: Int): Int? {
    return when {
        itemCount <= 0 -> -1
        currentIndex >= itemCount -> itemCount - 1
        else -> null
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
