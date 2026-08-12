package me.yummydroid.app.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.flow.distinctUntilChanged

// VideoPickerGridFocus
internal class EpisodeGridNavigator(
    private val layout: EpisodeGridLayout,
    private val totalItemCount: Int,
    private val visibleItemCount: Int,
    private val focusGridState: VisualFocusGridState?,
    private val focusIndexOffset: Int,
    private val focusRequesterAt: (Int) -> FocusRequester?,
    private val onChangePage: (page: Int, targetLocalIndex: Int?) -> Boolean,
) {
    fun requestFocus(localIndex: Int): Boolean {
        return focusRequesterAt(localIndex)?.requestFocusSafely() ?: false
    }

    fun handleDirection(localIndex: Int, key: Key): Boolean {
        val direction = key.visualGridDirection() ?: return false
        val target = visualGridMoveTarget(
            index = localIndex,
            total = visibleItemCount,
            columns = layout.columns,
            direction = direction,
        )
        if (target != null) {
            return requestFocus(target)
        }
        return when (direction) {
            VisualGridDirection.Left,
            VisualGridDirection.Right -> changePageFromEdge(localIndex, direction)
            VisualGridDirection.Up,
            VisualGridDirection.Down -> focusGridState?.requestFocusTarget(
                index = focusIndexOffset + localIndex,
                direction = direction,
                exit = null,
            ) ?: false
        }
    }

    private fun changePageFromEdge(
        localIndex: Int,
        direction: VisualGridDirection,
    ): Boolean {
        val targetPage = layout.normalizedPage + if (direction == VisualGridDirection.Right) 1 else -1
        if (targetPage !in 0 until layout.pageCount) return false
        val targetLocalIndex = visualGridHorizontalPageTarget(
            sourceLocalIndex = localIndex,
            sourceTotal = visibleItemCount,
            targetTotal = layout.itemCount(targetPage, total = totalItemCount),
            columns = layout.columns,
            direction = direction,
        )
        return onChangePage(targetPage, targetLocalIndex)
    }
}

@Composable
internal fun EpisodeGridEffects(
    requestedPage: Int,
    layout: EpisodeGridLayout,
    pagerState: PagerState,
    pendingFocusIndex: Int?,
    visibleItemCount: Int,
    navigator: EpisodeGridNavigator,
    onRequestedPageChange: (Int) -> Unit,
    onPagerSettled: (Int) -> Unit,
    onPendingFocusHandled: () -> Unit,
) {
    val latestRequestedPage by rememberUpdatedState(requestedPage)

    LaunchedEffect(layout.normalizedPage, requestedPage) {
        if (requestedPage != layout.normalizedPage) {
            onRequestedPageChange(layout.normalizedPage)
        }
    }
    val needsPageAlignment = pagerState.currentPage != layout.normalizedPage
    val needsFocusRestore = pendingFocusIndex != null
    UiControlEffect(
        layout.normalizedPage,
        layout.pageCount,
        pendingFocusIndex,
        visibleItemCount,
        enabled = needsPageAlignment || needsFocusRestore,
    ) {
        if (pagerState.currentPage != layout.normalizedPage) {
            pagerState.animateScrollToPage(layout.normalizedPage)
        }
        val targetIndex = pendingFocusIndex ?: return@UiControlEffect
        repeat(6) {
            withFrameNanos { }
            if (navigator.requestFocus(targetIndex)) {
                onPendingFocusHandled()
                return@UiControlEffect
            }
        }
        onPendingFocusHandled()
    }
    LaunchedEffect(pagerState, layout.pageCount) {
        snapshotFlow { pagerState.settledPage.coerceIn(0, layout.pageCount - 1) }
            .distinctUntilChanged()
            .collect { page ->
                if (page != latestRequestedPage) {
                    onPagerSettled(page)
                }
            }
    }
}

private fun Key.visualGridDirection(): VisualGridDirection? = when (this) {
    Key.DirectionLeft -> VisualGridDirection.Left
    Key.DirectionRight -> VisualGridDirection.Right
    Key.DirectionUp -> VisualGridDirection.Up
    Key.DirectionDown -> VisualGridDirection.Down
    else -> null
}
