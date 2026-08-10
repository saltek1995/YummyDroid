package me.yummydroid.app.ui

import me.yummydroid.app.BrowseSection

internal class BrowseFocusStore {
    private var catalogFocusedIndex: Int = -1
    private var historyFocusedIndex: Int = -1
    private var scheduleFocusedIndex: Int = 0

    fun focusedIndex(section: BrowseSection): Int = when (section) {
        BrowseSection.Catalog -> catalogFocusedIndex
        BrowseSection.Schedule -> scheduleFocusedIndex
        BrowseSection.History -> historyFocusedIndex
        BrowseSection.Downloads -> -1
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
