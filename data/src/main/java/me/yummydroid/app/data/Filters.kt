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

@Serializable
data class FilterCatalog(
    val genres: List<FilterOption> = emptyList(),
    val types: List<FilterOption> = emptyList(),
    val studios: List<FilterOption> = emptyList(),
    val creators: List<FilterOption> = emptyList(),
) {
    companion object {
        val Empty = FilterCatalog()
    }
}

@Serializable
data class FilterOption(
    val title: String,
    val value: String,
)

enum class AnimeSort(
    val title: String,
    val apiValue: String,
    val forward: Boolean,
) {
    Rating("Rating", "rating", false),
    RatingCounters("Votes", "rating_counters", false),
    Views("Views", "views", false),
    Year("New", "year", false),
    Top("Top", "top", false),
    Title("A-Z", "title", true),
    Id("Recently added", "id", false),
    Random("Random", "random", true),
}

val statusFilterOptions = listOf(
    FilterOption("Released", "released"),
    FilterOption("Ongoing", "ongoing"),
    FilterOption("Announcements", "announcement"),
)

val seasonFilterOptions = listOf(
    FilterOption("Winter", "winter"),
    FilterOption("Spring", "spring"),
    FilterOption("Summer", "summer"),
    FilterOption("Fall", "fall"),
)

val translateFilterOptions = listOf(
    FilterOption("Full dubbing", "dubbing"),
    FilterOption("Multi voice", "multivoice"),
    FilterOption("Two voice", "duet"),
    FilterOption("Single voice", "onevoice"),
    FilterOption("Subtitles", "subtitles"),
)

val ageRatingFilterOptions = listOf(
    FilterOption("PG", "1"),
    FilterOption("PG-13", "2"),
    FilterOption("R-17+", "3"),
    FilterOption("R+", "4"),
    FilterOption("Rx", "5"),
)

val userMarkFilterOptions = listOf(
    FilterOption("Watching", "0"),
    FilterOption("Planned", "1"),
    FilterOption("Watched", "2"),
    FilterOption("Dropped", "3"),
    FilterOption("Postponed", "5"),
    FilterOption("Favorites", "4"),
)

enum class AnimeStatusFilter(
    val title: String,
    val apiValue: String?,
) {
    All("All", null),
}

enum class AnimeGenreFilter(
    val title: String,
    val apiValue: String?,
) {
    All("All genres", null),
}
