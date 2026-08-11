package me.yummydroid.app.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import kotlinx.coroutines.launch
import me.yummydroid.app.data.ScheduleAnime
import me.yummydroid.app.ui.components.clearFocusAfterTouch
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
        onExitUp = onExitUp,
        onExitDown = onExitDown,
    )
}

// ScheduleCalendarCards
@Composable
internal fun ScheduleRow(
    item: ScheduleAnime,
    timeFormatter: DateTimeFormatter,
    onOpenAnime: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val episodeIsAlreadyOutText = uiText(UiStringKey.EpisodeIsAlreadyOut)
    val metaText = remember(item.airedEpisodes, episodeIsAlreadyOutText) {
        "${item.airedEpisodes} $episodeIsAlreadyOutText"
    }
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

// ScheduleCalendarChrome
internal fun Modifier.scheduleCalendarStickyMonthMask(maskStartPx: Float): Modifier {
    if (maskStartPx <= 0.5f) return this
    return drawWithContent {
        val left = maskStartPx.coerceIn(0f, size.width)
        if (left >= size.width) return@drawWithContent
        clipRect(left = left) {
            this@drawWithContent.drawContent()
        }
    }
}

@Composable
internal fun ScheduleCalendarMonthStrip(
    monthOverlay: ScheduleCalendarMonthOverlay?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val resolvedMonthOverlay = monthOverlay ?: return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ScheduleDayTileHeight)
            .clipToBounds(),
    ) {
        resolvedMonthOverlay.chips.forEach { chip ->
            ScheduleMonthInlineChip(
                title = chip.title,
                modifier = Modifier.offset(
                    x = with(density) { chip.offsetPx.toDp() },
                ),
            )
        }
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
    val contentClipStartPx = remember(monthOverlay, runtime.monthSlotWidthPx) {
        monthOverlay
            ?.chips
            ?.maxOfOrNull { chip ->
                (chip.offsetPx + runtime.monthSlotWidthPx)
                    .coerceIn(0f, runtime.monthSlotWidthPx)
            }
            ?: 0f
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        ScheduleCalendarMonthStrip(
            monthOverlay = monthOverlay,
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
                    .scheduleCalendarStickyMonthMask(contentClipStartPx)
                    .onFocusChanged { focusState ->
                        onCalendarFocusChanged(focusState.hasFocus)
                    }
                    .focusGroup()
                    .scheduleCalendarKeyNavigation(runtime, onExitUp, onExitDown),
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
                        ScheduleCalendarEntryContent(
                            runtime = runtime,
                            entry = entry,
                            focusEnabled = focusEnabled,
                            onCalendarFocusChanged = onCalendarFocusChanged,
                            onExitUp = onExitUp,
                            onExitDown = onExitDown,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleCalendarEntryContent(
    runtime: ScheduleCalendarRuntime,
    entry: ScheduleCalendarEntry,
    focusEnabled: Boolean,
    onCalendarFocusChanged: (Boolean) -> Unit,
    onExitUp: () -> Boolean,
    onExitDown: () -> Boolean,
) {
    when (entry.type) {
        ScheduleCalendarEntryType.MonthDay -> Row(
            horizontalArrangement = Arrangement.spacedBy(runtime.itemGap),
            verticalAlignment = Alignment.Top,
        ) {
            ScheduleMonthInlineChip(
                title = entry.title,
                modifier = Modifier.focusProperties { canFocus = false },
            )
            ScheduleCalendarDayEntry(
                runtime = runtime,
                index = entry.dayIndex,
                focusEnabled = focusEnabled,
                onCalendarFocusChanged = onCalendarFocusChanged,
                onExitUp = onExitUp,
                onExitDown = onExitDown,
            )
        }

        ScheduleCalendarEntryType.Day -> ScheduleCalendarDayEntry(
            runtime = runtime,
            index = entry.dayIndex,
            focusEnabled = focusEnabled,
            onCalendarFocusChanged = onCalendarFocusChanged,
            onExitUp = onExitUp,
            onExitDown = onExitDown,
        )
    }
}

@Composable
private fun ScheduleCalendarDayEntry(
    runtime: ScheduleCalendarRuntime,
    index: Int,
    focusEnabled: Boolean,
    onCalendarFocusChanged: (Boolean) -> Unit,
    onExitUp: () -> Boolean,
    onExitDown: () -> Boolean,
) {
    val group = runtime.dayGroups.getOrNull(index) ?: return
    ScheduleDayTile(
        group = group,
        selected = group.epochDay == runtime.navigationEpochDay,
        locale = runtime.locale,
        focusRequester = runtime.dayFocusRequesters[index],
        focusEnabled = focusEnabled,
        onFocusedChanged = onCalendarFocusChanged,
        onExitUp = onExitUp,
        onExitDown = onExitDown,
        onMovePrevious = { runtime.moveSelectedDay(-1) },
        onMoveNext = { runtime.moveSelectedDay(1) },
        onClick = { runtime.selectDayAt(index, moveFocus = false) },
    )
}

@Composable
private fun rememberScheduleCalendarMonthOverlay(
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

private fun Modifier.scheduleCalendarKeyNavigation(
    runtime: ScheduleCalendarRuntime,
    onExitUp: () -> Boolean,
    onExitDown: () -> Boolean,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    val delta = when (event.key) {
        Key.DirectionLeft -> -1
        Key.DirectionRight -> 1
        Key.DirectionUp -> return@onPreviewKeyEvent onExitUp()
        Key.DirectionDown -> return@onPreviewKeyEvent onExitDown()
        else -> return@onPreviewKeyEvent false
    }
    runtime.moveSelectedDay(delta)
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
    locale: Locale,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    focusEnabled: Boolean = true,
    onFocusedChanged: (Boolean) -> Unit = {},
    onExitUp: () -> Boolean,
    onExitDown: () -> Boolean,
    onMovePrevious: () -> Boolean,
    onMoveNext: () -> Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    var focused by remember { mutableStateOf(false) }
    val inputModeManager = LocalInputModeManager.current
    val focusVisible = focused && inputModeManager.inputMode != InputMode.Touch
    val dayContentColor = yummyActionContentColor(selected = selected, focused = focusVisible)
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(ScheduleDayTileWidth),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(ScheduleDayTileHeight)
                .focusProperties { canFocus = focusEnabled }
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    val hasFocus = focusState.isFocused || focusState.hasFocus
                    focused = hasFocus
                    onFocusedChanged(hasFocus)
                }
                .clearFocusAfterTouch()
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .scheduleDayTileKeyNavigation(onMovePrevious, onMoveNext, onExitUp, onExitDown),
            color = yummyActionSurfaceColor(selected = selected, focused = focusVisible),
            contentColor = dayContentColor,
            border = yummyActionBorder(selected = selected, focused = focusVisible),
            shape = shape,
        ) {
            ScheduleDayTileContent(
                group = group,
                locale = locale,
                focusVisible = focusVisible,
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
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionLeft -> onMovePrevious()
        Key.DirectionRight -> onMoveNext()
        Key.DirectionUp -> onExitUp()
        Key.DirectionDown -> onExitDown()
        else -> false
    }
}

// ScheduleCalendarMonthOverlay
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
