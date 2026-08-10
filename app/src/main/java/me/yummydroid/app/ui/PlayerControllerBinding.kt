package me.yummydroid.app.ui

import androidx.media3.exoplayer.ExoPlayer
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant

internal class PlayerControllerBinding(
    val player: ExoPlayer,
    val animeTitle: String,
    val currentVideo: VideoVariant,
    val isLocalPlayback: Boolean,
    val groups: Map<String, List<VideoVariant>>,
    val selectedKey: String?,
    val sourceOptions: List<SourceOption>,
    val selectedSourceKey: String?,
    val previousVideo: VideoVariant?,
    val nextVideo: VideoVariant?,
    val allowSubscription: Boolean,
    val subscriptionActive: Boolean,
    val onToggleSubscription: () -> Unit,
    val qualityOptions: List<QualityOption>,
    val selectedQualityKey: String?,
    val onSelectedQualityKeyChange: (String) -> Unit,
    val subtitleOptions: List<SubtitleOption>,
    val subtitlesLoading: Boolean,
    val selectedSubtitleKey: String,
    val onSelectedSubtitleKeyChange: (String) -> Unit,
    val onSelectLocalQuality: (OfflineVideoFile) -> Unit,
    val onSelectPreferredQuality: (PreferredQuality) -> Unit,
    val onSelectGroup: (String, VideoVariant?, Long) -> Unit,
    val onSelectSource: (VideoVariant, Long) -> Unit,
    val onPlayVideoAt: (VideoVariant, Long) -> Unit,
    val canUsePictureInPicture: Boolean,
    val onEnterPictureInPicture: () -> Unit,
    val settings: AppSettings,
    val skipControlsTimelineReady: Boolean,
    val texts: PlayerControlTexts,
    val onSettingsChange: (AppSettings) -> Unit,
    val onBack: () -> Unit,
    val onRequestPlay: () -> Unit,
    val onPausePlayback: () -> Unit,
    val onRememberPlayerControlFocus: (Int) -> Unit,
)
