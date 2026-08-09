package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.delay
import me.yummydroid.app.YummyDroidUiState

@Stable
internal class YummyDroidAppLayerState {
    val detailsScreenUiStates: SnapshotStateMap<AppScreenKey.Details, DetailsScreenUiState> =
        mutableStateMapOf()
    var appLayers by mutableStateOf(emptyList<AppScreenLayer>())
    var exitingAppLayers by mutableStateOf(emptyList<AppScreenLayer>())
}

internal data class YummyDroidAppLayerSnapshot(
    val renderedLayers: List<AppScreenLayer>,
    val exitingLayers: List<AppScreenLayer>,
    val activeLayerKey: AppScreenKey?,
    val detailsScreenUiStates: SnapshotStateMap<AppScreenKey.Details, DetailsScreenUiState>,
)

@Composable
internal fun rememberYummyDroidAppLayerSnapshot(
    state: YummyDroidUiState,
): YummyDroidAppLayerSnapshot {
    val layerState = remember { YummyDroidAppLayerState() }
    val renderedLayers = layerState.appLayers.syncedWith(state)
    val renderedLayerKeys = renderedLayers.map { layer -> layer.key }.toSet()
    val pendingExitingLayers = layerState.appLayers.filter { layer -> layer.key !in renderedLayerKeys }
    val displayedExitingLayers = (layerState.exitingAppLayers + pendingExitingLayers)
        .filter { layer -> layer.key !in renderedLayerKeys }
        .distinctBy { layer -> layer.key }

    SideEffect {
        if (layerState.exitingAppLayers != displayedExitingLayers) {
            layerState.exitingAppLayers = displayedExitingLayers
        }
        if (layerState.appLayers != renderedLayers) {
            layerState.appLayers = renderedLayers
        }
    }
    RetainActiveLayerState(
        layerState = layerState,
        renderedLayerKeys = renderedLayerKeys,
        displayedExitingLayers = displayedExitingLayers,
    )

    return YummyDroidAppLayerSnapshot(
        renderedLayers = renderedLayers,
        exitingLayers = displayedExitingLayers,
        activeLayerKey = renderedLayers.lastOrNull()?.key,
        detailsScreenUiStates = layerState.detailsScreenUiStates,
    )
}

@Composable
private fun RetainActiveLayerState(
    layerState: YummyDroidAppLayerState,
    renderedLayerKeys: Set<AppScreenKey>,
    displayedExitingLayers: List<AppScreenLayer>,
) {
    val displayedExitingKeys = displayedExitingLayers.map { layer -> layer.key }
    LaunchedEffect(renderedLayerKeys, displayedExitingKeys) {
        val retainedDetailsKeys = (renderedLayerKeys + displayedExitingKeys)
            .filterIsInstance<AppScreenKey.Details>()
            .toSet()
        layerState.detailsScreenUiStates.keys.toList().forEach { key ->
            if (key !in retainedDetailsKeys) {
                layerState.detailsScreenUiStates.remove(key)
            }
        }
    }
    LaunchedEffect(displayedExitingKeys) {
        if (displayedExitingKeys.isEmpty()) return@LaunchedEffect
        val exitingKeys = displayedExitingKeys.toSet()
        delay(YUMMY_FADE_OUT_MS.toLong())
        layerState.exitingAppLayers = layerState.exitingAppLayers.filterNot { layer ->
            layer.key in exitingKeys
        }
    }
}
