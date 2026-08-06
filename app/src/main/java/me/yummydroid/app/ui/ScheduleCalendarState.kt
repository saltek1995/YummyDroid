package me.yummydroid.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import me.yummydroid.app.data.ScheduleAnime

internal data class VisibleScheduleCalendarItem(
    val index: Int,
    val offsetPx: Int,
    val sizePx: Int,
)

internal enum class ScheduleCalendarEntryType {
    MonthDay,
    Day,
}

internal data class ScheduleCalendarEntry(
    val key: String,
    val type: ScheduleCalendarEntryType,
    val monthKey: String,
    val title: String,
    val dayIndex: Int,
    val startsMonth: Boolean,
    val endsMonth: Boolean,
)

internal fun scheduleCalendarEntries(
    dayGroups: List<ScheduleDayGroup>,
    locale: Locale,
): List<ScheduleCalendarEntry> {
    val entries = ArrayList<ScheduleCalendarEntry>(dayGroups.size * 2)
    dayGroups.forEachIndexed { index, group ->
        val startsMonth = index == 0 || dayGroups.isScheduleMonthBoundary(index)
        val endsMonth = dayGroups.isScheduleMonthBoundary(index + 1)
        entries += ScheduleCalendarEntry(
            key = "schedule-day-${group.epochDay}",
            type = if (startsMonth) ScheduleCalendarEntryType.MonthDay else ScheduleCalendarEntryType.Day,
            monthKey = group.scheduleMonthKey(),
            title = group.scheduleMonthTitle(locale),
            dayIndex = index,
            startsMonth = startsMonth,
            endsMonth = endsMonth,
        )
    }
    return entries
}

internal fun scheduleCalendarFullyVisibleItems(
    visibleItems: List<VisibleScheduleCalendarItem>,
    viewportStartPx: Int,
    viewportEndPx: Int,
): List<VisibleScheduleCalendarItem> {
    return visibleItems
        .filter { item ->
            item.offsetPx >= viewportStartPx &&
                item.offsetPx + item.sizePx <= viewportEndPx
        }
        .sortedBy { item -> item.index }
}

internal fun scheduleCalendarEdgeScrollFirstVisibleIndex(
    visibleItems: List<VisibleScheduleCalendarItem>,
    viewportStartPx: Int,
    viewportEndPx: Int,
    targetIndex: Int,
): Int? {
    val visible = visibleItems.sortedBy { item -> item.index }
    if (visible.isEmpty()) return null

    val fullyVisible = scheduleCalendarFullyVisibleItems(
        visibleItems = visible,
        viewportStartPx = viewportStartPx,
        viewportEndPx = viewportEndPx,
    )
    val capacity = fullyVisible.size.takeIf { count -> count > 0 } ?: visible.size
    val first = fullyVisible.firstOrNull() ?: visible.first()
    val last = fullyVisible.lastOrNull() ?: visible.last()
    val target = visible.firstOrNull { item -> item.index == targetIndex }

    return when {
        target != null && target.offsetPx >= viewportStartPx && target.offsetPx + target.sizePx <= viewportEndPx -> null
        targetIndex <= first.index -> targetIndex.coerceAtLeast(0)
        targetIndex >= last.index -> (targetIndex - capacity + 1).coerceAtLeast(0)
        target != null && target.offsetPx < viewportStartPx -> targetIndex.coerceAtLeast(0)
        target != null && target.offsetPx + target.sizePx > viewportEndPx -> {
            (targetIndex - capacity + 1).coerceAtLeast(0)
        }
        else -> null
    }
}

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

private fun ScheduleDayGroup.sameScheduleMonth(other: ScheduleDayGroup): Boolean {
    return date.year == other.date.year && date.monthValue == other.date.monthValue
}

private fun List<ScheduleDayGroup>.isScheduleMonthBoundary(index: Int): Boolean {
    return index > 0 &&
        index in indices &&
        !this[index].sameScheduleMonth(this[index - 1])
}

private fun ScheduleDayGroup.scheduleMonthTitle(locale: Locale): String {
    return date.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).uppercase(locale)
}

private fun ScheduleDayGroup.scheduleMonthKey(): String {
    return "${date.year}-${date.monthValue}"
}

internal data class ScheduleDayGroup(
    val date: LocalDate,
    val epochDay: Long,
    val items: List<ScheduleAnime>,
)

private data class ScheduleTimedItem(
    val item: ScheduleAnime,
    val timestampSeconds: Long,
)

internal fun List<ScheduleAnime>.toScheduleDayGroups(zoneId: ZoneId): List<ScheduleDayGroup> {
    return asSequence()
        .mapNotNull { item ->
            item.scheduleDisplayTimestampSeconds()?.let { timestamp ->
                ScheduleTimedItem(item = item, timestampSeconds = timestamp)
            }
        }
        .groupBy { timedItem ->
            Instant.ofEpochSecond(timedItem.timestampSeconds).atZone(zoneId).toLocalDate()
        }
        .map { (date, items) ->
            ScheduleDayGroup(
                date = date,
                epochDay = date.toEpochDay(),
                items = items
                    .sortedWith(compareBy<ScheduleTimedItem> { it.timestampSeconds }.thenBy { it.item.anime.title })
                    .map { it.item },
            )
        }
        .sortedBy { it.epochDay }
}

internal fun List<ScheduleDayGroup>.todayOrClosest(): ScheduleDayGroup? {
    if (isEmpty()) return null
    val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
    return firstOrNull { group -> group.epochDay == today }
        ?: firstOrNull { group -> group.epochDay > today }
        ?: last()
}

private fun ScheduleAnime.scheduleDisplayTimestampSeconds(): Long? {
    return when {
        nextEpisodeAtSeconds > 0L -> nextEpisodeAtSeconds
        previousEpisodeAtSeconds > 0L -> previousEpisodeAtSeconds
        else -> null
    }
}

internal fun ScheduleAnime.formatScheduleTime(formatter: DateTimeFormatter): String {
    val timestamp = scheduleDisplayTimestampSeconds() ?: return "--:--"
    return Instant.ofEpochSecond(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
