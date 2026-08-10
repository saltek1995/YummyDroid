package me.yummydroid.app.ui

import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleMonthOverlayTest {
    @Test
    fun monthOverlayIsNotDrawnWhenPhysicalHeaderIsVisible() {
        val dayGroups = scheduleDayGroups(
            LocalDate.of(2026, 8, 3),
            LocalDate.of(2026, 8, 4),
        )
        val entries = scheduleCalendarEntries(dayGroups, Locale.ENGLISH)
        val overlay = resolveScheduleCalendarMonthOverlay(
            dayGroups = dayGroups,
            entries = entries,
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 0, offsetPx = 0, sizePx = 200),
                VisibleScheduleCalendarItem(index = 1, offsetPx = 208, sizePx = 96),
            ),
            fallbackDayIndex = 0,
            monthSlotWidthPx = 104f,
            viewportEndPx = 600,
        )

        assertNull(overlay)
    }

    @Test
    fun monthOverlayPinsCurrentMonthWhenHeaderScrolledAway() {
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
            ),
            fallbackDayIndex = 0,
            monthSlotWidthPx = 104f,
            viewportEndPx = 600,
        )

        assertEquals(listOf("AUGUST"), overlay?.chips?.map { chip -> chip.title })
        assertEquals(listOf(0f), overlay?.chips?.map { chip -> chip.offsetPx })
    }

    @Test
    fun monthOverlayMovesCurrentMonthWithLastDayBeforeNextPhysicalHeader() {
        val dayGroups = scheduleDayGroups(
            LocalDate.of(2026, 8, 28),
            LocalDate.of(2026, 9, 4),
        )
        val entries = scheduleCalendarEntries(dayGroups, Locale.ENGLISH)
        val overlay = resolveScheduleCalendarMonthOverlay(
            dayGroups = dayGroups,
            entries = entries,
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 0, offsetPx = -136, sizePx = 200),
                VisibleScheduleCalendarItem(index = 1, offsetPx = 72, sizePx = 200),
            ),
            fallbackDayIndex = 0,
            monthSlotWidthPx = 104f,
            viewportEndPx = 600,
        )

        assertEquals(listOf("AUGUST"), overlay?.chips?.map { chip -> chip.title })
        assertEquals(listOf(-32f), overlay?.chips?.map { chip -> chip.offsetPx })
    }

    @Test
    fun physicalIncomingMonthTakesOverAtViewportStart() {
        val dayGroups = scheduleDayGroups(
            LocalDate.of(2026, 8, 28),
            LocalDate.of(2026, 9, 4),
        )
        val entries = scheduleCalendarEntries(dayGroups, Locale.ENGLISH)
        val overlay = resolveScheduleCalendarMonthOverlay(
            dayGroups = dayGroups,
            entries = entries,
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 1, offsetPx = 0, sizePx = 200),
            ),
            fallbackDayIndex = 0,
            monthSlotWidthPx = 104f,
            viewportEndPx = 600,
        )

        assertNull(overlay)
    }

    @Test
    fun incomingMonthPinsImmediatelyAfterCrossingViewportStart() {
        val dayGroups = scheduleDayGroups(
            LocalDate.of(2026, 8, 28),
            LocalDate.of(2026, 9, 4),
        )
        val entries = scheduleCalendarEntries(dayGroups, Locale.ENGLISH)
        val overlay = resolveScheduleCalendarMonthOverlay(
            dayGroups = dayGroups,
            entries = entries,
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 1, offsetPx = -1, sizePx = 200),
            ),
            fallbackDayIndex = 1,
            monthSlotWidthPx = 104f,
            viewportEndPx = 600,
        )

        assertEquals(listOf("SEPTEMBER"), overlay?.chips?.map { chip -> chip.title })
        assertEquals(listOf(0f), overlay?.chips?.map { chip -> chip.offsetPx })
    }

    @Test
    fun monthOverlayFallsBackWhenNoDayIsVisible() {
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
}
