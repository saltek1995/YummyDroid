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
}
