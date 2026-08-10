package me.yummydroid.app.ui

import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ScheduleCalendarEntriesTest {
    @Test
    fun calendarEntriesGlueMonthHeaderToFirstDayEntry() {
        val entries = scheduleCalendarEntries(
            dayGroups = scheduleDayGroups(
                LocalDate.of(2026, 7, 31),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 2),
            ),
            locale = Locale.ENGLISH,
        )

        assertEquals(3, entries.size)
        assertEquals(
            listOf(
                ScheduleCalendarEntryType.MonthDay,
                ScheduleCalendarEntryType.MonthDay,
                ScheduleCalendarEntryType.Day,
            ),
            entries.map { entry -> entry.type },
        )
        assertEquals(listOf(0, 1, 2), entries.map { entry -> entry.dayIndex })
        assertEquals("JULY", entries[0].title)
        assertEquals("AUGUST", entries[1].title)
    }

    @Test
    fun calendarEntriesDoNotCreateStandaloneMonthItems() {
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

        assertFalse(entries.any { entry -> entry.type.name == "Month" })
        val septemberMonthDayIndex = entries.indexOfFirst { entry ->
            entry.type == ScheduleCalendarEntryType.MonthDay &&
                entry.title == "SEPTEMBER"
        }
        assertEquals(4, septemberMonthDayIndex)
        assertEquals(4, entries[septemberMonthDayIndex].dayIndex)
    }
}
