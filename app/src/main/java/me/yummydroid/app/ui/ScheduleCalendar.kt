package me.yummydroid.app.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import me.yummydroid.app.data.ScheduleAnime

// ScheduleCalendar
@Composable
internal fun ScheduleCalendarBlock(
    dayGroups: List<ScheduleDayGroup>,
    selectedEpochDay: Long,
    locale: Locale,
    modifier: Modifier = Modifier,
    focusRequestNonce: Long = 0L,
    focusEnabled: Boolean = true,
    onCalendarFocusChanged: (Boolean) -> Unit = {},
    onExitUp: () -> Boolean,
    onExitDown: () -> Boolean,
    onSelectDay: (Long) -> Unit,
) {
    val runtime = rememberScheduleCalendarRuntime(
        dayGroups = dayGroups,
        selectedEpochDay = selectedEpochDay,
        locale = locale,
        onSelectDay = onSelectDay,
    )
    ScheduleCalendarEffects(
        runtime = runtime,
        focusRequestNonce = focusRequestNonce,
        focusEnabled = focusEnabled,
    )
    ScheduleCalendarContent(
        runtime = runtime,
        modifier = modifier,
        focusEnabled = focusEnabled,
        onCalendarFocusChanged = onCalendarFocusChanged,
        onExitUp = { runtime.exitCalendar(onExitUp) },
        onExitDown = { runtime.exitCalendar(onExitDown) },
    )
}

