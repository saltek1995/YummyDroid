package me.yummydroid.app.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
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
    private val focusedItemHeightPx: Float? = null,
    private val focusScope: CoroutineScope,
    private val focusRequestJob: FocusRequestJobRef,
) {
    fun rowStartIndex(index: Int): Int {
        return if (columns > 0) (index / columns) * columns else index
    }

    fun gridIndex(index: Int): Int = index + leadingGridItemCount

    fun cancelPendingRequest() {
        focusRequestJob.clearPending()
    }

    suspend fun focusItemAfterLayout(index: Int) {
        repeat(8) {
            withFrameNanos { }
            if (requestItemFocus(index)) return
        }
    }

    suspend fun focusItemWhenVisible(index: Int) {
        if (index !in 0 until itemCount) return
        val focusedImmediately = requestItemFocus(index)
        gridState.requestGridItemIntoFocusPosition(index)
        if (!focusedImmediately) {
            focusItemAfterLayout(index)
        }
    }

    fun moveFocusTo(index: Int): Boolean {
        if (index !in 0 until itemCount) return false
        cancelPendingRequest()
        val sourceIndex = currentFocusedIndex().takeIf { it in 0 until itemCount }
        val verticalMove = sourceIndex == null || rowStartIndex(index) != rowStartIndex(sourceIndex)
        updateFocusedIndex(index)
        val focusedImmediately = requestItemFocus(index)

        if (!verticalMove && focusedImmediately) {
            return true
        }

        if (verticalMove) {
            gridState.requestGridItemIntoFocusPosition(index)
        } else {
            gridState.scrollToItemIfNeeded(scrollIndexForRowStart(rowStartIndex(index)), 0)
        }
        if (!focusedImmediately) {
            focusRequestJob.requestFocusWhenReady(
                index = index,
                focusScope = focusScope,
                requestItemFocus = requestItemFocus,
            )
        }
        return true
    }

    private fun scrollIndexForRowStart(rowStart: Int): Int {
        return if (rowStart <= 0) 0 else gridIndex(rowStart)
    }

    private fun LazyGridState.requestGridItemIntoFocusPosition(index: Int) {
        val targetRowStart = rowStartIndex(index)
        if (targetRowStart == 0) {
            scrollToItemIfNeeded(0, 0)
            return
        }

        val scrollOffset = focusedGridScrollOffsetPx(
            itemHeight = focusedItemHeightPx ?: 0f,
            containerHeight = layoutInfo.viewportSize.height.toFloat(),
            protectedTopPx = protectedTopPx,
            protectedBottomPx = protectedBottomPx,
        )
        scrollToItemIfNeeded(scrollIndexForRowStart(targetRowStart), scrollOffset)
    }
}

internal fun browseGridFocusController(
    gridState: LazyGridState,
    itemFocusRequesters: List<FocusRequester>,
    columns: Int,
    leadingGridItemCount: Int,
    currentFocusedIndex: () -> Int,
    updateFocusedIndex: (Int) -> Unit,
    protectedTopPx: Float,
    protectedBottomPx: Float,
    focusedItemHeightPx: Float,
    focusScope: CoroutineScope,
    focusRequestJob: FocusRequestJobRef,
): BrowseGridFocusController {
    return BrowseGridFocusController(
        gridState = gridState,
        itemCount = itemFocusRequesters.size,
        columns = columns,
        leadingGridItemCount = leadingGridItemCount,
        currentFocusedIndex = currentFocusedIndex,
        updateFocusedIndex = updateFocusedIndex,
        requestItemFocus = itemFocusRequesters::requestBrowseGridItemFocus,
        protectedTopPx = protectedTopPx,
        protectedBottomPx = protectedBottomPx,
        focusedItemHeightPx = focusedItemHeightPx,
        focusScope = focusScope,
        focusRequestJob = focusRequestJob,
    )
}

private fun List<FocusRequester>.requestBrowseGridItemFocus(index: Int): Boolean {
    val requester = getOrNull(index) ?: return false
    return requester.requestFocusSafely()
}

private fun LazyGridState.scrollToItemIfNeeded(index: Int, scrollOffset: Int) {
    if (firstVisibleItemIndex == index && firstVisibleItemScrollOffset == scrollOffset) return
    requestScrollToItem(index, scrollOffset)
}

private fun focusedGridScrollOffsetPx(
    itemHeight: Float,
    containerHeight: Float,
    protectedTopPx: Float,
    protectedBottomPx: Float,
): Int {
    if (containerHeight <= 0f || itemHeight <= 0f) return 0
    val safeTop = protectedTopPx.coerceIn(0f, containerHeight)
    val safeBottom = (containerHeight - protectedBottomPx.coerceAtLeast(0f)).coerceIn(safeTop, containerHeight)
    val safeHeight = safeBottom - safeTop
    if (safeHeight <= 0f) return 0

    val targetItemTop = safeTop + (safeHeight - itemHeight) / 2f
    return (-targetItemTop).roundToInt()
}
