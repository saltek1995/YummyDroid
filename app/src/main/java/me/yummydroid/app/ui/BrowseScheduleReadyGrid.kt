package me.yummydroid.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.ScheduleAnime

@Composable
@OptIn(ExperimentalFoundationApi::class)
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
BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val responsiveWidth = currentResponsiveWindowSizeDp().width
        val columnsCount = remember(maxWidth, cardSize) {
            cardSize.resolveCatalogColumns(maxWidth.value.roundToInt())
        }
        val density = LocalDensity.current
        val touchOverscrollEnabled = LocalInputModeManager.current.inputMode == InputMode.Touch
        val zoneId = remember { ZoneId.systemDefault() }
        val scheduleTimeFormatter = remember(locale) {
            DateTimeFormatter.ofPattern("HH:mm", locale)
        }
        val dayGroups = remember(schedule, zoneId, precomputedDayGroups) {
            precomputedDayGroups ?: schedule.toScheduleDayGroups(zoneId)
        }
        val dayGroupKeys = remember(dayGroups) { dayGroups.map { group -> group.epochDay } }
        val selectedScheduleDay = selectedEpochDay
        val selectedGroup = remember(dayGroups, selectedScheduleDay) {
            dayGroups.firstOrNull { group -> group.epochDay == selectedScheduleDay }
                ?: dayGroups.todayOrClosest()
        }
        val visibleItems = selectedGroup?.items.orEmpty()
        val focusScope = rememberCoroutineScope()
        val scheduleDayKey = selectedGroup?.epochDay ?: Long.MIN_VALUE
        val itemFocusRequesters = remember(scheduleDayKey, visibleItems.size, columnsCount) {
            List(visibleItems.size) { FocusRequester() }
        }
        val focusedGridTopInset = browseGridFocusedCardTopInset(pinnedTopPadding, responsiveWidth)
        val focusedGridTopInsetPx = with(density) { focusedGridTopInset.toPx() }
        val focusedGridBottomInset = BrowseFocusedCardBottomGap + contentBottomPadding
        val focusedGridBottomInsetPx = with(density) { focusedGridBottomInset.toPx() }
        var internalCalendarFocusRequestNonce by remember(scheduleDayKey) { mutableLongStateOf(0L) }
        var handledPersistentFocusResetNonce by remember { mutableLongStateOf(0L) }
        var handledTransientFocusResetNonce by remember { mutableLongStateOf(0L) }
        var handledCurrentFocusRequestNonce by remember { mutableLongStateOf(0L) }
        var suppressCalendarFocusAfterBackToTop by remember(scheduleDayKey) { mutableStateOf(false) }
        var scheduleCalendarHasFocus by remember(scheduleDayKey) { mutableStateOf(false) }
        val focusRequestJob = remember(scheduleDayKey, columnsCount) { FocusRequestJobRef() }
        val baseScheduleGridBottomContentPadding = if (contentBottomPadding > 0.dp) {
            focusedGridBottomInset
        } else {
            24.dp + BrowseFocusedCardBottomGap
        }
        val leadingGridItemCount = if (showCalendarInGrid) 1 else 0
        val scheduleGridTopContentPadding = if (showCalendarInGrid) {
            pinnedTopPadding + ScheduleCalendarTopGap
        } else {
            pinnedTopPadding + BrowseGridTopContentPadding
        }
        val scheduleGridVerticalGap = if (showCalendarInGrid) {
            BrowseTvScheduleBlockGap
        } else {
            BrowseChromeItemGap
        }
        val scheduleGridHorizontalPadding = browseGridHorizontalContentPadding(responsiveWidth)
        val focusedGridItemHeightPx = with(density) {
            browseGridItemHeight(
                maxWidth = maxWidth,
                columns = columnsCount,
                horizontalPadding = scheduleGridHorizontalPadding,
            ).toPx()
        }
        val scheduleGridBottomContentPadding = browseGridFocusedCardBottomPadding(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            columns = columnsCount,
            horizontalPadding = scheduleGridHorizontalPadding,
            topInset = focusedGridTopInset,
            bottomInset = focusedGridBottomInset,
            basePadding = baseScheduleGridBottomContentPadding,
        )
        fun updateFocusedScheduleIndex(index: Int) {
            if (currentFocusedIndex() != index) {
                onFocusedIndexChange(index)
            }
        }

        val focusController = browseGridFocusController(
            gridState = gridState,
            itemFocusRequesters = itemFocusRequesters,
            columns = columnsCount,
            leadingGridItemCount = leadingGridItemCount,
            currentFocusedIndex = currentFocusedIndex,
            updateFocusedIndex = ::updateFocusedScheduleIndex,
            protectedTopPx = focusedGridTopInsetPx,
            protectedBottomPx = focusedGridBottomInsetPx,
            focusedItemHeightPx = focusedGridItemHeightPx,
            focusScope = focusScope,
            focusRequestJob = focusRequestJob,
        )

        fun requestScheduleCalendarFocus(): Boolean {
            if (!showCalendarInGrid) {
                return onExitUp()
            }
            suppressCalendarFocusAfterBackToTop = false
            focusController.cancelPendingRequest()
            focusRequestJob.job = focusScope.launch {
                if (gridState.firstVisibleItemIndex != 0 || gridState.firstVisibleItemScrollOffset != 0) {
                    gridState.animateScrollToItem(0, 0)
                }
                withFrameNanos { }
                internalCalendarFocusRequestNonce += 1L
            }
            return true
        }

        fun requestScheduleContentFocus(): Boolean {
            suppressCalendarFocusAfterBackToTop = false
            if (visibleItems.isEmpty()) return false
            return focusController.moveFocusTo(0)
        }

        fun handleScheduleGridDirection(index: Int, key: Key): Boolean {
            return handleVisualGridNavigationKey(
                key = key,
                itemCount = visibleItems.size,
                columns = columnsCount,
                currentFocusedIndex = currentFocusedIndex(),
                fallbackIndex = index,
                moveFocusTo = focusController::moveFocusTo,
                onEdgeExit = { direction ->
                    when (direction) {
                        VisualGridDirection.Left,
                        VisualGridDirection.Right -> onExitHorizontalDirection(direction)
                        VisualGridDirection.Up -> requestScheduleCalendarFocus()
                        VisualGridDirection.Down -> onExitDown()
                    }
                },
            )
        }

        fun canHandleBackToTop(): Boolean {
            return gridState.canHandleBrowseRootBackToTop(BrowseSection.Schedule)
        }

        fun handleBackToTop(withFocus: Boolean): Boolean {
            if (!canHandleBackToTop()) return false
            focusController.cancelPendingRequest()
            if (!withFocus || visibleItems.isEmpty()) {
                focusRequestJob.job = focusScope.launch {
                    gridState.animateScrollToItem(0, 0)
                }
                return true
            }
            updateFocusedScheduleIndex(0)
            suppressCalendarFocusAfterBackToTop = true
            focusRequestJob.job = focusScope.launch {
                try {
                    focusController.focusItemWhenVisible(0)
                } finally {
                    suppressCalendarFocusAfterBackToTop = false
                }
            }
            return true
        }

        LaunchedEffect(dayGroupKeys) {
            if (dayGroups.isEmpty()) {
                onSelectedEpochDayChange(Long.MIN_VALUE)
                updateFocusedScheduleIndex(-1)
                return@LaunchedEffect
            }
            if (dayGroups.none { group -> group.epochDay == selectedScheduleDay }) {
                onSelectedEpochDayChange(dayGroups.todayOrClosest()?.epochDay ?: dayGroups.first().epochDay)
                updateFocusedScheduleIndex(0)
            }
        }

        DisposableEffect(visibleItems.size, onRegisterBackToTopHandler) {
            val register = onRegisterBackToTopHandler
            if (register != null && visibleItems.isNotEmpty()) {
                register(
                    HomeBackToTopHandler(
                        section = BrowseSection.Schedule,
                        canHandle = ::canHandleBackToTop,
                        handle = ::handleBackToTop,
                    ),
                )
            } else {
                register?.invoke(null)
            }
            onDispose { register?.invoke(null) }
        }

        LaunchedEffect(focusFirstRequest, visibleItems.size) {
            if (visibleItems.isEmpty()) return@LaunchedEffect
            val shouldHandlePersistent = focusFirstRequest.persistentNonce > 0L &&
                focusFirstRequest.persistentNonce != handledPersistentFocusResetNonce
            val shouldHandleTransient = focusFirstRequest.transientNonce > 0L &&
                focusFirstRequest.transientNonce != handledTransientFocusResetNonce
            if (!shouldHandlePersistent && !shouldHandleTransient) {
                return@LaunchedEffect
            }
            focusController.cancelPendingRequest()
            updateFocusedScheduleIndex(0)
            focusController.focusItemWhenVisible(0)
            if (shouldHandlePersistent) {
                handledPersistentFocusResetNonce = focusFirstRequest.persistentNonce
            }
            if (shouldHandleTransient) {
                handledTransientFocusResetNonce = focusFirstRequest.transientNonce
            }
        }

        LaunchedEffect(visibleItems.size) {
            updateFocusedScheduleIndex(
                when {
                visibleItems.isEmpty() -> -1
                currentFocusedIndex() < 0 -> 0
                currentFocusedIndex() !in visibleItems.indices -> visibleItems.lastIndex
                else -> currentFocusedIndex()
                },
            )
        }

        LaunchedEffect(focusCurrentRequestNonce, visibleItems.size) {
            if (
                !contentFocusEnabled ||
                focusCurrentRequestNonce <= 0L ||
                focusCurrentRequestNonce == handledCurrentFocusRequestNonce ||
                visibleItems.isEmpty()
            ) {
                return@LaunchedEffect
            }
            withFrameNanos { }
            val focusedGridIndex = currentFocusedIndex()
                .takeIf { index -> index in visibleItems.indices }
            val targetGridIndex = focusedGridIndex
                ?: (gridState.firstVisibleItemIndex - leadingGridItemCount).coerceIn(0, visibleItems.lastIndex)
            val targetIndex = targetGridIndex.coerceIn(0, visibleItems.lastIndex)
            updateFocusedScheduleIndex(targetIndex)
            focusController.focusItemWhenVisible(targetIndex)
            handledCurrentFocusRequestNonce = focusCurrentRequestNonce
        }

        if (schedule.isEmpty()) {
            EmptyPane(message = uiText(UiStringKey.ScheduleIsEmpty), modifier = Modifier.fillMaxSize())
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                if (dayGroups.isEmpty() || visibleItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = pinnedTopPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = uiText(UiStringKey.NoUpcomingReleasesYet),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    BrowseGridScrollLocalProvider(touchOverscrollEnabled = touchOverscrollEnabled) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columnsCount),
                            state = gridState,
                            modifier = Modifier
                                .fillMaxSize()
                                .browseTouchBounceOverscroll(
                                    enabled = touchOverscrollEnabled,
                                    gridState = gridState,
                                )
                                .onPreviewKeyEvent { event ->
                                    event.type == KeyEventType.KeyDown &&
                                        !scheduleCalendarHasFocus &&
                                        contentFocusEnabled &&
                                        currentFocusedIndex().let { index ->
                                            index in visibleItems.indices && handleScheduleGridDirection(index, event.key)
                                        }
                                }
                                .focusGroup(),
                            contentPadding = PaddingValues(
                                start = scheduleGridHorizontalPadding,
                                top = scheduleGridTopContentPadding,
                                end = scheduleGridHorizontalPadding,
                                bottom = scheduleGridBottomContentPadding,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(BrowseGridHorizontalGap),
                            verticalArrangement = Arrangement.spacedBy(scheduleGridVerticalGap),
                        ) {
                            if (showCalendarInGrid) {
                                item(
                                    key = "schedule-calendar",
                                    span = { GridItemSpan(maxLineSpan) },
                                    contentType = "schedule-calendar",
                                ) {
                                    ScheduleCalendarBlock(
                                        dayGroups = dayGroups,
                                        selectedEpochDay = selectedGroup?.epochDay ?: Long.MIN_VALUE,
                                        locale = locale,
                                        focusRequestNonce = calendarFocusRequestNonce * 1_000_000L +
                                            internalCalendarFocusRequestNonce,
                                        focusEnabled = contentFocusEnabled && !suppressCalendarFocusAfterBackToTop,
                                        onCalendarFocusChanged = { hasFocus ->
                                            scheduleCalendarHasFocus = hasFocus
                                        },
                                        onExitUp = onExitUp,
                                        onExitDown = ::requestScheduleContentFocus,
                                        onSelectDay = { epochDay ->
                                            onSelectedEpochDayChange(epochDay)
                                            updateFocusedScheduleIndex(0)
                                            focusController.cancelPendingRequest()
                                            focusRequestJob.job = focusScope.launch {
                                                gridState.animateScrollToItem(0, 0)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }

                            itemsIndexed(
                                items = visibleItems,
                                key = { _, item -> item.anime.id },
                                contentType = { _, _ -> "schedule-card" },
                            ) { index, item ->
                                ScheduleRow(
                                    item = item,
                                    timeFormatter = scheduleTimeFormatter,
                                    onOpenAnime = onOpenAnime,
                                    modifier = Modifier
                                        .focusProperties { canFocus = contentFocusEnabled }
                                        .focusRequester(itemFocusRequesters[index])
                                        .onPreviewKeyEvent { event ->
                                            event.type == KeyEventType.KeyDown &&
                                                handleScheduleGridDirection(index, event.key)
                                        }
                                        .onFocusChanged { focusState ->
                                            if (focusState.hasFocus) {
                                                updateFocusedScheduleIndex(index)
                                            }
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
