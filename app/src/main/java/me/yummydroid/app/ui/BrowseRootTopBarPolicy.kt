package me.yummydroid.app.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.canHandleRootHomeBackToTop

internal fun browseRootTopBarVisibilityProgress(
    section: BrowseSection,
    collapseDistancePx: Float,
    leadingScrollAnchorItems: Int,
    catalogGridState: LazyGridState,
    scheduleGridState: LazyGridState,
    historyGridState: LazyGridState,
): Float = when (section) {
    BrowseSection.Catalog -> catalogGridState.topBarScrollProgress(collapseDistancePx)
    BrowseSection.Schedule -> scheduleGridState.topBarScrollProgress(collapseDistancePx, leadingScrollAnchorItems)
    BrowseSection.History -> historyGridState.topBarScrollProgress(collapseDistancePx)
    BrowseSection.Downloads -> 1f
}

private fun LazyGridState.topBarScrollProgress(
    collapseDistancePx: Float,
    leadingScrollAnchorItems: Int = 0,
): Float = browseTopBarVisibilityProgress(
    firstVisibleItemIndex = firstVisibleItemIndex,
    firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
    canScrollBackward = canScrollBackward,
    collapseDistancePx = collapseDistancePx,
    leadingScrollAnchorItems = leadingScrollAnchorItems,
)

internal fun browseTopBarVisibilityProgress(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    canScrollBackward: Boolean,
    collapseDistancePx: Float,
    leadingScrollAnchorItems: Int = 0,
): Float {
    if (collapseDistancePx <= 0f) return if (!canScrollBackward) 1f else 0f
    val anchorIndex = leadingScrollAnchorItems.coerceAtLeast(0)
    val consumedPx = when {
        firstVisibleItemIndex < anchorIndex -> 0f
        firstVisibleItemIndex == anchorIndex -> firstVisibleItemScrollOffset.toFloat()
        else -> collapseDistancePx
    }
    return (1f - consumedPx / collapseDistancePx).coerceIn(0f, 1f)
}

internal fun LazyGridState.canHandleBrowseRootBackToTop(section: BrowseSection): Boolean {
    return canScrollBackward ||
        canHandleRootHomeBackToTop(
            isRootHome = true,
            homeSection = section,
            firstVisibleItemIndex = firstVisibleItemIndex,
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
        )
}
