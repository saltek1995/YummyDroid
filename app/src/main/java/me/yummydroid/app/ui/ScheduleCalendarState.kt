package me.yummydroid.app.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
