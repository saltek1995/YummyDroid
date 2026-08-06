package me.yummydroid.app

import me.yummydroid.app.data.downloadEpisodeSlotKey
import me.yummydroid.app.data.downloadVoiceSlotKey
import me.yummydroid.app.data.hasDownloadedQuality
import me.yummydroid.app.data.isCompletedDownload
import me.yummydroid.app.data.matchingDisplayVoiceTitle
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.sourceProviderRank
import me.yummydroid.app.data.VideoVariant

internal fun downloadBatchKey(
    animeId: Long,
    videoId: Long?,
    groupKey: String,
    quality: PreferredQuality,
): String {
    return listOf(
        animeId.toString(),
        videoId?.toString() ?: "all",
        groupKey,
        quality.name,
        System.currentTimeMillis().toString(),
    ).joinToString(":")
}

internal fun List<VideoVariant>.selectDownloadAllTargets(preferredGroupKey: String): List<VideoVariant> {
    val preferredVoiceKey = firstOrNull { it.groupKey == preferredGroupKey }
        ?.matchingVoiceKey
    return groupBy { it.downloadEpisodeSlotKey }
        .toSortedMap(compareBy<String> { it.toDoubleOrNull() ?: Double.MAX_VALUE }.thenBy { it })
        .values
        .mapNotNull { episodeVideos ->
            if (preferredVoiceKey != null) {
                episodeVideos
                    .filter { it.matchingVoiceKey == preferredVoiceKey }
                    .sortedWith(downloadTargetComparator(preferredGroupKey))
                    .firstOrNull()
            } else {
                episodeVideos.sortedWith(downloadTargetComparator()).firstOrNull()
            }
        }
}

internal fun List<VideoVariant>.hasDownloadedRequestedSlot(
    video: VideoVariant,
    preferredQuality: PreferredQuality,
): Boolean {
    val key = video.downloadVoiceSlotKey
    return any { candidate ->
        candidate.downloadVoiceSlotKey == key &&
            candidate.hasDownloadedQuality(preferredQuality)
    }
}

internal fun VideoVariant.completedDownloadFile(preferredQuality: PreferredQuality): OfflineVideoFile? {
    return offlineFiles.firstOrNull { it.isCompletedDownload(preferredQuality) }
        ?: offlineFiles.firstOrNull()
}

internal fun VideoVariant.downloadTaskSubtitle(
    quality: String,
    voice: String = "",
): String {
    val voiceTitle = voice.ifBlank {
        matchingDisplayVoiceTitle
    }.ifBlank { "Voice" }
    val qualityTitle = quality.ifBlank { "Auto" }
    return listOf(voiceTitle, qualityTitle)
        .filter { it.isNotBlank() }
        .joinToString(" вЂў ")
}

private fun downloadTargetComparator(preferredGroupKey: String = ""): Comparator<VideoVariant> {
    return compareByDescending<VideoVariant> { it.isOfflineAvailable }
        .thenBy { if (preferredGroupKey.isNotBlank() && it.groupKey == preferredGroupKey) 0 else 1 }
        .thenBy { sourceProviderRank(it.player) }
        .thenBy { it.index }
}
