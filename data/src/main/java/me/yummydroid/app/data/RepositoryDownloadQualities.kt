package me.yummydroid.app.data
internal data class SourceQualityResolveResult(
    val candidate: VideoVariant,
    val qualities: List<SourceQuality>,
)


internal fun List<SourceQualityResolveResult>.availableDownloadHeights(allEpisodes: Boolean): Set<Int> {
    if (isEmpty()) return emptySet()
    if (!allEpisodes) {
        return flatMap { it.qualities }
            .normalizedSourceQualities()
            .mapNotNullTo(mutableSetOf()) { it.height }
    }

    val heightsByEpisode = groupBy { it.candidate.downloadEpisodeSlotKey }
        .values
        .map { episodeSources ->
            episodeSources
                .flatMap { it.qualities }
                .normalizedSourceQualities()
                .mapNotNullTo(mutableSetOf()) { it.height }
        }
        .filter { it.isNotEmpty() }
    if (heightsByEpisode.isEmpty()) return emptySet()
    return heightsByEpisode.reduce { common, episodeHeights ->
        common.intersect(episodeHeights).toMutableSet()
    }
}
