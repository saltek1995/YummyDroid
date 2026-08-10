package me.yummydroid.app

import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.canMaybeProvideDownloadQuality
import me.yummydroid.app.data.downloadPlanVoiceKey
import me.yummydroid.app.data.formatEpisodeRanges
import me.yummydroid.app.data.hasDownloadedQuality
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.maxKnownSourceQualityHeight
import me.yummydroid.app.data.mergeEpisodeRanges
import me.yummydroid.app.data.qualityHeight
import me.yummydroid.app.data.sourceProviderRank
import me.yummydroid.app.data.subtractEpisodeRanges

fun parseDownloadEpisodeSelection(input: String): DownloadEpisodeSelectionParseResult {
    val normalizedInput = input
        .trim()
        .replace('\u2013', '-')
        .replace('\u2014', '-')
    if (normalizedInput.isBlank()) {
        return DownloadEpisodeSelectionParseResult(DownloadEpisodeSelection())
    }

    val ranges = mutableListOf<IntRange>()
    normalizedInput
        .split(',', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { token ->
            val bounds = token.split(Regex("""\s*-\s*"""))
            val range = when (bounds.size) {
                1 -> {
                    val value = bounds.single().toPositiveEpisodeNumberOrNull()
                        ?: return DownloadEpisodeSelectionParseResult(
                            selection = DownloadEpisodeSelection(ranges),
                            error = DownloadEpisodeSelectionError.InvalidEpisodeNumber(token),
                        )
                    value..value
                }
                2 -> {
                    val start = bounds[0].toPositiveEpisodeNumberOrNull()
                    val end = bounds[1].toPositiveEpisodeNumberOrNull()
                    if (start == null || end == null || start > end) {
                        return DownloadEpisodeSelectionParseResult(
                            selection = DownloadEpisodeSelection(ranges),
                            error = DownloadEpisodeSelectionError.InvalidEpisodeRange(token),
                        )
                    }
                    start..end
                }
                else -> {
                    return DownloadEpisodeSelectionParseResult(
                        selection = DownloadEpisodeSelection(ranges),
                        error = DownloadEpisodeSelectionError.InvalidEpisodeRange(token),
                    )
                }
            }
            ranges += range
        }

    return DownloadEpisodeSelectionParseResult(DownloadEpisodeSelection(ranges.mergeEpisodeRanges()))
}

fun validateDownloadEpisodeSelection(
    input: String,
    availableRanges: List<IntRange>,
): DownloadEpisodeSelectionParseResult {
    val parsed = parseDownloadEpisodeSelection(input)
    if (parsed.error != null || !parsed.selection.isRestricted || availableRanges.isEmpty()) {
        return parsed
    }
    val missingRanges = parsed.selection.ranges
        .flatMap { selectedRange -> selectedRange.subtractEpisodeRanges(availableRanges) }
        .mergeEpisodeRanges()
    if (missingRanges.isEmpty()) return parsed
    return parsed.copy(
        error = DownloadEpisodeSelectionError.MissingEpisodes(
            ranges = missingRanges.formatEpisodeRanges(limit = 6),
        ),
    )
}

fun DownloadPlanItem.resolveVideo(videos: List<VideoVariant>): VideoVariant? {
    val quality = preferredQuality
    return videos.firstOrNull { it.id == videoId }
        ?.takeIf { it.canMaybeProvideDownloadQuality(quality) }
        ?: videos
            .filter { it.matchingEpisodeKey == episodeKey && it.downloadPlanVoiceKey == voiceKey }
            .selectDownloadPlanCandidate(quality)
}

fun List<VideoVariant>.hasDownloadedEpisodeForPlan(
    episodeKey: String,
    preferredQuality: PreferredQuality,
): Boolean {
    return any { it.matchingEpisodeKey == episodeKey && it.hasDownloadedQuality(preferredQuality) }
}

private fun String.toPositiveEpisodeNumberOrNull(): Int? {
    return trim()
        .toIntOrNull()
        ?.takeIf { it > 0 }
}

private fun List<VideoVariant>.selectDownloadPlanCandidate(preferredQuality: PreferredQuality): VideoVariant? {
    val qualityMatches = filter { it.canMaybeProvideDownloadQuality(preferredQuality) }
    return qualityMatches
        .sortedWith(downloadPlanSourceComparator())
        .firstOrNull()
}

internal fun List<VideoVariant>.selectSortedDownloadPlanCandidate(preferredQuality: PreferredQuality): VideoVariant? {
    return firstOrNull { it.canMaybeProvideDownloadQuality(preferredQuality) }
}

internal fun downloadPlanSourceComparator(): Comparator<VideoVariant> {
    return compareByDescending<VideoVariant> { it.isOfflineAvailable }
        .thenBy { sourceProviderRank(it.player) }
        .thenByDescending { it.maxKnownSourceQualityHeight() }
        .thenByDescending { it.offlineFiles.maxOfOrNull { file -> file.qualityHeight() } ?: 0 }
        .thenBy { it.index }
        .thenBy { it.id }
}
