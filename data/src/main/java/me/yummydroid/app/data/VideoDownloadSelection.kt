package me.yummydroid.app.data

import java.util.Locale

data class DownloadEpisodeSlot(
    val key: String,
    val title: String,
    val order: Double?,
)

val VideoVariant.downloadPlanVoiceKey: String
    get() = matchingVoiceKey.ifBlank { groupKey.lowercase(Locale.ROOT) }

val VideoVariant.downloadPlanVoiceTitle: String
    get() = matchingVoiceTitle
        .ifBlank { dubbing.cleanVideoSourceLabel() }
        .ifBlank { groupTitle }
        .ifBlank { player.cleanVideoSourceLabel() }
        .ifBlank { "Озвучка" }

fun VideoVariant.downloadEpisodeSlot(): DownloadEpisodeSlot {
    return DownloadEpisodeSlot(
        key = matchingEpisodeKey,
        title = episode.trim().takeIf { it.isNotBlank() } ?: matchingEpisodeKey,
        order = episodeOrderValue(),
    )
}

fun downloadEpisodeSlotComparator(): Comparator<DownloadEpisodeSlot> {
    return compareBy<DownloadEpisodeSlot> { it.order ?: Double.MAX_VALUE }
        .thenBy { it.title }
        .thenBy { it.key }
}

fun Iterable<VideoVariant>.sortedDownloadEpisodeSlots(): List<DownloadEpisodeSlot> {
    return distinctBy { it.matchingEpisodeKey }
        .map { it.downloadEpisodeSlot() }
        .sortedWith(downloadEpisodeSlotComparator())
}

fun List<DownloadEpisodeSlot>.compactEpisodeRanges(): List<String> {
    if (isEmpty()) return emptyList()
    val ranges = mutableListOf<String>()
    var start = first()
    var previous = first()

    drop(1).forEach { current ->
        val contiguous = previous.order?.let { previousOrder ->
            current.order?.let { currentOrder ->
                isWholeNumber(previousOrder) &&
                    isWholeNumber(currentOrder) &&
                    currentOrder.toInt() == previousOrder.toInt() + 1
            }
        } == true
        if (contiguous) {
            previous = current
        } else {
            ranges += start.rangeTitle(previous)
            start = current
            previous = current
        }
    }
    ranges += start.rangeTitle(previous)
    return ranges
}

fun List<DownloadEpisodeSlot>.compactEpisodeNumberRanges(): List<IntRange> {
    return mapNotNull { slot ->
        slot.order
            ?.takeIf(::isWholeNumber)
            ?.toInt()
            ?.takeIf { it > 0 }
            ?.let { it..it }
    }.mergeEpisodeRanges()
}

fun List<IntRange>.mergeEpisodeRanges(): List<IntRange> {
    if (isEmpty()) return emptyList()
    val sorted = sortedWith(compareBy<IntRange> { it.first }.thenBy { it.last })
    val merged = mutableListOf<IntRange>()
    var current = sorted.first()
    sorted.drop(1).forEach { next ->
        if (next.first <= current.last + 1) {
            current = current.first..maxOf(current.last, next.last)
        } else {
            merged += current
            current = next
        }
    }
    merged += current
    return merged
}

fun IntRange.subtractEpisodeRanges(availableRanges: List<IntRange>): List<IntRange> {
    var cursor = first
    val missing = mutableListOf<IntRange>()
    availableRanges
        .mergeEpisodeRanges()
        .forEach { available ->
            if (available.last < cursor) return@forEach
            if (available.first > last) return@forEach
            if (available.first > cursor) {
                missing += cursor..minOf(available.first - 1, last)
            }
            cursor = maxOf(cursor, available.last + 1)
            if (cursor > last) return missing
        }
    if (cursor <= last) {
        missing += cursor..last
    }
    return missing
}

fun List<IntRange>.formatEpisodeRanges(limit: Int): String {
    val visible = take(limit)
    val suffix = if (size > limit) ", ..." else ""
    return visible.joinToString(", ") { range ->
        if (range.first == range.last) range.first.toString() else "${range.first}-${range.last}"
    } + suffix
}

fun isWholeNumber(value: Double): Boolean {
    return value % 1.0 == 0.0
}

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
        .mapNotNull { it.height?.takeIf { height -> height in 100..4320 } }
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

private fun DownloadEpisodeSlot.rangeTitle(end: DownloadEpisodeSlot): String {
    val startTitle = order?.formatEpisodeNumber() ?: title
    val endTitle = end.order?.formatEpisodeNumber() ?: end.title
    return if (key == end.key) startTitle else "$startTitle-$endTitle"
}

private fun Double.formatEpisodeNumber(): String {
    val asInt = toInt()
    return if (isWholeNumber(this)) asInt.toString() else toString().trimEnd('0').trimEnd('.')
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
