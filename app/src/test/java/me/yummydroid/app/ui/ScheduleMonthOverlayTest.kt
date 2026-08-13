package me.yummydroid.app.ui

import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.ui.components.HorizontalScrollEdgeVisibility

class ScheduleMonthOverlayTest {
    @Test
    fun monthLayerDrawsPhysicalMonthSlotWhenItIsVisible() {
        val dayGroups = scheduleDayGroups(
            LocalDate.of(2026, 8, 3),
            LocalDate.of(2026, 8, 4),
        )
        val entries = scheduleCalendarEntries(dayGroups, Locale.ENGLISH)
        val overlay = resolveScheduleCalendarMonthOverlay(
            dayGroups = dayGroups,
            entries = entries,
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 0, offsetPx = 0, sizePx = 96),
                VisibleScheduleCalendarItem(index = 1, offsetPx = 104, sizePx = 96),
                VisibleScheduleCalendarItem(index = 2, offsetPx = 208, sizePx = 96),
            ),
            fallbackDayIndex = 0,
            monthSlotWidthPx = 104f,
            viewportEndPx = 600,
        )

        assertEquals(listOf("AUGUST"), overlay?.chips?.map { chip -> chip.title })
        assertEquals(listOf(0f), overlay?.chips?.map { chip -> chip.offsetPx })
    }

    @Test
    fun monthLayerPinsCurrentMonthWhenPhysicalSlotScrolledAway() {
        val dayGroups = scheduleDayGroups(
            LocalDate.of(2026, 8, 3),
            LocalDate.of(2026, 8, 4),
        )
        val entries = scheduleCalendarEntries(dayGroups, Locale.ENGLISH)
        val overlay = resolveScheduleCalendarMonthOverlay(
            dayGroups = dayGroups,
            entries = entries,
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 1, offsetPx = 104, sizePx = 96),
                VisibleScheduleCalendarItem(index = 2, offsetPx = 208, sizePx = 96),
            ),
            fallbackDayIndex = 0,
            monthSlotWidthPx = 104f,
            viewportEndPx = 600,
        )

        assertEquals(listOf("AUGUST"), overlay?.chips?.map { chip -> chip.title })
        assertEquals(listOf(0f), overlay?.chips?.map { chip -> chip.offsetPx })
    }

    @Test
    fun monthLayerShowsIncomingMonthSlotWhileCurrentMonthRemainsPinned() {
        val dayGroups = scheduleDayGroups(
            LocalDate.of(2026, 8, 28),
            LocalDate.of(2026, 9, 4),
        )
        val entries = scheduleCalendarEntries(dayGroups, Locale.ENGLISH)
        val overlay = resolveScheduleCalendarMonthOverlay(
            dayGroups = dayGroups,
            entries = entries,
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 1, offsetPx = 104, sizePx = 96),
                VisibleScheduleCalendarItem(index = 2, offsetPx = 208, sizePx = 96),
                VisibleScheduleCalendarItem(index = 3, offsetPx = 312, sizePx = 96),
            ),
            fallbackDayIndex = 0,
            monthSlotWidthPx = 104f,
            viewportEndPx = 600,
        )

        assertEquals(listOf("AUGUST", "SEPTEMBER"), overlay?.chips?.map { chip -> chip.title })
        assertEquals(listOf(0f, 208f), overlay?.chips?.map { chip -> chip.offsetPx })
    }

    @Test
    fun monthLayerSwitchesPinnedMonthWhenIncomingMonthSlotCrossesStart() {
        val dayGroups = scheduleDayGroups(
            LocalDate.of(2026, 8, 28),
            LocalDate.of(2026, 9, 4),
        )
        val entries = scheduleCalendarEntries(dayGroups, Locale.ENGLISH)
        val overlay = resolveScheduleCalendarMonthOverlay(
            dayGroups = dayGroups,
            entries = entries,
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 3, offsetPx = -1, sizePx = 96),
                VisibleScheduleCalendarItem(index = 4, offsetPx = 103, sizePx = 96),
            ),
            fallbackDayIndex = 1,
            monthSlotWidthPx = 104f,
            viewportEndPx = 600,
        )

        assertEquals(listOf("SEPTEMBER"), overlay?.chips?.map { chip -> chip.title })
        assertEquals(listOf(0f), overlay?.chips?.map { chip -> chip.offsetPx })
    }

    @Test
    fun monthLayerFallsBackWhenNoSlotIsVisible() {
        val dayGroups = scheduleDayGroups(
            LocalDate.of(2026, 8, 28),
            LocalDate.of(2026, 9, 4),
        )
        val entries = scheduleCalendarEntries(dayGroups, Locale.ENGLISH)
        val overlay = resolveScheduleCalendarMonthOverlay(
            dayGroups = dayGroups,
            entries = entries,
            visibleItems = emptyList(),
            fallbackDayIndex = 0,
            monthSlotWidthPx = 104f,
            viewportEndPx = 600,
        )

        assertEquals(listOf("AUGUST"), overlay?.chips?.map { chip -> chip.title })
        assertEquals(listOf(0f), overlay?.chips?.map { chip -> chip.offsetPx })
    }

    @Test
    fun monthLayerDoesNotApplyScreenLeftFadeToIncomingMonth() {
        val visibility = scheduleCalendarMonthLayerEdgeVisibility(
            HorizontalScrollEdgeVisibility(
                backward = true,
                forward = true,
                backwardFraction = 0.82f,
                forwardFraction = 0.64f,
            ),
        )

        assertFalse(visibility.backward)
        assertEquals(0f, visibility.backwardFraction)
        assertTrue(visibility.forward)
        assertEquals(0.64f, visibility.forwardFraction)
    }

    @Test
    fun monthAndDayLayerUseSameLeftBoundaryFadeWidth() {
        assertEquals(32.64f, scheduleCalendarBoundaryFadeWidthPx(96f), 0.0001f)
    }

    @Test
    fun monthDragForwardsTouchDeltaToUnderlyingListDirection() {
        assertEquals(42f, scheduleCalendarMonthDragListDeltaPx(-42f))
        assertEquals(-42f, scheduleCalendarMonthDragConsumedPx(42f))
    }

    @Test
    fun monthSlotItemFadeDoesNotAffectItemAfterSlot() {
        assertEquals(
            0f,
            scheduleCalendarMonthSlotHiddenPx(
                itemOffsetPx = 104,
                itemWidthPx = 96f,
                monthSlotWidthPx = 104f,
            ),
        )
    }

    @Test
    fun monthSlotItemFadeStartsOnlyWhenItemOverlapsSlot() {
        assertEquals(
            64f,
            scheduleCalendarMonthSlotHiddenPx(
                itemOffsetPx = 40,
                itemWidthPx = 96f,
                monthSlotWidthPx = 104f,
            ),
        )
    }
}
