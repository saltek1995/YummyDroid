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
    fun monthLabelSticksToViewportStartWhenFirstMonthDayIsClipped() {
        val labels = buildScheduleCalendarMonthLabels(
            dayGroups = scheduleDayGroups(
                LocalDate.of(2026, 1, 30),
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 1),
            ),
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 0, offsetPx = -80, sizePx = 96),
                VisibleScheduleCalendarItem(index = 1, offsetPx = 26, sizePx = 96),
                VisibleScheduleCalendarItem(index = 2, offsetPx = 132, sizePx = 96),
            ),
            fallbackIndex = 0,
            locale = Locale.ENGLISH,
            fallbackWidthPx = 112,
            viewportStartPx = 0,
            viewportEndPx = 600,
        )

        assertEquals("JANUARY", labels[0].title)
        assertEquals(0, labels[0].offsetPx)
        assertEquals("FEBRUARY", labels[1].title)
        assertEquals(132, labels[1].offsetPx)
    }

    @Test
    fun nextMonthPushesCurrentStickyMonthLabelAway() {
        val labels = buildScheduleCalendarMonthLabels(
            dayGroups = scheduleDayGroups(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 1),
            ),
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 0, offsetPx = -46, sizePx = 96),
                VisibleScheduleCalendarItem(index = 1, offsetPx = 60, sizePx = 96),
            ),
            fallbackIndex = 0,
            locale = Locale.ENGLISH,
            fallbackWidthPx = 112,
            viewportStartPx = 0,
            viewportEndPx = 600,
        )

        assertEquals("JANUARY", labels[0].title)
        assertEquals(-52, labels[0].offsetPx)
        assertEquals("FEBRUARY", labels[1].title)
        assertEquals(60, labels[1].offsetPx)
    }

    @Test
    fun monthLabelUsesRealTextWidthBeforePushAway() {
        val labels = buildScheduleCalendarMonthLabels(
            dayGroups = scheduleDayGroups(
                LocalDate.of(2026, 8, 28),
                LocalDate.of(2026, 9, 4),
            ),
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 0, offsetPx = 0, sizePx = 96),
                VisibleScheduleCalendarItem(index = 1, offsetPx = 106, sizePx = 96),
            ),
            fallbackIndex = 0,
            locale = Locale.ENGLISH,
            fallbackWidthPx = 112,
            viewportStartPx = 0,
            viewportEndPx = 600,
            labelWidthPx = { title -> if (title == "AUGUST") 72 else 112 },
        )

        assertEquals("AUGUST", labels[0].title)
        assertEquals(0, labels[0].offsetPx)
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
