package me.yummydroid.app.ui

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import me.yummydroid.app.AppLog
import me.yummydroid.app.R
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey

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
    val materializedSubtitles = remember(stream.subtitles) {
        stream.subtitles.filter { subtitle -> subtitle.isMaterializedSubtitleTrack() }
    }
    val pendingSubtitleCandidates = remember(stream.subtitles) {
        stream.subtitles.any { subtitle -> !subtitle.isMaterializedSubtitleTrack() }
    }
    val streamSubtitleSignature = remember(stream.url, materializedSubtitles) {
        materializedSubtitles.joinToString("|") { subtitle ->
            listOf(
                subtitle.uri,
                subtitle.label,
                subtitle.language.orEmpty(),
                subtitle.mimeType.orEmpty(),
            ).joinToString(":")
        }
    }
    var appliedSubtitleSignature by remember(player) { mutableStateOf(streamSubtitleSignature) }
    LaunchedEffect(player, stream.url, streamSubtitleSignature) {
        if (appliedSubtitleSignature == streamSubtitleSignature) return@LaunchedEffect
        if (player.prepareCurrentMediaItemIfSameVideo(stream.toMediaItem())) {
            appliedSubtitleSignature = streamSubtitleSignature
        } else {
            AppLog.w("YummyDroidPlayer", "Skipped subtitle media item update because the current video changed")
        }
    }

    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    val onlineQualityOptions = remember(tracks) { tracks.videoQualityOptions() }
    val resolvedSubtitles = remember(stream.subtitles, stream.embeddedSubtitles) {
        (
            stream.subtitles.mapIndexedNotNull { index, subtitle ->
                subtitle.toSubtitleDisplayReference(index)
            } +
                stream.embeddedSubtitles.mapIndexedNotNull { index, subtitle ->
                    subtitle.toSubtitleDisplayReference(stream.subtitles.size + index)
                }
            ).distinctBy { subtitle ->
                listOf(
                    subtitle.media3Id,
                    subtitle.sourceIndex?.toString().orEmpty(),
                    subtitle.label,
                ).joinToString(":")
            }
    }
    val subtitleOptions = remember(tracks, playerControlTexts, resolvedSubtitles) {
        tracks.subtitleOptions(playerControlTexts, resolvedSubtitles)
    }
    val playbackSourceOptions = remember(sourceOptions, currentVideo, subtitleOptions, sourceSubtitleLabel) {
        sourceOptions.withCurrentSubtitleMarker(
            currentVideo = currentVideo,
            hasSubtitles = subtitleOptions.isNotEmpty(),
            sourceSubtitleLabel = sourceSubtitleLabel,
        )
    }
    val subtitlesLoading = playbackMetadataLoading &&
        pendingSubtitleCandidates &&
        materializedSubtitles.isEmpty()
    val sourceQualityOptions = remember(
        groups,
        selectedKey,
        currentVideo.matchingEpisodeKey,
        currentVideo.matchingVoiceKey,
    ) {
        val sourceVideos = groups[selectedKey].orEmpty()
            .ifEmpty { groups[currentVideo.matchingVoiceKey].orEmpty() }
        sourceVideos.sourceQualityOptionsFor(currentVideo)
    }
    val streamQualityOptions = remember(stream.availableQualities) {
        stream.availableQualities.sourceQualityOptions()
    }
    val localQualityOptions = remember(
        currentVideo.matchingEpisodeKey,
        currentVideo.matchingVoiceKey,
        currentVideo.localPlaybackUrl,
        currentVideo.localFiles,
    ) {
        currentVideo.localQualityOptions()
    }
    val qualityOptions = remember(
        onlineQualityOptions,
        sourceQualityOptions,
        streamQualityOptions,
        localQualityOptions,
        offlineMode,
    ) {
        mergeVideoQualityOptions(
            onlineOptions = onlineQualityOptions + sourceQualityOptions + streamQualityOptions,
            localOptions = localQualityOptions,
            offlineMode = offlineMode,
            downloadedLabel = playerControlTexts.downloaded,
        )
    }
    val streamSelectedQualityKey = remember(currentVideo.id, stream.url, stream.selectedVideoHeight) {
        stream.selectedVideoHeight
            ?.takeIf { it > 0 }
            ?.let { "height:$it" }
    }
    var selectedQualityKey by remember(currentVideo.id, stream.url, stream.selectedVideoHeight) {
        mutableStateOf(
            resolveInitialNativeQualityKey(
                selectedLocalQualityKey = currentVideo.selectedLocalQualityKey(stream.url),
                streamSelectedQualityKey = streamSelectedQualityKey,
                qualityOptions = qualityOptions,
                playbackPreferredQuality = playbackPreferredQuality,
                defaultQuality = settings.defaultQuality,
            ),
        )
    }
    var selectedSubtitleKey by remember(currentVideo.id, stream.url) {
        mutableStateOf(SUBTITLE_OFF_KEY)
    }
    var subtitleSelectionTouched by remember(currentVideo.id, stream.url) {
        mutableStateOf(false)
    }

    NativePlayerQualitySelectionEffects(
        player = player,
        playerView = playerView,
        streamUrl = stream.url,
        qualityOptions = qualityOptions,
        streamSelectedQualityKey = streamSelectedQualityKey,
        playbackPreferredQuality = playbackPreferredQuality,
        defaultQuality = settings.defaultQuality,
        selectedQualityKey = selectedQualityKey,
        onSelectedQualityKeyChanged = { selectedQualityKey = it },
    )
    NativePlayerSubtitleSelectionEffect(
        player = player,
        playerView = playerView,
        subtitleOptions = subtitleOptions,
        selectedSubtitleKey = selectedSubtitleKey,
        subtitleSelectionTouched = subtitleSelectionTouched,
        onSelectedSubtitleKeyChanged = { selectedSubtitleKey = it },
    )

    return NativePlayerSelectionSnapshot(
        tracks = tracks,
        playbackSourceOptions = playbackSourceOptions,
        subtitleOptions = subtitleOptions,
        subtitlesLoading = subtitlesLoading,
        qualityOptions = qualityOptions,
        selectedQualityKey = selectedQualityKey,
        selectedSubtitleKey = selectedSubtitleKey,
        streamSelectedQualityKey = streamSelectedQualityKey,
        onTracksChanged = { tracks = it },
        onSelectedQualityKeyChanged = { selectedQualityKey = it },
        onSelectedSubtitleKeyChanged = { selectedSubtitleKey = it },
        onControllerSelectedSubtitleKeyChanged = {
            subtitleSelectionTouched = true
            selectedSubtitleKey = it
        },
    )
}

