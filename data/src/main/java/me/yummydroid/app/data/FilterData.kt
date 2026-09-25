package me.yummydroid.app.data

import android.content.Context
import androidx.core.content.edit
import java.util.Locale
import kotlinx.serialization.Serializable

// BrowseFilters
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
    val dependsOnUserMarks: Boolean
        get() = userMarks.isNotEmpty() || excludedUserMarks.isNotEmpty()

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

// FilterCatalog
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

// FilterOptions
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

// SearchHistoryStorage
class SearchHistoryStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): List<String> {
        return prefs.getJsonOrNull<List<String>>(KEY_HISTORY)
            .orEmpty()
            .normalizedSearchHistory()
    }

    fun add(query: String): List<String> {
        val normalizedQuery = query.normalizedSearchQuery()
        if (normalizedQuery.isBlank()) return read()

        val updated = (listOf(normalizedQuery) + read())
            .normalizedSearchHistory()
            .take(MAX_HISTORY_ENTRIES)
        prefs.edit { putString(KEY_HISTORY, updated.encodeAppJson()) }
        return updated
    }

    fun remove(query: String): List<String> {
        val normalizedQuery = query.normalizedSearchQuery()
        val updated = read()
            .filterNot { it.equals(normalizedQuery, ignoreCase = true) }
            .normalizedSearchHistory()
        prefs.edit { putString(KEY_HISTORY, updated.encodeAppJson()) }
        return updated
    }

    private companion object {
        const val PREFS_NAME = "yummydroid_search_history"
        const val KEY_HISTORY = "history"
        const val MAX_HISTORY_ENTRIES = 8
    }
}

internal fun String.normalizedSearchQuery(): String {
    return trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ")
}

internal fun List<String>.normalizedSearchHistory(): List<String> {
    val seen = mutableSetOf<String>()
    return map { it.normalizedSearchQuery() }
        .filter { it.isNotBlank() }
        .filter { seen.add(it.lowercase(Locale.ROOT)) }
}

// Only fields with reliable saved metadata participate in offline filtering.
enum class OfflineFilterField { Statuses, Types, Genres, Seasons, Translations, AgeRatings, UserMarks, Sort }
data class OfflineFilterPolicy(val effectiveFilters: BrowseFilters, val unsupportedFields: Set<OfflineFilterField>)
fun String.offlineFilterIdentity(): String = trim().substringBefore('?').substringBefore('#')
    .trimEnd('/').substringAfterLast('/').trim().lowercase(Locale.ROOT)
private fun String.offlineLabel() = offlineFilterIdentity().replace('ё', 'е').replace(Regex("[\\s_-]+"), " ").trim()
fun String.offlineStatusKey(): String? = when (offlineLabel()) {
    "released", "completed", "complete", "finished", "вышел", "вышло", "завершен", "завершено", "вийшов", "вийшло", "завершений" -> "released"
    "ongoing", "онгоинг", "онґоїнґ", "онгоїнг" -> "ongoing"
    "announcement", "announcements", "announced", "анонс", "анонсы", "анонси", "анонсирован", "анонсовано", "не вышел", "не вышло", "не вийшов", "не вийшло" -> "announcement"
    else -> null
}
fun String.offlineTypeKey(): String? = when (offlineLabel()) {
    "tv", "tv series", "television", "series", "тв", "тв сериал", "сериал", "телесериал", "тв серіал", "серіал", "телесеріал" -> "tv"
    "movie", "film", "фильм", "фільм", "полнометражный фильм", "повнометражний фільм" -> "movie"
    "ova" -> "ova"
    "ona" -> "ona"
    "special", "specials", "tv special", "спешл", "спэшл", "тв спешл", "спецвыпуск", "спецвипуск" -> "special"
    "music", "music video", "музыкальный клип", "музичний кліп" -> "music"
    else -> null
}
fun String.offlineAgeRatingKey(): String? = when (offlineFilterIdentity().replace(Regex("\\s+"), "")) {
    "1", "pg" -> "1"
    "2", "pg-13" -> "2"
    "3", "r-17+" -> "3"
    "4", "r+" -> "4"
    "5", "rx" -> "5"
    else -> null
}
fun BrowseFilters.offlineFilterPolicy(entries: List<OfflineAnimeEntry> = emptyList()): OfflineFilterPolicy {
    val unsupported = linkedSetOf<OfflineFilterField>()
    fun missing(condition: Boolean, field: OfflineFilterField): Boolean {
        if (condition) unsupported += field
        return condition
    }
    val noStatuses = missing(statuses.isNotEmpty() && (statuses.any { it.offlineStatusKey() == null } || entries.any {
        it.details.status.ifBlank { it.anime.status }.offlineStatusKey() == null
    }), OfflineFilterField.Statuses)
    val noTypes = missing(types.isNotEmpty() && (types.any { it.offlineTypeKey() == null } || entries.any {
        it.details.type.ifBlank { it.anime.type }.offlineTypeKey() == null
    }), OfflineFilterField.Types)
    val noGenres = missing((genres.isNotEmpty() || excludedGenres.isNotEmpty()) && entries.any {
        it.details.genreTags.none { tag -> tag.value.isNotBlank() } && (it.details.genres.isNotEmpty() || it.anime.genres.isNotEmpty())
    }, OfflineFilterField.Genres)
    val noAges = missing(ageRatings.isNotEmpty() && (ageRatings.any { it.offlineAgeRatingKey() == null } || entries.any {
        it.details.minAge.offlineAgeRatingKey() == null
    }), OfflineFilterField.AgeRatings)
    missing(seasons.isNotEmpty(), OfflineFilterField.Seasons)
    missing(translates.isNotEmpty(), OfflineFilterField.Translations)
    missing(userMarks.isNotEmpty() || excludedUserMarks.isNotEmpty(), OfflineFilterField.UserMarks)
    val noSort = missing(sort == AnimeSort.Top || sort == AnimeSort.Random, OfflineFilterField.Sort)
    return OfflineFilterPolicy(copy(
        sort = if (noSort) AnimeSort.Rating else sort,
        statuses = if (noStatuses) emptySet() else statuses,
        types = if (noTypes) emptySet() else types,
        genres = if (noGenres) emptySet() else genres,
        excludedGenres = if (noGenres) emptySet() else excludedGenres,
        ageRatings = if (noAges) emptySet() else ageRatings,
        seasons = emptySet(), translates = emptySet(), userMarks = emptySet(), excludedUserMarks = emptySet(),
    ), unsupported)
}
