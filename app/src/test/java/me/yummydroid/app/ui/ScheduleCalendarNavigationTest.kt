package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleCalendarNavigationTest {
    @Test
    fun visibleTargetDoesNotScroll() {
        val targetFirstIndex = scheduleCalendarEdgeScrollFirstVisibleIndex(
            visibleItems = visibleItems(3, 8),
            viewportStartPx = 0,
            viewportEndPx = 600,
            targetIndex = 5,
        )

        assertNull(targetFirstIndex)
    }

    @Test
    fun targetBeforeVisibleWindowBecomesFirstVisibleItem() {
        val targetFirstIndex = scheduleCalendarEdgeScrollFirstVisibleIndex(
            visibleItems = visibleItems(3, 8),
            viewportStartPx = 0,
            viewportEndPx = 600,
            targetIndex = 2,
        )

        assertEquals(2, targetFirstIndex)
    }

    @Test
    fun targetAfterVisibleWindowBecomesLastVisibleItem() {
        val targetFirstIndex = scheduleCalendarEdgeScrollFirstVisibleIndex(
            visibleItems = visibleItems(3, 8),
            viewportStartPx = 0,
            viewportEndPx = 600,
            targetIndex = 9,
        )

        assertEquals(5, targetFirstIndex)
    }

    @Test
    fun clippedLeftTargetSnapsToWholeTile() {
        val targetFirstIndex = scheduleCalendarEdgeScrollFirstVisibleIndex(
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 3, offsetPx = -20, sizePx = 96),
                VisibleScheduleCalendarItem(index = 4, offsetPx = 86, sizePx = 96),
            ),
            viewportStartPx = 0,
            viewportEndPx = 600,
            targetIndex = 3,
        )

        assertEquals(3, targetFirstIndex)
    }

    @Test
    fun clippedRightTargetSnapsToWholeTile() {
        val targetFirstIndex = scheduleCalendarEdgeScrollFirstVisibleIndex(
            visibleItems = visibleItems(3, 8).map { item ->
                if (item.index == 8) item.copy(offsetPx = 530) else item
            },
            viewportStartPx = 0,
            viewportEndPx = 600,
            targetIndex = 8,
        )

        assertEquals(4, targetFirstIndex)
    }

    @Test
    fun fullyVisibleItemsDropClippedScrollEdges() {
        val stableItems = scheduleCalendarFullyVisibleItems(
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 1, offsetPx = -12, sizePx = 96),
                VisibleScheduleCalendarItem(index = 2, offsetPx = 94, sizePx = 96),
                VisibleScheduleCalendarItem(index = 3, offsetPx = 200, sizePx = 96),
                VisibleScheduleCalendarItem(index = 4, offsetPx = 530, sizePx = 96),
            ),
            viewportStartPx = 0,
            viewportEndPx = 600,
        )

        assertEquals(listOf(2, 3), stableItems.map { item -> item.index })
    }
}
