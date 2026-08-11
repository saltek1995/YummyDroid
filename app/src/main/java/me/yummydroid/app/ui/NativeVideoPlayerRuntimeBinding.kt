package me.yummydroid.app.ui

import androidx.compose.ui.Modifier
import me.yummydroid.app.PlaybackFailure
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant

internal class NativeVideoPlayerRuntimeBinding(
    val stream: ResolvedVideoStream,
    val animeTitle: String,
    val currentVideo: VideoVariant,
    val interactive: Boolean,
    val settings: AppSettings,
    val startPositionMs: Long,
    val playbackPreferredQuality: PreferredQuality,
    val playbackMetadataLoading: Boolean,
    val groups: Map<String, List<VideoVariant>>,
    val selectedKey: String?,
    val sourceOptions: List<SourceOption>,
    val selectedSourceKey: String?,
    val previousVideo: VideoVariant?,
    val nextVideo: VideoVariant?,
    val allowSubscription: Boolean,
    val subscriptionActive: Boolean,
    val onToggleSubscription: () -> Unit,
    val onSelectGroup: (String, VideoVariant?, Long) -> Unit,
    val onSelectSource: (VideoVariant, Long) -> Unit,
    val onPlayVideoAt: (VideoVariant, Long) -> Unit,
    val onPlayVideoAtQuality: (VideoVariant, Long, PreferredQuality) -> Unit,
    val onPlaybackFailed: (VideoVariant, Long, PlaybackFailure) -> Unit,
    val onPlaybackStarted: (VideoVariant) -> Unit,
    val onPlaybackEnded: (VideoVariant) -> Unit,
    val onPlaybackProgress: (VideoVariant, Long, Long) -> Unit,
    val canUsePictureInPicture: Boolean,
    val isInPictureInPicture: Boolean,
    val onEnterPictureInPicture: () -> Unit,
    val onSettingsChange: (AppSettings) -> Unit,
    val onBack: () -> Unit,
    val onRegisterPlayerInputActionHandler: (PlayerInputController?) -> Unit,
    val offlineMode: Boolean,
    val modifier: Modifier,
    val playerControlFocusToRestoreId: Int?,
    val keepControlsVisibleAfterReady: Boolean,
    val onRememberPlayerControlFocus: (Int) -> Unit,
    val onPlayerControlFocusRestored: () -> Unit,
    val onKeepControlsVisibleAfterReadyRequested: () -> Unit,
    val onControlsKeptVisibleAfterReady: () -> Unit,
)
