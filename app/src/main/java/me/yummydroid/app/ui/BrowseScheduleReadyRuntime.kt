package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue

@Composable
internal fun ScheduleReadyCoordinator(
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
