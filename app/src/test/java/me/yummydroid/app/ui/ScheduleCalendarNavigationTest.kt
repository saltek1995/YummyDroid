package me.yummydroid.app.ui

import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleCalendarNavigationTest {
    @Test
    fun focusRequestRequiresEnabledFreshNonceAndDays() {
        assertFalse(shouldHandleScheduleCalendarFocusRequest(false, 2L, 1L, true))
        assertFalse(shouldHandleScheduleCalendarFocusRequest(true, 0L, -1L, true))
        assertFalse(shouldHandleScheduleCalendarFocusRequest(true, 2L, 2L, true))
        assertFalse(shouldHandleScheduleCalendarFocusRequest(true, 2L, 1L, false))
        assertTrue(shouldHandleScheduleCalendarFocusRequest(true, 2L, 1L, true))
    }

    @Test
    fun repeatedHorizontalNavigationStaysInsideCalendarBounds() {
        assertNull(scheduleCalendarTargetDayIndex(itemCount = 0, currentIndex = 0, delta = 1))
        assertEquals(0, scheduleCalendarTargetDayIndex(itemCount = 4, currentIndex = 0, delta = -1))
        assertEquals(1, scheduleCalendarTargetDayIndex(itemCount = 4, currentIndex = 0, delta = 1))
        assertEquals(3, scheduleCalendarTargetDayIndex(itemCount = 4, currentIndex = 3, delta = 1))
        assertEquals(2, scheduleCalendarTargetDayIndex(itemCount = 4, currentIndex = 3, delta = -1))
    }

    @Test
    fun edgeScrollDoesNotMoveWhenTargetIsFullyVisibleAfterMonthSlot() {
        val targetFirstIndex = scheduleCalendarEdgeScrollFirstVisibleIndex(
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 4, offsetPx = 104, sizePx = 96),
                VisibleScheduleCalendarItem(index = 5, offsetPx = 208, sizePx = 96),
            ),
            viewportStartPx = 104,
            viewportEndPx = 600,
            targetIndex = 4,
        )

        assertNull(targetFirstIndex)
    }

    @Test
    fun edgeScrollMovesOnlyWhenTargetIsBehindMonthSlot() {
        assertEquals(
            4,
            scheduleCalendarEdgeScrollFirstVisibleIndex(
                visibleItems = listOf(
                    VisibleScheduleCalendarItem(index = 4, offsetPx = 40, sizePx = 96),
                    VisibleScheduleCalendarItem(index = 5, offsetPx = 144, sizePx = 96),
                ),
                viewportStartPx = 104,
                viewportEndPx = 600,
                targetIndex = 4,
            ),
        )
    }
}
