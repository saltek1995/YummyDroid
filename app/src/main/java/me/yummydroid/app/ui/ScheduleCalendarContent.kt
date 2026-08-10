package me.yummydroid.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummySurfaceContentColor

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
