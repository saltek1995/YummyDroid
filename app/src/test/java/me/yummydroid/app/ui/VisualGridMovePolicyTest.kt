package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VisualGridMovePolicyTest {
    @Test
    fun horizontalNavigationDoesNotWrapRows() {
        assertNull(visualGridMoveTarget(4, 12, 5, VisualGridDirection.Right))
        assertNull(visualGridMoveTarget(5, 12, 5, VisualGridDirection.Left))
        assertEquals(3, visualGridMoveTarget(4, 12, 5, VisualGridDirection.Left))
        assertEquals(6, visualGridMoveTarget(5, 12, 5, VisualGridDirection.Right))
    }

    @Test
    fun verticalNavigationKeepsVisualColumn() {
        assertEquals(7, visualGridMoveTarget(2, 12, 5, VisualGridDirection.Down))
        assertEquals(2, visualGridMoveTarget(7, 12, 5, VisualGridDirection.Up))
        assertNull(visualGridMoveTarget(8, 12, 5, VisualGridDirection.Down))
    }

    @Test
    fun invalidGridInputsHaveNoTarget() {
        assertNull(visualGridMoveTarget(-1, 6, 3, VisualGridDirection.Right))
        assertNull(visualGridMoveTarget(6, 6, 3, VisualGridDirection.Left))
        assertNull(visualGridMoveTarget(0, 0, 3, VisualGridDirection.Down))
        assertNull(visualGridMoveTarget(0, 6, 0, VisualGridDirection.Down))
    }
}
