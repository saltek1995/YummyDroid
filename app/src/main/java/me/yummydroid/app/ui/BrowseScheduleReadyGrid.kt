package me.yummydroid.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.ScheduleAnime

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
    val params = ScheduleReadyParams(
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
    )
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val data = rememberScheduleReadyData(params)
        val layout = rememberScheduleReadyLayout(params, data, maxWidth, maxHeight)
        ScheduleReadyCoordinator(params, data, layout)
    }
}

@Composable
private fun ScheduleReadyCoordinator(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    layout: ScheduleReadyLayout,
) {
    val focusScope = rememberCoroutineScope()
    var internalCalendarFocusRequestNonce by remember(data.scheduleDayKey) { mutableLongStateOf(0L) }
    var handledPersistentFocusResetNonce by remember { mutableLongStateOf(0L) }
    var handledTransientFocusResetNonce by remember { mutableLongStateOf(0L) }
    var handledCurrentFocusRequestNonce by remember { mutableLongStateOf(0L) }
    var suppressCalendarFocusAfterBackToTop by remember(data.scheduleDayKey) { mutableStateOf(false) }
    var scheduleCalendarHasFocus by remember(data.scheduleDayKey) { mutableStateOf(false) }
    val focusRequestJob = remember(data.scheduleDayKey, layout.columnsCount) { FocusRequestJobRef() }
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
        focusRequestJob = focusRequestJob,
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
        scheduleCalendarHasFocus = scheduleCalendarHasFocus,
        onScheduleCalendarFocusChange = { scheduleCalendarHasFocus = it },
    )
}
