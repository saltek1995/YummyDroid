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

    fun topBarVisible(section: BrowseSection): Boolean {
        return browseRootTopBarVisible(
            section = section,
            catalogGridState = catalogGridState,
            scheduleGridState = scheduleGridState,
            historyGridState = historyGridState,
        )
    }

    fun canScrollToTop(section: BrowseSection): Boolean {
        return gridState(section)?.canHandleBrowseRootBackToTop(section) == true
    }

    suspend fun scrollToTop(section: BrowseSection) {
        gridState(section)?.scrollToItem(0, 0)
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

private fun browseRootTopBarVisible(
    section: BrowseSection,
    catalogGridState: LazyGridState,
    scheduleGridState: LazyGridState,
    historyGridState: LazyGridState,
): Boolean {
    return when (section) {
        BrowseSection.Catalog -> catalogGridState.isAtBrowseRootTop()
        BrowseSection.Schedule -> scheduleGridState.isAtBrowseRootTop()
        BrowseSection.History -> historyGridState.isAtBrowseRootTop()
        BrowseSection.Downloads -> true
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

private fun LazyGridState.isAtBrowseRootTop(): Boolean = !canScrollBackward

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
