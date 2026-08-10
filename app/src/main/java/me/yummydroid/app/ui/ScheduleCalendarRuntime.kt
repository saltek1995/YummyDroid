package me.yummydroid.app.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ScheduleCalendarRuntime(
    val dayGroups: List<ScheduleDayGroup>,
    val selectedEpochDay: Long,
    val locale: Locale,
    val listState: LazyListState,
    private val scope: CoroutineScope,
    val itemGap: Dp,
    val bottomPadding: Dp,
    val monthSlotWidthPx: Float,
    val dayTileWidthPx: Float,
    val dayKeys: List<Long>,
    val dayFocusRequesters: List<FocusRequester>,
    val entries: List<ScheduleCalendarEntry>,
    private val dayEntryIndices: IntArray,
    private val navigationEpochDayState: MutableLongState,
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

    fun selectedDayIndex(): Int {
        return dayGroups.indexOfFirst { group -> group.epochDay == navigationEpochDay }
            .takeIf { index -> index >= 0 }
            ?: dayGroups.indexOfFirst { group -> group.epochDay == selectedEpochDay }
                .takeIf { index -> index >= 0 }
            ?: 0
    }

    suspend fun scrollToDayStart(dayIndex: Int) {
        val entryIndex = calendarEntryIndexForDay(dayIndex)
        val entry = entries.getOrNull(entryIndex)
        val scrollOffset = if (entry?.startsMonth == true) {
            0
        } else {
            -monthSlotWidthPx.roundToInt()
        }
        listState.scrollToItem(entryIndex, scrollOffset)
    }

    fun selectDayAt(targetIndex: Int, moveFocus: Boolean): Boolean {
        if (dayGroups.isEmpty()) return true
        val boundedIndex = targetIndex.coerceIn(dayGroups.indices)
        val targetDay = dayGroups[boundedIndex].epochDay
        if (navigationEpochDay != targetDay) {
            navigationEpochDay = targetDay
        }
        if (targetDay != selectedEpochDay) {
            onSelectDay(targetDay)
        }
        scope.launch {
            scrollToRevealIndex(boundedIndex)
            if (moveFocus) {
                withFrameNanos { }
                dayFocusRequesters[boundedIndex].requestFocusSafely()
            }
        }
        return true
    }

    fun moveSelectedDay(delta: Int): Boolean {
        return selectDayAt(selectedDayIndex() + delta, moveFocus = true)
    }

    private fun calendarEntryIndexForDay(dayIndex: Int): Int {
        return dayEntryIndices
            .getOrNull(dayIndex)
            ?.takeIf { index -> index >= 0 }
            ?: dayIndex
    }

    private suspend fun scrollToRevealIndex(targetIndex: Int) {
        val layoutInfo = listState.layoutInfo
        val targetFirstIndex = scheduleCalendarEdgeScrollFirstVisibleIndex(
            visibleItems = layoutInfo.visibleItemsInfo.mapNotNull { item ->
                val entry = entries.getOrNull(item.index) ?: return@mapNotNull null
                val itemOffsetPx = if (entry.startsMonth) {
                    item.offset + monthSlotWidthPx.roundToInt()
                } else {
                    item.offset
                }
                VisibleScheduleCalendarItem(
                    index = entry.dayIndex,
                    offsetPx = itemOffsetPx,
                    sizePx = dayTileWidthPx.roundToInt(),
                )
            },
            viewportStartPx = monthSlotWidthPx.roundToInt(),
            viewportEndPx = layoutInfo.viewportSize.width,
            targetIndex = targetIndex,
        )
        if (targetFirstIndex != null) {
            scrollToDayStart(targetFirstIndex)
        }
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
    val scope = rememberCoroutineScope()
    val isWide = currentWindowSizeDp().width >= 720.dp
    val itemGap = if (isWide) ScheduleDayTileWideGap else ScheduleDayTilePhoneGap
    val bottomPadding = if (isWide) ScheduleCalendarWideBottomPadding else ScheduleCalendarPhoneBottomPadding
    val dayKeys = remember(dayGroups) { dayGroups.map { it.epochDay } }
    val focusRequesters = remember(dayKeys) { List(dayKeys.size) { FocusRequester() } }
    val density = LocalDensity.current
    val monthSlotWidthPx = remember(density, itemGap) {
        with(density) { (ScheduleMonthInlineLabelWidth + itemGap).toPx() }
    }
    val dayTileWidthPx = remember(density) {
        with(density) { ScheduleDayTileWidth.toPx() }
    }
    val entries = remember(dayGroups, locale) {
        scheduleCalendarEntries(dayGroups, locale)
    }
    val dayEntryIndices = remember(dayGroups, entries) {
        IntArray(dayGroups.size) { -1 }.also { indices ->
            entries.forEachIndexed { entryIndex, entry ->
                if (entry.dayIndex in indices.indices) {
                    indices[entry.dayIndex] = entryIndex
                }
            }
        }
    }
    val navigationEpochDayState = remember(dayKeys) { mutableLongStateOf(selectedEpochDay) }
    val handledFocusRequestNonceState = remember { mutableLongStateOf(0L) }
    return ScheduleCalendarRuntime(
        dayGroups = dayGroups,
        selectedEpochDay = selectedEpochDay,
        locale = locale,
        listState = listState,
        scope = scope,
        itemGap = itemGap,
        bottomPadding = bottomPadding,
        monthSlotWidthPx = monthSlotWidthPx,
        dayTileWidthPx = dayTileWidthPx,
        dayKeys = dayKeys,
        dayFocusRequesters = focusRequesters,
        entries = entries,
        dayEntryIndices = dayEntryIndices,
        navigationEpochDayState = navigationEpochDayState,
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
        if (
            runtime.selectedEpochDay != Long.MIN_VALUE &&
            runtime.navigationEpochDay != runtime.selectedEpochDay
        ) {
            runtime.navigationEpochDay = runtime.selectedEpochDay
        }
    }
    LaunchedEffect(focusRequestNonce, runtime.dayKeys) {
        if (
            !focusEnabled ||
            focusRequestNonce <= 0L ||
            focusRequestNonce == runtime.handledFocusRequestNonce ||
            runtime.dayGroups.isEmpty()
        ) {
            return@LaunchedEffect
        }
        val targetIndex = runtime.selectedDayIndex().coerceIn(runtime.dayGroups.indices)
        runtime.scrollToDayStart(targetIndex)
        withFrameNanos { }
        runtime.dayFocusRequesters[targetIndex].requestFocusSafely()
        runtime.handledFocusRequestNonce = focusRequestNonce
    }
    LaunchedEffect(runtime.dayKeys) {
        val selectedIndex = runtime.dayGroups.indexOfFirst { group ->
            group.epochDay == runtime.selectedEpochDay
        }
        if (selectedIndex >= 0) {
            runtime.scrollToDayStart(selectedIndex)
        }
    }
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
