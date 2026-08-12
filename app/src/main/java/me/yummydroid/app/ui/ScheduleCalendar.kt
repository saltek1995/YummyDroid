package me.yummydroid.app.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
        onExitUp = onExitUp,
        onExitDown = onExitDown,
    )
}


// ScheduleCalendarRuntime
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
    private val pendingNavigationEpochDayState: MutableState<Long?>,
    private val handledFocusRequestNonceState: MutableLongState,
    private val uiControls: UiControlCoordinator,
    private val controlOwner: Any,
    private val onSelectDay: (Long) -> Unit,
) {
    var navigationEpochDay: Long
        get() = navigationEpochDayState.longValue
        set(value) {
            navigationEpochDayState.longValue = value
        }

    var pendingNavigationEpochDay: Long?
        get() = pendingNavigationEpochDayState.value
        private set(value) {
            pendingNavigationEpochDayState.value = value
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
        pendingNavigationEpochDay = null
        uiControls.launch(scope, controlOwner, UiControlOperation.NavigationLatest) {
            selectDayWhenOwned(boundedIndex, moveFocus)
        }
        return true
    }

    fun moveSelectedDay(delta: Int): Boolean {
        val currentIndex = selectedDayIndex()
        val targetIndex = scheduleCalendarTargetDayIndex(
            itemCount = dayGroups.size,
            currentIndex = currentIndex,
            delta = delta,
        ) ?: return true
        if (targetIndex == currentIndex) return true
        val targetDay = dayGroups[targetIndex].epochDay
        navigationEpochDay = targetDay
        pendingNavigationEpochDay = targetDay
        uiControls.launch(scope, controlOwner, UiControlOperation.NavigationSerial) {
            drainPendingNavigation()
        }
        return true
    }

    fun cancelPendingNavigation() {
        pendingNavigationEpochDay = null
        uiControls.cancel(controlOwner, UiControlOperation.NavigationLatest)
    }

    fun synchronizeSelectedDay() {
        val pendingDay = pendingNavigationEpochDay
        if (pendingDay != null) {
            if (selectedEpochDay == pendingDay) pendingNavigationEpochDay = null
            return
        }
        if (selectedEpochDay != Long.MIN_VALUE && navigationEpochDay != selectedEpochDay) {
            navigationEpochDay = selectedEpochDay
        }
    }

    private suspend fun drainPendingNavigation() {
        try {
            while (true) {
                val targetDay = pendingNavigationEpochDay ?: return
                val targetIndex = dayGroups.indexOfFirst { group -> group.epochDay == targetDay }
                if (targetIndex < 0) {
                    pendingNavigationEpochDay = null
                    return
                }
                selectDayWhenOwned(targetIndex, moveFocus = true)
                if (pendingNavigationEpochDay == targetDay) return
            }
        } catch (cancellation: CancellationException) {
            pendingNavigationEpochDay = null
            throw cancellation
        }
    }

    private suspend fun selectDayWhenOwned(targetIndex: Int, moveFocus: Boolean) {
        val targetDay = dayGroups[targetIndex].epochDay
        navigationEpochDay = targetDay
        val focusedImmediately = moveFocus && dayFocusRequesters[targetIndex].requestFocusSafely()
        if (targetDay != selectedEpochDay) {
            onSelectDay(targetDay)
        }
        scrollToRevealIndex(targetIndex)
        if (moveFocus && !focusedImmediately) {
            requestDayFocusWhenReady(targetIndex)
        }
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

    private suspend fun requestDayFocusWhenReady(targetIndex: Int) {
        repeat(ScheduleCalendarFocusRequestAttempts) {
            withFrameNanos { }
            if (dayFocusRequesters[targetIndex].requestFocusSafely()) return
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
    val uiControls = LocalUiControlCoordinator.current
    val isWide = currentResponsiveWindowSizeDp().width >= 720.dp
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
    val pendingNavigationEpochDayState = remember(dayKeys) { mutableStateOf<Long?>(null) }
    val handledFocusRequestNonceState = remember { mutableLongStateOf(0L) }
    val controlOwner = remember(dayKeys) { Any() }
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
        pendingNavigationEpochDayState = pendingNavigationEpochDayState,
        handledFocusRequestNonceState = handledFocusRequestNonceState,
        uiControls = uiControls,
        controlOwner = controlOwner,
        onSelectDay = onSelectDay,
    )
}

@Composable
internal fun ScheduleCalendarEffects(
    runtime: ScheduleCalendarRuntime,
    focusRequestNonce: Long,
    focusEnabled: Boolean,
) {
    DisposableEffect(runtime.dayFocusRequesters) {
        onDispose { runtime.cancelPendingNavigation() }
    }
    val pendingNavigationEpochDay = runtime.pendingNavigationEpochDay
    LaunchedEffect(runtime.selectedEpochDay, pendingNavigationEpochDay) {
        runtime.synchronizeSelectedDay()
    }
    val shouldRequestFocus = shouldHandleScheduleCalendarFocusRequest(
        focusEnabled = focusEnabled,
        focusRequestNonce = focusRequestNonce,
        handledFocusRequestNonce = runtime.handledFocusRequestNonce,
        hasDays = runtime.dayGroups.isNotEmpty(),
    )
    val selectedIndex = runtime.dayGroups.indexOfFirst { group ->
        group.epochDay == runtime.selectedEpochDay
    }
    UiControlEffect(
        focusRequestNonce,
        runtime.dayKeys,
        runtime.selectedEpochDay,
        enabled = shouldRequestFocus || selectedIndex >= 0,
    ) {
        val targetIndex = if (shouldRequestFocus) {
            runtime.selectedDayIndex().coerceIn(runtime.dayGroups.indices)
        } else {
            selectedIndex
        }
        if (targetIndex >= 0) {
            runtime.scrollToDayStart(targetIndex)
        }
        if (shouldRequestFocus && targetIndex >= 0) {
            withFrameNanos { }
            runtime.dayFocusRequesters[targetIndex].requestFocusSafely()
            runtime.handledFocusRequestNonce = focusRequestNonce
        }
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

private const val ScheduleCalendarFocusRequestAttempts = 8

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
