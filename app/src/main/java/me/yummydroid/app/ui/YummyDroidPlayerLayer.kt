package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import me.yummydroid.app.AppRoute
import me.yummydroid.app.data.canShowVideoSubscriptions
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.readyListOrEmpty

@Composable
internal fun PlayerLayerScreen(
    layer: AppScreenLayer,
    active: Boolean,
    zIndex: Float,
    visible: Boolean,
    runtime: YummyDroidAppLayerRuntime,
) {
    val route = layer.state.route as? AppRoute.Player ?: return
    val actions = runtime.actions
    AppLayerContainer(zIndex = zIndex, visible = visible, scaleFrom = 1f) {
        key(AppScreenKey.Player) {
            PlayerScreen(
                animeTitle = route.animeTitle,
                video = route.video,
                interactive = active,
                settings = layer.state.settings,
                startPositionMs = route.startPositionMs,
                preferredQuality = route.preferredQuality,
                allVideos = layer.state.videos.readyListOrEmpty(),
                selectedGroup = layer.state.selectedVideoGroup,
                streamState = layer.state.playerStream,
                playbackMetadataLoading = layer.state.playbackMetadataLoading,
                resumeChoicePositionMs = route.resumeChoicePositionMs,
                isInPictureInPicture = runtime.isInPictureInPicture,
                forcedOfflineMode = layer.state.forcedOfflineMode,
                allowSubscriptions = layer.state.auth.profile != null &&
                    !layer.state.forcedOfflineMode &&
                    (layer.state.details.readyDataOrNull()?.canShowVideoSubscriptions() == true),
                subscriptions = layer.state.detailsExtras.readyDataOrNull()?.subscriptions.orEmpty(),
                onSelectGroup = activeLayerValue(active, actions.onSelectVideoGroup, { _ -> }),
                onPlayVideo = activeLayerValue(active, actions.onPlayVideo, { _ -> }),
                onPlayVideoAt = activeLayerValue(active, actions.onPlayVideoAt, { _, _ -> }),
                onPlayVideoAtQuality = activeLayerValue(active, actions.onPlayVideoAtQuality, { _, _, _ -> }),
                onSelectPlaybackSource = activeLayerValue(
                    active,
                    actions.onSelectPlaybackSource,
                    { _, _ -> },
                ),
                onChooseResumePosition = activeLayerValue(
                    active,
                    actions.onChoosePlayerResumePosition,
                    { _ -> },
                ),
                onToggleVideoSubscription = activeLayerValue(
                    active,
                    actions.onTogglePlayerVideoSubscription,
                    { _ -> },
                ),
                onRetry = activeLayerValue(active, actions.onRetryVideo, {}),
                onPlaybackFailed = activeLayerValue(active, actions.onPlaybackFailed, { _, _, _ -> }),
                onPlaybackStarted = activeLayerValue(active, actions.onPlaybackStarted, { _ -> }),
                onPlaybackEnded = activeLayerValue(active, actions.onPlaybackEnded, { _ -> }),
                onPlaybackProgress = activeLayerValue(active, actions.onPlaybackProgress, { _, _, _ -> }),
                canUsePictureInPicture = active && runtime.canUsePictureInPicture,
                onEnterPictureInPicture = activeLayerValue(active, actions.onEnterPictureInPicture, {}),
                onSettingsChange = activeLayerValue(active, actions.onSettingsChange, { _ -> }),
                onBack = activeLayerValue(active, actions.onBack, {}),
                onRegisterModalInputActionHandler = activeLayerValue(
                    active,
                    { handler -> runtime.onRegisterModalInputActionHandler(AppScreenKey.Player, handler) },
                    {},
                ),
                onRegisterPlayerInputActionHandler = activeLayerValue(
                    active,
                    runtime.onPlayerInputControllerChange,
                    {},
                ),
            )
        }
    }
}
