package me.yummydroid.app.ui

import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.normalizedVoiceKey
import me.yummydroid.app.data.siteDefaultVideo

internal data class HeroResumeTarget(
    val video: VideoVariant,
    val positionMs: Long,
)

internal fun List<VideoVariant>.heroStartVideo(selectedGroup: String?): VideoVariant? {
    if (isEmpty()) return null
    val preferredGroup = selectedGroup?.takeIf { groupKey -> any { it.groupKey == groupKey } }
        ?: siteDefaultVideo()?.groupKey
    val preferredVoice = matchingVoiceKeyForGroup(preferredGroup)
    return sortedForPlayer(preferredGroup, preferredVoice).firstOrNull()
        ?: siteDefaultVideo()
}

internal fun PlaybackProgress?.resolveResumeTarget(
    videos: List<VideoVariant>,
): HeroResumeTarget? {
    val progress = this ?: return null
    if (progress.positionMs <= 0L || videos.isEmpty()) return null
    val video = videos.firstOrNull { candidate ->
        candidate.matchesPlaybackProgress(progress, requireGroup = true)
    } ?: videos.firstOrNull { candidate ->
        candidate.matchesPlaybackProgress(progress, requireGroup = false)
    } ?: return null

    val safePosition = progress.safeResumePosition()
    if (safePosition <= 0L) return null
    return HeroResumeTarget(video, safePosition)
}

private fun PlaybackProgress.safeResumePosition(): Long {
    val duration = durationMs.takeIf { it > 0L } ?: return positionMs.coerceAtLeast(0L)
    return positionMs.coerceIn(0L, (duration - 5_000L).coerceAtLeast(0L))
}

internal fun List<PlaybackProgress>.progressFor(video: VideoVariant): PlaybackProgress? {
    return firstOrNull { progress -> video.matchesPlaybackProgress(progress, requireGroup = true) }
        ?: firstOrNull { progress -> video.matchesPlaybackProgress(progress, requireGroup = false) }
}

internal fun VideoVariant.matchesPlaybackProgress(
    progress: PlaybackProgress,
    requireGroup: Boolean,
): Boolean {
    if (progress.videoId > 0L && id == progress.videoId) return true
    if (requireGroup && (progress.groupKey.isBlank() || groupKey != progress.groupKey)) return false
    if (!requireGroup && !matchesProgressVoice(progress)) return false
    if (progress.episode.isBlank()) return false
    return episode.matchesProgressEpisode(progress.episode) ||
        matchingEpisodeKey == progress.episode ||
        matchingEpisodeKey.matchesProgressEpisode(progress.episode)
}

private fun VideoVariant.matchesProgressVoice(progress: PlaybackProgress): Boolean {
    val progressVoiceKey = progress.groupKey
        .substringAfter('|', progress.groupKey)
        .normalizedVoiceKey()
    return progressVoiceKey.isBlank() || matchingVoiceKey == progressVoiceKey
}

internal fun String.matchesProgressEpisode(progressEpisode: String): Boolean {
    val current = trim()
    val saved = progressEpisode.trim()
    if (current == saved) return true
    val currentNumber = current.replace(',', '.').toDoubleOrNull()
    val savedNumber = saved.replace(',', '.').toDoubleOrNull()
    return currentNumber != null && savedNumber != null && currentNumber == savedNumber
}
