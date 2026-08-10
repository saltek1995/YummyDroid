package me.yummydroid.app.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.canExitRootCatalog

@Composable
internal fun rememberBrowseRootUiCoordinator(
    catalogGridState: LazyGridState,
    scheduleGridState: LazyGridState,
    historyGridState: LazyGridState,
): BrowseRootUiCoordinator {
    val focusStore = remember { BrowseFocusStore() }
    return remember(catalogGridState, scheduleGridState, historyGridState, focusStore) {
        BrowseRootUiCoordinator(catalogGridState, scheduleGridState, historyGridState, focusStore)
    }
}

internal class BrowseRootUiCoordinator(
    val catalogGridState: LazyGridState,
    val scheduleGridState: LazyGridState,
    val historyGridState: LazyGridState,
    private val focusStore: BrowseFocusStore,
) {
    fun gridState(section: BrowseSection): LazyGridState? = when (section) {
        BrowseSection.Catalog -> catalogGridState
        BrowseSection.Schedule -> scheduleGridState
        BrowseSection.History -> historyGridState
        BrowseSection.Downloads -> null
    }

    fun topBarVisible(section: BrowseSection, leadingScrollAnchorItems: Int = 0): Boolean {
        return topBarVisibilityProgress(section, 1f, leadingScrollAnchorItems) > 0.999f
    }

    fun topBarVisibilityProgress(
        section: BrowseSection,
        collapseDistancePx: Float,
        leadingScrollAnchorItems: Int = 0,
    ): Float = browseRootTopBarVisibilityProgress(
        section = section,
        collapseDistancePx = collapseDistancePx,
        leadingScrollAnchorItems = leadingScrollAnchorItems,
        catalogGridState = catalogGridState,
        scheduleGridState = scheduleGridState,
        historyGridState = historyGridState,
    )

    fun canScrollToTop(section: BrowseSection): Boolean =
        gridState(section)?.canHandleBrowseRootBackToTop(section) == true

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

    fun setFocusedIndex(section: BrowseSection, index: Int) = focusStore.setFocusedIndex(section, index)
}
