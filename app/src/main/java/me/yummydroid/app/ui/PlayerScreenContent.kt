package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.LoadState
import me.yummydroid.app.PlaybackFailure

internal data class PlayerScreenActions(
    val onSelectGroup: (String) -> Unit,
    val onPlayVideoAtQuality: (VideoVariant, Long, PreferredQuality) -> Unit,
    val onSelectPlaybackSource: (VideoVariant, Long) -> Unit,
    val onChooseResumePosition: (Long) -> Unit,
    val onToggleVideoSubscription: (VideoVariant) -> Unit,
    val onRetry: () -> Unit,
    val onPlaybackFailed: (VideoVariant, Long, PlaybackFailure) -> Unit,
    val onPlaybackStarted: (VideoVariant) -> Unit,
    val onPlaybackEnded: (VideoVariant) -> Unit,
    val onPlaybackProgress: (VideoVariant, Long, Long) -> Unit,
    val onEnterPictureInPicture: () -> Unit,
    val onSettingsChange: (AppSettings) -> Unit,
    val onBack: () -> Unit,
    val onRegisterPlayerInputActionHandler: (PlayerInputController?) -> Unit,
)

internal data class PlayerControlFocusBinding(
    val restoreId: Int?,
    val keepVisibleAfterReady: Boolean,
    val onRemember: (Int) -> Unit,
    val onRestored: () -> Unit,
    val onKeepVisibleRequested: () -> Unit,
    val onKeptVisible: () -> Unit,
)