// ScheduleCalendarRuntime
internal class ScheduleCalendarRuntime(
    val dayGroups: List<ScheduleDayGroup>,
    val selectedEpochDay: Long,
    val locale: Locale,
    val listState: LazyListState,
    val itemGap: Dp,
    val bottomPadding: Dp,
    val monthSlotWidthPx: Float,
    val dayTileWidthPx: Float,
    private val edgeFadeWidthPx: Float,
    val dayKeys: List<Long>,
    val focusRequester: FocusRequester,
    val entries: List<ScheduleCalendarEntry>,
    private val dayEntryIndices: IntArray,
    private val navigationEpochDayState: MutableLongState,
    private val pendingSelectionEpochDayState: MutableLongState,
    private val handledFocusRequestNonceState: MutableLongState,
    private val onSelectDay: (Long) -> Unit,
) {
    var navigationEpochDay: Long
        get() = navigationEpochDayState.longValue
        set(value) {
            navigationEpochDayState.longValue = value
        }

    var handledFocusRequestNonce: Long
        get() = handledFocusRequestNonceState.longValue
        set(value) {
            handledFocusRequestNonceState.longValue = value
        }

    private var pendingSelectionEpochDay: Long
        get() = pendingSelectionEpochDayState.longValue
        set(value) {
            pendingSelectionEpochDayState.longValue = value
        }

    fun selectedDayIndex(): Int {
        return dayGroups.indexOfFirst { group -> group.epochDay == navigationEpochDay }
            .takeIf { index -> index >= 0 }
            ?: dayGroups.indexOfFirst { group -> group.epochDay == selectedEpochDay }
                .takeIf { index -> index >= 0 }
            ?: 0
    }

    suspend fun scrollToDayStart(dayIndex: Int) {
        val entryIndex = calendarEntryIndexForDay(dayIndex)
        val scrollOffset = -monthSlotWidthPx.roundToInt()
        listState.scrollToItem(entryIndex, scrollOffset)
    }

    fun selectDayAt(targetIndex: Int, moveFocus: Boolean): Boolean {
        if (dayGroups.isEmpty()) return true
        val boundedIndex = targetIndex.coerceIn(dayGroups.indices)
        val targetDay = dayGroups[boundedIndex].epochDay
        navigationEpochDay = targetDay
        pendingSelectionEpochDay = targetDay
        requestDayVisible(boundedIndex)
        if (moveFocus) focusRequester.requestFocusSafely()
        if (targetDay != selectedEpochDay) onSelectDay(targetDay)
        return true
    }

    fun moveSelectedDay(delta: Int): Boolean {
        val requestedIndex = selectedDayIndex()
        val targetIndex = scheduleCalendarTargetDayIndex(
            itemCount = dayGroups.size,
            currentIndex = requestedIndex,
            delta = delta,
        ) ?: return true
        if (targetIndex == requestedIndex) {
            focusRequester.requestFocusSafely()
            return true
        }
        val targetDay = dayGroups[targetIndex].epochDay
        navigationEpochDay = targetDay
        pendingSelectionEpochDay = targetDay
        requestDayVisible(targetIndex)
        if (targetDay != selectedEpochDay) onSelectDay(targetDay)
        focusRequester.requestFocusSafely()
        return true
    }

    fun exitCalendar(onExit: () -> Boolean): Boolean {
        pendingSelectionEpochDay = NoPendingScheduleCalendarSelection
        onExit()
        return true
    }

    fun synchronizeSelectedDay() {
        if (pendingSelectionEpochDay != NoPendingScheduleCalendarSelection) {
            if (selectedEpochDay != pendingSelectionEpochDay) return
            pendingSelectionEpochDay = NoPendingScheduleCalendarSelection
        }
        if (selectedEpochDay != Long.MIN_VALUE && navigationEpochDay != selectedEpochDay) {
            navigationEpochDay = selectedEpochDay
            requestDayVisible(selectedDayIndex())
        }
    }

    private fun requestDayVisible(targetIndex: Int) {
        val layoutInfo = listState.layoutInfo
        val visibleItems = currentVisibleDayItems()
        val firstVisibleDayIndex = scheduleCalendarEdgeScrollFirstVisibleIndex(
            visibleItems = visibleItems,
            viewportStartPx = monthSlotWidthPx.roundToInt(),
            viewportEndPx = navigationViewportEndPx(layoutInfo.viewportSize.width),
            targetIndex = targetIndex,
        )
            ?: return
        val readableFirstDayIndex = scheduleCalendarReadableFirstDayIndexForTarget(
            firstDayIndex = firstVisibleDayIndex,
            targetDayIndex = targetIndex,
            dayEntryIndices = dayEntryIndices,
            monthSlotWidthPx = monthSlotWidthPx,
            dayTileWidthPx = dayTileWidthPx,
            viewportEndPx = navigationViewportEndPx(layoutInfo.viewportSize.width),
        )
        val entryIndex = calendarEntryIndexForDay(readableFirstDayIndex)
        val scrollOffset = -monthSlotWidthPx.roundToInt()
        listState.requestScrollToItem(entryIndex, scrollOffset)
    }

    private fun currentVisibleDayItems(): List<VisibleScheduleCalendarItem> {
        val layoutInfo = listState.layoutInfo
        return layoutInfo.visibleItemsInfo.mapNotNull { item ->
            val entry = entries.getOrNull(item.index) ?: return@mapNotNull null
            if (entry.type != ScheduleCalendarEntryType.Day) return@mapNotNull null
            VisibleScheduleCalendarItem(
                index = entry.dayIndex,
                offsetPx = item.offset,
                sizePx = item.size,
            )
        }
    }

    private fun calendarEntryIndexForDay(dayIndex: Int): Int {
        return dayEntryIndices
            .getOrNull(dayIndex)
            ?.takeIf { index -> index >= 0 }
            ?: dayIndex
    }

    private fun navigationViewportEndPx(viewportEndPx: Int): Int {
        if (!listState.canScrollForward) return viewportEndPx
        return (viewportEndPx - edgeFadeWidthPx.roundToInt())
            .coerceAtLeast(monthSlotWidthPx.roundToInt())
    }

}

private data class ScheduleCalendarLayoutState(
    val itemGap: Dp,
    val bottomPadding: Dp,
    val monthSlotWidthPx: Float,
    val dayTileWidthPx: Float,
    val edgeFadeWidthPx: Float,
    val dayKeys: List<Long>,
    val entries: List<ScheduleCalendarEntry>,
    val dayEntryIndices: IntArray,
)

