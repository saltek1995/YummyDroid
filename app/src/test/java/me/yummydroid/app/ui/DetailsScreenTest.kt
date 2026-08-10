package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetailsScreenTest {
    @Test
    fun screenStateStartsAtTheTopWithCollapsedSections() {
        val state = DetailsScreenUiState()

        assertEquals(0, state.scrollState.value)
        assertFalse(state.relatedExpanded)
        assertFalse(state.subscriptionsExpanded)
        assertFalse(state.commentsExpanded)
        assertNull(state.retainedFocusKey)
        assertFalse(state.suppressInitialFocusOnReactivation)
    }

    @Test
    fun screenStateKeepsSectionAndFocusChanges() {
        val state = DetailsScreenUiState()

        state.relatedExpanded = true
        state.subscriptionsExpanded = true
        state.commentsExpanded = true
        state.retainedFocusKey = "episode-3"
        state.suppressInitialFocusOnReactivation = true

        assertTrue(state.relatedExpanded)
        assertTrue(state.subscriptionsExpanded)
        assertTrue(state.commentsExpanded)
        assertEquals("episode-3", state.retainedFocusKey)
        assertTrue(state.suppressInitialFocusOnReactivation)
    }

    @Test
    fun screenStateInstancesDoNotShareMutableValues() {
        val first = DetailsScreenUiState()
        val second = DetailsScreenUiState()

        first.relatedExpanded = true
        first.retainedFocusKey = 42L

        assertFalse(second.relatedExpanded)
        assertNull(second.retainedFocusKey)
    }
}