@Composable
internal fun PlayerScreenContent(
    animeTitle: String,
    requestedVideo: VideoVariant,
    interactive: Boolean,
    settings: AppSettings,
    presentation: PlayerScreenPresentation,
    streamState: LoadState<*>,
    playbackMetadataLoading: Boolean,
    resumeChoicePositionMs: Long?,
    isInPictureInPicture: Boolean,
    forcedOfflineMode: Boolean,
    allowSubscriptions: Boolean,
    subscriptions: List<VideoSubscription>,
    canUsePictureInPicture: Boolean,
    actions: PlayerScreenActions,
    controlFocus: PlayerControlFocusBinding,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (presentation.playbackStream != null && resumeChoicePositionMs == null) {
            NativeVideoPlayer(
                stream = presentation.playbackStream,
                animeTitle = animeTitle,
                currentVideo = presentation.playbackVideo,
                interactive = interactive,
                settings = settings,
                startPositionMs = presentation.playbackStartPositionMs,
                playbackPreferredQuality = presentation.playbackPreferredQuality,
                playbackMetadataLoading = playbackMetadataLoading,
                groups = presentation.groups,
                selectedKey = presentation.selectedVoiceKey,
                sourceOptions = presentation.sourceOptions,
                selectedSourceKey = presentation.selectedSourceKey,
                previousVideo = presentation.previousVideo,
                nextVideo = presentation.nextVideo,
                allowSubscription = allowSubscriptions,
                subscriptionActive = subscriptions.isVideoVoiceSubscribed(presentation.playbackVideo),
                onToggleSubscription = {
                    actions.onToggleVideoSubscription(presentation.playbackVideo)
                },
                onSelectGroup = { groupKey, replacement, positionMs ->
                    if (replacement != null) {
                        actions.onSelectGroup(replacement.groupKey)
                        actions.onPlayVideoAtQuality(
                            replacement,
                            positionMs,
                            presentation.playbackPreferredQuality,
                        )
                    } else {
                        actions.onSelectGroup(groupKey)
                    }
                },
                onSelectSource = { source, positionMs ->
                    actions.onSelectGroup(source.groupKey)
                    actions.onSelectPlaybackSource(source, positionMs)
                },
                onPlayVideo = { next ->
                    actions.onSelectGroup(next.groupKey)
                    actions.onPlayVideoAtQuality(next, 0L, presentation.playbackPreferredQuality)
                },
                onPlayVideoAt = { next, positionMs ->
                    actions.onSelectGroup(next.groupKey)
                    actions.onPlayVideoAtQuality(next, positionMs, presentation.playbackPreferredQuality)
                },
                onPlayVideoAtQuality = { next, positionMs, nextPreferredQuality ->
                    actions.onSelectGroup(next.groupKey)
                    actions.onPlayVideoAtQuality(next, positionMs, nextPreferredQuality)
                },
                onPlaybackFailed = actions.onPlaybackFailed,
                onPlaybackStarted = actions.onPlaybackStarted,
                onPlaybackEnded = actions.onPlaybackEnded,
                onPlaybackProgress = actions.onPlaybackProgress,
                canUsePictureInPicture = canUsePictureInPicture,
                isInPictureInPicture = isInPictureInPicture,
                onEnterPictureInPicture = actions.onEnterPictureInPicture,
                onSettingsChange = actions.onSettingsChange,
                onBack = actions.onBack,
                onRegisterPlayerInputActionHandler = actions.onRegisterPlayerInputActionHandler,
                offlineMode = forcedOfflineMode,
                playerControlFocusToRestoreId = controlFocus.restoreId,
                keepControlsVisibleAfterReady = controlFocus.keepVisibleAfterReady,
                onRememberPlayerControlFocus = controlFocus.onRemember,
                onPlayerControlFocusRestored = controlFocus.onRestored,
                onKeepControlsVisibleAfterReadyRequested = controlFocus.onKeepVisibleRequested,
                onControlsKeptVisibleAfterReady = controlFocus.onKeptVisible,
                modifier = Modifier.fillMaxSize(),
            )
            if (presentation.useRetainedPlayback) {
                PlayerLoadingOverlay()
            }
        } else {
            PlayerShellPane(
                animeTitle = animeTitle,
                currentVideo = presentation.playbackVideo,
                settings = settings,
                groups = presentation.groups,
                selectedKey = presentation.selectedVoiceKey,
                sourceOptions = presentation.sourceOptions,
                selectedSourceKey = presentation.selectedSourceKey,
                previousVideo = presentation.previousVideo,
                nextVideo = presentation.nextVideo,
                allowSubscription = allowSubscriptions,
                subscriptionActive = subscriptions.isVideoVoiceSubscribed(presentation.playbackVideo),
                canUsePictureInPicture = canUsePictureInPicture,
                onToggleSubscription = {
                    actions.onToggleVideoSubscription(presentation.playbackVideo)
                },
                onSelectGroup = { groupKey, replacement ->
                    if (replacement != null) {
                        actions.onSelectGroup(replacement.groupKey)
                        actions.onPlayVideoAtQuality(
                            replacement,
                            presentation.playbackStartPositionMs,
                            presentation.playbackPreferredQuality,
                        )
                    } else {
                        actions.onSelectGroup(groupKey)
                    }
                },
                onSelectSource = { source ->
                    actions.onSelectGroup(source.groupKey)
                    actions.onSelectPlaybackSource(source, presentation.playbackStartPositionMs)
                },
                onPlayVideo = { next ->
                    actions.onSelectGroup(next.groupKey)
                    actions.onPlayVideoAtQuality(next, 0L, presentation.playbackPreferredQuality)
                },
                message = (streamState as? LoadState.Error)?.message,
                onRetry = actions.onRetry,
                onBack = actions.onBack,
                playerControlFocusToRestoreId = controlFocus.restoreId,
                onRememberPlayerControlFocus = controlFocus.onRemember,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (resumeChoicePositionMs != null) {
            PlayerResumeChoiceDialog(
                video = requestedVideo,
                positionMs = resumeChoicePositionMs,
                onStartOver = { actions.onChooseResumePosition(0L) },
                onResume = { actions.onChooseResumePosition(resumeChoicePositionMs) },
                onDismiss = actions.onBack,
            )
        }
    }
}
