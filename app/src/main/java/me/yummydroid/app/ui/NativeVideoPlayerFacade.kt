package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import me.yummydroid.app.PlaybackFailure
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant

@OptIn(UnstableApi::class)
@Composable
internal fun NativeVideoPlayer(
    stream: ResolvedVideoStream,
    animeTitle: String,
    currentVideo: VideoVariant,
    interactive: Boolean,
    settings: AppSettings,
    startPositionMs: Long,
    playbackPreferredQuality: PreferredQuality,
    playbackMetadataLoading: Boolean,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    sourceOptions: List<SourceOption>,
    selectedSourceKey: String?,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    allowSubscription: Boolean,
    subscriptionActive: Boolean,
    onToggleSubscription: () -> Unit,
    onSelectGroup: (String, VideoVariant?, Long) -> Unit,
    onSelectSource: (VideoVariant, Long) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onPlayVideoAtQuality: (VideoVariant, Long, PreferredQuality) -> Unit,
    onPlaybackFailed: (VideoVariant, Long, PlaybackFailure) -> Unit,
    onPlaybackStarted: (VideoVariant) -> Unit,
    onPlaybackEnded: (VideoVariant) -> Unit,
    onPlaybackProgress: (VideoVariant, Long, Long) -> Unit,
    canUsePictureInPicture: Boolean,
    isInPictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onRegisterPlayerInputActionHandler: ((PlayerInputController?) -> Unit),
    offlineMode: Boolean,
    modifier: Modifier = Modifier,
    playerControlFocusToRestoreId: Int? = null,
    keepControlsVisibleAfterReady: Boolean = false,
    onRememberPlayerControlFocus: (Int) -> Unit = {},
    onPlayerControlFocusRestored: () -> Unit = {},
    onKeepControlsVisibleAfterReadyRequested: () -> Unit = {},
    onControlsKeptVisibleAfterReady: () -> Unit = {},
) {
    NativeVideoPlayerRuntime(
        NativeVideoPlayerRuntimeBinding(
            stream = stream,
            animeTitle = animeTitle,
            currentVideo = currentVideo,
            interactive = interactive,
            settings = settings,
            startPositionMs = startPositionMs,
            playbackPreferredQuality = playbackPreferredQuality,
            playbackMetadataLoading = playbackMetadataLoading,
            groups = groups,
            selectedKey = selectedKey,
            sourceOptions = sourceOptions,
            selectedSourceKey = selectedSourceKey,
            previousVideo = previousVideo,
            nextVideo = nextVideo,
            allowSubscription = allowSubscription,
            subscriptionActive = subscriptionActive,
            onToggleSubscription = onToggleSubscription,
            onSelectGroup = onSelectGroup,
            onSelectSource = onSelectSource,
            onPlayVideoAt = onPlayVideoAt,
            onPlayVideoAtQuality = onPlayVideoAtQuality,
            onPlaybackFailed = onPlaybackFailed,
            onPlaybackStarted = onPlaybackStarted,
            onPlaybackEnded = onPlaybackEnded,
            onPlaybackProgress = onPlaybackProgress,
            canUsePictureInPicture = canUsePictureInPicture,
            isInPictureInPicture = isInPictureInPicture,
            onEnterPictureInPicture = onEnterPictureInPicture,
            onSettingsChange = onSettingsChange,
            onBack = onBack,
            onRegisterPlayerInputActionHandler = onRegisterPlayerInputActionHandler,
            offlineMode = offlineMode,
            modifier = modifier,
            playerControlFocusToRestoreId = playerControlFocusToRestoreId,
            keepControlsVisibleAfterReady = keepControlsVisibleAfterReady,
            onRememberPlayerControlFocus = onRememberPlayerControlFocus,
            onPlayerControlFocusRestored = onPlayerControlFocusRestored,
            onKeepControlsVisibleAfterReadyRequested = onKeepControlsVisibleAfterReadyRequested,
            onControlsKeptVisibleAfterReady = onControlsKeptVisibleAfterReady,
        ),
    )
}
