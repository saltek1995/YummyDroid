package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowseSearchDialogFocusStateTest {
    @Test
    fun focusStartsOutsideInputMicAndHistory() {
        val state = SearchDialogFocusState()

        assertFalse(state.inputFocused)
        assertFalse(state.micFocused)
        assertEquals(-1, state.focusedHistoryIndex)
    }

    @Test
    fun inputAndMicFocusClearRememberedHistory() {
        val state = SearchDialogFocusState()

        state.setHistoryFocused(3, true)
        state.updateInputFocus(true)
        assertTrue(state.inputFocused)
        assertEquals(-1, state.focusedHistoryIndex)

        state.setHistoryFocused(2, true)
        state.updateMicFocus(true)
        assertTrue(state.micFocused)
        assertEquals(-1, state.focusedHistoryIndex)
    }

    @Test
    fun historyIsClearedOnlyByItsOwnFocusLossOrRemoval() {
        val state = SearchDialogFocusState()

        state.setHistoryFocused(4, true)
        state.setHistoryFocused(2, false)
        assertEquals(4, state.focusedHistoryIndex)

        state.setHistoryFocused(4, false)
        assertEquals(-1, state.focusedHistoryIndex)

        state.setHistoryFocused(3, true)
        state.retainHistoryIndexWithin(3)
        assertEquals(-1, state.focusedHistoryIndex)
    }
}
