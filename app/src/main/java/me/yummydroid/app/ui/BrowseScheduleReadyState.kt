package me.yummydroid.app.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.ScheduleAnime

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
    val columnsCount = remember(maxWidth, params.cardSize) {
        params.cardSize.resolveCatalogColumns(maxWidth.value.roundToInt())
    }
    val density = LocalDensity.current
    val focusedGridTopInset = browseGridFocusedCardTopInset(params.pinnedTopPadding, responsiveWidth)
    val focusedGridBottomInset = BrowseFocusedCardBottomGap + params.contentBottomPadding
    val horizontalPadding = browseGridHorizontalContentPadding(responsiveWidth)
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
    private val focusRequestJob: FocusRequestJobRef,
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
        focusRequestJob.job = focusScope.launch {
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
        return focusController.moveFocusTo(0)
    }

    fun handleGridDirection(index: Int, key: Key): Boolean {
        return handleVisualGridNavigationKey(
            key = key,
            itemCount = data.visibleItems.size,
            columns = layout.columnsCount,
            currentFocusedIndex = params.currentFocusedIndex(),
            fallbackIndex = index,
            moveFocusTo = focusController::moveFocusTo,
            onEdgeExit = { direction ->
                when (direction) {
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
            focusRequestJob.job = focusScope.launch {
                params.gridState.animateScrollToItem(0, 0)
            }
            return true
        }
        updateFocusedIndex(0)
        setSuppressCalendarFocusAfterBackToTop(true)
        focusRequestJob.job = focusScope.launch {
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
        focusRequestJob.job = focusScope.launch {
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

internal fun shouldRequestScheduleCurrentFocus(
    contentFocusEnabled: Boolean,
    requestNonce: Long,
    handledNonce: Long,
    itemCount: Int,
): Boolean {
    if (!contentFocusEnabled || itemCount <= 0) return false
    return requestNonce > 0L && requestNonce != handledNonce
}