@Composable
private fun rememberScheduleCalendarLayoutState(
    dayGroups: List<ScheduleDayGroup>,
    locale: Locale,
): ScheduleCalendarLayoutState {
    val isWide = currentResponsiveWindowSizeDp().width >= 720.dp
    val itemGap = if (isWide) ScheduleDayTileWideGap else ScheduleDayTilePhoneGap
    val bottomPadding = if (isWide) ScheduleCalendarWideBottomPadding else ScheduleCalendarPhoneBottomPadding
    val dayKeys = remember(dayGroups) { dayGroups.map { it.epochDay } }
    val density = LocalDensity.current
    val monthSlotWidthPx = remember(density, itemGap) {
        with(density) { (ScheduleMonthInlineLabelWidth + itemGap).toPx() }
    }
    val dayTileWidthPx = remember(density) { with(density) { ScheduleDayTileWidth.toPx() } }
    val edgeFadeWidthPx = remember(density) { with(density) { ScheduleCalendarEdgeFadeWidth.toPx() } }
    val entries = remember(dayGroups, locale) { scheduleCalendarEntries(dayGroups, locale) }
    val dayEntryIndices = remember(dayGroups, entries) { scheduleCalendarDayEntryIndices(dayGroups.size, entries) }
    return ScheduleCalendarLayoutState(
        itemGap = itemGap,
        bottomPadding = bottomPadding,
        monthSlotWidthPx = monthSlotWidthPx,
        dayTileWidthPx = dayTileWidthPx,
        edgeFadeWidthPx = edgeFadeWidthPx,
        dayKeys = dayKeys,
        entries = entries,
        dayEntryIndices = dayEntryIndices,
    )
}

private fun scheduleCalendarDayEntryIndices(
    dayCount: Int,
    entries: List<ScheduleCalendarEntry>,
): IntArray = IntArray(dayCount) { -1 }.also { indices ->
    entries.forEachIndexed { entryIndex, entry ->
        if (entry.dayIndex in indices.indices) indices[entry.dayIndex] = entryIndex
    }
}

@Composable
internal fun rememberScheduleCalendarRuntime(
    dayGroups: List<ScheduleDayGroup>,
    selectedEpochDay: Long,
    locale: Locale,
    onSelectDay: (Long) -> Unit,
): ScheduleCalendarRuntime {
    val listState = rememberLazyListState()
    val layout = rememberScheduleCalendarLayoutState(dayGroups, locale)
    val navigationEpochDayState = remember(layout.dayKeys) { mutableLongStateOf(selectedEpochDay) }
    val pendingSelectionEpochDayState = remember(layout.dayKeys) {
        mutableLongStateOf(NoPendingScheduleCalendarSelection)
    }
    val focusRequester = remember(layout.dayKeys) { FocusRequester() }
    val handledFocusRequestNonceState = remember { mutableLongStateOf(0L) }
    return ScheduleCalendarRuntime(
        dayGroups = dayGroups,
        selectedEpochDay = selectedEpochDay,
        locale = locale,
        listState = listState,
        itemGap = layout.itemGap,
        bottomPadding = layout.bottomPadding,
        monthSlotWidthPx = layout.monthSlotWidthPx,
        dayTileWidthPx = layout.dayTileWidthPx,
        edgeFadeWidthPx = layout.edgeFadeWidthPx,
        dayKeys = layout.dayKeys,
        focusRequester = focusRequester,
        entries = layout.entries,
        dayEntryIndices = layout.dayEntryIndices,
        navigationEpochDayState = navigationEpochDayState,
        pendingSelectionEpochDayState = pendingSelectionEpochDayState,
        handledFocusRequestNonceState = handledFocusRequestNonceState,
        onSelectDay = onSelectDay,
    )
}

