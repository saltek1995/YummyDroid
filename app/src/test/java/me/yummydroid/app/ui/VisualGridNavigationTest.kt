package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun activePageLocalIndexRejectsComposedNeighbourPageOverflow() {
        assertTrue(visualGridActivePageLocalIndex(activePage = true, localIndex = 5, activeTotal = 6))
        assertFalse(visualGridActivePageLocalIndex(activePage = true, localIndex = 6, activeTotal = 6))
        assertFalse(visualGridActivePageLocalIndex(activePage = false, localIndex = 5, activeTotal = 6))
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
    fun looseHeroHorizontalTargetStillRequiresVisualRowOverlap() {
        val bounds = listOf(
            focusBounds(index = 0, left = 0f, top = 220f, right = 150f, bottom = 268f),
            focusBounds(index = 1, left = 180f, top = 20f, right = 420f, bottom = 70f),
            focusBounds(index = 2, left = 180f, top = 170f, right = 420f, bottom = 218f),
        )

        assertNull(
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 0,
                direction = VisualGridDirection.Right,
                allowLoosePerpendicularMatch = true,
            ),
        )
        assertNull(
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

    @Test
    fun looseHeroVerticalTargetUsesNearestVisualLayer() {
        val bounds = listOf(
            focusBounds(index = 0, left = 140f, top = 0f, right = 220f, bottom = 80f),
            focusBounds(index = 1, left = 0f, top = 120f, right = 100f, bottom = 200f),
            focusBounds(index = 2, left = 120f, top = 120f, right = 240f, bottom = 200f),
        )

        assertEquals(
            2,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 0,
                direction = VisualGridDirection.Down,
                allowLoosePerpendicularMatch = true,
            ),
        )
    }

    @Test
    fun crossBlockNavigationEntersTargetBlockFirstItem() {
        val bounds = listOf(
            focusBounds(
                index = 1,
                left = 0f,
                top = 0f,
                right = 80f,
                bottom = 48f,
                blockKey = "marks",
                blockEntryIndex = 1,
            ),
            focusBounds(
                index = 2,
                left = 100f,
                top = 0f,
                right = 180f,
                bottom = 48f,
                blockKey = "marks",
                blockEntryIndex = 1,
            ),
            focusBounds(
                index = 10,
                left = 0f,
                top = 96f,
                right = 80f,
                bottom = 144f,
                blockKey = "screenshots",
                blockEntryIndex = 10,
            ),
            focusBounds(
                index = 11,
                left = 100f,
                top = 96f,
                right = 180f,
                bottom = 144f,
                blockKey = "screenshots",
                blockEntryIndex = 10,
            ),
        )

        assertEquals(
            10,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 2,
                direction = VisualGridDirection.Down,
            ),
        )
        assertEquals(
            1,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 11,
                direction = VisualGridDirection.Up,
            ),
        )
    }

    @Test
    fun sameBlockNavigationKeepsVisualTarget() {
        val bounds = listOf(
            focusBounds(
                index = 1,
                left = 0f,
                top = 0f,
                right = 80f,
                bottom = 48f,
                blockKey = "marks",
                blockEntryIndex = 1,
            ),
            focusBounds(
                index = 2,
                left = 100f,
                top = 0f,
                right = 180f,
                bottom = 48f,
                blockKey = "marks",
                blockEntryIndex = 1,
            ),
        )

        assertEquals(2, visualFocusDirectionalTarget(bounds, 1, VisualGridDirection.Right))
    }

    @Test
    fun crossBlockVerticalTargetUsesPreviousBlockBeforeOverlappingAction() {
        val bounds = listOf(
            focusBounds(
                index = 0,
                left = 0f,
                top = 0f,
                right = 220f,
                bottom = 80f,
                blockKey = "actions",
                blockEntryIndex = 0,
            ),
            focusBounds(
                index = 24,
                left = 360f,
                top = 160f,
                right = 440f,
                bottom = 240f,
                blockKey = "marks",
                blockEntryIndex = 24,
            ),
            focusBounds(
                index = 25,
                left = 460f,
                top = 160f,
                right = 540f,
                bottom = 240f,
                blockKey = "marks",
                blockEntryIndex = 24,
            ),
            focusBounds(
                index = 80,
                left = 0f,
                top = 120f,
                right = 320f,
                bottom = 300f,
                blockKey = "screenshots",
                blockEntryIndex = 80,
            ),
        )

        assertEquals(
            24,
            visualFocusDirectionalTarget(
                bounds = bounds,
                sourceIndex = 80,
                direction = VisualGridDirection.Up,
                allowLoosePerpendicularMatch = true,
            ),
        )
    }

    private fun focusBounds(
        index: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        blockKey: Any? = null,
        blockEntryIndex: Int = index,
    ) = VisualFocusBounds(
        index = index,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        blockKey = blockKey,
        blockEntryIndex = blockEntryIndex,
    )
}
