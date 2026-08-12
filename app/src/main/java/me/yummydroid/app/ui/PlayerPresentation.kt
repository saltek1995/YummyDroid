package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import me.yummydroid.app.LoadState
import me.yummydroid.app.formatPlaybackTime
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingSourceKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.sourceSelectionKey
import me.yummydroid.app.ui.theme.YummySpacing

// PlayerScreenModel
internal data class RetainedReadyPlayback(
    val stream: ResolvedVideoStream,
    val video: VideoVariant,
    val startPositionMs: Long,
    val preferredQuality: PreferredQuality,
)

internal data class PlayerScreenPresentation(
    val playbackStream: ResolvedVideoStream?,
    val playbackVideo: VideoVariant,
    val playbackStartPositionMs: Long,
    val playbackPreferredQuality: PreferredQuality,
    val videos: List<VideoVariant>,
    val groups: Map<String, List<VideoVariant>>,
    val selectedVoiceKey: String?,
    val sourceOptions: List<SourceOption>,
    val selectedSourceKey: String,
    val previousVideo: VideoVariant?,
    val nextVideo: VideoVariant?,
    val useRetainedPlayback: Boolean,
)

private data class PlayerPlaybackTarget(
    val stream: ResolvedVideoStream?,
    val video: VideoVariant,
    val startPositionMs: Long,
    val preferredQuality: PreferredQuality,
    val retained: Boolean,
)

private data class PlayerSubtitleSources(
    val sourceKeys: Set<String>,
    val selectionKeys: Set<String>,
)

internal fun buildPlayerScreenPresentation(
    video: VideoVariant,
    startPositionMs: Long,
    preferredQuality: PreferredQuality,
    allVideos: List<VideoVariant>,
    selectedGroup: String?,
    streamState: LoadState<ResolvedVideoStream>,
    retainedReadyPlayback: RetainedReadyPlayback?,
    resumeChoicePositionMs: Long?,
    forcedOfflineMode: Boolean,
    sourceSubtitleLabel: String,
): PlayerScreenPresentation {
    val playback = resolvePlayerPlaybackTarget(
        requestedVideo = video,
        requestedStartPositionMs = startPositionMs,
        requestedQuality = preferredQuality,
        streamState = streamState,
        retainedReadyPlayback = retainedReadyPlayback,
        resumeChoicePositionMs = resumeChoicePositionMs,
    )
    val videos = resolvePlayerVideos(
        allVideos = allVideos,
        playbackVideo = playback.video,
        forcedOfflineMode = forcedOfflineMode,
    )
    val groups = videos.groupBy(VideoVariant::matchingVoiceKey)
    val selectedVoiceKey = resolvePlayerVoiceKey(videos, groups, selectedGroup, playback.video)
    val subtitleSources = resolvePlayerSubtitleSources(playback.stream, playback.video)
    val sourceOptions = resolvePlayerSourceOptions(
        videos = videos,
        playbackVideo = playback.video,
        selectedVoiceKey = selectedVoiceKey,
        subtitleSources = subtitleSources,
        sourceSubtitleLabel = sourceSubtitleLabel,
        forcedOfflineMode = forcedOfflineMode,
    )

    return PlayerScreenPresentation(
        playbackStream = playback.stream,
        playbackVideo = playback.video,
        playbackStartPositionMs = playback.startPositionMs,
        playbackPreferredQuality = playback.preferredQuality,
        videos = videos,
        groups = groups,
        selectedVoiceKey = selectedVoiceKey,
        sourceOptions = sourceOptions,
        selectedSourceKey = playback.video.sourceSelectionKey,
        previousVideo = findAdjacentPlayerVideo(
            currentVideo = playback.video,
            allVideos = videos,
            selectedGroup = selectedGroup,
            forward = false,
        ),
        nextVideo = findAdjacentPlayerVideo(
            currentVideo = playback.video,
            allVideos = videos,
            selectedGroup = selectedGroup,
            forward = true,
        ),
        useRetainedPlayback = playback.retained,
    )
}
private fun resolvePlayerPlaybackTarget(
    requestedVideo: VideoVariant,
    requestedStartPositionMs: Long,
    requestedQuality: PreferredQuality,
    streamState: LoadState<ResolvedVideoStream>,
    retainedReadyPlayback: RetainedReadyPlayback?,
    resumeChoicePositionMs: Long?,
): PlayerPlaybackTarget {
    val retained = shouldUseRetainedPlayback(
        requestedVideo = requestedVideo,
        streamState = streamState,
        retainedReadyPlayback = retainedReadyPlayback,
        resumeChoicePositionMs = resumeChoicePositionMs,
    )
    val readyStream = (streamState as? LoadState.Ready<ResolvedVideoStream>)?.data
    return PlayerPlaybackTarget(
        stream = readyStream ?: retainedReadyPlayback?.stream?.takeIf { retained },
        video = retainedReadyPlayback?.video?.takeIf { retained } ?: requestedVideo,
        startPositionMs = retainedReadyPlayback?.startPositionMs?.takeIf { retained }
            ?: requestedStartPositionMs,
        preferredQuality = retainedReadyPlayback?.preferredQuality?.takeIf { retained }
            ?: requestedQuality,
        retained = retained,
    )
}
private fun shouldUseRetainedPlayback(
    requestedVideo: VideoVariant,
    streamState: LoadState<ResolvedVideoStream>,
    retainedReadyPlayback: RetainedReadyPlayback?,
    resumeChoicePositionMs: Long?,
): Boolean {
    if (streamState != LoadState.Loading) return false
    if (resumeChoicePositionMs != null) return false
    return retainedReadyPlayback?.video?.animeId == requestedVideo.animeId
}

