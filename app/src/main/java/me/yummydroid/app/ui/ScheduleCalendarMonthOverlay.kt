package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import me.yummydroid.app.ui.components.HorizontalScrollEdgeVisibility

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

private const val ScheduleCalendarBoundaryFadeWidthFraction = 0.34f

internal fun ScheduleCalendarMonthChip.isFixedAtMonthSlot(): Boolean {
    return offsetPx == 0f
}

internal fun scheduleCalendarBoundaryFadeWidthPx(itemWidthPx: Float): Float {
    return itemWidthPx * ScheduleCalendarBoundaryFadeWidthFraction
}

internal fun scheduleCalendarMonthLayerEdgeVisibility(
    edgeVisibility: HorizontalScrollEdgeVisibility,
): HorizontalScrollEdgeVisibility {
    return edgeVisibility.copy(backward = false, backwardFraction = 0f)
}

@Composable
internal fun rememberScheduleCalendarMonthOverlay(
    runtime: ScheduleCalendarRuntime,
) = remember(
    runtime.listState,
    runtime.entries,
    runtime.dayGroups,
    runtime.monthSlotWidthPx,
    runtime.dayTileWidthPx,
) {
    derivedStateOf {
        resolveScheduleCalendarMonthOverlay(
            dayGroups = runtime.dayGroups,
            entries = runtime.entries,
            visibleItems = runtime.listState.layoutInfo.visibleItemsInfo.map { item ->
                VisibleScheduleCalendarItem(
                    index = item.index,
                    offsetPx = item.offset,
                    sizePx = runtime.dayTileWidthPx.roundToInt(),
                )
            },
            fallbackDayIndex = runtime.selectedDayIndex(),
            monthSlotWidthPx = runtime.monthSlotWidthPx,
            viewportEndPx = runtime.listState.layoutInfo.viewportSize.width,
        )
    }
}

internal fun resolveScheduleCalendarMonthOverlay(
    dayGroups: List<ScheduleDayGroup>,
    entries: List<ScheduleCalendarEntry>,
    visibleItems: List<VisibleScheduleCalendarItem>,
    fallbackDayIndex: Int,
    monthSlotWidthPx: Float,
    viewportEndPx: Int,
): ScheduleCalendarMonthOverlay? {
    if (dayGroups.isEmpty()) return null
    val visibleEntries = visibleScheduleCalendarEntries(entries, visibleItems)
    val fallbackMonthEntryIndex = fallbackScheduleCalendarMonthEntryIndex(
        dayGroups = dayGroups,
        entries = entries,
        fallbackDayIndex = fallbackDayIndex,
    )
    if (visibleEntries.isEmpty()) {
        return entries.getOrNull(fallbackMonthEntryIndex ?: -1)
            ?.scheduleCalendarMonthOverlay(offsetPx = 0f)
    }
    val physicalMonthChips = visibleEntries
        .filter { visible ->
            visible.entry.type == ScheduleCalendarEntryType.Month &&
                visible.item.offsetPx >= 0 &&
                visible.item.offsetPx < viewportEndPx
        }
        .map { visible -> visible.entry.scheduleCalendarMonthChip(visible.item.offsetPx.toFloat()) }
    val currentMonthEntryIndex = currentScheduleCalendarMonthEntryIndex(
        entries = entries,
        visibleEntries = visibleEntries,
        fallbackMonthEntryIndex = fallbackMonthEntryIndex,
    ) ?: return null
    val currentMonth = entries.getOrNull(currentMonthEntryIndex) ?: return null
    val physicalCurrentMonth = visibleEntries.entryAt(currentMonthEntryIndex)
    val pinnedCurrentMonthChip = if (physicalCurrentMonth.isVisibleMonthHeader(viewportEndPx)) {
        null
    } else {
        val currentOffsetPx = pinnedScheduleCalendarMonthOffset(
            entries = entries,
            visibleEntries = visibleEntries,
            currentMonthEntryIndex = currentMonthEntryIndex,
            physicalCurrentMonth = physicalCurrentMonth,
            monthSlotWidthPx = monthSlotWidthPx,
        )
        currentMonth
            .takeIf { currentOffsetPx > -monthSlotWidthPx }
            ?.scheduleCalendarMonthChip(currentOffsetPx)
    }
    val chips = buildList {
        pinnedCurrentMonthChip?.let(::add)
        addAll(physicalMonthChips)
    }.distinctBy { chip -> chip.key }
    return chips
        .takeIf { it.isNotEmpty() }
        ?.let(::ScheduleCalendarMonthOverlay)
}

