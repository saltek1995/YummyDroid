package me.yummydroid.app.ui

import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.downloadPlanVoiceKey
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.siteDefaultVoiceKey

// VideoPickerPresentation
private const val EpisodeProgressMinVisibleFraction = 0.08f

internal data class VideoPickerPresentation(
    val selectedSourceKey: String?,
    val selectedVoiceKey: String,
    val displayVideos: List<VideoVariant>,
    val episodeViewsByKey: Map<String, Long>,
)

internal fun buildVideoPickerPresentation(
    videos: List<VideoVariant>,
    selectedGroup: String?,
): VideoPickerPresentation {
    require(videos.isNotEmpty())

    val voiceGroups = videos.groupBy(VideoVariant::downloadPlanVoiceKey)
    val selectedSourceKey = selectedGroup
        ?.takeIf { groupKey -> videos.any { video -> video.groupKey == groupKey } }
    val selectedVoiceKey = videos.matchingVoiceKeyForGroup(selectedSourceKey)
        ?: selectedGroup?.takeIf(voiceGroups::containsKey)
        ?: videos.siteDefaultVoiceKey()
        ?: voiceGroups.keys.first()
    val displayVideos = videos.sortedForPlayer(selectedSourceKey, selectedVoiceKey)
    val episodeViewsByKey = videos
        .distinctBy(VideoVariant::id)
        .groupBy(VideoVariant::matchingEpisodeKey)
        .mapValues { (_, episodeVideos) -> episodeVideos.sumOf(VideoVariant::views) }

    return VideoPickerPresentation(
        selectedSourceKey = selectedSourceKey,
        selectedVoiceKey = selectedVoiceKey,
        displayVideos = displayVideos,
        episodeViewsByKey = episodeViewsByKey,
    )
}

internal fun PlaybackProgress.watchProgressFraction(): Float {
    if (positionMs <= 0L) return 0f
    val duration = durationMs.takeIf { it > 0L } ?: return EpisodeProgressMinVisibleFraction
    return (positionMs.toFloat() / duration.toFloat())
        .coerceIn(EpisodeProgressMinVisibleFraction, 1f)
}

internal fun PlaybackProgress.safeResumePositionMs(): Long? {
    val knownDurationMs = durationMs.takeIf { it > 0L }
    val safePositionMs = if (knownDurationMs != null) {
        positionMs.coerceIn(0L, (knownDurationMs - 5_000L).coerceAtLeast(0L))
    } else {
        positionMs.coerceAtLeast(0L)
    }
    return safePositionMs.takeIf { it > 0L }
}
