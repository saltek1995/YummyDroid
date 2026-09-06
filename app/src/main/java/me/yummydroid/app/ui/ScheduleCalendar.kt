package me.yummydroid.app.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.ScheduleAnime
import me.yummydroid.app.ui.components.HorizontalScrollEdgeVisibility
import me.yummydroid.app.ui.components.clearFocusAfterTouch
import me.yummydroid.app.ui.components.horizontalScrollEdgeContentFade
import me.yummydroid.app.ui.components.physicalEdgeContentFade
import me.yummydroid.app.ui.components.rememberHorizontalScrollEdgeVisibility
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor

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
        val scrollOffset = -navigationViewportStartPx(listState.layoutInfo.viewportSize.width)
        listState.scrollToItem(entryIndex, scrollOffset)
    }

    fun selectDayAt(targetIndex: Int, moveFocus: Boolean): Boolean {
        if (dayGroups.isEmpty()) return true
        val boundedIndex = targetIndex.coerceIn(dayGroups.indices)
        val targetDay = dayGroups[boundedIndex].epochDay
        navigationEpochDay = targetDay
        pendingSelectionEpochDay = targetDay
        requestDayVisible(boundedIndex, direction = 0)
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
        requestDayVisible(targetIndex, direction = delta)
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
            requestDayVisible(selectedDayIndex(), direction = 0)
        }
    }

    private fun requestDayVisible(targetIndex: Int, direction: Int) {
        val layoutInfo = listState.layoutInfo
        val visibleItems = currentVisibleDayItems()
        val viewportEndPx = navigationViewportEndPx(layoutInfo.viewportSize.width)
        val viewportStartPx = navigationViewportStartPx(viewportEndPx)
        val scrollAnchor = scheduleCalendarTargetScrollAnchor(
            visibleItems = visibleItems,
            viewportStartPx = viewportStartPx,
            viewportEndPx = viewportEndPx,
            targetIndex = targetIndex,
            direction = direction,
        )
            ?: return
        val entryIndex = calendarEntryIndexForDay(targetIndex)
        val scrollOffset = scheduleCalendarTargetScrollOffsetPx(
            anchor = scrollAnchor,
            viewportStartPx = viewportStartPx,
            viewportEndPx = viewportEndPx,
            dayTileWidthPx = dayTileWidthPx,
        )
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

    private fun navigationViewportStartPx(viewportEndPx: Int): Int {
        val monthSlotEndPx = monthSlotWidthPx.roundToInt()
        if (!listState.canScrollBackward) return monthSlotEndPx
        val readableStartPx = (monthSlotWidthPx + edgeFadeWidthPx).roundToInt()
        return readableStartPx.coerceAtMost(viewportEndPx)
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

internal enum class ScheduleCalendarScrollAnchor {
    Start,
    End,
}

internal fun scheduleCalendarTargetScrollAnchor(
    visibleItems: List<VisibleScheduleCalendarItem>,
    viewportStartPx: Int,
    viewportEndPx: Int,
    targetIndex: Int,
    direction: Int,
): ScheduleCalendarScrollAnchor? {
    val visible = visibleItems.sortedBy { item -> item.index }
    val target = visible.firstOrNull { item -> item.index == targetIndex }
    if (target != null) {
        val targetEndPx = target.offsetPx + target.sizePx
        if (target.offsetPx >= viewportStartPx && targetEndPx <= viewportEndPx) return null
        return when {
            target.offsetPx < viewportStartPx -> ScheduleCalendarScrollAnchor.Start
            targetEndPx > viewportEndPx -> ScheduleCalendarScrollAnchor.End
            direction > 0 -> ScheduleCalendarScrollAnchor.End
            else -> ScheduleCalendarScrollAnchor.Start
        }
    }
    val first = visible.firstOrNull() ?: return if (direction > 0) {
        ScheduleCalendarScrollAnchor.End
    } else {
        ScheduleCalendarScrollAnchor.Start
    }
    val last = visible.last()
    return when {
        targetIndex <= first.index -> ScheduleCalendarScrollAnchor.Start
        targetIndex >= last.index -> ScheduleCalendarScrollAnchor.End
        direction > 0 -> ScheduleCalendarScrollAnchor.End
        else -> ScheduleCalendarScrollAnchor.Start
    }
}

internal fun scheduleCalendarTargetScrollOffsetPx(
    anchor: ScheduleCalendarScrollAnchor,
    viewportStartPx: Int,
    viewportEndPx: Int,
    dayTileWidthPx: Float,
): Int {
    val targetLeftPx = when (anchor) {
        ScheduleCalendarScrollAnchor.Start -> viewportStartPx
        ScheduleCalendarScrollAnchor.End -> (viewportEndPx - dayTileWidthPx.roundToInt())
            .coerceAtLeast(viewportStartPx)
    }
    return -targetLeftPx
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

// ScheduleCalendarCards
@Composable
internal fun ScheduleRow(
    item: ScheduleAnime,
    timeFormatter: DateTimeFormatter,
    onOpenAnime: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metaText = uiText(UiStringKey.EpisodeIsAlreadyOut, item.airedEpisodes)
    val scheduleTime = remember(item.nextEpisodeAtSeconds, item.previousEpisodeAtSeconds, timeFormatter) {
        item.formatScheduleTime(timeFormatter)
    }
    AnimeCard(
        anime = item.anime,
        onClick = { onOpenAnime(item.anime.id) },
        metaText = metaText,
        topEndContent = {
            ScheduleTimeBadge(time = scheduleTime)
        },
        modifier = modifier,
    )
}

@Composable
private fun ScheduleTimeBadge(time: String) {
    Surface(
        shape = YummyRadii.smallShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.94f),
        contentColor = Color(0xFF211200),
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

internal fun scheduleCalendarDayLayerBackwardEdgeInset(
    itemGap: Dp,
): Dp {
    return ScheduleMonthInlineLabelWidth + itemGap
}

internal fun scheduleCalendarMonthDragListDeltaPx(dragDeltaPx: Float): Float {
    return -dragDeltaPx
}

internal fun scheduleCalendarMonthDragConsumedPx(consumedListDeltaPx: Float): Float {
    return -consumedListDeltaPx
}

@Composable
private fun rememberScheduleCalendarMonthDragState(
    listState: LazyListState,
): ScrollableState {
    return rememberScrollableState { dragDeltaPx ->
        val consumedListDeltaPx = listState.dispatchRawDelta(
            scheduleCalendarMonthDragListDeltaPx(dragDeltaPx),
        )
        scheduleCalendarMonthDragConsumedPx(consumedListDeltaPx)
    }
}

@Composable
private fun BoxScope.ScheduleCalendarMonthChipLayer(
    chips: List<ScheduleCalendarMonthChip>,
    viewportEndPx: Float,
    dragState: ScrollableState,
    fadeBeforeRightEdge: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val chipWidthPx = remember(density) {
        with(density) { ScheduleMonthInlineLabelWidth.toPx() }
    }
    val fadeWidthPx = scheduleCalendarBoundaryFadeWidthPx(chipWidthPx)
    Box(modifier = modifier.matchParentSize()) {
        chips.forEach { chip ->
            ScheduleMonthInlineChip(
                title = chip.title,
                modifier = Modifier
                    .offset(
                        x = with(density) { chip.offsetPx.toDp() },
                    )
                    .scrollable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                    )
                    .physicalEdgeContentFade(
                        offsetPx = chip.offsetPx,
                        itemWidthPx = chipWidthPx,
                        viewportEndPx = viewportEndPx,
                        fadeWidthPx = fadeWidthPx,
                        fadeBeforeLeftEdge = false,
                        fadeBeforeRightEdge = fadeBeforeRightEdge,
                    ),
            )
        }
    }
}

@Composable
internal fun ScheduleCalendarMonthLayer(
    monthOverlay: ScheduleCalendarMonthOverlay?,
    viewportEndPx: Float,
    edgeVisibility: HorizontalScrollEdgeVisibility,
    dragState: ScrollableState,
    modifier: Modifier = Modifier,
) {
    val resolvedMonthOverlay = monthOverlay ?: return
    val fixedChips = remember(resolvedMonthOverlay) {
        resolvedMonthOverlay.chips.filter { chip -> chip.isFixedAtMonthSlot() }
    }
    val scrollingChips = remember(resolvedMonthOverlay) {
        resolvedMonthOverlay.chips.filterNot { chip -> chip.isFixedAtMonthSlot() }
    }
    val scrollingEdgeVisibility = remember(edgeVisibility) {
        scheduleCalendarMonthLayerEdgeVisibility(edgeVisibility)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ScheduleDayTileHeight)
            .clipToBounds(),
    ) {
        ScheduleCalendarMonthChipLayer(
            chips = scrollingChips,
            viewportEndPx = viewportEndPx,
            dragState = dragState,
            fadeBeforeRightEdge = false,
            modifier = Modifier.horizontalScrollEdgeContentFade(
                visibility = scrollingEdgeVisibility,
                edgeWidth = ScheduleCalendarEdgeFadeWidth,
            ),
        )
        ScheduleCalendarMonthChipLayer(
            chips = fixedChips,
            viewportEndPx = viewportEndPx,
            dragState = dragState,
            fadeBeforeRightEdge = false,
        )
    }
}

@Composable
internal fun ScheduleMonthInlineChip(
    title: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(ScheduleMonthInlineLabelWidth)
            .height(ScheduleDayTileHeight),
        color = yummyActionSurfaceColor(),
        contentColor = yummyActionContentColor(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth(0.72f)
                    .height(ScheduleMonthInlineLabelAccentHeight)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = YummyRadii.pillShape,
                    ),
            )
        }
    }
}

