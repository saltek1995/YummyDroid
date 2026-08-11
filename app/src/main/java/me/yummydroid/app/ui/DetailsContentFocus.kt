package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

internal class DetailsLayerFocusState {
    var hasFocus by mutableStateOf(false)
}

@Composable
internal fun rememberDetailsLayerFocusState(): DetailsLayerFocusState = remember {
    DetailsLayerFocusState()
}

@Composable
internal fun DetailsContentFocusEffects(
    model: DetailsContentModel,
    actions: DetailsContentActions,
    presentation: DetailsContentPresentation,
    focusGridState: VisualFocusGridState,
    layerFocusState: DetailsLayerFocusState,
) {
    RetainDetailsFocusKeyEffect(model.screenUiState, focusGridState)
    RegisterDetailsFocusRecoveryEffect(model.screenUiState, actions, focusGridState, layerFocusState)
    RequestInitialDetailsFocusEffect(model, presentation, focusGridState)
    RequestRetainedDetailsFocusEffect(model, presentation, focusGridState)
}

@Composable
private fun RetainDetailsFocusKeyEffect(
    screenUiState: DetailsScreenUiState,
    focusGridState: VisualFocusGridState,
) {
    val lastFocusedDetailsKey = focusGridState.lastFocusedKey
    LaunchedEffect(lastFocusedDetailsKey) {
        if (lastFocusedDetailsKey != null) {
            screenUiState.retainedFocusKey = lastFocusedDetailsKey
        }
    }
}

@Composable
private fun RegisterDetailsFocusRecoveryEffect(
    screenUiState: DetailsScreenUiState,
    actions: DetailsContentActions,
    focusGridState: VisualFocusGridState,
    layerFocusState: DetailsLayerFocusState,
) {
    fun recoverFirstDetailsFocusIfMissing(): Boolean {
        if (layerFocusState.hasFocus && focusGridState.focusedIndex != null) return false
        val restored = focusGridState.requestFocusByKey(screenUiState.retainedFocusKey) == true ||
            focusGridState.requestRetainedOrFirstAvailableFocus()
        if (restored) screenUiState.suppressInitialFocusOnReactivation = false
        return restored
    }

    DisposableEffect(focusGridState, actions.onRegisterDpadFocusRecoveryHandler) {
        actions.onRegisterDpadFocusRecoveryHandler(::recoverFirstDetailsFocusIfMissing)
        onDispose { actions.onRegisterDpadFocusRecoveryHandler(null) }
    }
}

@Composable
private fun RequestInitialDetailsFocusEffect(
    model: DetailsContentModel,
    presentation: DetailsContentPresentation,
    focusGridState: VisualFocusGridState,
) {
    val detailsId = model.details.id
    val focusLayoutSize = presentation.focusLayout.size
    val hasHeroActions = presentation.watchVideo != null || presentation.hasWatchProgress
    LaunchedEffect(model.activeFocusRequestNonce, detailsId, hasHeroActions, focusLayoutSize) {
        if (model.activeFocusRequestNonce <= 0L || hasHeroActions) return@LaunchedEffect
        repeat(8) {
            withFrameNanos { }
            if (focusGridState.requestFirstAvailableFocus()) return@LaunchedEffect
        }
    }
}

@Composable
private fun RequestRetainedDetailsFocusEffect(
    model: DetailsContentModel,
    presentation: DetailsContentPresentation,
    focusGridState: VisualFocusGridState,
) {
    val screenUiState = model.screenUiState
    val detailsId = model.details.id
    val focusLayoutSize = presentation.focusLayout.size
    LaunchedEffect(model.retainedFocusRequestNonce, detailsId, focusLayoutSize) {
        if (model.retainedFocusRequestNonce <= 0L) return@LaunchedEffect
        repeat(8) {
            withFrameNanos { }
            val restored = focusGridState.requestFocusByKey(screenUiState.retainedFocusKey) == true ||
                (screenUiState.retainedFocusKey == null && focusGridState.requestLastFocusedFocus())
            if (restored) {
                screenUiState.suppressInitialFocusOnReactivation = false
                return@LaunchedEffect
            }
        }
    }
}
