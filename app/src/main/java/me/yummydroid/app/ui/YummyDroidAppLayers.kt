package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.InputAction

internal class YummyDroidAppLayerRuntime(
    val actions: YummyDroidAppActions,
    val browseCoordinator: BrowseRootUiCoordinator,
    val detailsScreenUiStates: SnapshotStateMap<AppScreenKey.Details, DetailsScreenUiState>,
    val activeLayerFocusRequestNonce: Long,
    val activeLayerFocusNonce: Long,
    val isInPictureInPicture: Boolean,
    val canUsePictureInPicture: Boolean,
    val loginDialogOpen: Boolean,
    val profileDialogOpen: Boolean,
    val settingsDialogOpen: Boolean,
    val onOpenAnimeFromCatalog: (Long) -> Unit,
    val onOpenLogin: () -> Unit,
    val onOpenProfile: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenDownloads: () -> Unit,
    val onHomeBackToTopHandlerChange: (BrowseSection, HomeBackToTopHandler?) -> Unit,
    val onHomeBrowseBackStateChange: (HomeBrowseBackState) -> Unit,
    val onRegisterModalInputActionHandler: (Any, ((InputAction) -> Boolean)?) -> Unit,
    val onRegisterDpadFocusRecoveryHandler: (Any, (() -> Boolean)?) -> Unit,
    val onPlayerInputControllerChange: (PlayerInputController?) -> Unit,
)

@Composable
internal fun YummyDroidAppLayerHost(
    renderedLayers: List<AppScreenLayer>,
    exitingLayers: List<AppScreenLayer>,
    runtime: YummyDroidAppLayerRuntime,
) {
    renderedLayers.forEachIndexed { index, layer ->
        key(layer.key) {
            AppLayerScreen(
                layer = layer,
                active = index == renderedLayers.lastIndex,
                zIndex = index.toFloat(),
                visible = true,
                runtime = runtime,
            )
        }
    }
    exitingLayers.forEachIndexed { index, layer ->
        key("exiting:${layer.key}") {
            AppLayerScreen(
                layer = layer,
                active = false,
                zIndex = (renderedLayers.size + index).toFloat() + 1_000f,
                visible = false,
                runtime = runtime,
            )
        }
    }
}

@Composable
private fun AppLayerScreen(
    layer: AppScreenLayer,
    active: Boolean,
    zIndex: Float,
    visible: Boolean,
    runtime: YummyDroidAppLayerRuntime,
) {
    when (layer.key) {
        AppScreenKey.Home -> HomeLayerScreen(layer, active, zIndex, visible, runtime)
        is AppScreenKey.Details -> DetailsLayerScreen(layer, active, zIndex, visible, runtime)
        AppScreenKey.Player -> PlayerLayerScreen(layer, active, zIndex, visible, runtime)
    }
}

@Composable
internal fun AppLayerContainer(
    zIndex: Float,
    visible: Boolean,
    scaleFrom: Float = 0.99f,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(zIndex)
            .yummyAppearMotion(visible = visible, scaleFrom = scaleFrom),
    ) {
        content()
    }
}

internal fun <T> activeLayerValue(active: Boolean, activeValue: T, inactiveValue: T): T =
    if (active) activeValue else inactiveValue
