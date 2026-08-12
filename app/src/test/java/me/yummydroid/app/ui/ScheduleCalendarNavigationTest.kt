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
    fun repeatedNavigationAlwaysReachesBothCalendarEdges() {
        repeat(10) {
            var index = 0
            var firstVisibleIndex = 0
            repeat(200) {
                index = scheduleCalendarTargetDayIndex(61, index, 1)!!
                firstVisibleIndex = scheduleCalendarWindowFirstIndex(
                    itemCount = 61,
                    currentFirstIndex = firstVisibleIndex,
                    visibleCapacity = 8,
                    targetIndex = index,
                )
            }
            assertEquals(60, index)
            assertEquals(53, firstVisibleIndex)
            repeat(200) {
                index = scheduleCalendarTargetDayIndex(61, index, -1)!!
                firstVisibleIndex = scheduleCalendarWindowFirstIndex(
                    itemCount = 61,
                    currentFirstIndex = firstVisibleIndex,
                    visibleCapacity = 8,
                    targetIndex = index,
                )
            }
            assertEquals(0, index)
            assertEquals(0, firstVisibleIndex)
        }
    }

    @Test
    fun calendarWindowOnlyMovesWhenTargetLeavesItsCurrentRange() {
        assertEquals(4, scheduleCalendarWindowFirstIndex(20, 4, 5, 4))
        assertEquals(4, scheduleCalendarWindowFirstIndex(20, 4, 5, 8))
        assertEquals(5, scheduleCalendarWindowFirstIndex(20, 4, 5, 9))
        assertEquals(3, scheduleCalendarWindowFirstIndex(20, 4, 5, 3))
        assertEquals(15, scheduleCalendarWindowFirstIndex(20, 15, 5, 19))
    }
}
