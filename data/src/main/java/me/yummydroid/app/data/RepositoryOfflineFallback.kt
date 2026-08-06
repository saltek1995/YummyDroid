package me.yummydroid.app.data
internal fun List<VideoVariant>.withOfflineDownloads(
    offlineVideos: List<VideoVariant>,
    details: AnimeDetails,
): List<VideoVariant> {
    val availableOfflineVideos = offlineVideos.filter { it.isOfflineAvailable }
    val offlineById = availableOfflineVideos.groupBy { it.id }
    val offlineBySlot = availableOfflineVideos.groupBy { it.sourceSlotKey }
    val offlineByVoiceSlot = availableOfflineVideos.groupBy { it.downloadVoiceSlotKey }

    return map { video ->
        val offlineMatches = buildList {
            addAll(offlineById[video.id].orEmpty())
            addAll(offlineBySlot[video.sourceSlotKey].orEmpty())
            addAll(offlineByVoiceSlot[video.downloadVoiceSlotKey].orEmpty())
        }.distinctBy { it.id to it.localPlaybackUrl }

        if (offlineMatches.isNotEmpty()) {
            val offlineFiles = offlineMatches
                .flatMap { it.offlineFiles }
                .filter { it.playbackUrl.isNotBlank() }
                .distinctBy { it.playbackUrl }
                .sortedWith(compareByDescending<OfflineVideoFile> { it.qualityHeight() }.thenBy { it.qualityTitle })
            val primaryFile = offlineFiles.firstOrNull()
            val fallbackOffline = offlineMatches.first()
            video.copy(
                previewUrl = video.previewUrl.ifBlank { fallbackOffline.previewUrl },
                localPlaybackUrl = primaryFile?.playbackUrl ?: fallbackOffline.localPlaybackUrl,
                localMimeType = primaryFile?.mimeType ?: fallbackOffline.localMimeType,
                localBytes = primaryFile?.bytes ?: fallbackOffline.localBytes,
                localFiles = offlineFiles.ifEmpty { fallbackOffline.offlineFiles },
            )
        } else {
            video
        }
    }
}

internal data class UserMarkFilterIds(
    val includedIds: Set<Long>?,
    val excludedIds: Set<Long>,
)

internal fun VideoVariant.withoutOfflinePlayback(): VideoVariant {
    return copy(
        localPlaybackUrl = "",
        localMimeType = null,
        localBytes = 0L,
        localFiles = emptyList(),
    )
}

internal fun List<OfflineAnimeEntry>.filteredOfflineAnime(
    query: String = "",
    filters: BrowseFilters,
): List<Anime> {
    val normalizedQuery = query.normalizedFilterToken()
    return asSequence()
        .filter { entry ->
            val anime = entry.anime
            val details = entry.details
            val year = details.year ?: anime.year
            val rating = details.rating ?: anime.rating
            val genres = (details.genreTags.map { it.title } + details.genres + anime.genres)
                .map { it.normalizedFilterToken() }
                .filterTo(mutableSetOf()) { it.isNotBlank() }
            val type = details.type.ifBlank { anime.type }.normalizedFilterToken()
            val status = details.status.ifBlank { anime.status }.normalizedFilterToken()
            val episodeCount = entry.downloadedVideos.size

            if (normalizedQuery.isNotBlank()) {
                val haystack = listOf(
                    anime.title,
                    anime.description,
                    details.description,
                    details.otherTitles.joinToString(" "),
                    details.genreTags.joinToString(" ") { it.title },
                    details.genres.joinToString(" "),
                ).joinToString(" ").normalizedFilterToken()
                if (!haystack.contains(normalizedQuery)) return@filter false
            }
            if (filters.fromYear != null && (year == null || year < filters.fromYear)) return@filter false
            if (filters.toYear != null && (year == null || year > filters.toYear)) return@filter false
            if (filters.minRating != null && (rating == null || rating < filters.minRating)) return@filter false
            if (filters.maxRating != null && (rating == null || rating > filters.maxRating)) return@filter false
            if (filters.episodeFrom != null && episodeCount < filters.episodeFrom) return@filter false
            if (filters.episodeTo != null && episodeCount > filters.episodeTo) return@filter false
            if (filters.statuses.isNotEmpty() && filters.statuses.none { status.matchesFilterToken(it) }) return@filter false
            if (filters.types.isNotEmpty() && filters.types.none { type.matchesFilterToken(it) }) return@filter false
            if (filters.genres.isNotEmpty() && genres.none { genre -> filters.genres.any { genre.matchesFilterToken(it) } }) {
                return@filter false
            }
            if (filters.excludedGenres.isNotEmpty() && genres.any { genre ->
                    filters.excludedGenres.any { genre.matchesFilterToken(it) }
                }
            ) {
                return@filter false
            }
            true
        }
        .map { it.anime }
        .toList()
        .sortedOffline(filters.sort)
}

private fun List<Anime>.sortedOffline(sort: AnimeSort): List<Anime> {
    return when (sort) {
        AnimeSort.Title -> sortedBy { it.title.lowercase() }
        AnimeSort.Views -> sortedByDescending { it.views }
        AnimeSort.Year -> sortedByDescending { it.year ?: 0 }
        AnimeSort.Top,
        AnimeSort.Rating -> sortedByDescending { it.rating ?: 0.0 }
        AnimeSort.RatingCounters,
        AnimeSort.Id -> sortedByDescending { it.id }
        AnimeSort.Random -> shuffled()
    }
}

private fun String.matchesFilterToken(selected: String): Boolean {
    val value = normalizedFilterToken()
    val token = selected.normalizedFilterToken().substringAfterLast("/")
    return value == token || value.contains(token) || token.contains(value)
}

private fun String.normalizedFilterToken(): String {
    return trim()
        .lowercase()
        .replace('\u0451', '\u0435')
        .replace(Regex("[^a-z\\u0430-\\u044f0-9]+"), " ")
        .trim()
}
