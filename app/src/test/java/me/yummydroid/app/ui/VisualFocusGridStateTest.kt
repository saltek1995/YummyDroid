package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class VisualFocusGridStateTest {
    @Test
    fun requesterAccessStaysInsideConfiguredGridSize() {
        val state = VisualFocusGridState(size = 3)

        assertEquals(3, state.size)
        assertNull(state.requester(-1))
        assertNotNull(state.requester(0))
        assertNotNull(state.requester(2))
        assertNull(state.requester(3))
    }

    @Test
    fun focusUpdatesKeepLastFocusedIndexAfterFocusLeaves() {
        val state = VisualFocusGridState(size = 3)

        state.updateFocusedIndex(index = 1, focused = true)

        assertEquals(1, state.focusedIndex)
        assertEquals(1, state.lastFocusedIndex)

        state.updateFocusedIndex(index = 1, focused = false)

        assertNull(state.focusedIndex)
        assertEquals(1, state.lastFocusedIndex)
    }

    @Test
    fun directionalFallbackUsesAdjacentRequesterBeforeLayout() {
        val state = VisualFocusGridState(size = 3)

        assertSame(
            state.requester(1),
            state.focusTarget(index = 0, direction = VisualGridDirection.Right, exit = null),
        )
        assertNull(state.focusTarget(index = 0, direction = VisualGridDirection.Left, exit = null))
    }
}
