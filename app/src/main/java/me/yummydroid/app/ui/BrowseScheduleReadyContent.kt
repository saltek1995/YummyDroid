package me.yummydroid.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

@Composable
internal fun ScheduleReadyContent(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    layout: ScheduleReadyLayout,
    actions: ScheduleReadyActions,
    internalCalendarFocusRequestNonce: Long,
    suppressCalendarFocusAfterBackToTop: Boolean,
    scheduleCalendarHasFocus: Boolean,
    onScheduleCalendarFocusChange: (Boolean) -> Unit,
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
                scheduleCalendarHasFocus = scheduleCalendarHasFocus,
                onScheduleCalendarFocusChange = onScheduleCalendarFocusChange,
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
    scheduleCalendarHasFocus: Boolean,
    onScheduleCalendarFocusChange: (Boolean) -> Unit,
) {
    BrowseGridScrollLocalProvider(touchOverscrollEnabled = layout.touchOverscrollEnabled) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(layout.columnsCount),
            state = params.gridState,
            modifier = Modifier
                .fillMaxSize()
                .browseTouchBounceOverscroll(
                    enabled = layout.touchOverscrollEnabled,
                    gridState = params.gridState,
                )
                .onPreviewKeyEvent { event ->
                    when {
                        event.type != KeyEventType.KeyDown -> false
                        scheduleCalendarHasFocus -> false
                        !params.contentFocusEnabled -> false
                        else -> params.currentFocusedIndex().let { index ->
                            index in data.visibleItems.indices && actions.handleGridDirection(index, event.key)
                        }
                    }
                }
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
                onFocusChange = onScheduleCalendarFocusChange,
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
    onFocusChange: (Boolean) -> Unit,
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
            onCalendarFocusChanged = onFocusChange,
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
                .onPreviewKeyEvent { event ->
                    event.type == KeyEventType.KeyDown && actions.handleGridDirection(index, event.key)
                }
                .onFocusChanged { focusState ->
                    if (focusState.hasFocus) actions.updateFocusedIndex(index)
                },
        )
    }
}
