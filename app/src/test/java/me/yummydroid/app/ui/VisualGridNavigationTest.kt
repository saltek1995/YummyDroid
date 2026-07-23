package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VisualGridNavigationTest {
    @Test
    fun horizontalNavigationDoesNotWrapRows() {
        assertNull(visualGridMoveTarget(4, total = 12, columns = 5, VisualGridDirection.Right))
        assertNull(visualGridMoveTarget(5, total = 12, columns = 5, VisualGridDirection.Left))
        assertEquals(3, visualGridMoveTarget(4, total = 12, columns = 5, VisualGridDirection.Left))
        assertEquals(6, visualGridMoveTarget(5, total = 12, columns = 5, VisualGridDirection.Right))
    }

    @Test
    fun verticalNavigationKeepsVisualColumn() {
        assertEquals(7, visualGridMoveTarget(2, total = 12, columns = 5, VisualGridDirection.Down))
        assertEquals(2, visualGridMoveTarget(7, total = 12, columns = 5, VisualGridDirection.Up))
        assertNull(visualGridMoveTarget(8, total = 12, columns = 5, VisualGridDirection.Down))
    }

    @Test
    fun pageMathUsesVisibleGridCapacity() {
        assertEquals(20, visualGridPageSize(columns = 5, rows = 4))
        assertEquals(3, visualGridPageCount(total = 41, pageSize = 20))
        assertEquals(40, visualGridPageStart(page = 2, pageSize = 20, total = 41))
        assertEquals(40, visualGridPageStart(page = 99, pageSize = 20, total = 41))
    }
}
