package me.yummydroid.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal class BrowseGridFocusController(
    private val gridState: LazyGridState,
    private val itemCount: Int,
    private val columns: Int,
    private val leadingGridItemCount: Int,
    private val currentFocusedIndex: () -> Int,
    private val updateFocusedIndex: (Int) -> Unit,
    private val requestItemFocus: (Int) -> Boolean,
    private val protectedTopPx: Float,
    private val protectedBottomPx: Float,
    private val focusScope: CoroutineScope,
    private val focusRequestJob: FocusRequestJobRef,
) {
    fun rowStartIndex(index: Int): Int {
        return if (columns > 0) (index / columns) * columns else index
    }

    fun gridIndex(index: Int): Int = index + leadingGridItemCount

    fun cancelPendingRequest() {
        focusRequestJob.cancel()
    }

    suspend fun focusItemAfterLayout(index: Int) {
        repeat(8) {
            withFrameNanos { }
            if (requestItemFocus(index)) return
        }
    }

    suspend fun focusItemWhenVisible(index: Int) {
        if (index !in 0 until itemCount) return
        gridState.scrollGridItemIntoFocusPosition(index)
        focusItemAfterLayout(index)
    }

    fun moveFocusTo(index: Int): Boolean {
        if (index !in 0 until itemCount) return false
        cancelPendingRequest()
        val sourceIndex = currentFocusedIndex().takeIf { it in 0 until itemCount }
        val verticalMove = sourceIndex == null || rowStartIndex(index) != rowStartIndex(sourceIndex)
        updateFocusedIndex(index)

        if (!verticalMove && requestItemFocus(index)) {
            return true
        }

        focusRequestJob.job = focusScope.launch {
            if (verticalMove) {
                gridState.scrollGridItemIntoFocusPosition(index)
            } else {
                gridState.animateScrollToItemIfNeeded(scrollIndexForRowStart(rowStartIndex(index)), 0)
            }
            focusItemAfterLayout(index)
        }
        return true
    }

    private fun scrollIndexForRowStart(rowStart: Int): Int {
        return if (rowStart <= 0) 0 else gridIndex(rowStart)
    }

    private suspend fun LazyGridState.scrollGridItemIntoFocusPosition(index: Int) {
        val targetRowStart = rowStartIndex(index)
        if (targetRowStart == 0) {
            animateScrollToItemIfNeeded(0, 0)
            withFrameNanos { }
            return
        }

        val targetGridIndex = gridIndex(index)
        if (!centerVisibleGridItem(targetGridIndex)) {
            scrollToItem(scrollIndexForRowStart(targetRowStart), 0)
            withFrameNanos { }
            centerVisibleGridItem(targetGridIndex)
        }
        withFrameNanos { }
    }

    private suspend fun LazyGridState.centerVisibleGridItem(gridIndex: Int): Boolean {
        val item = layoutInfo.visibleItemsInfo.firstOrNull { visibleItem -> visibleItem.index == gridIndex }
            ?: return false
        centerItemAt(
            itemTop = item.offset.y.toFloat() - layoutInfo.viewportStartOffset.toFloat(),
            itemHeight = item.size.height.toFloat(),
        )
        return true
    }

    private suspend fun LazyGridState.centerItemAt(
        itemTop: Float,
        itemHeight: Float,
    ) {
        val scrollDelta = focusedGridScrollDelta(
            itemTop = itemTop,
            itemHeight = itemHeight,
            containerHeight = layoutInfo.viewportSize.height.toFloat(),
            protectedTopPx = protectedTopPx,
            protectedBottomPx = protectedBottomPx,
        )
        if (abs(scrollDelta) > 1f) {
            animateScrollBy(
                value = scrollDelta,
                animationSpec = tween(
                    durationMillis = focusedGridScrollDurationMillis(scrollDelta),
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }
}

internal class FocusRequestJobRef {
    var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
    }
}

private suspend fun LazyGridState.animateScrollToItemIfNeeded(index: Int, scrollOffset: Int) {
    if (firstVisibleItemIndex == index && firstVisibleItemScrollOffset == scrollOffset) return
    animateScrollToItem(index, scrollOffset)
}

private fun focusedGridScrollDurationMillis(deltaPx: Float): Int {
    val distance = abs(deltaPx)
    return when {
        distance >= 900f -> 150
        distance >= 650f -> 120
        else -> (distance / 18f).roundToInt().coerceIn(45, 95)
    }
}

private fun focusedGridScrollDelta(
    itemTop: Float,
    itemHeight: Float,
    containerHeight: Float,
    protectedTopPx: Float,
    protectedBottomPx: Float,
): Float {
    if (containerHeight <= 0f || itemHeight <= 0f) return 0f
    val safeTop = protectedTopPx.coerceIn(0f, containerHeight)
    val safeBottom = (containerHeight - protectedBottomPx.coerceAtLeast(0f)).coerceIn(safeTop, containerHeight)
    val safeHeight = safeBottom - safeTop
    if (safeHeight <= 0f) return 0f

    val itemCenter = itemTop + itemHeight / 2f
    val targetCenter = safeTop + safeHeight / 2f
    return itemCenter - targetCenter
}
