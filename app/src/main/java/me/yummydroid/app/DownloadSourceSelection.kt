package me.yummydroid.app

import java.util.Locale
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.downloadEpisodeSlotKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.sourceProviderRank

internal fun List<VideoVariant>.downloadRetryCandidatesFor(
    requested: VideoVariant,
    preferredQuality: PreferredQuality,
): List<VideoVariant> {
    val requestedVoiceKey = requested.matchingVoiceKey
    val preferredHeight = preferredQuality.height
    val sameVoiceEpisode = filter { candidate ->
        candidate.animeId == requested.animeId &&
            candidate.downloadEpisodeSlotKey == requested.downloadEpisodeSlotKey &&
            candidate.matchingVoiceKey == requestedVoiceKey
    }.ifEmpty { listOf(requested) }

    return sameVoiceEpisode
        .asSequence()
        .filter { candidate ->
            preferredHeight == null ||
                candidate.sourceQualities.isEmpty() ||
                candidate.sourceQualities.any { quality -> quality.height == preferredHeight }
        }
        .distinctBy { candidate -> candidate.downloadRetrySourceKey() }
        .sortedWith(downloadRetryCandidateComparator(requested, preferredQuality))
        .toList()
        .ifEmpty { listOf(requested) }
}

internal fun List<VideoVariant>.downloadRetryCandidateForAttempt(attempt: Int): VideoVariant? {
    if (isEmpty()) return null
    val index = (attempt - 1).coerceAtLeast(0) % size
    return this[index]
}

private fun downloadRetryCandidateComparator(
    requested: VideoVariant,
    preferredQuality: PreferredQuality,
): Comparator<VideoVariant> {
    return compareByDescending<VideoVariant> { candidate -> candidate.id == requested.id }
        .thenBy { candidate -> candidate.downloadQualityAvailabilityRank(preferredQuality) }
        .thenBy { candidate -> sourceProviderRank(candidate.player) }
        .thenByDescending { candidate -> candidate.maxKnownDownloadQualityHeight() }
        .thenBy { candidate -> candidate.index }
        .thenBy { candidate -> candidate.id }
}

private fun VideoVariant.downloadQualityAvailabilityRank(preferredQuality: PreferredQuality): Int {
    val preferredHeight = preferredQuality.height ?: return 0
    if (sourceQualities.any { it.height == preferredHeight }) return 0
    return 1
}

private fun VideoVariant.maxKnownDownloadQualityHeight(): Int {
    return sourceQualities
        .mapNotNull { quality -> quality.height?.takeIf { it > 0 } }
        .maxOrNull()
        ?: 0
}

private fun VideoVariant.downloadRetrySourceKey(): String {
    if (id > 0L) return "id:$id"
    return listOf(
        animeId.toString(),
        downloadEpisodeSlotKey,
        matchingVoiceKey,
        player.trim().lowercase(Locale.ROOT),
        url.trim().substringBefore('#').substringBefore('?').lowercase(Locale.ROOT),
        index.toString(),
    ).joinToString("|")
}