@Composable
internal fun ScheduleCalendarEffects(
    runtime: ScheduleCalendarRuntime,
    focusRequestNonce: Long,
    focusEnabled: Boolean,
) {
    LaunchedEffect(runtime.selectedEpochDay) {
        runtime.synchronizeSelectedDay()
    }
    val shouldRequestFocus = shouldHandleScheduleCalendarFocusRequest(
        focusEnabled = focusEnabled,
        focusRequestNonce = focusRequestNonce,
        handledFocusRequestNonce = runtime.handledFocusRequestNonce,
        hasDays = runtime.dayGroups.isNotEmpty(),
    )
    UiControlEffect(
        focusRequestNonce,
        runtime.dayKeys,
        enabled = shouldRequestFocus,
    ) {
        val targetIndex = runtime.selectedDayIndex().coerceIn(runtime.dayGroups.indices)
        runtime.scrollToDayStart(targetIndex)
        withFrameNanos { }
        runtime.focusRequester.requestFocusSafely()
        runtime.handledFocusRequestNonce = focusRequestNonce
    }
}

internal fun scheduleCalendarTargetDayIndex(
    itemCount: Int,
    currentIndex: Int,
    delta: Int,
): Int? {
    if (itemCount <= 0) return null
    return (currentIndex.coerceIn(0, itemCount - 1) + delta).coerceIn(0, itemCount - 1)
}

private const val NoPendingScheduleCalendarSelection = Long.MIN_VALUE

internal fun shouldHandleScheduleCalendarFocusRequest(
    focusEnabled: Boolean,
    focusRequestNonce: Long,
    handledFocusRequestNonce: Long,
    hasDays: Boolean,
): Boolean {
    if (!focusEnabled) return false
    if (focusRequestNonce <= 0L) return false
    if (focusRequestNonce == handledFocusRequestNonce) return false
    return hasDays
}

internal val ScheduleCalendarPagerBoundary = object : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        return if (available.x != 0f) Offset(x = available.x, y = 0f) else Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        return if (available.x != 0f) Velocity(x = available.x, y = 0f) else Velocity.Zero
    }
}

// ScheduleCalendarState
internal data class VisibleScheduleCalendarItem(
    val index: Int,
    val offsetPx: Int,
    val sizePx: Int,
)

internal enum class ScheduleCalendarEntryType {
    Month,
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
        if (startsMonth) {
            entries += ScheduleCalendarEntry(
                key = "schedule-month-${group.scheduleMonthKey()}-${group.epochDay}",
                type = ScheduleCalendarEntryType.Month,
                monthKey = group.scheduleMonthKey(),
                title = group.scheduleMonthTitle(locale),
                dayIndex = index,
                startsMonth = true,
                endsMonth = false,
            )
        }
        entries += ScheduleCalendarEntry(
            key = "schedule-day-${group.epochDay}",
            type = ScheduleCalendarEntryType.Day,
            monthKey = group.scheduleMonthKey(),
            title = group.scheduleMonthTitle(locale),
            dayIndex = index,
            startsMonth = false,
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

internal fun scheduleCalendarReadableFirstDayIndexForTarget(
    firstDayIndex: Int,
    targetDayIndex: Int,
    dayEntryIndices: IntArray,
    monthSlotWidthPx: Float,
    dayTileWidthPx: Float,
    viewportEndPx: Int,
): Int {
    if (targetDayIndex <= firstDayIndex) return firstDayIndex.coerceAtLeast(0)
    val targetEntryIndex = dayEntryIndices.getOrNull(targetDayIndex)
        ?.takeIf { index -> index >= 0 }
        ?: return firstDayIndex.coerceAtLeast(0)
    var first = firstDayIndex.coerceIn(0, targetDayIndex)
    while (first < targetDayIndex) {
        val firstEntryIndex = dayEntryIndices.getOrNull(first)
            ?.takeIf { index -> index >= 0 }
            ?: return first
        val targetEndPx = monthSlotWidthPx +
            (targetEntryIndex - firstEntryIndex) * monthSlotWidthPx +
            dayTileWidthPx
        if (targetEndPx <= viewportEndPx + 0.5f) return first
        first += 1
    }
    return first
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
