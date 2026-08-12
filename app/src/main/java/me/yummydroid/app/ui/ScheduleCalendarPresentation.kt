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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import me.yummydroid.app.data.ScheduleAnime
import me.yummydroid.app.ui.components.clearFocusAfterTouch
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor

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
                    .focusGroup(),
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
                onExitUp = onExitUp,
                onExitDown = onExitDown,
            )
        }

        ScheduleCalendarEntryType.Day -> ScheduleCalendarDayEntry(
            runtime = runtime,
            index = entry.dayIndex,
            focusEnabled = focusEnabled,
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
    handleManagedDpadNavigationKey(event.key) { direction ->
        when (direction) {
            VisualGridDirection.Left -> onMovePrevious()
            VisualGridDirection.Right -> onMoveNext()
            VisualGridDirection.Up -> onExitUp()
            VisualGridDirection.Down -> onExitDown()
        }
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
    val currentMonthEntryIndex = currentScheduleCalendarMonthEntryIndex(
        entries = entries,
        visibleEntries = visibleEntries,
        fallbackMonthEntryIndex = fallbackMonthEntryIndex,
    ) ?: return null
    val currentMonth = entries.getOrNull(currentMonthEntryIndex) ?: return null
    val physicalCurrentMonth = visibleEntries.entryAt(currentMonthEntryIndex)
    if (physicalCurrentMonth.isVisibleMonthHeader(viewportEndPx)) return null
    val currentOffsetPx = pinnedScheduleCalendarMonthOffset(
        entries = entries,
        visibleEntries = visibleEntries,
        currentMonthEntryIndex = currentMonthEntryIndex,
        physicalCurrentMonth = physicalCurrentMonth,
        monthSlotWidthPx = monthSlotWidthPx,
    )
    return currentMonth
        .takeIf { currentOffsetPx > -monthSlotWidthPx }
        ?.scheduleCalendarMonthOverlay(currentOffsetPx)
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
