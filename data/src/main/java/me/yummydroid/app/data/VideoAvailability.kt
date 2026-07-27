package me.yummydroid.app.data

fun Iterable<VideoVariant>.availableEpisodeCount(): Int {
    return map { it.matchingEpisodeKey }
        .filter { it.isNotBlank() }
        .distinct()
        .size
}

fun Iterable<VideoVariant>.maxSourceEpisodeCount(): Int {
    return sourceEpisodeCounts()
        .values
        .maxOrNull()
        ?: 0
}

fun Iterable<VideoVariant>.sourceEpisodeCounts(): Map<String, Int> {
    return groupBy { it.matchingSourceKey }
        .mapValues { (_, variants) ->
            variants.availableEpisodeCount()
        }
}
