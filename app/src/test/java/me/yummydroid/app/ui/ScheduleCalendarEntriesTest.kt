package me.yummydroid.app.ui

import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class ScheduleCalendarEntriesTest {
    @Test
    fun calendarEntriesCreateSeparateMonthAndDaySlots() {
        val entries = scheduleCalendarEntries(
            dayGroups = scheduleDayGroups(
                LocalDate.of(2026, 7, 31),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 2),
            ),
            locale = Locale.ENGLISH,
        )

        assertEquals(5, entries.size)
        assertEquals(
            listOf(
                ScheduleCalendarEntryType.Month,
                ScheduleCalendarEntryType.Day,
                ScheduleCalendarEntryType.Month,
                ScheduleCalendarEntryType.Day,
                ScheduleCalendarEntryType.Day,
            ),
            entries.map { entry -> entry.type },
        )
        assertEquals(listOf(0, 0, 1, 1, 2), entries.map { entry -> entry.dayIndex })
        assertEquals("JULY", entries[0].title)
        assertEquals("AUGUST", entries[2].title)
    }

    @Test
    fun calendarEntriesKeepMonthMarkersInIndependentMonthLayerSlots() {
        val entries = scheduleCalendarEntries(
            dayGroups = scheduleDayGroups(
                LocalDate.of(2026, 8, 19),
                LocalDate.of(2026, 8, 21),
                LocalDate.of(2026, 8, 23),
                LocalDate.of(2026, 8, 28),
                LocalDate.of(2026, 9, 4),
            ),
            locale = Locale.ENGLISH,
        )

        val septemberMonthIndex = entries.indexOfFirst { entry ->
            entry.type == ScheduleCalendarEntryType.Month &&
                entry.title == "SEPTEMBER"
        }
        assertEquals(5, septemberMonthIndex)
        assertEquals(4, entries[septemberMonthIndex].dayIndex)
        assertEquals(ScheduleCalendarEntryType.Day, entries[septemberMonthIndex + 1].type)
        assertEquals(4, entries[septemberMonthIndex + 1].dayIndex)
    }
}
