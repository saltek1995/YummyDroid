package me.yummydroid.app.ui

import android.content.Context
import android.widget.Toast
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.matchingVoiceTitle
import me.yummydroid.app.data.availableVoiceEpisodeCount
import me.yummydroid.app.data.sourceProviderRank
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.localizedString
import me.yummydroid.app.R

internal fun VideoVariant.playbackSubtitle(
    texts: PlayerControlTexts,
    videos: Collection<VideoVariant> = emptyList(),
): String {
    val voice = dubbing.cleanVideoSourceLabel()
    return listOf(voice, localizedPlaybackEpisodeTitle(texts, videos))
        .filterNot { it.isNullOrBlank() }
        .joinToString(" \u2022 ")
}

private fun VideoVariant.localizedPlaybackEpisodeTitle(
    texts: PlayerControlTexts,
    videos: Collection<VideoVariant>,
): String {
    val episodeNumber = episode.trim()
    if (episodeNumber.isBlank()) return texts.episodeFallback
    val episodeCount = playbackEpisodeCount(videos)
    return if (episodeCount > 0) {
        "${texts.episode} $episodeNumber ${texts.of} $episodeCount"
    } else {
        "${texts.episode} $episodeNumber"
    }
}

private fun VideoVariant.playbackEpisodeCount(videos: Collection<VideoVariant>): Int {
    val candidates = videos.ifEmpty { listOf(this) }
    val sameAnime = candidates.filter { it.animeId == animeId }
    val sameVoice = sameAnime.filter { it.matchingVoiceKey == matchingVoiceKey }
    return sameVoice
        .ifEmpty { sameAnime }
        .ifEmpty { candidates.toList() }
        .availableVoiceEpisodeCount()
}

internal fun findAdjacentPlayerVideo(
    currentVideo: VideoVariant,
    allVideos: List<VideoVariant>,
    selectedGroup: String?,
    forward: Boolean,
): VideoVariant? {
    val videos = allVideos.ifEmpty { listOf(currentVideo) }
    val preferredVoiceKey = selectedGroup
        ?.let { groupKey -> videos.firstOrNull { it.groupKey == groupKey }?.matchingVoiceKey }
        ?: currentVideo.matchingVoiceKey
    val preferredGroupKey = selectedGroup?.takeIf { groupKey -> videos.any { it.groupKey == groupKey } }
        ?: currentVideo.groupKey
    val voiceScopedVideos = videos
        .filter { it.matchingVoiceKey == preferredVoiceKey }
        .ifEmpty { videos }

    val episodeVideos = voiceScopedVideos
        .groupBy { it.matchingEpisodeKey }
        .values
        .mapNotNull { variants ->
            variants.minWithOrNull(
                compareBy<VideoVariant> { if (it.matchingVoiceKey == preferredVoiceKey) 0 else 1 }
                    .thenBy { if (it.groupKey == preferredGroupKey) 0 else 1 }
                    .thenBy { if (it.isOfflineAvailable) 0 else 1 }
                    .thenBy { sourceProviderRank(it.player) }
                    .thenBy { it.index }
                    .thenBy { it.id },
            )
        }
        .sortedForPlayer()

    val currentIndex = episodeVideos.indexOfFirst { it.isSameEpisodeAs(currentVideo) }
        .takeIf { it >= 0 }
        ?: return null
    val nextIndex = if (forward) currentIndex + 1 else currentIndex - 1
    return episodeVideos.getOrNull(nextIndex)
}

internal fun showVoiceFallbackToast(
    context: Context,
    previousVideo: VideoVariant,
    nextVideo: VideoVariant,
) {
    if (previousVideo.matchingVoiceKey == nextVideo.matchingVoiceKey) return
    val language = AppSettingsStorage(context).read().contentLanguage
    Toast.makeText(
        context,
        context.localizedString(
            R.string.ui_voice_fallback_toast,
            language,
            previousVideo.matchingVoiceTitle,
            nextVideo.episodeTitle,
            nextVideo.matchingVoiceTitle,
        ),
        Toast.LENGTH_LONG,
    ).show()
}

