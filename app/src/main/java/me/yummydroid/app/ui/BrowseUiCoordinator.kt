package me.yummydroid.app.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.canExitRootCatalog
import me.yummydroid.app.canHandleRootHomeBackToTop

@Composable
internal fun rememberBrowseRootUiCoordinator(
    catalogGridState: LazyGridState,
    scheduleGridState: LazyGridState,
    historyGridState: LazyGridState,
): BrowseRootUiCoordinator {
    val focusStore = remember { BrowseFocusStore() }
    return remember(catalogGridState, scheduleGridState, historyGridState, focusStore) {
        BrowseRootUiCoordinator(
            catalogGridState = catalogGridState,
            scheduleGridState = scheduleGridState,
            historyGridState = historyGridState,
            focusStore = focusStore,
        )
    }
}

internal class BrowseRootUiCoordinator(
    val catalogGridState: LazyGridState,
    val scheduleGridState: LazyGridState,
    val historyGridState: LazyGridState,
    private val focusStore: BrowseFocusStore,
) {
    fun gridState(section: BrowseSection): LazyGridState? {
        return section.browseRootGridState(
            catalogGridState = catalogGridState,
            scheduleGridState = scheduleGridState,
            historyGridState = historyGridState,
        )
    }

    fun topBarVisible(section: BrowseSection, leadingScrollAnchorItems: Int = 0): Boolean {
        return topBarVisibilityProgress(
            section = section,
            collapseDistancePx = 1f,
            leadingScrollAnchorItems = leadingScrollAnchorItems,
        ) > 0.999f
    }

    fun topBarVisibilityProgress(
        section: BrowseSection,
        collapseDistancePx: Float,
        leadingScrollAnchorItems: Int = 0,
    ): Float {
        return browseRootTopBarVisibilityProgress(
            section = section,
            collapseDistancePx = collapseDistancePx,
            leadingScrollAnchorItems = leadingScrollAnchorItems,
            catalogGridState = catalogGridState,
            scheduleGridState = scheduleGridState,
            historyGridState = historyGridState,
        )
    }

    fun canScrollToTop(section: BrowseSection): Boolean {
        return gridState(section)?.canHandleBrowseRootBackToTop(section) == true
    }

    suspend fun scrollToTop(section: BrowseSection) {
        gridState(section)?.animateScrollToItem(0, 0)
    }

    fun canExitAppFromBack(section: BrowseSection, settledAtSection: Boolean): Boolean {
        if (!settledAtSection || catalogGridState.canScrollBackward) return false
        return canExitRootCatalog(
            isRootHome = true,
            homeSection = section,
            firstVisibleItemIndex = catalogGridState.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = catalogGridState.firstVisibleItemScrollOffset,
            browsePagerSettledAtStateSection = settledAtSection,
        )
    }

    fun focusedIndex(section: BrowseSection): Int = focusStore.focusedIndex(section)

    fun setFocusedIndex(section: BrowseSection, index: Int) {
        focusStore.setFocusedIndex(section, index)
    }
}

private fun browseRootTopBarVisibilityProgress(
    section: BrowseSection,
    collapseDistancePx: Float,
    leadingScrollAnchorItems: Int,
    catalogGridState: LazyGridState,
    scheduleGridState: LazyGridState,
    historyGridState: LazyGridState,
): Float {
    return when (section) {
        BrowseSection.Catalog -> catalogGridState.topBarScrollProgress(collapseDistancePx)
        BrowseSection.Schedule -> scheduleGridState.topBarScrollProgress(
            collapseDistancePx = collapseDistancePx,
            leadingScrollAnchorItems = leadingScrollAnchorItems,
        )
        BrowseSection.History -> historyGridState.topBarScrollProgress(collapseDistancePx)
        BrowseSection.Downloads -> 1f
    }
}

private fun BrowseSection.browseRootGridState(
    catalogGridState: LazyGridState,
    scheduleGridState: LazyGridState,
    historyGridState: LazyGridState,
): LazyGridState? {
    return when (this) {
        BrowseSection.Catalog -> catalogGridState
        BrowseSection.Schedule -> scheduleGridState
        BrowseSection.History -> historyGridState
        BrowseSection.Downloads -> null
    }
}

private fun LazyGridState.topBarScrollProgress(
    collapseDistancePx: Float,
    leadingScrollAnchorItems: Int = 0,
): Float {
    if (collapseDistancePx <= 0f) {
        return if (!canScrollBackward) 1f else 0f
    }
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

internal class BrowseFocusStore {
    private var catalogFocusedIndex: Int = -1
    private var historyFocusedIndex: Int = -1
    private var scheduleFocusedIndex: Int = 0

    fun focusedIndex(section: BrowseSection): Int {
        return when (section) {
            BrowseSection.Catalog -> catalogFocusedIndex
            BrowseSection.Schedule -> scheduleFocusedIndex
            BrowseSection.History -> historyFocusedIndex
            BrowseSection.Downloads -> -1
        }
    }

    fun setFocusedIndex(section: BrowseSection, index: Int) {
        when (section) {
            BrowseSection.Catalog -> catalogFocusedIndex = index
            BrowseSection.Schedule -> scheduleFocusedIndex = index
            BrowseSection.History -> historyFocusedIndex = index
            BrowseSection.Downloads -> Unit
        }
    }
}
