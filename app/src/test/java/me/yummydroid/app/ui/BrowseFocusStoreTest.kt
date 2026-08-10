package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.BrowseSection

class BrowseFocusStoreTest {
    @Test
    fun sectionDefaultsMatchTheirInitialFocusContracts() {
        val store = BrowseFocusStore()

        assertEquals(-1, store.focusedIndex(BrowseSection.Catalog))
        assertEquals(0, store.focusedIndex(BrowseSection.Schedule))
        assertEquals(-1, store.focusedIndex(BrowseSection.History))
        assertEquals(-1, store.focusedIndex(BrowseSection.Downloads))
    }

    @Test
    fun focusIsStoredIndependentlyForEachNavigableSection() {
        val store = BrowseFocusStore()

        store.setFocusedIndex(BrowseSection.Catalog, 2)
        store.setFocusedIndex(BrowseSection.Schedule, 3)
        store.setFocusedIndex(BrowseSection.History, 4)
        store.setFocusedIndex(BrowseSection.Downloads, 9)

        assertEquals(2, store.focusedIndex(BrowseSection.Catalog))
        assertEquals(3, store.focusedIndex(BrowseSection.Schedule))
        assertEquals(4, store.focusedIndex(BrowseSection.History))
        assertEquals(-1, store.focusedIndex(BrowseSection.Downloads))
    }
}