internal val ScheduleDayTileWidth = 96.dp
internal val ScheduleDayTileHeight = 78.dp
internal val ScheduleDayTilePhoneGap = BrowseChromeItemGap
internal val ScheduleDayTileWideGap = BrowseChromeItemGap
internal val ScheduleCalendarOuterHorizontalPadding = 0.dp
internal val ScheduleCalendarHorizontalPadding = 0.dp
internal val ScheduleMonthInlineLabelWidth = ScheduleDayTileWidth
internal val ScheduleCalendarEdgeFadeWidth = ScheduleDayTileWidth + 32.dp
private val ScheduleMonthInlineLabelAccentHeight = 2.dp
internal val ScheduleCalendarPhoneBottomPadding = 0.dp
internal val ScheduleCalendarWideBottomPadding = 0.dp
internal val ScheduleCalendarTopGap = 0.dp

@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal val ScheduleCalendarBringIntoViewSpec = object : BringIntoViewSpec {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(
        durationMillis = 420,
        easing = FastOutSlowInEasing,
    )

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}

// ScheduleCalendarContent
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ScheduleCalendarContent(
    runtime: ScheduleCalendarRuntime,
    modifier: Modifier,
    focusEnabled: Boolean,
    onCalendarFocusChanged: (Boolean) -> Unit,
    onExitUp: () -> Boolean,
    onExitDown: () -> Boolean,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScheduleCalendarOuterHorizontalPadding)
            .nestedScroll(ScheduleCalendarPagerBoundary),
        color = Color.Transparent,
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Panel),
    ) {
        Column {
            if (runtime.dayGroups.isEmpty()) {
                ScheduleCalendarEmptyState()
            } else {
                ScheduleCalendarDayList(
                    runtime = runtime,
                    focusEnabled = focusEnabled,
                    onCalendarFocusChanged = onCalendarFocusChanged,
                    onExitUp = onExitUp,
                    onExitDown = onExitDown,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ScheduleCalendarDayList(
    runtime: ScheduleCalendarRuntime,
    focusEnabled: Boolean,
    onCalendarFocusChanged: (Boolean) -> Unit,
    onExitUp: () -> Boolean,
    onExitDown: () -> Boolean,
) {
    val monthOverlay by rememberScheduleCalendarMonthOverlay(runtime)
    val dayLayerBackwardEdgeInset = scheduleCalendarDayLayerBackwardEdgeInset(runtime.itemGap)
    val edgeVisibility = rememberHorizontalScrollEdgeVisibility(
        state = runtime.listState,
        edgeWidth = ScheduleCalendarEdgeFadeWidth,
        backwardEdgeInset = dayLayerBackwardEdgeInset,
    )
    val monthDragState = rememberScheduleCalendarMonthDragState(runtime.listState)
    var calendarFocused by remember { mutableStateOf(false) }
    val inputModeManager = LocalInputModeManager.current
    val showFocusedSelection = calendarFocused && inputModeManager.inputMode != InputMode.Touch
    Box(modifier = Modifier.fillMaxWidth()) {
        ScheduleCalendarMonthLayer(
            monthOverlay = monthOverlay,
            viewportEndPx = runtime.listState.layoutInfo.viewportSize.width.toFloat(),
            edgeVisibility = edgeVisibility,
            dragState = monthDragState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .zIndex(1f)
                .focusProperties { canFocus = false },
        )
        CompositionLocalProvider(LocalBringIntoViewSpec provides ScheduleCalendarBringIntoViewSpec) {
            LazyRow(
                state = runtime.listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScrollEdgeContentFade(
                        visibility = edgeVisibility,
                        edgeWidth = ScheduleCalendarEdgeFadeWidth,
                        backwardEdgeInset = dayLayerBackwardEdgeInset,
                    )
                    .focusProperties { canFocus = focusEnabled }
                    .focusRequester(runtime.focusRequester)
                    .onFocusChanged { focusState ->
                        calendarFocused = focusState.isFocused || focusState.hasFocus
                        onCalendarFocusChanged(calendarFocused)
                    }
                    .scheduleDayTileKeyNavigation(
                        onMovePrevious = { runtime.moveSelectedDay(-1) },
                        onMoveNext = { runtime.moveSelectedDay(1) },
                        onExitUp = onExitUp,
                        onExitDown = onExitDown,
                    )
                    .focusable(),
                contentPadding = PaddingValues(
                    start = 0.dp,
                    top = 0.dp,
                    end = ScheduleCalendarHorizontalPadding,
                    bottom = runtime.bottomPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(runtime.itemGap),
            ) {
                runtime.entries.forEach { entry ->
                    item(key = entry.key, contentType = entry.type) {
                        ScheduleCalendarDayLayerSlot(
                            runtime = runtime,
                            entry = entry,
                            showFocusedSelection = showFocusedSelection,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleCalendarDayLayerSlot(
    runtime: ScheduleCalendarRuntime,
    entry: ScheduleCalendarEntry,
    showFocusedSelection: Boolean,
) {
    when (entry.type) {
        ScheduleCalendarEntryType.Month -> ScheduleCalendarEmptySlot()
        ScheduleCalendarEntryType.Day -> ScheduleCalendarDayEntry(
            runtime = runtime,
            index = entry.dayIndex,
            showFocusedSelection = showFocusedSelection,
        )
    }
}

@Composable
private fun ScheduleCalendarEmptySlot() {
    Box(
        modifier = Modifier
            .width(ScheduleDayTileWidth)
            .height(ScheduleDayTileHeight)
            .focusProperties { canFocus = false },
    )
}

@Composable
private fun ScheduleCalendarDayEntry(
    runtime: ScheduleCalendarRuntime,
    index: Int,
    showFocusedSelection: Boolean,
) {
    val group = runtime.dayGroups.getOrNull(index) ?: return
    ScheduleDayTile(
        group = group,
        selected = group.epochDay == runtime.navigationEpochDay,
        focused = showFocusedSelection && group.epochDay == runtime.navigationEpochDay,
        locale = runtime.locale,
        onClick = { runtime.selectDayAt(index, moveFocus = false) },
    )
}

@Composable
private fun ScheduleCalendarEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = uiText(UiStringKey.NoUpcomingReleasesYet),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ScheduleCalendarDayTile
@Composable
internal fun ScheduleDayTile(
    group: ScheduleDayGroup,
    selected: Boolean,
    focused: Boolean,
    locale: Locale,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val dayContentColor = yummyActionContentColor(selected = selected, focused = focused)
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(ScheduleDayTileWidth),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(ScheduleDayTileHeight)
                .focusProperties { canFocus = false }
                .clearFocusAfterTouch(navigable = false)
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            color = yummyActionSurfaceColor(selected = selected, focused = focused),
            contentColor = dayContentColor,
            border = yummyActionBorder(selected = selected, focused = focused),
            shape = shape,
        ) {
            ScheduleDayTileContent(
                group = group,
                locale = locale,
                focusVisible = focused,
                dayContentColor = dayContentColor,
            )
        }
    }
}

@Composable
private fun ScheduleDayTileContent(
    group: ScheduleDayGroup,
    locale: Locale,
    focusVisible: Boolean,
    dayContentColor: Color,
) {
    val dayOfWeek = remember(group.date, locale) {
        group.date.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
            .replace(".", "")
            .replaceFirstChar { char -> char.uppercase(locale) }
    }
    val isWeekend = remember(group.date) { group.date.dayOfWeek.value >= 6 }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = dayOfWeek,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = if (focusVisible) dayContentColor else if (isWeekend) Color(0xFFFF626B) else dayContentColor,
            )
            Text(
                text = group.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = dayContentColor,
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 2.dp, end = 2.dp),
            shape = YummyRadii.pillShape,
            color = YummyColors.offline,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ) {
            Text(
                text = group.items.size.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

private fun Modifier.scheduleDayTileKeyNavigation(
    onMovePrevious: () -> Boolean,
    onMoveNext: () -> Boolean,
    onExitUp: () -> Boolean,
    onExitDown: () -> Boolean,
): Modifier = navigationKeyPolicy { event ->
    if (event.type != KeyEventType.KeyDown) return@navigationKeyPolicy false
    handleManagedDpadNavigationKey(event.key) { direction ->
        when (direction) {
            VisualGridDirection.Left -> onMovePrevious()
            VisualGridDirection.Right -> onMoveNext()
            VisualGridDirection.Up -> onExitUp()
            VisualGridDirection.Down -> onExitDown()
        }
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

// BrowseScheduleFilter
internal fun upcomingScheduleItems(
    items: List<ScheduleAnime>,
    nowSeconds: Long = System.currentTimeMillis() / 1000L,
): List<ScheduleAnime> = items.filter { item -> item.nextEpisodeAtSeconds > nowSeconds }

// BrowseScheduleGrid
@Composable
internal fun ScheduleSection(
    state: LoadState<List<ScheduleAnime>>,
    precomputedDayGroups: List<ScheduleDayGroup>? = null,
    gridState: LazyGridState,
    cardSize: PosterCardSize,
    locale: Locale,
    focusFirstRequest: FocusFirstRequest,
    focusCurrentRequestNonce: Long,
    calendarFocusRequestNonce: Long = 0L,
    contentFocusEnabled: Boolean = true,
    showCalendarInGrid: Boolean = true,
    selectedEpochDay: Long,
    onSelectedEpochDayChange: (Long) -> Unit,
    currentFocusedIndex: () -> Int,
    onFocusedIndexChange: (Int) -> Unit,
    pinnedTopPadding: Dp = 0.dp,
    contentBottomPadding: Dp = 0.dp,
    onRegisterBackToTopHandler: ((HomeBackToTopHandler?) -> Unit)? = null,
    onRetry: () -> Unit,
    onExitHorizontalDirection: (VisualGridDirection) -> Boolean = { true },
    onExitUp: () -> Boolean = { false },
    onExitDown: () -> Boolean = { false },
    onOpenAnime: (Long) -> Unit,
) {
    when (state) {
        LoadState.Loading -> LoadingPane(Modifier.fillMaxSize())
        is LoadState.Error -> ErrorPane(
            message = state.message,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
        )
        is LoadState.Ready -> ScheduleReadySection(
            schedule = state.data,
            precomputedDayGroups = precomputedDayGroups,
            gridState = gridState,
            cardSize = cardSize,
            locale = locale,
            focusFirstRequest = focusFirstRequest,
            focusCurrentRequestNonce = focusCurrentRequestNonce,
            calendarFocusRequestNonce = calendarFocusRequestNonce,
            contentFocusEnabled = contentFocusEnabled,
            showCalendarInGrid = showCalendarInGrid,
            selectedEpochDay = selectedEpochDay,
            onSelectedEpochDayChange = onSelectedEpochDayChange,
            currentFocusedIndex = currentFocusedIndex,
            onFocusedIndexChange = onFocusedIndexChange,
            pinnedTopPadding = pinnedTopPadding,
            contentBottomPadding = contentBottomPadding,
            onRegisterBackToTopHandler = onRegisterBackToTopHandler,
            onExitHorizontalDirection = onExitHorizontalDirection,
            onExitUp = onExitUp,
            onExitDown = onExitDown,
            onOpenAnime = onOpenAnime,
        )
    }
}

// BrowseScheduleReadyContent
@Composable
internal fun ScheduleReadyContent(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    layout: ScheduleReadyLayout,
    actions: ScheduleReadyActions,
    internalCalendarFocusRequestNonce: Long,
    suppressCalendarFocusAfterBackToTop: Boolean,
) {
    if (params.schedule.isEmpty()) {
        EmptyPane(message = uiText(UiStringKey.ScheduleIsEmpty), modifier = Modifier.fillMaxSize())
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (data.dayGroups.isEmpty() || data.visibleItems.isEmpty()) {
            ScheduleNoUpcomingReleases(params)
        } else {
            ScheduleReadyGrid(
                params = params,
                data = data,
                layout = layout,
                actions = actions,
                internalCalendarFocusRequestNonce = internalCalendarFocusRequestNonce,
                suppressCalendarFocusAfterBackToTop = suppressCalendarFocusAfterBackToTop,
            )
        }
    }
}

@Composable
private fun ScheduleNoUpcomingReleases(params: ScheduleReadyParams) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = params.pinnedTopPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = uiText(UiStringKey.NoUpcomingReleasesYet),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ScheduleReadyGrid(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    layout: ScheduleReadyLayout,
    actions: ScheduleReadyActions,
    internalCalendarFocusRequestNonce: Long,
    suppressCalendarFocusAfterBackToTop: Boolean,
) {
    BrowseGridScrollLocalProvider(touchOverscrollEnabled = layout.touchOverscrollEnabled) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(layout.columnsCount),
            state = params.gridState,
            modifier = Modifier.navigationScrollRegion(params.gridState)
                .fillMaxSize()
                .browseTouchBounceOverscroll(
                    enabled = layout.touchOverscrollEnabled,
                    gridState = params.gridState,
                )
                .focusGroup(),
            contentPadding = PaddingValues(
                start = layout.gridHorizontalPadding,
                top = layout.gridTopContentPadding,
                end = layout.gridHorizontalPadding,
                bottom = layout.gridBottomContentPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(BrowseGridHorizontalGap),
            verticalArrangement = Arrangement.spacedBy(layout.gridVerticalGap),
        ) {
            scheduleCalendarItem(
                params = params,
                data = data,
                actions = actions,
                internalFocusRequestNonce = internalCalendarFocusRequestNonce,
                suppressFocusAfterBackToTop = suppressCalendarFocusAfterBackToTop,
            )
            scheduleCards(params, data, layout, actions)
        }
    }
}

private fun LazyGridScope.scheduleCalendarItem(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    actions: ScheduleReadyActions,
    internalFocusRequestNonce: Long,
    suppressFocusAfterBackToTop: Boolean,
) {
    if (!params.showCalendarInGrid) return
    item(
        key = "schedule-calendar",
        span = { GridItemSpan(maxLineSpan) },
        contentType = "schedule-calendar",
    ) {
        ScheduleCalendarBlock(
            dayGroups = data.dayGroups,
            selectedEpochDay = data.selectedGroup?.epochDay ?: Long.MIN_VALUE,
            locale = params.locale,
            focusRequestNonce = params.calendarFocusRequestNonce * 1_000_000L + internalFocusRequestNonce,
            focusEnabled = params.contentFocusEnabled && !suppressFocusAfterBackToTop,
            onExitUp = params.onExitUp,
            onExitDown = actions::requestContentFocus,
            onSelectDay = actions::selectDay,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun LazyGridScope.scheduleCards(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    layout: ScheduleReadyLayout,
    actions: ScheduleReadyActions,
) {
    itemsIndexed(
        items = data.visibleItems,
        key = { _, item -> item.anime.id },
        contentType = { _, _ -> "schedule-card" },
    ) { index, item ->
        ScheduleRow(
            item = item,
            timeFormatter = data.timeFormatter,
            onOpenAnime = params.onOpenAnime,
            modifier = Modifier
                .focusProperties { canFocus = params.contentFocusEnabled }
                .focusRequester(layout.itemFocusRequesters[index])
                .navigationKeyPolicy { event ->
                    event.type == KeyEventType.KeyDown && actions.handleGridDirection(index, event.key)
                }
                .onFocusChanged { focusState ->
                    if (focusState.hasFocus) actions.updateFocusedIndex(index)
                },
        )
    }
}

// BrowseScheduleReadyEffects
@Composable
internal fun ScheduleReadyEffects(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    layout: ScheduleReadyLayout,
    actions: ScheduleReadyActions,
    focusController: BrowseGridFocusController,
    handledPersistentFocusResetNonce: Long,
    onHandledPersistentFocusResetNonceChange: (Long) -> Unit,
    handledTransientFocusResetNonce: Long,
    onHandledTransientFocusResetNonceChange: (Long) -> Unit,
    handledCurrentFocusRequestNonce: Long,
    onHandledCurrentFocusRequestNonceChange: (Long) -> Unit,
) {
    ScheduleDaySelectionEffect(params, data, actions)
    ScheduleBackToTopRegistrationEffect(params, data, actions)
    ScheduleFocusEffect(
        params = params,
        data = data,
        layout = layout,
        actions = actions,
        focusController = focusController,
        handledPersistentNonce = handledPersistentFocusResetNonce,
        onHandledPersistentNonceChange = onHandledPersistentFocusResetNonceChange,
        handledTransientNonce = handledTransientFocusResetNonce,
        onHandledTransientNonceChange = onHandledTransientFocusResetNonceChange,
        handledCurrentNonce = handledCurrentFocusRequestNonce,
        onHandledCurrentNonceChange = onHandledCurrentFocusRequestNonceChange,
    )
    ScheduleFocusedIndexEffect(params, data, actions)
}

@Composable
private fun ScheduleDaySelectionEffect(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    actions: ScheduleReadyActions,
) {
    LaunchedEffect(data.dayGroupKeys) {
        if (data.dayGroups.isEmpty()) {
            params.onSelectedEpochDayChange(Long.MIN_VALUE)
            actions.updateFocusedIndex(-1)
            return@LaunchedEffect
        }
        if (data.dayGroups.none { group -> group.epochDay == params.selectedEpochDay }) {
            val fallbackDay = data.dayGroups.todayOrClosest()?.epochDay ?: data.dayGroups.first().epochDay
            params.onSelectedEpochDayChange(fallbackDay)
            actions.updateFocusedIndex(0)
        }
    }
}

@Composable
private fun ScheduleBackToTopRegistrationEffect(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    actions: ScheduleReadyActions,
) {
    DisposableEffect(data.visibleItems.size, params.onRegisterBackToTopHandler) {
        val register = params.onRegisterBackToTopHandler
        if (register != null && data.visibleItems.isNotEmpty()) {
            register(
                HomeBackToTopHandler(
                    section = BrowseSection.Schedule,
                    canHandle = actions::canHandleBackToTop,
                    handle = actions::handleBackToTop,
                ),
            )
        } else {
            register?.invoke(null)
        }
        onDispose { register?.invoke(null) }
    }
}

@Composable
private fun ScheduleFocusEffect(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    layout: ScheduleReadyLayout,
    actions: ScheduleReadyActions,
    focusController: BrowseGridFocusController,
    handledPersistentNonce: Long,
    onHandledPersistentNonceChange: (Long) -> Unit,
    handledTransientNonce: Long,
    onHandledTransientNonceChange: (Long) -> Unit,
    handledCurrentNonce: Long,
    onHandledCurrentNonceChange: (Long) -> Unit,
) {
    val persistentNonce = params.focusFirstRequest.persistentNonce
    val transientNonce = params.focusFirstRequest.transientNonce
    val request = scheduleFocusRequest(
        visibleItemsCount = data.visibleItems.size,
        persistentNonce = persistentNonce,
        handledPersistentNonce = handledPersistentNonce,
        transientNonce = transientNonce,
        handledTransientNonce = handledTransientNonce,
    )
    val shouldFocusCurrent = shouldRequestBrowseCurrentFocus(
        contentFocusEnabled = params.contentFocusEnabled,
        requestNonce = params.focusCurrentRequestNonce,
        handledNonce = handledCurrentNonce,
        itemCount = data.visibleItems.size,
    )
    UiControlEffect(
        params.focusFirstRequest,
        params.focusCurrentRequestNonce,
        data.visibleItems.size,
        layout.columnsCount,
        enabled = request.shouldFocusFirst || shouldFocusCurrent,
    ) {
        focusController.cancelPendingRequest()
        if (request.shouldFocusFirst) {
            focusFirstScheduleItem(
                request = request,
                actions = actions,
                focusController = focusController,
                focusCurrentRequestNonce = params.focusCurrentRequestNonce,
                shouldFocusCurrent = shouldFocusCurrent,
                onHandledPersistentNonceChange = onHandledPersistentNonceChange,
                onHandledTransientNonceChange = onHandledTransientNonceChange,
                onHandledCurrentNonceChange = onHandledCurrentNonceChange,
            )
            return@UiControlEffect
        }
        focusCurrentScheduleItem(
            params = params,
            data = data,
            layout = layout,
            actions = actions,
            focusController = focusController,
            onHandledCurrentNonceChange = onHandledCurrentNonceChange,
        )
    }
}

private data class ScheduleFocusRequest(
    val persistentNonce: Long,
    val transientNonce: Long,
    val handlePersistent: Boolean,
    val handleTransient: Boolean,
) {
    val shouldFocusFirst: Boolean = handlePersistent || handleTransient
}

private fun scheduleFocusRequest(
    visibleItemsCount: Int,
    persistentNonce: Long,
    handledPersistentNonce: Long,
    transientNonce: Long,
    handledTransientNonce: Long,
): ScheduleFocusRequest {
    val hasVisibleItems = visibleItemsCount > 0
    return ScheduleFocusRequest(
        persistentNonce = persistentNonce,
        transientNonce = transientNonce,
        handlePersistent = hasVisibleItems && persistentNonce.isUnhandledFocusNonce(handledPersistentNonce),
        handleTransient = hasVisibleItems && transientNonce.isUnhandledFocusNonce(handledTransientNonce),
    )
}

private fun Long.isUnhandledFocusNonce(handledNonce: Long): Boolean {
    return this > 0L && this != handledNonce
}

private suspend fun focusFirstScheduleItem(
    request: ScheduleFocusRequest,
    actions: ScheduleReadyActions,
    focusController: BrowseGridFocusController,
    focusCurrentRequestNonce: Long,
    shouldFocusCurrent: Boolean,
    onHandledPersistentNonceChange: (Long) -> Unit,
    onHandledTransientNonceChange: (Long) -> Unit,
    onHandledCurrentNonceChange: (Long) -> Unit,
) {
    actions.updateFocusedIndex(0)
    focusController.focusItemWhenVisible(0)
    if (request.handlePersistent) onHandledPersistentNonceChange(request.persistentNonce)
    if (request.handleTransient) onHandledTransientNonceChange(request.transientNonce)
    if (shouldFocusCurrent) onHandledCurrentNonceChange(focusCurrentRequestNonce)
}

private suspend fun focusCurrentScheduleItem(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    layout: ScheduleReadyLayout,
    actions: ScheduleReadyActions,
    focusController: BrowseGridFocusController,
    onHandledCurrentNonceChange: (Long) -> Unit,
) {
    withFrameNanos { }
    val targetIndex = scheduleCurrentFocusTargetIndex(
        currentFocusedIndex = params.currentFocusedIndex(),
        firstVisibleItemIndex = params.gridState.firstVisibleItemIndex,
        leadingGridItemCount = layout.leadingGridItemCount,
        lastIndex = data.visibleItems.lastIndex,
    )
    actions.updateFocusedIndex(targetIndex)
    focusController.focusItemWhenVisible(targetIndex)
    onHandledCurrentNonceChange(params.focusCurrentRequestNonce)
}

private fun scheduleCurrentFocusTargetIndex(
    currentFocusedIndex: Int,
    firstVisibleItemIndex: Int,
    leadingGridItemCount: Int,
    lastIndex: Int,
): Int {
    val focusedGridIndex = currentFocusedIndex.takeIf { index -> index in 0..lastIndex }
    return (
        focusedGridIndex ?: (firstVisibleItemIndex - leadingGridItemCount)
            .coerceIn(0, lastIndex)
        ).coerceIn(0, lastIndex)
}

@Composable
private fun ScheduleFocusedIndexEffect(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    actions: ScheduleReadyActions,
) {
    LaunchedEffect(data.visibleItems.size) {
        actions.updateFocusedIndex(
            normalizedScheduleFocusedIndex(
                itemCount = data.visibleItems.size,
                currentIndex = params.currentFocusedIndex(),
            ),
        )
    }
}

// BrowseScheduleReadyGrid
@Composable
internal fun ScheduleReadyGridRoot(params: ScheduleReadyParams) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val data = rememberScheduleReadyData(params)
        val layout = rememberScheduleReadyLayout(params, data, maxWidth, maxHeight)
        ScheduleReadyCoordinator(params, data, layout)
    }
}

// BrowseScheduleReadyRuntime
@Composable
internal fun ScheduleReadyCoordinator(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    layout: ScheduleReadyLayout,
) {
    val focusScope = rememberCoroutineScope()
    val uiControls = LocalAppNavigationController.current
    var internalCalendarFocusRequestNonce by remember(data.scheduleDayKey) { mutableLongStateOf(0L) }
    var handledPersistentFocusResetNonce by remember { mutableLongStateOf(0L) }
    var handledTransientFocusResetNonce by remember { mutableLongStateOf(0L) }
    var handledCurrentFocusRequestNonce by remember { mutableLongStateOf(0L) }
    var suppressCalendarFocusAfterBackToTop by remember(data.scheduleDayKey) { mutableStateOf(false) }
    val focusRequestJob = remember(layout.columnsCount, uiControls) { FocusRequestJobRef(uiControls) }
    val updateFocusedIndex = { index: Int ->
        if (params.currentFocusedIndex() != index) params.onFocusedIndexChange(index)
    }
    val focusController = browseGridFocusController(
        gridState = params.gridState,
        itemFocusRequesters = layout.itemFocusRequesters,
        columns = layout.columnsCount,
        leadingGridItemCount = layout.leadingGridItemCount,
        currentFocusedIndex = params.currentFocusedIndex,
        updateFocusedIndex = updateFocusedIndex,
        protectedTopPx = layout.focusedGridTopInsetPx,
        protectedBottomPx = layout.focusedGridBottomInsetPx,
        focusedItemHeightPx = layout.focusedGridItemHeightPx,
        focusScope = focusScope,
        focusRequestJob = focusRequestJob,
    )
    val actions = ScheduleReadyActions(
        params = params,
        data = data,
        layout = layout,
        focusController = focusController,
        focusScope = focusScope,
        uiControls = uiControls,
        setSuppressCalendarFocusAfterBackToTop = { suppressCalendarFocusAfterBackToTop = it },
        incrementCalendarFocusNonce = { internalCalendarFocusRequestNonce += 1L },
    )

    ScheduleReadyEffects(
        params = params,
        data = data,
        layout = layout,
        actions = actions,
        focusController = focusController,
        handledPersistentFocusResetNonce = handledPersistentFocusResetNonce,
        onHandledPersistentFocusResetNonceChange = { handledPersistentFocusResetNonce = it },
        handledTransientFocusResetNonce = handledTransientFocusResetNonce,
        onHandledTransientFocusResetNonceChange = { handledTransientFocusResetNonce = it },
        handledCurrentFocusRequestNonce = handledCurrentFocusRequestNonce,
        onHandledCurrentFocusRequestNonceChange = { handledCurrentFocusRequestNonce = it },
    )
    ScheduleReadyContent(
        params = params,
        data = data,
        layout = layout,
        actions = actions,
        internalCalendarFocusRequestNonce = internalCalendarFocusRequestNonce,
        suppressCalendarFocusAfterBackToTop = suppressCalendarFocusAfterBackToTop,
    )
}

// BrowseScheduleReadySection
@Composable
internal fun ScheduleReadySection(
    schedule: List<ScheduleAnime>,
    precomputedDayGroups: List<ScheduleDayGroup>? = null,
    gridState: LazyGridState,
    cardSize: PosterCardSize,
    locale: Locale,
    focusFirstRequest: FocusFirstRequest,
    focusCurrentRequestNonce: Long,
    calendarFocusRequestNonce: Long = 0L,
    contentFocusEnabled: Boolean = true,
    showCalendarInGrid: Boolean = true,
    selectedEpochDay: Long,
    onSelectedEpochDayChange: (Long) -> Unit,
    currentFocusedIndex: () -> Int,
    onFocusedIndexChange: (Int) -> Unit,
    pinnedTopPadding: Dp = 0.dp,
    contentBottomPadding: Dp = 0.dp,
    onRegisterBackToTopHandler: ((HomeBackToTopHandler?) -> Unit)? = null,
    onExitHorizontalDirection: (VisualGridDirection) -> Boolean = { true },
    onExitUp: () -> Boolean = { false },
    onExitDown: () -> Boolean = { false },
    onOpenAnime: (Long) -> Unit,
) {
    ScheduleReadyGridRoot(
        ScheduleReadyParams(
            schedule = schedule,
            precomputedDayGroups = precomputedDayGroups,
            gridState = gridState,
            cardSize = cardSize,
            locale = locale,
            focusFirstRequest = focusFirstRequest,
            focusCurrentRequestNonce = focusCurrentRequestNonce,
            calendarFocusRequestNonce = calendarFocusRequestNonce,
            contentFocusEnabled = contentFocusEnabled,
            showCalendarInGrid = showCalendarInGrid,
            selectedEpochDay = selectedEpochDay,
            onSelectedEpochDayChange = onSelectedEpochDayChange,
            currentFocusedIndex = currentFocusedIndex,
            onFocusedIndexChange = onFocusedIndexChange,
            pinnedTopPadding = pinnedTopPadding,
            contentBottomPadding = contentBottomPadding,
            onRegisterBackToTopHandler = onRegisterBackToTopHandler,
            onExitHorizontalDirection = onExitHorizontalDirection,
            onExitUp = onExitUp,
            onExitDown = onExitDown,
            onOpenAnime = onOpenAnime,
        ),
    )
}

// BrowseScheduleReadyState
internal data class ScheduleReadyParams(
    val schedule: List<ScheduleAnime>,
    val precomputedDayGroups: List<ScheduleDayGroup>?,
    val gridState: LazyGridState,
    val cardSize: PosterCardSize,
    val locale: Locale,
    val focusFirstRequest: FocusFirstRequest,
    val focusCurrentRequestNonce: Long,
    val calendarFocusRequestNonce: Long,
    val contentFocusEnabled: Boolean,
    val showCalendarInGrid: Boolean,
    val selectedEpochDay: Long,
    val onSelectedEpochDayChange: (Long) -> Unit,
    val currentFocusedIndex: () -> Int,
    val onFocusedIndexChange: (Int) -> Unit,
    val pinnedTopPadding: Dp,
    val contentBottomPadding: Dp,
    val onRegisterBackToTopHandler: ((HomeBackToTopHandler?) -> Unit)?,
    val onExitHorizontalDirection: (VisualGridDirection) -> Boolean,
    val onExitUp: () -> Boolean,
    val onExitDown: () -> Boolean,
    val onOpenAnime: (Long) -> Unit,
)

internal data class ScheduleReadyData(
    val dayGroups: List<ScheduleDayGroup>,
    val dayGroupKeys: List<Long>,
    val selectedGroup: ScheduleDayGroup?,
    val visibleItems: List<ScheduleAnime>,
    val scheduleDayKey: Long,
    val timeFormatter: DateTimeFormatter,
)

internal data class ScheduleReadyLayout(
    val columnsCount: Int,
    val touchOverscrollEnabled: Boolean,
    val itemFocusRequesters: List<FocusRequester>,
    val focusedGridTopInsetPx: Float,
    val focusedGridBottomInsetPx: Float,
    val focusedGridItemHeightPx: Float,
    val leadingGridItemCount: Int,
    val gridTopContentPadding: Dp,
    val gridBottomContentPadding: Dp,
    val gridHorizontalPadding: Dp,
    val gridVerticalGap: Dp,
)

@Composable
internal fun rememberScheduleReadyData(params: ScheduleReadyParams): ScheduleReadyData {
    val zoneId = remember { ZoneId.systemDefault() }
    val timeFormatter = remember(params.locale) {
        DateTimeFormatter.ofPattern("HH:mm", params.locale)
    }
    val dayGroups = remember(params.schedule, zoneId, params.precomputedDayGroups) {
        params.precomputedDayGroups ?: params.schedule.toScheduleDayGroups(zoneId)
    }
    val dayGroupKeys = remember(dayGroups) { dayGroups.map { group -> group.epochDay } }
    val selectedGroup = remember(dayGroups, params.selectedEpochDay) {
        dayGroups.firstOrNull { group -> group.epochDay == params.selectedEpochDay }
            ?: dayGroups.todayOrClosest()
    }
    val visibleItems = selectedGroup?.items.orEmpty()
    return ScheduleReadyData(
        dayGroups = dayGroups,
        dayGroupKeys = dayGroupKeys,
        selectedGroup = selectedGroup,
        visibleItems = visibleItems,
        scheduleDayKey = selectedGroup?.epochDay ?: Long.MIN_VALUE,
        timeFormatter = timeFormatter,
    )
}

@Composable
internal fun rememberScheduleReadyLayout(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    maxWidth: Dp,
    maxHeight: Dp,
): ScheduleReadyLayout {
    val responsiveWidth = currentResponsiveWindowSizeDp().width
    val horizontalPadding = browseGridHorizontalContentPadding(responsiveWidth)
    val columnsCount = remember(maxWidth, horizontalPadding, params.cardSize) {
        params.cardSize.resolveCatalogColumns(maxWidth.value.roundToInt(), horizontalPadding.value.roundToInt())
    }
    val density = LocalDensity.current
    val focusedGridTopInset = browseGridFocusedCardTopInset(params.pinnedTopPadding, responsiveWidth)
    val focusedGridBottomInset = BrowseFocusedCardBottomGap + params.contentBottomPadding
    val baseBottomPadding = if (params.contentBottomPadding > 0.dp) {
        focusedGridBottomInset
    } else {
        24.dp + BrowseFocusedCardBottomGap
    }
    val itemFocusRequesters = remember(data.scheduleDayKey, data.visibleItems.size, columnsCount) {
        List(data.visibleItems.size) { FocusRequester() }
    }
    return ScheduleReadyLayout(
        columnsCount = columnsCount,
        touchOverscrollEnabled = LocalInputModeManager.current.inputMode == InputMode.Touch,
        itemFocusRequesters = itemFocusRequesters,
        focusedGridTopInsetPx = with(density) { focusedGridTopInset.toPx() },
        focusedGridBottomInsetPx = with(density) { focusedGridBottomInset.toPx() },
        focusedGridItemHeightPx = with(density) {
            browseGridItemHeight(
                maxWidth = maxWidth,
                columns = columnsCount,
                horizontalPadding = horizontalPadding,
            ).toPx()
        },
        leadingGridItemCount = if (params.showCalendarInGrid) 1 else 0,
        gridTopContentPadding = if (params.showCalendarInGrid) {
            params.pinnedTopPadding + ScheduleCalendarTopGap
        } else {
            params.pinnedTopPadding + BrowseGridTopContentPadding
        },
        gridBottomContentPadding = browseGridFocusedCardBottomPadding(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            columns = columnsCount,
            horizontalPadding = horizontalPadding,
            topInset = focusedGridTopInset,
            bottomInset = focusedGridBottomInset,
            basePadding = baseBottomPadding,
        ),
        gridHorizontalPadding = horizontalPadding,
        gridVerticalGap = if (params.showCalendarInGrid) BrowseTvScheduleBlockGap else BrowseChromeItemGap,
    )
}

internal class ScheduleReadyActions(
    private val params: ScheduleReadyParams,
    private val data: ScheduleReadyData,
    private val layout: ScheduleReadyLayout,
    private val focusController: BrowseGridFocusController,
    private val focusScope: CoroutineScope,
    private val uiControls: AppNavigationController,
    private val setSuppressCalendarFocusAfterBackToTop: (Boolean) -> Unit,
    private val incrementCalendarFocusNonce: () -> Unit,
) {
    fun updateFocusedIndex(index: Int) {
        if (params.currentFocusedIndex() != index) {
            params.onFocusedIndexChange(index)
        }
    }

    fun requestCalendarFocus(): Boolean {
        if (!params.showCalendarInGrid) return params.onExitUp()
        setSuppressCalendarFocusAfterBackToTop(false)
        focusController.cancelPendingRequest()
        uiControls.launch(focusScope, this, UiControlOperation.NavigationLatest) {
            if (params.gridState.firstVisibleItemIndex != 0 || params.gridState.firstVisibleItemScrollOffset != 0) {
                params.gridState.animateScrollToItem(0, 0)
            }
            withFrameNanos { }
            incrementCalendarFocusNonce()
        }
        return true
    }

    fun requestContentFocus(): Boolean {
        setSuppressCalendarFocusAfterBackToTop(false)
        if (data.visibleItems.isEmpty()) return false
        uiControls.cancel(UiControlOperation.NavigationLatest)
        return focusController.moveFocusTo(0)
    }

    fun handleGridDirection(index: Int, key: Key): Boolean {
        return handleVisualGridNavigationKey(
            key = key,
            itemCount = data.visibleItems.size,
            columns = layout.columnsCount,
            sourceIndex = index,
            moveFocusTo = { target -> focusController.moveFocusTo(target) },
            onEdgeExit = { direction ->
                if (!uiControls.scrollFocusedRegion(direction)) when (direction) {
                    VisualGridDirection.Left,
                    VisualGridDirection.Right -> params.onExitHorizontalDirection(direction)
                    VisualGridDirection.Up -> requestCalendarFocus()
                    VisualGridDirection.Down -> params.onExitDown()
                }
            },
        )
    }

    fun canHandleBackToTop(): Boolean {
        return params.gridState.canHandleBrowseRootBackToTop(BrowseSection.Schedule)
    }

    fun handleBackToTop(withFocus: Boolean): Boolean {
        if (!canHandleBackToTop()) return false
        focusController.cancelPendingRequest()
        if (!withFocus || data.visibleItems.isEmpty()) {
            uiControls.launch(focusScope, this, UiControlOperation.NavigationLatest) {
                params.gridState.animateScrollToItem(0, 0)
            }
            return true
        }
        updateFocusedIndex(0)
        setSuppressCalendarFocusAfterBackToTop(true)
        uiControls.launch(focusScope, this, UiControlOperation.NavigationLatest) {
            try {
                focusController.focusItemWhenVisible(0)
            } finally {
                setSuppressCalendarFocusAfterBackToTop(false)
            }
        }
        return true
    }

    fun selectDay(epochDay: Long) {
        params.onSelectedEpochDayChange(epochDay)
        updateFocusedIndex(0)
        focusController.cancelPendingRequest()
        uiControls.launch(focusScope, this, UiControlOperation.ContentScrollLatest) {
            params.gridState.animateScrollToItem(0, 0)
        }
    }
}

internal fun normalizedScheduleFocusedIndex(itemCount: Int, currentIndex: Int): Int {
    return when {
        itemCount <= 0 -> -1
        currentIndex < 0 -> 0
        currentIndex >= itemCount -> itemCount - 1
        else -> currentIndex
    }
}

internal fun shouldRequestBrowseCurrentFocus(
    contentFocusEnabled: Boolean,
    requestNonce: Long,
    handledNonce: Long,
    itemCount: Int,
): Boolean {
    if (!contentFocusEnabled || itemCount <= 0) return false
    return requestNonce > 0L && requestNonce != handledNonce
}
