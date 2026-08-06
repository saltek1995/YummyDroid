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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
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

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ScheduleCalendarBlock(
    dayGroups: List<ScheduleDayGroup>,
    selectedEpochDay: Long,
    locale: Locale,
    focusRequestNonce: Long = 0L,
    focusEnabled: Boolean = true,
    onCalendarFocusChanged: (Boolean) -> Unit = {},
    onExitUp: () -> Boolean,
    onExitDown: () -> Boolean,
    onSelectDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val calendarListState = rememberLazyListState()
    val calendarScope = rememberCoroutineScope()
    val calendarIsWide = LocalConfiguration.current.screenWidthDp >= 720
    val calendarItemGap = if (calendarIsWide) {
        ScheduleDayTileWideGap
    } else {
        ScheduleDayTilePhoneGap
    }
    val calendarBottomPadding = if (calendarIsWide) {
        ScheduleCalendarWideBottomPadding
    } else {
        ScheduleCalendarPhoneBottomPadding
    }
    val dayKeys = remember(dayGroups) { dayGroups.map { it.epochDay } }
    val dayFocusRequesters = remember(dayKeys) { List(dayKeys.size) { FocusRequester() } }
    val density = LocalDensity.current
    val monthSlotWidth = ScheduleMonthInlineLabelWidth + calendarItemGap
    val monthSlotWidthPx = remember(density, calendarItemGap) {
        with(density) {
            monthSlotWidth.toPx()
        }
    }
    val dayTileWidthPx = remember(density) {
        with(density) { ScheduleDayTileWidth.toPx() }
    }
    val calendarEntries = remember(dayGroups, locale) {
        scheduleCalendarEntries(dayGroups, locale)
    }
    val dayCalendarEntryIndices = remember(dayGroups, calendarEntries) {
        IntArray(dayGroups.size) { -1 }.also { indices ->
            calendarEntries.forEachIndexed { entryIndex, entry ->
                if (entry.dayIndex in indices.indices) {
                    indices[entry.dayIndex] = entryIndex
                }
            }
        }
    }
    var navigationEpochDay by remember(dayKeys) { mutableLongStateOf(selectedEpochDay) }
    var handledFocusRequestNonce by remember { mutableLongStateOf(0L) }
    LaunchedEffect(selectedEpochDay) {
        if (selectedEpochDay != Long.MIN_VALUE && navigationEpochDay != selectedEpochDay) {
            navigationEpochDay = selectedEpochDay
        }
    }
    val calendarPagerBoundary = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                return if (available.x != 0f) {
                    Offset(x = available.x, y = 0f)
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return if (available.x != 0f) {
                    Velocity(x = available.x, y = 0f)
                } else {
                    Velocity.Zero
                }
            }
        }
    }
    fun selectedDayIndex(): Int {
        return dayGroups.indexOfFirst { group -> group.epochDay == navigationEpochDay }
            .takeIf { index -> index >= 0 }
            ?: dayGroups.indexOfFirst { group -> group.epochDay == selectedEpochDay }
                .takeIf { index -> index >= 0 }
            ?: 0
    }

    val calendarMonthOverlay by remember(
        calendarListState,
        calendarEntries,
        dayGroups,
        monthSlotWidthPx,
        dayTileWidthPx,
    ) {
        derivedStateOf {
            resolveScheduleCalendarMonthOverlay(
                dayGroups = dayGroups,
                entries = calendarEntries,
                visibleItems = calendarListState.layoutInfo.visibleItemsInfo.map { item ->
                    VisibleScheduleCalendarItem(
                        index = item.index,
                        offsetPx = item.offset,
                        sizePx = dayTileWidthPx.roundToInt(),
                    )
                },
                fallbackDayIndex = selectedDayIndex(),
                monthSlotWidthPx = monthSlotWidthPx,
                viewportEndPx = calendarListState.layoutInfo.viewportSize.width,
            )
        }
    }
    val calendarContentClipStartPx = remember(calendarMonthOverlay, monthSlotWidthPx) {
        calendarMonthOverlay
            ?.chips
            ?.maxOfOrNull { chip ->
                (chip.offsetPx + monthSlotWidthPx).coerceIn(0f, monthSlotWidthPx)
            }
            ?: 0f
    }

    fun calendarEntryIndexForDay(dayIndex: Int): Int {
        return dayCalendarEntryIndices
            .getOrNull(dayIndex)
            ?.takeIf { index -> index >= 0 }
            ?: dayIndex
    }

    suspend fun scrollCalendarToDayStart(dayIndex: Int) {
        val entryIndex = calendarEntryIndexForDay(dayIndex)
        val entry = calendarEntries.getOrNull(entryIndex)
        val scrollOffset = if (entry?.startsMonth == true) {
            0
        } else {
            -monthSlotWidthPx.roundToInt()
        }
        calendarListState.scrollToItem(entryIndex, scrollOffset)
    }

    suspend fun scrollCalendarToRevealIndex(targetIndex: Int) {
        val layoutInfo = calendarListState.layoutInfo
        val targetFirstIndex = scheduleCalendarEdgeScrollFirstVisibleIndex(
            visibleItems = layoutInfo.visibleItemsInfo.mapNotNull { item ->
                val entry = calendarEntries.getOrNull(item.index) ?: return@mapNotNull null
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
            scrollCalendarToDayStart(targetFirstIndex)
        }
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
        calendarScope.launch {
            scrollCalendarToRevealIndex(boundedIndex)
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

    LaunchedEffect(focusRequestNonce, dayKeys) {
        if (
            !focusEnabled ||
            focusRequestNonce <= 0L ||
            focusRequestNonce == handledFocusRequestNonce ||
            dayGroups.isEmpty()
        ) {
            return@LaunchedEffect
        }
        val targetIndex = selectedDayIndex().coerceIn(dayGroups.indices)
        scrollCalendarToDayStart(targetIndex)
        withFrameNanos { }
        dayFocusRequesters[targetIndex].requestFocusSafely()
        handledFocusRequestNonce = focusRequestNonce
    }

    LaunchedEffect(dayKeys) {
        val selectedIndex = dayGroups.indexOfFirst { group -> group.epochDay == selectedEpochDay }
        if (selectedIndex >= 0) {
            scrollCalendarToDayStart(selectedIndex)
        }
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScheduleCalendarOuterHorizontalPadding)
            .nestedScroll(calendarPagerBoundary),
        color = Color.Transparent,
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Panel),
    ) {
        Column {
            if (dayGroups.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ScheduleCalendarMonthStrip(
                        monthOverlay = calendarMonthOverlay,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .zIndex(1f)
                            .focusProperties { canFocus = false },
                    )
                    CompositionLocalProvider(LocalBringIntoViewSpec provides ScheduleCalendarBringIntoViewSpec) {
                        LazyRow(
                            state = calendarListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .scheduleCalendarStickyMonthMask(calendarContentClipStartPx)
                                .onFocusChanged { focusState ->
                                    onCalendarFocusChanged(focusState.hasFocus)
                                }
                                .focusGroup()
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    val delta = when (event.key) {
                                        Key.DirectionLeft -> -1
                                        Key.DirectionRight -> 1
                                        Key.DirectionUp -> return@onPreviewKeyEvent onExitUp()
                                        Key.DirectionDown -> return@onPreviewKeyEvent onExitDown()
                                        else -> return@onPreviewKeyEvent false
                                    }
                                    moveSelectedDay(delta)
                            },
                            contentPadding = PaddingValues(
                                start = 0.dp,
                                top = 0.dp,
                                end = ScheduleCalendarHorizontalPadding,
                                bottom = calendarBottomPadding,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(calendarItemGap),
                        ) {
                            calendarEntries.forEach { entry ->
                                item(
                                    key = entry.key,
                                    contentType = entry.type,
                                ) {
                                    when (entry.type) {
                                        ScheduleCalendarEntryType.MonthDay -> {
                                            val index = entry.dayIndex
                                            val group = dayGroups.getOrNull(index) ?: return@item
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(calendarItemGap),
                                                verticalAlignment = Alignment.Top,
                                            ) {
                                                ScheduleMonthInlineChip(
                                                    title = entry.title,
                                                    modifier = Modifier.focusProperties { canFocus = false },
                                                )
                                                ScheduleDayTile(
                                                    group = group,
                                                    selected = group.epochDay == navigationEpochDay,
                                                    locale = locale,
                                                    focusRequester = dayFocusRequesters[index],
                                                    focusEnabled = focusEnabled,
                                                    onFocusedChanged = onCalendarFocusChanged,
                                                    onExitUp = onExitUp,
                                                    onExitDown = onExitDown,
                                                    onMovePrevious = { moveSelectedDay(-1) },
                                                    onMoveNext = { moveSelectedDay(1) },
                                                    onClick = { selectDayAt(index, moveFocus = false) },
                                                )
                                            }
                                        }

                                        ScheduleCalendarEntryType.Day -> {
                                            val index = entry.dayIndex
                                            val group = dayGroups.getOrNull(index) ?: return@item
                                            ScheduleDayTile(
                                                group = group,
                                                selected = group.epochDay == navigationEpochDay,
                                                locale = locale,
                                                focusRequester = dayFocusRequesters[index],
                                                focusEnabled = focusEnabled,
                                                onFocusedChanged = onCalendarFocusChanged,
                                                onExitUp = onExitUp,
                                                onExitDown = onExitDown,
                                                onMovePrevious = { moveSelectedDay(-1) },
                                                onMoveNext = { moveSelectedDay(1) },
                                                onClick = { selectDayAt(index, moveFocus = false) },
                                            )
                                        }

                                    }
                                }
                            }
                        }
                    }
                }
            } else {
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
        }
    }
}

