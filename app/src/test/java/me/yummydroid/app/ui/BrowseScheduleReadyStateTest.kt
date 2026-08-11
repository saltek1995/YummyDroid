package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowseScheduleReadyStateTest {
    @Test
    fun focusedIndexIsKeptInsideVisibleScheduleItems() {
        assertEquals(-1, normalizedScheduleFocusedIndex(itemCount = 0, currentIndex = 3))
        assertEquals(0, normalizedScheduleFocusedIndex(itemCount = 4, currentIndex = -1))
        assertEquals(2, normalizedScheduleFocusedIndex(itemCount = 4, currentIndex = 2))
        assertEquals(3, normalizedScheduleFocusedIndex(itemCount = 4, currentIndex = 7))
    }

    @Test
    fun currentFocusRequestRequiresNewPositiveNonceAndVisibleContent() {
        assertTrue(
            shouldRequestBrowseCurrentFocus(
                contentFocusEnabled = true,
                requestNonce = 2L,
                handledNonce = 1L,
                itemCount = 5,
            ),
        )
        assertFalse(shouldRequestBrowseCurrentFocus(true, 0L, 0L, 5))
        assertFalse(shouldRequestBrowseCurrentFocus(true, 2L, 2L, 5))
        assertFalse(shouldRequestBrowseCurrentFocus(true, 2L, 1L, 0))
        assertFalse(shouldRequestBrowseCurrentFocus(false, 2L, 1L, 5))
    }
}
