package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant

internal data class NativePlayerSelectionSnapshot(
    val tracks: Tracks,
    val playbackSourceOptions: List<SourceOption>,
    val subtitleOptions: List<SubtitleOption>,
    val subtitlesLoading: Boolean,
    val qualityOptions: List<QualityOption>,
    val selectedQualityKey: String?,
    val selectedSubtitleKey: String,
    val streamSelectedQualityKey: String?,
    val onTracksChanged: (Tracks) -> Unit,
    val onSelectedQualityKeyChanged: (String?) -> Unit,
    val onSelectedSubtitleKeyChanged: (String) -> Unit,
    val onControllerSelectedSubtitleKeyChanged: (String) -> Unit,
)

internal fun resolveInitialNativeQualityKey(
    selectedLocalQualityKey: String?,
    streamSelectedQualityKey: String?,
    qualityOptions: List<QualityOption>,
    playbackPreferredQuality: PreferredQuality,
    defaultQuality: PreferredQuality,
): String? {
    val preferredOption = qualityOptions.preferredOption(
        playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto } ?: defaultQuality,
    )
    return selectedLocalQualityKey
        ?: streamSelectedQualityKey?.takeIf { key ->
            qualityOptions.any { it.matchesSelectedQualityKey(key) }
        }
        ?: preferredOption?.qualityOptionIdentity()
}

@Composable
internal fun rememberNativePlayerSelection(
    stream: ResolvedVideoStream,
    currentVideo: VideoVariant,
    player: ExoPlayer,
    playerView: () -> PlayerView?,
    playerControlTexts: PlayerControlTexts,
    sourceSubtitleLabel: String,
    playbackMetadataLoading: Boolean,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    sourceOptions: List<SourceOption>,
    playbackPreferredQuality: PreferredQuality,
    settings: AppSettings,
    offlineMode: Boolean,
): NativePlayerSelectionSnapshot {
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    val subtitles = rememberNativePlayerSubtitlePresentation(
        stream = stream,
        currentVideo = currentVideo,
        player = player,
        playerControlTexts = playerControlTexts,
        sourceSubtitleLabel = sourceSubtitleLabel,
        playbackMetadataLoading = playbackMetadataLoading,
        sourceOptions = sourceOptions,
        tracks = tracks,
    )
    val quality = rememberNativePlayerQualitySelection(
        stream = stream,
        currentVideo = currentVideo,
        player = player,
        playerView = playerView,
        playerControlTexts = playerControlTexts,
        groups = groups,
        selectedKey = selectedKey,
        playbackPreferredQuality = playbackPreferredQuality,
        defaultQuality = settings.defaultQuality,
        offlineMode = offlineMode,
        tracks = tracks,
    )
    val subtitleSelection = rememberNativePlayerSubtitleSelection(
        currentVideo = currentVideo,
        streamUrl = stream.url,
        player = player,
        playerView = playerView,
        subtitleOptions = subtitles.options,
    )
    return NativePlayerSelectionSnapshot(
        tracks = tracks,
        playbackSourceOptions = subtitles.playbackSourceOptions,
        subtitleOptions = subtitles.options,
        subtitlesLoading = subtitles.loading,
        qualityOptions = quality.options,
        selectedQualityKey = quality.selectedKey,
        selectedSubtitleKey = subtitleSelection.selectedKey,
        streamSelectedQualityKey = quality.streamSelectedKey,
        onTracksChanged = { tracks = it },
        onSelectedQualityKeyChanged = quality.onSelectedKeyChanged,
        onSelectedSubtitleKeyChanged = subtitleSelection.onSelectedKeyChanged,
        onControllerSelectedSubtitleKeyChanged = subtitleSelection.onControllerSelectedKeyChanged,
    )
}
