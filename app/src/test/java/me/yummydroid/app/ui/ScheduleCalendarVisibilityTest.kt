package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleCalendarVisibilityTest {
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