private fun resolvePlayerVideos(
    allVideos: List<VideoVariant>,
    playbackVideo: VideoVariant,
    forcedOfflineMode: Boolean,
): List<VideoVariant> {
    val sourceVideos = allVideos.ifEmpty { listOf(playbackVideo) }
    if (!forcedOfflineMode) return sourceVideos
    return sourceVideos.filter(VideoVariant::isOfflineAvailable)
        .ifEmpty { listOf(playbackVideo).filter(VideoVariant::isOfflineAvailable) }
}

private fun resolvePlayerVoiceKey(
    videos: List<VideoVariant>,
    groups: Map<String, List<VideoVariant>>,
    selectedGroup: String?,
    playbackVideo: VideoVariant,
): String? {
    val selectedVoice = selectedGroup
        ?.let { groupKey -> videos.firstOrNull { candidate -> candidate.groupKey == groupKey }?.matchingVoiceKey }
        ?.takeIf(groups::containsKey)
    return selectedVoice
        ?: playbackVideo.matchingVoiceKey.takeIf(groups::containsKey)
        ?: groups.keys.firstOrNull()
}

private fun resolvePlayerSubtitleSources(
    stream: ResolvedVideoStream?,
    playbackVideo: VideoVariant,
): PlayerSubtitleSources {
    if (stream == null) return PlayerSubtitleSources(emptySet(), emptySet())
    val sourceKey = playbackVideo.matchingSourceKey.takeIf {
        it.isNotBlank() && stream.hasResolvedSubtitles
    }
    val selectionKey = playbackVideo.sourceSelectionKey.takeIf {
        it.isNotBlank() && stream.hasResolvedSubtitles
    }
    return PlayerSubtitleSources(
        sourceKeys = stream.sourceSubtitleSourceKeys + listOfNotNull(sourceKey),
        selectionKeys = setOfNotNull(selectionKey),
    )
}

private fun resolvePlayerSourceOptions(
    videos: List<VideoVariant>,
    playbackVideo: VideoVariant,
    selectedVoiceKey: String?,
    subtitleSources: PlayerSubtitleSources,
    sourceSubtitleLabel: String,
    forcedOfflineMode: Boolean,
): List<SourceOption> {
    if (forcedOfflineMode) return emptyList()
    return videos.sourceOptionsFor(
        currentVideo = playbackVideo,
        selectedVoiceKey = selectedVoiceKey,
        sourceSubtitleSourceKeys = subtitleSources.sourceKeys,
        sourceSubtitleSelectionKeys = subtitleSources.selectionKeys,
        sourceSubtitleLabel = sourceSubtitleLabel,
    )
}

@Composable
internal fun PlayerResumeChoiceDialog(
    video: VideoVariant,
    positionMs: Long,
    onStartOver: () -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
) {
    val resumeTime = formatPlaybackTime(positionMs)
    val resumeFocusRequester = remember { FocusRequester() }
    val inputModeManager = LocalInputModeManager.current

    UiControlEffect(
        video.id,
        positionMs,
        inputModeManager.inputMode,
        enabled = inputModeManager.inputMode != InputMode.Touch,
    ) {
        withFrameNanos { }
        resumeFocusRequester.requestFocusSafely()
    }

    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.ContinueWatching)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(YummySpacing.xs),
            ) {
                Text(
                    text = video.localizedEpisodeTitle(),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${uiText(UiStringKey.SavedPosition)}: $resumeTime",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            DialogActionButton(
                text = "${uiText(UiStringKey.Continue)} $resumeTime",
                primary = true,
                modifier = Modifier.focusRequester(resumeFocusRequester),
                onClick = onResume,
            )
        },
        dismissButton = {
            DialogActionButton(
                text = uiText(UiStringKey.FromStart),
                onClick = onStartOver,
            )
        },
    )
}
