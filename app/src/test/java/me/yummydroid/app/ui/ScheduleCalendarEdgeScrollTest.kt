package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleCalendarEdgeScrollTest {
    @Test
    fun readableTargetDoesNotScroll() {
        val anchor = scheduleCalendarTargetScrollAnchor(
            visibleItems = visibleItems(3, 8),
            viewportStartPx = 0,
            viewportEndPx = 600,
            targetIndex = 5,
            direction = 1,
        )

        assertNull(anchor)
    }

    @Test
    fun targetBeforeVisibleWindowAnchorsToStart() {
        val anchor = scheduleCalendarTargetScrollAnchor(
            visibleItems = visibleItems(3, 8),
            viewportStartPx = 0,
            viewportEndPx = 600,
            targetIndex = 2,
            direction = -1,
        )

        assertEquals(ScheduleCalendarScrollAnchor.Start, anchor)
    }

    @Test
    fun targetAfterVisibleWindowAnchorsToEnd() {
        val anchor = scheduleCalendarTargetScrollAnchor(
            visibleItems = visibleItems(3, 8),
            viewportStartPx = 0,
            viewportEndPx = 600,
            targetIndex = 9,
            direction = 1,
        )

        assertEquals(ScheduleCalendarScrollAnchor.End, anchor)
    }

    @Test
    fun clippedLeftTargetAnchorsToStart() {
        val anchor = scheduleCalendarTargetScrollAnchor(
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 3, offsetPx = -20, sizePx = 96),
                VisibleScheduleCalendarItem(index = 4, offsetPx = 86, sizePx = 96),
            ),
            viewportStartPx = 0,
            viewportEndPx = 600,
            targetIndex = 3,
            direction = -1,
        )

        assertEquals(ScheduleCalendarScrollAnchor.Start, anchor)
    }

    @Test
    fun clippedRightTargetAnchorsToEnd() {
        val anchor = scheduleCalendarTargetScrollAnchor(
            visibleItems = visibleItems(3, 8).map { item ->
                if (item.index == 8) item.copy(offsetPx = 530) else item
            },
            viewportStartPx = 0,
            viewportEndPx = 600,
            targetIndex = 8,
            direction = 1,
        )

        assertEquals(ScheduleCalendarScrollAnchor.End, anchor)
    }

    @Test
    fun endAnchorPlacesTargetAtReadableRightEdge() {
        val offsetPx = scheduleCalendarTargetScrollOffsetPx(
            anchor = ScheduleCalendarScrollAnchor.End,
            viewportStartPx = 232,
            viewportEndPx = 892,
            dayTileWidthPx = 96f,
        )

        assertEquals(-796, offsetPx)
    }

    @Test
    fun startAnchorPlacesTargetAtReadableLeftEdge() {
        val offsetPx = scheduleCalendarTargetScrollOffsetPx(
            anchor = ScheduleCalendarScrollAnchor.Start,
            viewportStartPx = 232,
            viewportEndPx = 892,
            dayTileWidthPx = 96f,
        )

        assertEquals(-232, offsetPx)
    }

    @Test
    fun endAnchorFallsBackToStartWhenReadableAreaIsTooNarrow() {
        val offsetPx = scheduleCalendarTargetScrollOffsetPx(
            anchor = ScheduleCalendarScrollAnchor.End,
            viewportStartPx = 232,
            viewportEndPx = 280,
            dayTileWidthPx = 96f,
        )

        assertEquals(-232, offsetPx)
    }
}
