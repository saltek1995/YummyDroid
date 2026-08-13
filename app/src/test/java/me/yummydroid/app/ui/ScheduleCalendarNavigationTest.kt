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
    fun rightNavigationAnchorsTargetAtReadableRightEdge() {
        val anchor = scheduleCalendarTargetScrollAnchor(
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 20, offsetPx = 544, sizePx = 96),
                VisibleScheduleCalendarItem(index = 21, offsetPx = 648, sizePx = 96),
                VisibleScheduleCalendarItem(index = 22, offsetPx = 936, sizePx = 96),
            ),
            viewportStartPx = 232,
            viewportEndPx = 892,
            targetIndex = 22,
            direction = 1,
        )

        assertEquals(ScheduleCalendarScrollAnchor.End, anchor)
        assertEquals(
            -796,
            scheduleCalendarTargetScrollOffsetPx(
                anchor = requireNotNull(anchor),
                viewportStartPx = 232,
                viewportEndPx = 892,
                dayTileWidthPx = 96f,
            ),
        )
    }

    @Test
    fun leftNavigationAnchorsTargetAtReadableLeftEdge() {
        val anchor = scheduleCalendarTargetScrollAnchor(
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 20, offsetPx = 128, sizePx = 96),
                VisibleScheduleCalendarItem(index = 21, offsetPx = 232, sizePx = 96),
            ),
            viewportStartPx = 232,
            viewportEndPx = 892,
            targetIndex = 20,
            direction = -1,
        )

        assertEquals(ScheduleCalendarScrollAnchor.Start, anchor)
        assertEquals(
            -232,
            scheduleCalendarTargetScrollOffsetPx(
                anchor = requireNotNull(anchor),
                viewportStartPx = 232,
                viewportEndPx = 892,
                dayTileWidthPx = 96f,
            ),
        )
    }

    @Test
    fun readableTargetDoesNotRequestScroll() {
        val anchor = scheduleCalendarTargetScrollAnchor(
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 20, offsetPx = 336, sizePx = 96),
                VisibleScheduleCalendarItem(index = 21, offsetPx = 440, sizePx = 96),
            ),
            viewportStartPx = 232,
            viewportEndPx = 892,
            targetIndex = 21,
            direction = 1,
        )

        assertNull(anchor)
    }
}
