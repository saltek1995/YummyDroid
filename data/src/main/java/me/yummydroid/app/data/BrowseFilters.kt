package me.yummydroid.app.data

import kotlinx.serialization.Serializable

@Serializable
data class BrowseFilters(
    val sort: AnimeSort = AnimeSort.Rating,
    val fromYear: Int? = null,
    val toYear: Int? = null,
    val minRating: Double? = null,
    val maxRating: Double? = null,
    val episodeFrom: Int? = null,
    val episodeTo: Int? = null,
    val statuses: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val excludedGenres: Set<String> = emptySet(),
    val seasons: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val studios: Set<String> = emptySet(),
    val studioTitles: Map<String, String> = emptyMap(),
    val creators: Set<String> = emptySet(),
    val creatorTitles: Map<String, String> = emptyMap(),
    val translates: Set<String> = emptySet(),
    val ageRatings: Set<String> = emptySet(),
    val userMarks: Set<String> = emptySet(),
    val excludedUserMarks: Set<String> = emptySet(),
    val offlineOnly: Boolean = false,
) {
    val activeCount: Int
        get() = statuses.size +
            genres.size +
            excludedGenres.size +
            seasons.size +
            types.size +
            studios.size +
            creators.size +
            translates.size +
            ageRatings.size +
            userMarks.size +
            excludedUserMarks.size +
            listOfNotNull(fromYear, toYear, minRating, maxRating, episodeFrom, episodeTo).size +
            (if (offlineOnly) 1 else 0) +
            if (sort == AnimeSort.Rating) 0 else 1

    val status: AnimeStatusFilter
        get() = AnimeStatusFilter.All

    val genre: AnimeGenreFilter
        get() = AnimeGenreFilter.All
}
