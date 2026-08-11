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
    val screenUiState = model.screenUiState
    val lastFocusedDetailsKey = focusGridState.lastFocusedKey
    LaunchedEffect(lastFocusedDetailsKey) {
        if (lastFocusedDetailsKey != null) {
            screenUiState.retainedFocusKey = lastFocusedDetailsKey
        }
    }

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