private fun Modifier.scheduleCalendarStickyMonthMask(maskStartPx: Float): Modifier {
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
private fun ScheduleCalendarMonthStrip(
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
private fun ScheduleMonthInlineChip(
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

@Composable
private fun ScheduleDayTile(
    group: ScheduleDayGroup,
    selected: Boolean,
    locale: Locale,
    focusRequester: FocusRequester,
    focusEnabled: Boolean = true,
    modifier: Modifier = Modifier,
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
    val dayOfWeek = remember(group.date, locale) {
        group.date.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, locale)
            .replace(".", "")
            .replaceFirstChar { char -> char.uppercase(locale) }
    }
    val isWeekend = remember(group.date) { group.date.dayOfWeek.value >= 6 }
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
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> onMovePrevious()
                        Key.DirectionRight -> onMoveNext()
                        Key.DirectionUp -> onExitUp()
                        Key.DirectionDown -> onExitDown()
                        else -> false
                    }
                },
            color = yummyActionSurfaceColor(selected = selected, focused = focusVisible),
            contentColor = dayContentColor,
            border = yummyActionBorder(selected = selected, focused = focusVisible),
            shape = shape,
        ) {
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
    }
}

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

private val ScheduleDayTileWidth = 96.dp
private val ScheduleDayTileHeight = 78.dp
private val ScheduleDayTilePhoneGap = BrowseChromeItemGap
private val ScheduleDayTileWideGap = BrowseChromeItemGap
private val ScheduleCalendarOuterHorizontalPadding = 0.dp
private val ScheduleCalendarHorizontalPadding = 0.dp
private val ScheduleMonthInlineLabelWidth = ScheduleDayTileWidth
private val ScheduleMonthInlineLabelAccentHeight = 2.dp
private val ScheduleCalendarPhoneBottomPadding = 0.dp
private val ScheduleCalendarWideBottomPadding = 0.dp
internal val ScheduleCalendarTopGap = 0.dp

@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
private val ScheduleCalendarBringIntoViewSpec = object : BringIntoViewSpec {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(
        durationMillis = 420,
        easing = FastOutSlowInEasing,
    )

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}
