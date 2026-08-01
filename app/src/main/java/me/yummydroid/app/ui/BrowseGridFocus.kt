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
        val targetRowStart = rowStartIndex(index)
        val visibleIndexes = gridState.layoutInfo.visibleItemsInfo
            .asSequence()
            .map { item -> item.index - leadingGridItemCount }
            .filter { visibleIndex -> visibleIndex in 0 until itemCount }
            .toSet()
        if (targetRowStart == 0 && !gridState.isAtAbsoluteTop()) {
            gridState.scrollToItem(0, 0)
            withFrameNanos { }
        } else if (index !in visibleIndexes) {
            gridState.scrollToItem(scrollIndexForRowStart(targetRowStart), 0)
            withFrameNanos { }
        }
        focusItemAfterLayout(index)
    }

    fun moveFocusTo(index: Int): Boolean {
        if (index !in 0 until itemCount) return false
        focusRequestJob.cancel()
        val verticalMove = rowStartIndex(index) != rowStartIndex(currentFocusedIndex())
        updateFocusedIndex(index)
        if (rowStartIndex(index) == 0 && !gridState.isAtAbsoluteTop()) {
            focusRequestJob.job = focusScope.launch {
                gridState.scrollToItem(0, 0)
                focusItemAfterLayout(index)
            }
            return true
        }
        if (requestItemFocus(index)) {
            focusRequestJob.job = if (verticalMove) {
                focusScope.launch {
                    withFrameNanos { }
                    gridState.centerVisibleGridItem(
                        gridIndex = gridIndex(index),
                        protectedTopPx = protectedTopPx,
                        protectedBottomPx = protectedBottomPx,
                    )
                }
            } else {
                null
            }
            return true
        }
        focusRequestJob.job = focusScope.launch {
            gridState.scrollToItem(scrollIndexForRowStart(rowStartIndex(index)), 0)
            focusItemAfterLayout(index)
            if (verticalMove) {
                withFrameNanos { }
                gridState.centerVisibleGridItem(
                    gridIndex = gridIndex(index),
                    protectedTopPx = protectedTopPx,
                    protectedBottomPx = protectedBottomPx,
                )
            }
        }
        return true
    }

    private fun scrollIndexForRowStart(rowStart: Int): Int {
        return if (rowStart <= 0) 0 else gridIndex(rowStart)
    }
}

internal class FocusRequestJobRef {
    var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
    }
}

private fun LazyGridState.isAtAbsoluteTop(): Boolean {
    return firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
}

private fun focusedGridScrollDurationMillis(deltaPx: Float): Int {
    val distance = abs(deltaPx)
    return when {
        distance >= 900f -> 150
        distance >= 650f -> 120
        else -> (distance / 18f).roundToInt().coerceIn(45, 95)
    }
}

internal suspend fun LazyGridState.centerVisibleGridItem(
    gridIndex: Int,
    protectedTopPx: Float,
    protectedBottomPx: Float,
) {
    val layoutInfo = this.layoutInfo
    val item = layoutInfo.visibleItemsInfo.firstOrNull { visibleItem -> visibleItem.index == gridIndex } ?: return
    val containerHeight = layoutInfo.viewportSize.height.toFloat()
    if (containerHeight <= 0f || item.size.height <= 0) return
    val safeTop = protectedTopPx.coerceIn(0f, containerHeight)
    val safeBottom = (containerHeight - protectedBottomPx.coerceAtLeast(0f)).coerceIn(safeTop, containerHeight)
    val safeHeight = safeBottom - safeTop
    if (safeHeight <= 0f || item.size.height.toFloat() > safeHeight) return

    val itemTop = item.offset.y.toFloat()
    val itemCenter = itemTop + item.size.height / 2f
    val targetCenter = safeTop + safeHeight / 2f
    val scrollDelta = itemCenter - targetCenter
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
