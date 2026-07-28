package me.yummydroid.app

import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.canMaybeProvideDownloadQuality
import me.yummydroid.app.data.downloadCandidatesFor
import me.yummydroid.app.data.maxKnownSourceQualityHeight
import me.yummydroid.app.data.sourceProviderRank
import me.yummydroid.app.data.sourceResolveIdentity

internal fun List<VideoVariant>.downloadRetryCandidatesFor(
    requested: VideoVariant,
    preferredQuality: PreferredQuality,
): List<VideoVariant> {
    return downloadCandidatesFor(requested)
        .asSequence()
        .filter { candidate -> candidate.canMaybeProvideDownloadQuality(preferredQuality) }
        .distinctBy { candidate -> candidate.sourceResolveIdentity() }
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
        .thenByDescending { candidate -> candidate.maxKnownSourceQualityHeight() }
        .thenBy { candidate -> candidate.index }
        .thenBy { candidate -> candidate.id }
}

private fun VideoVariant.downloadQualityAvailabilityRank(preferredQuality: PreferredQuality): Int {
    val preferredHeight = preferredQuality.height ?: return 0
    if (sourceQualities.any { it.height == preferredHeight }) return 0
    return 1
}
