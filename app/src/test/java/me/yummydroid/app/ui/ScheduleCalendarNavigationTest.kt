package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
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

    private fun visibleItems(firstIndex: Int, lastIndex: Int): List<VisibleScheduleCalendarItem> {
        return (firstIndex..lastIndex).map { index ->
            VisibleScheduleCalendarItem(
                index = index,
                offsetPx = (index - firstIndex) * 106,
                sizePx = 96,
            )
        }
    }
}