private fun visibleScheduleCalendarEntries(
    entries: List<ScheduleCalendarEntry>,
    visibleItems: List<VisibleScheduleCalendarItem>,
): List<VisibleScheduleCalendarEntry> {
    return visibleItems
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
}

private fun currentScheduleCalendarMonthEntryIndex(
    entries: List<ScheduleCalendarEntry>,
    visibleEntries: List<VisibleScheduleCalendarEntry>,
    fallbackMonthEntryIndex: Int?,
): Int? {
    return visibleEntries
        .lastOrNull { visible -> visible.entry.startsMonth && visible.item.offsetPx <= 0 }
        ?.entryIndex
        ?: entries.monthEntryIndexAtOrBefore(visibleEntries.first().entryIndex)
        ?: fallbackMonthEntryIndex
}

private fun pinnedScheduleCalendarMonthOffset(
    entries: List<ScheduleCalendarEntry>,
    visibleEntries: List<VisibleScheduleCalendarEntry>,
    currentMonthEntryIndex: Int,
    physicalCurrentMonth: VisibleScheduleCalendarEntry?,
    monthSlotWidthPx: Float,
): Float {
    val nextMonthOffset = entries.monthEntryIndexAfter(currentMonthEntryIndex)
        ?.let(visibleEntries::entryAt)
        ?.monthPushOffset(monthSlotWidthPx)
    if (nextMonthOffset != null) return nextMonthOffset
    if (physicalCurrentMonth?.item?.offsetPx?.let { offset -> offset < 0 } == true) return 0f
    return physicalCurrentMonth?.monthPushOffset(monthSlotWidthPx) ?: 0f
}

private fun List<VisibleScheduleCalendarEntry>.entryAt(
    entryIndex: Int,
): VisibleScheduleCalendarEntry? = firstOrNull { visible -> visible.entryIndex == entryIndex }

private fun VisibleScheduleCalendarEntry?.isVisibleMonthHeader(viewportEndPx: Int): Boolean {
    val offsetPx = this?.item?.offsetPx ?: return false
    return offsetPx in 0 until viewportEndPx
}

private fun VisibleScheduleCalendarEntry.monthPushOffset(monthSlotWidthPx: Float): Float? {
    val offsetPx = item.offsetPx
    if (offsetPx >= monthSlotWidthPx) return null
    return (offsetPx - monthSlotWidthPx)
        .coerceAtLeast(-monthSlotWidthPx)
        .coerceAtMost(0f)
}

private fun ScheduleCalendarEntry.scheduleCalendarMonthOverlay(
    offsetPx: Float,
): ScheduleCalendarMonthOverlay = ScheduleCalendarMonthOverlay(
    chips = listOf(scheduleCalendarMonthChip(offsetPx)),
)

private fun List<ScheduleCalendarEntry>.monthEntryIndexAtOrBefore(
    entryIndex: Int,
): Int? = indices.lastOrNull { index -> index <= entryIndex && this[index].startsMonth }

private fun List<ScheduleCalendarEntry>.monthEntryIndexAfter(
    entryIndex: Int,
): Int? = indices.firstOrNull { index -> index > entryIndex && this[index].startsMonth }

private fun fallbackScheduleCalendarMonthEntryIndex(
    dayGroups: List<ScheduleDayGroup>,
    entries: List<ScheduleCalendarEntry>,
    fallbackDayIndex: Int,
): Int? {
    val fallbackGroup = dayGroups.getOrNull(fallbackDayIndex.coerceIn(dayGroups.indices)) ?: return null
    return entries.indices.firstOrNull { index ->
        entries[index].startsMonth &&
            entries[index].monthKey == fallbackGroup.scheduleMonthKey()
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
