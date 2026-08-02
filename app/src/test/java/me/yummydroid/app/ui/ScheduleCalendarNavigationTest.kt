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
    fun boundaryMonthIsShownUntilPinnedMonthStartsPushing() {
        val dayGroups = scheduleDayGroups(
            LocalDate.of(2026, 8, 28),
            LocalDate.of(2026, 9, 4),
        )
        val visibleItems = mapOf(
            1 to VisibleScheduleCalendarItem(index = 1, offsetPx = 140, sizePx = 96),
        )

        assertTrue(
            shouldShowScheduleCalendarBoundaryMonth(
                dayGroups = dayGroups,
                visibleItemsByIndex = visibleItems,
                index = 1,
                viewportEndPx = 600,
                monthPushDistancePx = 104f,
            ),
        )
    }

    @Test
    fun boundaryMonthIsHiddenWhilePinnedMonthPushes() {
        val dayGroups = scheduleDayGroups(
            LocalDate.of(2026, 8, 28),
            LocalDate.of(2026, 9, 4),
        )
        val visibleItems = mapOf(
            1 to VisibleScheduleCalendarItem(index = 1, offsetPx = 80, sizePx = 96),
        )

        assertFalse(
            shouldShowScheduleCalendarBoundaryMonth(
                dayGroups = dayGroups,
                visibleItemsByIndex = visibleItems,
                index = 1,
                viewportEndPx = 600,
                monthPushDistancePx = 104f,
            ),
        )
    }

    @Test
    fun pinnedMonthKeepsPreviousMonthUntilBoundaryReachesStrip() {
        val pinnedMonth = resolveScheduleCalendarPinnedMonth(
            dayGroups = scheduleDayGroups(
                LocalDate.of(2026, 8, 28),
                LocalDate.of(2026, 9, 4),
            ),
            visibleItems = listOf(
                VisibleScheduleCalendarItem(index = 1, offsetPx = 140, sizePx = 96),
            ),
            fallbackIndex = 0,
            locale = Locale.ENGLISH,
            chipWidthPx = 96f,
            chipGapPx = 8f,
        )

        assertEquals("AUGUST", pinnedMonth?.title)
        assertNull(pinnedMonth?.nextTitle)
        assertEquals(0f, pinnedMonth?.currentOffsetFraction)
    }

    @Test
    fun pinnedMonthUsesFirstVisibleDayMonth() {
        val pinnedMonth = resolveScheduleCalendarPinnedMonth(
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
            chipWidthPx = 92f,
            chipGapPx = 10f,
        )

        assertEquals("JANUARY", pinnedMonth?.title)
        assertEquals("FEBRUARY", pinnedMonth?.nextTitle)
        assertEquals(0f, pinnedMonth?.currentOffsetFraction)
    }

    @Test
    fun nextMonthPushesPinnedMonthAway() {
        val pinnedMonth = resolveScheduleCalendarPinnedMonth(
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
            chipWidthPx = 92f,
            chipGapPx = 10f,
        )

        assertEquals("JANUARY", pinnedMonth?.title)
        assertEquals("FEBRUARY", pinnedMonth?.nextTitle)
        assertEquals(-0.4117647f, pinnedMonth?.currentOffsetFraction ?: 0f, absoluteTolerance = 0.0001f)
    }

    @Test
    fun pinnedMonthFallsBackWhenNoDayIsVisible() {
        val pinnedMonth = resolveScheduleCalendarPinnedMonth(
            dayGroups = scheduleDayGroups(
                LocalDate.of(2026, 8, 28),
                LocalDate.of(2026, 9, 4),
            ),
            visibleItems = emptyList(),
            fallbackIndex = 0,
            locale = Locale.ENGLISH,
            chipWidthPx = 92f,
            chipGapPx = 10f,
        )

        assertEquals("AUGUST", pinnedMonth?.title)
        assertNull(pinnedMonth?.nextTitle)
        assertEquals(0f, pinnedMonth?.currentOffsetFraction)
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
