package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VisualGridPagingTest {
    @Test
    fun pageMathUsesVisibleGridCapacity() {
        assertEquals(20, visualGridPageSize(columns = 5, rows = 4))
        assertEquals(3, visualGridPageCount(total = 41, pageSize = 20))
        assertEquals(40, visualGridPageStart(page = 2, pageSize = 20, total = 41))
        assertEquals(40, visualGridPageStart(page = 99, pageSize = 20, total = 41))
    }

    @Test
    fun pageMathClampsInvalidDimensionsAndAvoidsIntegerOverflow() {
        assertEquals(1, visualGridPageSize(columns = 0, rows = -1))
        assertEquals(Int.MAX_VALUE, visualGridPageSize(columns = Int.MAX_VALUE, rows = 2))
        assertEquals(1, visualGridPageCount(total = 0, pageSize = 0))
        assertEquals(2, visualGridPageCount(total = Int.MAX_VALUE, pageSize = Int.MAX_VALUE - 1))
        assertEquals(
            Int.MAX_VALUE - 1,
            visualGridPageStart(1, Int.MAX_VALUE - 1, Int.MAX_VALUE),
        )
    }

    @Test
    fun activePageLocalIndexRejectsComposedNeighbourPageOverflow() {
        assertTrue(visualGridActivePageLocalIndex(true, 5, 6))
        assertFalse(visualGridActivePageLocalIndex(true, 6, 6))
        assertFalse(visualGridActivePageLocalIndex(false, 5, 6))
    }

    @Test
    fun horizontalPageTargetKeepsVisualRowAcrossPages() {
        assertEquals(4, visualGridHorizontalPageTarget(0, 20, 20, 5, VisualGridDirection.Left))
        assertEquals(5, visualGridHorizontalPageTarget(9, 20, 20, 5, VisualGridDirection.Right))
    }

    @Test
    fun horizontalPageTargetClampsToShortTargetPageAndRejectsInnerColumns() {
        assertEquals(5, visualGridHorizontalPageTarget(19, 20, 6, 5, VisualGridDirection.Right))
        assertNull(visualGridHorizontalPageTarget(7, 20, 20, 5, VisualGridDirection.Right))
        assertNull(visualGridHorizontalPageTarget(0, 20, 20, 5, VisualGridDirection.Up))
    }
}
