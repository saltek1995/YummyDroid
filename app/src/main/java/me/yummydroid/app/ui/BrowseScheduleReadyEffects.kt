package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import me.yummydroid.app.BrowseSection

@Composable
internal fun ScheduleReadyEffects(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    layout: ScheduleReadyLayout,
    actions: ScheduleReadyActions,
    focusController: BrowseGridFocusController,
    handledPersistentFocusResetNonce: Long,
    onHandledPersistentFocusResetNonceChange: (Long) -> Unit,
    handledTransientFocusResetNonce: Long,
    onHandledTransientFocusResetNonceChange: (Long) -> Unit,
    handledCurrentFocusRequestNonce: Long,
    onHandledCurrentFocusRequestNonceChange: (Long) -> Unit,
) {
    ScheduleDaySelectionEffect(params, data, actions)
    ScheduleBackToTopRegistrationEffect(params, data, actions)
    ScheduleFirstFocusEffect(
        params = params,
        data = data,
        actions = actions,
        focusController = focusController,
        handledPersistentNonce = handledPersistentFocusResetNonce,
        onHandledPersistentNonceChange = onHandledPersistentFocusResetNonceChange,
        handledTransientNonce = handledTransientFocusResetNonce,
        onHandledTransientNonceChange = onHandledTransientFocusResetNonceChange,
    )
    ScheduleFocusedIndexEffect(params, data, actions)
    ScheduleCurrentFocusEffect(
        params = params,
        data = data,
        layout = layout,
        actions = actions,
        focusController = focusController,
        handledNonce = handledCurrentFocusRequestNonce,
        onHandledNonceChange = onHandledCurrentFocusRequestNonceChange,
    )
}

@Composable
private fun ScheduleDaySelectionEffect(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    actions: ScheduleReadyActions,
) {
    LaunchedEffect(data.dayGroupKeys) {
        if (data.dayGroups.isEmpty()) {
            params.onSelectedEpochDayChange(Long.MIN_VALUE)
            actions.updateFocusedIndex(-1)
            return@LaunchedEffect
        }
        if (data.dayGroups.none { group -> group.epochDay == params.selectedEpochDay }) {
            val fallbackDay = data.dayGroups.todayOrClosest()?.epochDay ?: data.dayGroups.first().epochDay
            params.onSelectedEpochDayChange(fallbackDay)
            actions.updateFocusedIndex(0)
        }
    }
}

@Composable
private fun ScheduleBackToTopRegistrationEffect(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    actions: ScheduleReadyActions,
) {
    DisposableEffect(data.visibleItems.size, params.onRegisterBackToTopHandler) {
        val register = params.onRegisterBackToTopHandler
        if (register != null && data.visibleItems.isNotEmpty()) {
            register(
                HomeBackToTopHandler(
                    section = BrowseSection.Schedule,
                    canHandle = actions::canHandleBackToTop,
                    handle = actions::handleBackToTop,
                ),
            )
        } else {
            register?.invoke(null)
        }
        onDispose { register?.invoke(null) }
    }
}

@Composable
private fun ScheduleFirstFocusEffect(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    actions: ScheduleReadyActions,
    focusController: BrowseGridFocusController,
    handledPersistentNonce: Long,
    onHandledPersistentNonceChange: (Long) -> Unit,
    handledTransientNonce: Long,
    onHandledTransientNonceChange: (Long) -> Unit,
) {
    LaunchedEffect(params.focusFirstRequest, data.visibleItems.size) {
        if (data.visibleItems.isEmpty()) return@LaunchedEffect
        val persistentNonce = params.focusFirstRequest.persistentNonce
        val transientNonce = params.focusFirstRequest.transientNonce
        val shouldHandlePersistent = persistentNonce > 0L && persistentNonce != handledPersistentNonce
        val shouldHandleTransient = transientNonce > 0L && transientNonce != handledTransientNonce
        if (!shouldHandlePersistent && !shouldHandleTransient) return@LaunchedEffect

        focusController.cancelPendingRequest()
        actions.updateFocusedIndex(0)
        focusController.focusItemWhenVisible(0)
        if (shouldHandlePersistent) onHandledPersistentNonceChange(persistentNonce)
        if (shouldHandleTransient) onHandledTransientNonceChange(transientNonce)
    }
}

@Composable
private fun ScheduleFocusedIndexEffect(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    actions: ScheduleReadyActions,
) {
    LaunchedEffect(data.visibleItems.size) {
        actions.updateFocusedIndex(
            normalizedScheduleFocusedIndex(
                itemCount = data.visibleItems.size,
                currentIndex = params.currentFocusedIndex(),
            ),
        )
    }
}

@Composable
private fun ScheduleCurrentFocusEffect(
    params: ScheduleReadyParams,
    data: ScheduleReadyData,
    layout: ScheduleReadyLayout,
    actions: ScheduleReadyActions,
    focusController: BrowseGridFocusController,
    handledNonce: Long,
    onHandledNonceChange: (Long) -> Unit,
) {
    LaunchedEffect(params.focusCurrentRequestNonce, data.visibleItems.size) {
        if (
            !shouldRequestScheduleCurrentFocus(
                contentFocusEnabled = params.contentFocusEnabled,
                requestNonce = params.focusCurrentRequestNonce,
                handledNonce = handledNonce,
                itemCount = data.visibleItems.size,
            )
        ) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        val focusedGridIndex = params.currentFocusedIndex().takeIf { index -> index in data.visibleItems.indices }
        val targetGridIndex = focusedGridIndex
            ?: (params.gridState.firstVisibleItemIndex - layout.leadingGridItemCount)
                .coerceIn(0, data.visibleItems.lastIndex)
        val targetIndex = targetGridIndex.coerceIn(0, data.visibleItems.lastIndex)
        actions.updateFocusedIndex(targetIndex)
        focusController.focusItemWhenVisible(targetIndex)
        onHandledNonceChange(params.focusCurrentRequestNonce)
    }
}
