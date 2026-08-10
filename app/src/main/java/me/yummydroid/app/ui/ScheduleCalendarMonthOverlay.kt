package me.yummydroid.app.ui

import java.time.format.TextStyle
import java.util.Locale

internal data class ScheduleCalendarMonthOverlay(
    val chips: List<ScheduleCalendarMonthChip>,
)

internal data class ScheduleCalendarMonthChip(
    val key: String,
    val monthKey: String,
    val title: String,
    val offsetPx: Float,
)

private data class VisibleScheduleCalendarEntry(
    val entryIndex: Int,
    val item: VisibleScheduleCalendarItem,
    val entry: ScheduleCalendarEntry,
)

internal fun resolveScheduleCalendarMonthOverlay(
    dayGroups: List<ScheduleDayGroup>,
    entries: List<ScheduleCalendarEntry>,
    visibleItems: List<VisibleScheduleCalendarItem>,
    fallbackDayIndex: Int,
    monthSlotWidthPx: Float,
    viewportEndPx: Int,
): ScheduleCalendarMonthOverlay? {
    if (dayGroups.isEmpty()) return null
    val visibleEntries = visibleItems
        .mapNotNull { item ->
            val entry = entries.getOrNull(item.index) ?: return@mapNotNull null
            if (item.offsetPx + item.sizePx <= 0) return@mapNotNull null
            VisibleScheduleCalendarEntry(
                entryIndex = item.index,
                item = item,
                entry = entry,
            )
        }
        .sortedBy { visible -> visible.item.offsetPx }
    val fallbackMonth = fallbackScheduleCalendarMonthEntry(
        dayGroups = dayGroups,
        entries = entries,
        fallbackDayIndex = fallbackDayIndex,
    )
    if (visibleEntries.isEmpty()) {
        val month = fallbackMonth ?: return null
        return ScheduleCalendarMonthOverlay(
            chips = listOf(month.scheduleCalendarMonthChip(offsetPx = 0f)),
        )
    }
    val currentMonth = visibleEntries
        .lastOrNull { visible ->
            visible.entry.startsMonth &&
                visible.item.offsetPx <= 0
        }
        ?.entry
        ?: scheduleCalendarMonthEntryAtOrBefore(entries, visibleEntries.first().entryIndex)
        ?: fallbackMonth
        ?: return null
    val currentMonthEntryIndex = entries.indexOf(currentMonth).takeIf { index -> index >= 0 } ?: return null
    val physicalCurrentMonth = visibleEntries.firstOrNull { visible ->
        visible.entryIndex == currentMonthEntryIndex
    }
    if (
        physicalCurrentMonth != null &&
        physicalCurrentMonth.item.offsetPx >= 0 &&
        physicalCurrentMonth.item.offsetPx < viewportEndPx
    ) {
        return null
    }
    val nextMonth = nextScheduleCalendarMonthEntry(entries, currentMonthEntryIndex)
    val nextMonthVisible = nextMonth?.let { month ->
        val nextMonthEntryIndex = entries.indexOf(month)
        visibleEntries.firstOrNull { visible -> visible.entryIndex == nextMonthEntryIndex }
    }
    val pushOffsetPx = nextMonthVisible
        ?.takeIf { visible -> visible.item.offsetPx < monthSlotWidthPx }
        ?.let { visible ->
            (visible.item.offsetPx - monthSlotWidthPx)
                .coerceAtLeast(-monthSlotWidthPx)
                .coerceAtMost(0f)
        }
    val currentOffsetPx = if (physicalCurrentMonth?.item?.offsetPx?.let { offset -> offset < 0 } == true) {
        pushOffsetPx ?: 0f
    } else {
        pushOffsetPx ?: visibleEntries
            .firstOrNull { visible -> visible.entryIndex == currentMonthEntryIndex }
            ?.takeIf { visible -> visible.item.offsetPx < monthSlotWidthPx }
            ?.let { visible ->
                (visible.item.offsetPx - monthSlotWidthPx)
                    .coerceAtLeast(-monthSlotWidthPx)
                    .coerceAtMost(0f)
            }
            ?: 0f
    }
    return if (currentOffsetPx > -monthSlotWidthPx) {
        ScheduleCalendarMonthOverlay(
            chips = listOf(currentMonth.scheduleCalendarMonthChip(offsetPx = currentOffsetPx)),
        )
    } else {
        null
    }
}

private fun scheduleCalendarMonthEntryAtOrBefore(
    entries: List<ScheduleCalendarEntry>,
    entryIndex: Int,
): ScheduleCalendarEntry? {
    return entries
        .take(entryIndex + 1)
        .asReversed()
        .firstOrNull { entry -> entry.startsMonth }
}

private fun nextScheduleCalendarMonthEntry(
    entries: List<ScheduleCalendarEntry>,
    entryIndex: Int,
): ScheduleCalendarEntry? {
    return entries
        .drop(entryIndex + 1)
        .firstOrNull { entry -> entry.startsMonth }
}

private fun fallbackScheduleCalendarMonthEntry(
    dayGroups: List<ScheduleDayGroup>,
    entries: List<ScheduleCalendarEntry>,
    fallbackDayIndex: Int,
): ScheduleCalendarEntry? {
    val fallbackGroup = dayGroups.getOrNull(fallbackDayIndex.coerceIn(dayGroups.indices)) ?: return null
    return entries.firstOrNull { entry ->
        entry.startsMonth &&
            entry.monthKey == fallbackGroup.scheduleMonthKey()
    }
}

private fun ScheduleCalendarEntry.scheduleCalendarMonthChip(
    offsetPx: Float,
): ScheduleCalendarMonthChip {
    return ScheduleCalendarMonthChip(
        key = key,
        monthKey = monthKey,
        title = title,
        offsetPx = offsetPx,
    )
}

internal fun ScheduleDayGroup.sameScheduleMonth(other: ScheduleDayGroup): Boolean {
    return date.year == other.date.year && date.monthValue == other.date.monthValue
}

internal fun List<ScheduleDayGroup>.isScheduleMonthBoundary(index: Int): Boolean {
    return index > 0 &&
        index in indices &&
        !this[index].sameScheduleMonth(this[index - 1])
}

internal fun ScheduleDayGroup.scheduleMonthTitle(locale: Locale): String {
    return date.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).uppercase(locale)
}

internal fun ScheduleDayGroup.scheduleMonthKey(): String {
    return "${date.year}-${date.monthValue}"
}
