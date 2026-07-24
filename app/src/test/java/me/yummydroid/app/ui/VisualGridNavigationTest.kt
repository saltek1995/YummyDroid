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

    @Test
    fun horizontalPageTargetKeepsVisualRowAcrossPages() {
        assertEquals(
            4,
            visualGridHorizontalPageTarget(
                sourceLocalIndex = 0,
                sourceTotal = 20,
                targetTotal = 20,
                columns = 5,
                direction = VisualGridDirection.Left,
            ),
        )
        assertEquals(
            5,
            visualGridHorizontalPageTarget(
                sourceLocalIndex = 9,
                sourceTotal = 20,
                targetTotal = 20,
                columns = 5,
                direction = VisualGridDirection.Right,
            ),
        )
    }

    @Test
    fun horizontalPageTargetClampsToShortTargetPage() {
        assertEquals(
            5,
            visualGridHorizontalPageTarget(
                sourceLocalIndex = 19,
                sourceTotal = 20,
                targetTotal = 6,
                columns = 5,
                direction = VisualGridDirection.Right,
            ),
        )
        assertNull(
            visualGridHorizontalPageTarget(
                sourceLocalIndex = 7,
                sourceTotal = 20,
                targetTotal = 20,
                columns = 5,
                direction = VisualGridDirection.Right,
            ),
        )
    }

    @Test
    fun visualHorizontalTargetUsesSameVisualRow() {
        val bounds = listOf(
            focusBounds(index = 0, left = 0f, top = 100f, right = 100f, bottom = 180f),
            focusBounds(index = 1, left = 120f, top = 0f, right = 220f, bottom = 80f),
            focusBounds(index = 2, left = 120f, top = 110f, right = 220f, bottom = 190f),
        )

        assertEquals(2, visualFocusDirectionalTarget(bounds, 0, VisualGridDirection.Right))
    }

    @Test
    fun visualHorizontalTargetRejectsRowsWithoutVerticalOverlap() {
        val bounds = listOf(
            focusBounds(index = 0, left = 0f, top = 100f, right = 100f, bottom = 180f),
            focusBounds(index = 1, left = 120f, top = 0f, right = 220f, bottom = 80f),
        )

        assertNull(visualFocusDirectionalTarget(bounds, 0, VisualGridDirection.Right))
    }

    @Test
    fun looseHeroHorizontalTargetUsesNearestVisualNeighborWithoutJumpingToTop() {
        val bounds = listOf(
            focusBounds(index = 0, left = 0f, top = 220f, right = 150f, bottom = 268f),
            focusBounds(index = 1, left = 180f, top = 20f, right = 420f, bottom = 70f),
            focusBounds(index = 2, left = 180f, top = 170f, right = 420f, bottom = 218f),
        )

        assertEquals(
            2,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 0,
                direction = VisualGridDirection.Right,
                allowLoosePerpendicularMatch = true,
            ),
        )
        assertEquals(
            0,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 2,
                direction = VisualGridDirection.Left,
                allowLoosePerpendicularMatch = true,
            ),
        )
    }

    @Test
    fun visualVerticalTargetUsesSameVisualColumn() {
        val bounds = listOf(
            focusBounds(index = 0, left = 100f, top = 0f, right = 180f, bottom = 80f),
            focusBounds(index = 1, left = 0f, top = 100f, right = 80f, bottom = 180f),
            focusBounds(index = 2, left = 110f, top = 100f, right = 190f, bottom = 180f),
        )

        assertEquals(2, visualFocusDirectionalTarget(bounds, 0, VisualGridDirection.Down))
    }

    private fun focusBounds(
        index: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ) = VisualFocusBounds(
        index = index,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
    )
}
