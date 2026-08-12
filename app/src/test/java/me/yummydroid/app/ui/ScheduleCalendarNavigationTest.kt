package me.yummydroid.app.ui

import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleCalendarNavigationTest {
    @Test
    fun staleCalendarNavigationCannotClearReplacement() {
        val pending = ScheduleCalendarPendingNavigation()
        val staleToken = pending.begin(10L)
        val replacementToken = pending.begin(11L)

        pending.clear(staleToken)

        assertEquals(11L, pending.epochDay)
        assertTrue(pending.owns(replacementToken, 11L))
        pending.clear(replacementToken)
        assertNull(pending.epochDay)
    }

    @Test
    fun calendarNavigationWaitsForOperationAndStateConfirmation() {
        val pending = ScheduleCalendarPendingNavigation()
        val token = pending.begin(10L)

        pending.complete(token)
        assertEquals(10L, pending.epochDay)

        pending.confirm(9L)
        assertEquals(10L, pending.epochDay)

        pending.confirm(10L)
        assertNull(pending.epochDay)
    }

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
    fun accumulatedCalendarNavigationAdvancesOneFocusTargetAtATime() {
        assertEquals(3, scheduleCalendarNextNavigationIndex(currentIndex = 2, targetIndex = 8))
        assertEquals(7, scheduleCalendarNextNavigationIndex(currentIndex = 8, targetIndex = 2))
        assertEquals(5, scheduleCalendarNextNavigationIndex(currentIndex = 5, targetIndex = 5))
    }
}
