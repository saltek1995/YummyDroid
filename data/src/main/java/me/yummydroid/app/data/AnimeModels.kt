package me.yummydroid.app.data

import kotlinx.serialization.Serializable

@Serializable
data class Anime(
    val id: Long,
    val title: String,
    val description: String,
    val posterUrl: String,
    val animeUrl: String,
    val year: Int?,
    val rating: Double?,
    val userRating: Int? = null,
    val views: Long,
    val status: String,
    val type: String,
    val genres: List<String>,
    val blockedIn: List<String>,
    val episodeAired: Int = 0,
    val episodeCount: Int = 0,
) {
    val meta: String
        get() = animeMetadata(year?.takeIf { it > 0 }?.toString(), type, status)
}

@Serializable
data class AnimeDetails(
    val id: Long,
    val title: String,
    val otherTitles: List<String>,
    val description: String,
    val posterUrl: String,
    val backdropUrl: String?,
    val year: Int?,
    val rating: Double?,
    val userRating: Int? = null,
    val views: Long,
    val status: String,
    val type: String,
    val minAge: String,
    val genreTags: List<FilterOption>,
    val genres: List<String>,
    val episodeSummary: String,
    val episodeAired: Int,
    val episodeCount: Int,
    val nextEpisodeText: String,
    val durationSeconds: Int,
    val ratingDetails: RatingDetails,
    val studios: List<FilterOption>,
    val creators: List<FilterOption>,
    val original: String,
    val commentsCount: Long,
    val listsCount: Long,
    val translations: List<String>,
    val relatedAnime: List<RelatedAnime>,
    val screenshots: List<String>,
    val blockedIn: List<String>,
) {
    val meta: String
        get() = animeMetadata(year?.takeIf { it > 0 }?.toString(), type, status, minAge)
}

@Serializable
data class RelatedAnime(
    val id: Long,
    val title: String,
    val posterUrl: String,
    val year: Int?,
    val rating: Double?,
    val type: String,
    val status: String,
    val relation: String,
    val isCurrent: Boolean,
) {
    val meta: String
        get() = animeMetadata(relation, year?.takeIf { it > 0 }?.toString(), type, status)
}

@Serializable
data class ScheduleAnime(
    val anime: Anime,
    val airedEpisodes: Int,
    val totalEpisodes: Int,
    val previousEpisodeAtSeconds: Long,
    val nextEpisodeAtSeconds: Long,
)

@Serializable
data class RatingDetails(
    val average: Double? = null,
    val counters: Long = 0,
    val kinopoisk: Double? = null,
    val shikimori: Double? = null,
    val myAnimeList: Double? = null,
    val worldArt: Double? = null,
    val aniDub: Double? = null,
)

private fun animeMetadata(vararg values: String?): String {
    return values.filterNotNull().filter(String::isNotBlank).joinToString(" \u2022 ")
}
