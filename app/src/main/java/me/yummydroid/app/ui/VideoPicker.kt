package me.yummydroid.app.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.key.Key
import kotlinx.coroutines.flow.distinctUntilChanged

// VideoPickerGridFocus
internal class EpisodeGridNavigator(
    private val layout: EpisodeGridLayout,
    private val totalItemCount: Int,
    private val visibleItemCount: Int,
    private val focusGridState: VisualFocusGridState?,
    private val focusIndexOffset: Int,
    private val requestFocusAt: (Int) -> Boolean,
    private val onChangePage: (page: Int, targetLocalIndex: Int?) -> Boolean,
) {
    fun requestFocus(focusSlot: Int): Boolean {
        return requestFocusAt(focusSlot)
    }

    fun handlePagerControlDirection(focusSlot: Int, key: Key): Boolean {
        return handleManagedDpadNavigationKey(
            key = key,
            ownsDirection = { direction ->
                direction == VisualGridDirection.Left || direction == VisualGridDirection.Right
            },
        ) { direction ->
            val target = when {
                focusSlot == EpisodePreviousPageFocusSlot &&
                    direction == VisualGridDirection.Right &&
                    layout.normalizedPage < layout.pageCount - 1 -> EpisodeNextPageFocusSlot
                focusSlot == EpisodeNextPageFocusSlot &&
                    direction == VisualGridDirection.Left &&
                    layout.normalizedPage > 0 -> EpisodePreviousPageFocusSlot
                else -> null
            }
            if (target != null) requestFocus(target)
        }
    }

    fun handleDirection(localIndex: Int, key: Key): Boolean {
        return handleManagedDpadNavigationKey(key) { direction ->
            val target = visualGridMoveTarget(
                index = localIndex,
                total = visibleItemCount,
                columns = layout.columns,
                direction = direction,
            )
            when {
                target != null -> requestFocus(target)
                direction == VisualGridDirection.Left || direction == VisualGridDirection.Right -> {
                    changePageFromEdge(localIndex, direction)
                }
                else -> focusGridState?.requestFocusTarget(
                    index = focusIndexOffset + localIndex,
                    direction = direction,
                    exit = null,
                )
            }
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
    pendingFocusSlot: Int?,
    visibleItemCount: Int,
    navigator: EpisodeGridNavigator,
    onRequestedPageChange: (Int) -> Unit,
    onPagerSettled: (Int) -> Unit,
    onPendingFocusHandled: () -> Unit,
) {
    EpisodeGridPageAlignmentEffect(
        requestedPage = requestedPage,
        layout = layout,
        pagerState = pagerState,
        onRequestedPageChange = onRequestedPageChange,
    )
    EpisodeGridFocusRestoreEffect(
        layout = layout,
        pagerState = pagerState,
        pendingFocusSlot = pendingFocusSlot,
        visibleItemCount = visibleItemCount,
        navigator = navigator,
        onPendingFocusHandled = onPendingFocusHandled,
    )
    EpisodeGridSettledEffect(
        requestedPage = requestedPage,
        pageCount = layout.pageCount,
        pagerState = pagerState,
        onPagerSettled = onPagerSettled,
    )
}

@Composable
private fun EpisodeGridPageAlignmentEffect(
    requestedPage: Int,
    layout: EpisodeGridLayout,
    pagerState: PagerState,
    onRequestedPageChange: (Int) -> Unit,
) {
    LaunchedEffect(layout.normalizedPage, requestedPage) {
        if (requestedPage != layout.normalizedPage) {
            onRequestedPageChange(layout.normalizedPage)
        }
    }
    UiControlEffect(
        layout.normalizedPage,
        layout.pageCount,
        operation = UiControlOperation.PageTransitionLatest,
    ) {
        if (
            pagerState.currentPage != layout.normalizedPage ||
            pagerState.currentPageOffsetFraction != 0f
        ) {
            pagerState.animateScrollToPage(layout.normalizedPage)
        }
    }
}

@Composable
private fun EpisodeGridFocusRestoreEffect(
    layout: EpisodeGridLayout,
    pagerState: PagerState,
    pendingFocusSlot: Int?,
    visibleItemCount: Int,
    navigator: EpisodeGridNavigator,
    onPendingFocusHandled: () -> Unit,
) {
    val canRestoreFocus = shouldRestoreEpisodeGridFocus(
        pendingFocusSlot = pendingFocusSlot,
        targetPage = layout.normalizedPage,
        settledPage = pagerState.settledPage,
        scrollInProgress = pagerState.isScrollInProgress,
    )
    UiControlEffect(
        layout.normalizedPage,
        pendingFocusSlot,
        visibleItemCount,
        pagerState.settledPage,
        pagerState.isScrollInProgress,
        enabled = canRestoreFocus,
    ) {
        val targetFocusSlot = pendingFocusSlot ?: return@UiControlEffect
        repeat(6) {
            withFrameNanos { }
            if (navigator.requestFocus(targetFocusSlot)) {
                onPendingFocusHandled()
                return@UiControlEffect
            }
        }
        onPendingFocusHandled()
    }
}

@Composable
private fun EpisodeGridSettledEffect(
    requestedPage: Int,
    pageCount: Int,
    pagerState: PagerState,
    onPagerSettled: (Int) -> Unit,
) {
    val latestRequestedPage by rememberUpdatedState(requestedPage)
    LaunchedEffect(pagerState, pageCount) {
        snapshotFlow { pagerState.settledPage.coerceIn(0, pageCount - 1) }
            .distinctUntilChanged()
            .collect { page ->
                if (page != latestRequestedPage) {
                    onPagerSettled(page)
                }
            }
    }
}

internal fun shouldRestoreEpisodeGridFocus(
    pendingFocusSlot: Int?,
    targetPage: Int,
    settledPage: Int,
    scrollInProgress: Boolean,
): Boolean {
    return pendingFocusSlot != null && settledPage == targetPage && !scrollInProgress
}
