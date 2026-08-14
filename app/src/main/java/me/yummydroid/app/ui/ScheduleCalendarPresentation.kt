package me.yummydroid.app.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
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
                .clearFocusAfterTouch()
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
