package me.yummydroid.app.ui

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import me.yummydroid.app.AppLog
import me.yummydroid.app.R
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant

internal data class NativePlayerSubtitlePresentation(
    val options: List<SubtitleOption>,
    val playbackSourceOptions: List<SourceOption>,
    val loading: Boolean,
)

internal data class NativePlayerSubtitleSelection(
    val selectedKey: String,
    val onSelectedKeyChanged: (String) -> Unit,
    val onControllerSelectedKeyChanged: (String) -> Unit,
)

private data class MaterializedStreamSubtitles(
    val tracks: List<me.yummydroid.app.data.ResolvedSubtitleTrack>,
    val hasPendingCandidates: Boolean,
)

@Composable
internal fun rememberNativePlayerSubtitlePresentation(
    stream: ResolvedVideoStream,
    currentVideo: VideoVariant,
    player: ExoPlayer,
    playerControlTexts: PlayerControlTexts,
    sourceSubtitleLabel: String,
    playbackMetadataLoading: Boolean,
    sourceOptions: List<SourceOption>,
    tracks: Tracks,
): NativePlayerSubtitlePresentation {
    val materialized = rememberMaterializedStreamSubtitles(stream, player)
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
    return NativePlayerSubtitlePresentation(
        options = subtitleOptions,
        playbackSourceOptions = playbackSourceOptions,
        loading = playbackMetadataLoading &&
            materialized.hasPendingCandidates &&
            materialized.tracks.isEmpty(),
    )
}

@Composable
private fun rememberMaterializedStreamSubtitles(
    stream: ResolvedVideoStream,
    player: ExoPlayer,
): MaterializedStreamSubtitles {
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
    return MaterializedStreamSubtitles(materializedSubtitles, pendingSubtitleCandidates)
}

@Composable
internal fun rememberNativePlayerSubtitleSelection(
    currentVideo: VideoVariant,
    streamUrl: String,
    player: ExoPlayer,
    playerView: () -> PlayerView?,
    subtitleOptions: List<SubtitleOption>,
): NativePlayerSubtitleSelection {
    var selectedSubtitleKey by remember(currentVideo.id, streamUrl) {
        mutableStateOf(SUBTITLE_OFF_KEY)
    }
    var subtitleSelectionTouched by remember(currentVideo.id, streamUrl) {
        mutableStateOf(false)
    }
    NativePlayerSubtitleSelectionEffect(
        player = player,
        playerView = playerView,
        subtitleOptions = subtitleOptions,
        selectedSubtitleKey = selectedSubtitleKey,
        subtitleSelectionTouched = subtitleSelectionTouched,
        onSelectedSubtitleKeyChanged = { selectedSubtitleKey = it },
    )
    return NativePlayerSubtitleSelection(
        selectedKey = selectedSubtitleKey,
        onSelectedKeyChanged = { selectedSubtitleKey = it },
        onControllerSelectedKeyChanged = {
            subtitleSelectionTouched = true
            selectedSubtitleKey = it
        },
    )
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
                    playerView()?.setSelectedSubtitleTag(SUBTITLE_OFF_KEY)
                }
                return@LaunchedEffect
            }
            player.selectSubtitle(defaultOption)
            val stableKey = defaultOption.subtitleOptionIdentity()
            onSelectedSubtitleKeyChanged(stableKey)
            playerView()?.setSelectedSubtitleTag(stableKey)
            return@LaunchedEffect
        }
        if (selectedSubtitleKey == SUBTITLE_OFF_KEY) return@LaunchedEffect
        if (!selectedSubtitleIsAvailable) {
            onSelectedSubtitleKeyChanged(SUBTITLE_OFF_KEY)
            player.disableSubtitles()
            playerView()?.setSelectedSubtitleTag(SUBTITLE_OFF_KEY)
        }
    }
}

private fun PlayerView.setSelectedSubtitleTag(key: String) {
    findViewById<View>(R.id.yummy_player_subtitles)
        ?.setTag(R.id.yummy_player_subtitles, key)
}
