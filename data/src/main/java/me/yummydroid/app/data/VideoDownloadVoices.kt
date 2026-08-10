package me.yummydroid.app.data

import java.util.Locale

val VideoVariant.downloadPlanVoiceKey: String
    get() = matchingVoiceKey.ifBlank { groupKey.lowercase(Locale.ROOT) }

val VideoVariant.downloadPlanVoiceTitle: String
    get() = matchingVoiceTitle
        .ifBlank { dubbing.cleanVideoSourceLabel() }
        .ifBlank { groupTitle }
        .ifBlank { player.cleanVideoSourceLabel() }
        .ifBlank { "Voice" }

fun List<VideoVariant>.siteDefaultVideo(): VideoVariant? {
    return firstOrNull()
}

fun Iterable<VideoVariant>.siteOrderedVoiceKeys(): List<String> {
    val keys = LinkedHashSet<String>()
    forEach { video ->
        video.downloadPlanVoiceKey
            .takeIf { it.isNotBlank() }
            ?.let(keys::add)
    }
    return keys.toList()
}

fun Iterable<VideoVariant>.siteDefaultVoiceKey(): String? {
    return siteOrderedVoiceKeys().firstOrNull()
}

fun Iterable<VideoVariant>.siteVoiceOrderIndex(): Map<String, Int> {
    return siteOrderedVoiceKeys()
        .withIndex()
        .associate { (index, key) -> key to index }
}

fun List<VideoVariant>.downloadVoiceOptions(selectedVideo: VideoVariant?): List<VideoVariant> {
    val siteVoiceOrder = siteVoiceOrderIndex()
    return groupBy { it.downloadPlanVoiceKey }
        .values
        .mapNotNull { group ->
            group.minWithOrNull(
                compareBy<VideoVariant> { if (selectedVideo != null && it.groupKey == selectedVideo.groupKey) 0 else 1 }
                    .thenBy { sourceProviderRank(it.player) }
                    .thenByDescending { it.isOfflineAvailable }
                    .thenBy { it.index }
                    .thenBy { it.id },
            )
        }
        .sortedWith(
            compareBy<VideoVariant> {
                if (selectedVideo != null && it.downloadPlanVoiceKey == selectedVideo.downloadPlanVoiceKey) 0 else 1
            }
                .thenBy { siteVoiceOrder[it.downloadPlanVoiceKey] ?: Int.MAX_VALUE }
                .thenBy { it.downloadPlanVoiceTitle },
        )
}

fun List<VideoVariant>.downloadEpisodeCandidates(video: VideoVariant): List<VideoVariant> {
    return filter { it.isSameEpisodeAs(video) }.ifEmpty { listOf(video) }
}

fun VideoVariant.downloadVoiceEpisodeCount(videos: List<VideoVariant>): Int {
    return videos
        .asSequence()
        .filter { it.downloadPlanVoiceKey == downloadPlanVoiceKey }
        .map { it.matchingEpisodeKey }
        .distinct()
        .count()
        .coerceAtLeast(1)
}

fun VideoVariant.downloadedVoiceEpisodeCount(videos: List<VideoVariant>): Int {
    return downloadedEpisodeCountForVoice(videos)
}

fun VideoVariant.downloadedQualityEpisodeCount(
    videos: List<VideoVariant>,
    quality: PreferredQuality,
): Int {
    return videos
        .asSequence()
        .filter { it.downloadPlanVoiceKey == downloadPlanVoiceKey }
        .filter { candidate -> candidate.hasDownloadedQuality(quality) }
        .map { it.matchingEpisodeKey }
        .distinct()
        .count()
}
