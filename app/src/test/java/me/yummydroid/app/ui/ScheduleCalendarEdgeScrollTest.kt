package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleCalendarEdgeScrollTest {
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
    fun readableTargetAccountsForInsertedMonthSlots() {
        val readableFirstIndex = scheduleCalendarReadableFirstDayIndexForTarget(
            firstDayIndex = 1,
            targetDayIndex = 7,
            dayEntryIndices = intArrayOf(1, 2, 3, 4, 5, 6, 7, 9),
            monthSlotWidthPx = 208f,
            dayTileWidthPx = 192f,
            viewportEndPx = 1680,
        )

        assertEquals(2, readableFirstIndex)
    }

    @Test
    fun readableTargetKeepsFirstDayWhenEntrySpanAlreadyFits() {
        val readableFirstIndex = scheduleCalendarReadableFirstDayIndexForTarget(
            firstDayIndex = 1,
            targetDayIndex = 6,
            dayEntryIndices = intArrayOf(1, 2, 3, 4, 5, 6, 7),
            monthSlotWidthPx = 208f,
            dayTileWidthPx = 192f,
            viewportEndPx = 1680,
        )

        assertEquals(1, readableFirstIndex)
    }
}
