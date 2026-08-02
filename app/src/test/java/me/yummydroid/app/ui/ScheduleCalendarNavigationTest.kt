package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.LocalDate
import java.util.Locale
import me.yummydroid.app.BrowseSection

class ScheduleCalendarNavigationTest {
    @Test
    fun browseCatalogActionsAreEnabledOnlyForOnlineCatalog() {
        assertTrue(browseCatalogActionsEnabledForSection(BrowseSection.Catalog, forcedOfflineMode = false))
        assertFalse(browseCatalogActionsEnabledForSection(BrowseSection.Schedule, forcedOfflineMode = false))
        assertFalse(browseCatalogActionsEnabledForSection(BrowseSection.History, forcedOfflineMode = false))
        assertFalse(browseCatalogActionsEnabledForSection(BrowseSection.Downloads, forcedOfflineMode = false))
        assertFalse(browseCatalogActionsEnabledForSection(BrowseSection.Catalog, forcedOfflineMode = true))
    }

    @Test
    fun visibleTargetDoesNotScroll() {
        val targetFirstIndex = scheduleCalendarEdgeScrollFirstVisibleIndex(
            visibleItems = visibleItems(3, 8),
            viewportStartPx = 0,
            viewportEndPx = 600,
            targetIndex = 5,
        )

        assertNull(targetFirstIndex)
    }

    @Test
    fun targetBeforeVisibleWindowBecomesFirstVisibleItem() {
        val targetFirstIndex = scheduleCalendarEdgeScrollFirstVisibleIndex(
            visibleItems = visibleItems(3, 8),
            viewportStartPx = 0,
            viewportEndPx = 600,
            targetIndex = 2,
        )

        assertEquals(2, targetFirstIndex)
    }

    @Test
    fun targetAfterVisibleWindowBecomesLastVisibleItem() {
        val targetFirstIndex = scheduleCalendarEdgeScrollFirstVisibleIndex(
            visibleItems = visibleItems(3, 8),
            viewportStartPx = 0,
            viewportEndPx = 600,
            targetIndex = 9,
        )

        assertEquals(5, targetFirstIndex)
    }

    @Test
    fun clippedLeftTargetSnapsToWholeTile() {
        val targetFirstIndex = scheduleCalendarEdgeScrollFirstVisibleIndex(
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 3, offsetPx = -20, sizePx = 96),
                VisibleScheduleCalendarItem(index = 4, offsetPx = 86, sizePx = 96),
            ),
            viewportStartPx = 0,
            viewportEndPx = 600,
            targetIndex = 3,
        )

        assertEquals(3, targetFirstIndex)
    }

    @Test
    fun clippedRightTargetSnapsToWholeTile() {
        val targetFirstIndex = scheduleCalendarEdgeScrollFirstVisibleIndex(
            visibleItems = visibleItems(3, 8).map { item ->
                if (item.index == 8) item.copy(offsetPx = 530) else item
            },
            viewportStartPx = 0,
            viewportEndPx = 600,
            targetIndex = 8,
        )

        assertEquals(4, targetFirstIndex)
    }

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

    private fun visibleItems(firstIndex: Int, lastIndex: Int): List<VisibleScheduleCalendarItem> {
        return (firstIndex..lastIndex).map { index ->
            VisibleScheduleCalendarItem(
                index = index,
                offsetPx = (index - firstIndex) * 106,
                sizePx = 96,
            )
        }
    }

    private fun scheduleDayGroups(vararg dates: LocalDate): List<ScheduleDayGroup> {
        return dates.map { date ->
            ScheduleDayGroup(
                date = date,
                epochDay = date.toEpochDay(),
                items = emptyList(),
            )
        }
    }
}
