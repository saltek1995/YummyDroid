package me.yummyani.app.data

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
    val creators: Set<String> = emptySet(),
    val translates: Set<String> = emptySet(),
    val ageRatings: Set<String> = emptySet(),
    val userMarks: Set<String> = emptySet(),
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
            listOfNotNull(fromYear, toYear, minRating, maxRating, episodeFrom, episodeTo).size +
            if (sort == AnimeSort.Rating) 0 else 1

    val status: AnimeStatusFilter
        get() = AnimeStatusFilter.All

    val genre: AnimeGenreFilter
        get() = AnimeGenreFilter.All
}

data class FilterCatalog(
    val genres: List<FilterOption> = emptyList(),
    val types: List<FilterOption> = emptyList(),
) {
    companion object {
        val Empty = FilterCatalog()
    }
}

data class FilterOption(
    val title: String,
    val value: String,
)

enum class AnimeSort(
    val title: String,
    val apiValue: String,
    val forward: Boolean,
) {
    Rating("Рейтинг", "rating", false),
    RatingCounters("Оценок", "rating_counters", false),
    Views("Просмотры", "views", false),
    Year("Новые", "year", false),
    Top("Топ", "top", false),
    Title("А-Я", "title", true),
    Id("По добавлению", "id", false),
    Random("Случайно", "random", true),
}

val statusFilterOptions = listOf(
    FilterOption("Вышло", "released"),
    FilterOption("Онгоинг", "ongoing"),
    FilterOption("Анонсы", "announcement"),
)

val seasonFilterOptions = listOf(
    FilterOption("Зима", "winter"),
    FilterOption("Весна", "spring"),
    FilterOption("Лето", "summer"),
    FilterOption("Осень", "fall"),
)

val translateFilterOptions = listOf(
    FilterOption("Полное дублирование", "dubbing"),
    FilterOption("Многоголосый", "multivoice"),
    FilterOption("Двухголосый", "duet"),
    FilterOption("Одноголосый", "onevoice"),
    FilterOption("Субтитры", "subtitles"),
)

val ageRatingFilterOptions = listOf(
    FilterOption("PG", "1"),
    FilterOption("PG-13", "2"),
    FilterOption("R-17+", "3"),
    FilterOption("R+", "4"),
    FilterOption("Rx", "5"),
)

val userMarkFilterOptions = listOf(
    FilterOption("Смотрю", "0"),
    FilterOption("В планах", "1"),
    FilterOption("Просмотрено", "2"),
    FilterOption("Брошено", "3"),
    FilterOption("Отложено", "5"),
    FilterOption("Любимые", "4"),
)

enum class AnimeStatusFilter(
    val title: String,
    val apiValue: String?,
) {
    All("Все", null),
}

enum class AnimeGenreFilter(
    val title: String,
    val apiValue: String?,
) {
    All("Все жанры", null),
}

