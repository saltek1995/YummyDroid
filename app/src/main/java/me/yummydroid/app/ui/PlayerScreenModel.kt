package me.yummydroid.app.ui

import me.yummydroid.app.data.matchingSourceKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.LoadState
import me.yummydroid.app.sourceSelectionKey

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
    val readyStream = (streamState as? LoadState.Ready<ResolvedVideoStream>)?.data
    val useRetainedPlayback = streamState == LoadState.Loading &&
        resumeChoicePositionMs == null &&
        retainedReadyPlayback != null &&
        retainedReadyPlayback.video.animeId == video.animeId
    val playbackStream = readyStream ?: retainedReadyPlayback?.stream?.takeIf { useRetainedPlayback }
    val playbackVideo = retainedReadyPlayback?.video?.takeIf { useRetainedPlayback } ?: video
    val playbackStartPositionMs = retainedReadyPlayback?.startPositionMs
        ?.takeIf { useRetainedPlayback }
        ?: startPositionMs
    val playbackPreferredQuality = retainedReadyPlayback?.preferredQuality
        ?.takeIf { useRetainedPlayback }
        ?: preferredQuality
    val sourceVideos = allVideos.ifEmpty { listOf(playbackVideo) }
    val videos = if (forcedOfflineMode) {
        sourceVideos.filter(VideoVariant::isOfflineAvailable)
            .ifEmpty { listOf(playbackVideo).filter(VideoVariant::isOfflineAvailable) }
    } else {
        sourceVideos
    }
    val groups = videos.groupBy(VideoVariant::matchingVoiceKey)
    val selectedVoiceKey = selectedGroup
        ?.let { groupKey -> videos.firstOrNull { candidate -> candidate.groupKey == groupKey }?.matchingVoiceKey }
        ?.takeIf(groups::containsKey)
        ?: playbackVideo.matchingVoiceKey.takeIf(groups::containsKey)
        ?: groups.keys.firstOrNull()
    val sourceSubtitleSourceKeys = playbackStream
        ?.let { stream ->
            stream.sourceSubtitleSourceKeys + listOfNotNull(
                playbackVideo.matchingSourceKey.takeIf { key ->
                    key.isNotBlank() && stream.hasResolvedSubtitles
                },
            )
        }
        .orEmpty()
    val sourceSubtitleSelectionKeys = playbackStream
        ?.let { stream ->
            listOfNotNull(
                playbackVideo.sourceSelectionKey.takeIf { key ->
                    key.isNotBlank() && stream.hasResolvedSubtitles
                },
            ).toSet()
        }
        .orEmpty()
    val sourceOptions = if (forcedOfflineMode) {
        emptyList()
    } else {
        videos.sourceOptionsFor(
            currentVideo = playbackVideo,
            selectedVoiceKey = selectedVoiceKey,
            sourceSubtitleSourceKeys = sourceSubtitleSourceKeys,
            sourceSubtitleSelectionKeys = sourceSubtitleSelectionKeys,
            sourceSubtitleLabel = sourceSubtitleLabel,
        )
    }

    return PlayerScreenPresentation(
        playbackStream = playbackStream,
        playbackVideo = playbackVideo,
        playbackStartPositionMs = playbackStartPositionMs,
        playbackPreferredQuality = playbackPreferredQuality,
        videos = videos,
        groups = groups,
        selectedVoiceKey = selectedVoiceKey,
        sourceOptions = sourceOptions,
        selectedSourceKey = playbackVideo.sourceSelectionKey,
        previousVideo = findAdjacentPlayerVideo(
            currentVideo = playbackVideo,
            allVideos = videos,
            selectedGroup = selectedGroup,
            forward = false,
        ),
        nextVideo = findAdjacentPlayerVideo(
            currentVideo = playbackVideo,
            allVideos = videos,
            selectedGroup = selectedGroup,
            forward = true,
        ),
        useRetainedPlayback = useRetainedPlayback,
    )
}
