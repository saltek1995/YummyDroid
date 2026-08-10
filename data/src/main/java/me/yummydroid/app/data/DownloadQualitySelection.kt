package me.yummydroid.app.data

import java.util.Locale

fun Iterable<VideoVariant>.downloadCoverageQualityTitles(
    resolvedQualities: List<PreferredQuality>,
): List<String> {
    val resolvedHeights = resolvedQualities.mapNotNull { it.height }
    val knownHeights = knownSourceQualityHeights()
    return (resolvedHeights + knownHeights)
        .distinct()
        .sortedDescending()
        .map { "${it}p" }
}

fun Iterable<VideoVariant>.sourceQualitiesForSameEpisodeVoice(
    currentVideo: VideoVariant,
): List<SourceQuality> {
    return filter { it.isSameEpisodeAs(currentVideo) && it.matchingVoiceKey == currentVideo.matchingVoiceKey }
        .flatMap { it.sourceQualities }
}

fun Iterable<VideoVariant>.knownSourceQualityHeights(): List<Int> {
    return flatMap { it.sourceQualities }
        .mapNotNull { it.height.validVideoQualityHeight() }
        .distinct()
        .sortedDescending()
}

fun VideoVariant.canMaybeProvideDownloadQuality(preferredQuality: PreferredQuality): Boolean {
    val height = preferredQuality.height ?: return true
    val qualities = sourceQualities
    return qualities.isEmpty() || qualities.any { it.height == height }
}

fun VideoVariant.hasDownloadedQuality(preferredQuality: PreferredQuality): Boolean {
    return offlineFiles.any { it.isCompletedDownload(preferredQuality) }
}

fun OfflineVideoFile.isCompletedDownload(preferredQuality: PreferredQuality): Boolean {
    return playbackUrl.isNotBlank() && bytes > 0L && matchesPreferredQuality(preferredQuality)
}

fun VideoVariant.maxKnownSourceQualityHeight(): Int {
    return listOf(this).knownSourceQualityHeights().maxOrNull() ?: 0
}

fun List<VideoVariant>.downloadCandidatesFor(requested: VideoVariant): List<VideoVariant> {
    val sameEpisode = filter { candidate ->
        candidate.animeId == requested.animeId && candidate.isSameEpisodeAs(requested)
    }.ifEmpty { listOf(requested) }
    val requestedVoiceKey = requested.matchingVoiceKey
    val sameVoiceEpisode = sameEpisode
        .filter { candidate -> candidate.matchingVoiceKey == requestedVoiceKey }
        .ifEmpty { listOf(requested) }

    return sameVoiceEpisode.sortedWith(
        compareByDescending<VideoVariant> { it.id == requested.id }
            .thenBy { it.index },
    )
}

fun List<VideoVariant>.downloadQualityCandidatesFor(
    requested: VideoVariant,
    allEpisodes: Boolean,
): List<VideoVariant> {
    if (!allEpisodes) return downloadCandidatesFor(requested)
    val requestedVoiceKey = requested.matchingVoiceKey
    return filter { candidate ->
        candidate.animeId == requested.animeId &&
            candidate.matchingVoiceKey == requestedVoiceKey
    }.ifEmpty { downloadCandidatesFor(requested) }
}

fun List<VideoVariant>.selectDownloadQualitySampleCandidate(): VideoVariant? {
    return minWithOrNull(downloadSampleComparator())
}

val VideoVariant.downloadSampleVoiceKey: String
    get() = downloadPlanVoiceKey

fun VideoVariant.sourceResolveIdentity(): String {
    if (id > 0L) return "id:$id"
    return listOf(
        animeId.toString(),
        matchingEpisodeKey,
        matchingVoiceKey,
        player.cleanVideoSourceLabel().lowercase(Locale.ROOT),
        url.sourceResolveFingerprint(),
        index.toString(),
    ).joinToString("|")
}

private fun downloadSampleComparator(): Comparator<VideoVariant> {
    return compareByDescending<VideoVariant> { it.maxKnownSourceQualityHeight() > 0 }
        .thenByDescending { it.maxKnownSourceQualityHeight() }
        .thenByDescending { it.episodeOrderValue() ?: Double.NEGATIVE_INFINITY }
        .thenBy { it.index }
        .thenBy { it.id }
}

private fun String.sourceResolveFingerprint(): String {
    return trim()
        .substringBefore('#')
        .lowercase(Locale.ROOT)
}