@Composable
private fun NativePlayerQualitySelectionEffects(
    player: ExoPlayer,
    playerView: () -> PlayerView?,
    streamUrl: String,
    qualityOptions: List<QualityOption>,
    streamSelectedQualityKey: String?,
    playbackPreferredQuality: PreferredQuality,
    defaultQuality: PreferredQuality,
    selectedQualityKey: String?,
    onSelectedQualityKeyChanged: (String?) -> Unit,
) {
    val currentSelectedQualityKey by rememberUpdatedState(selectedQualityKey)
    LaunchedEffect(qualityOptions) {
        val currentKey = currentSelectedQualityKey
        if (currentKey != null && qualityOptions.none { it.matchesSelectedQualityKey(currentKey) }) {
            val preferredQuality = playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto }
                ?: defaultQuality
            onSelectedQualityKeyChanged(
                streamSelectedQualityKey
                    ?.takeIf { key -> qualityOptions.any { it.matchesSelectedQualityKey(key) } }
                    ?: qualityOptions.preferredOption(preferredQuality)?.qualityOptionIdentity(),
            )
        }
    }
    LaunchedEffect(
        qualityOptions,
        playbackPreferredQuality,
        defaultQuality,
        streamUrl,
        streamSelectedQualityKey,
    ) {
        if (
            currentSelectedQualityKey != null &&
            qualityOptions.any { it.matchesSelectedQualityKey(currentSelectedQualityKey) }
        ) {
            return@LaunchedEffect
        }
        val resolvedSourceKey = streamSelectedQualityKey
            ?.takeIf { key -> qualityOptions.any { it.matchesSelectedQualityKey(key) } }
        val preferredOption = qualityOptions.preferredOption(
            playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto } ?: defaultQuality,
        )
        val preferredKey = resolvedSourceKey ?: preferredOption?.qualityOptionIdentity()
        if (preferredKey != null && currentSelectedQualityKey != preferredKey) {
            preferredOption?.takeIf { it.group != null }?.let(player::selectQuality)
            onSelectedQualityKeyChanged(preferredKey)
            playerView()?.findViewById<View>(R.id.yummy_player_quality)
                ?.setTag(R.id.yummy_player_quality, preferredKey)
        }
    }
    LaunchedEffect(player, qualityOptions, playbackPreferredQuality, defaultQuality, streamUrl) {
        val preferredQuality = playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto }
            ?: defaultQuality.takeUnless { it == PreferredQuality.Auto }
        val preferredOption = preferredQuality?.let { qualityOptions.preferredOption(it) }
        if (preferredOption?.group != null) {
            player.selectQuality(preferredOption)
        }
    }
}

@Composable
private fun NativePlayerSubtitleSelectionEffect(
    player: ExoPlayer,
    playerView: () -> PlayerView?,
    subtitleOptions: List<SubtitleOption>,
    selectedSubtitleKey: String,
    subtitleSelectionTouched: Boolean,
    onSelectedSubtitleKeyChanged: (String) -> Unit,
) {
    LaunchedEffect(player, subtitleOptions, selectedSubtitleKey, subtitleSelectionTouched) {
        val selectedSubtitleIsAvailable = subtitleOptions.any {
            it.matchesSelectedSubtitleKey(selectedSubtitleKey)
        }
        if (!subtitleSelectionTouched && (selectedSubtitleKey == SUBTITLE_OFF_KEY || !selectedSubtitleIsAvailable)) {
            val defaultOption = subtitleOptions.defaultSubtitleOption() ?: run {
                if (selectedSubtitleKey != SUBTITLE_OFF_KEY) {
                    onSelectedSubtitleKeyChanged(SUBTITLE_OFF_KEY)
                    player.disableSubtitles()
                    playerView()?.findViewById<View>(R.id.yummy_player_subtitles)
                        ?.setTag(R.id.yummy_player_subtitles, SUBTITLE_OFF_KEY)
                }
                return@LaunchedEffect
            }
            player.selectSubtitle(defaultOption)
            val stableKey = defaultOption.subtitleOptionIdentity()
            onSelectedSubtitleKeyChanged(stableKey)
            playerView()?.findViewById<View>(R.id.yummy_player_subtitles)
                ?.setTag(R.id.yummy_player_subtitles, stableKey)
            return@LaunchedEffect
        }
        if (selectedSubtitleKey == SUBTITLE_OFF_KEY) return@LaunchedEffect
        if (!selectedSubtitleIsAvailable) {
            onSelectedSubtitleKeyChanged(SUBTITLE_OFF_KEY)
            player.disableSubtitles()
            playerView()?.findViewById<View>(R.id.yummy_player_subtitles)
                ?.setTag(R.id.yummy_player_subtitles, SUBTITLE_OFF_KEY)
        }
    }
}
