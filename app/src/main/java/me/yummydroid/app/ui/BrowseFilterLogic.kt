package me.yummydroid.app.ui

import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.type
import java.text.Collator
import java.util.Locale
import me.yummydroid.app.data.AnimeSort
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.ScheduleAnime

internal fun List<ScheduleAnime>.filteredAndSortedSchedule(
    filters: BrowseFilters,
    catalog: FilterCatalog,
): List<ScheduleAnime> {
    val genreInclude = catalog.tokensFor(filters.genres, catalog.genres)
    val genreExclude = catalog.tokensFor(filters.excludedGenres, catalog.genres)
    val typeInclude = catalog.tokensFor(filters.types, catalog.types)

    return asSequence()
        .filter { item -> item.matchesScheduleFilters(filters, genreInclude, genreExclude, typeInclude) }
        .toList()
        .sortedForSchedule(filters.sort)
}

internal fun FilterCatalog.tokensFor(
    selected: Set<String>,
    options: List<FilterOption>,
): Set<String> {
    if (selected.isEmpty()) return emptySet()
    val byValue = options.filter { it.value in selected }
    return (selected + byValue.flatMap { option -> listOf(option.title, option.value) })
        .flatMap { value -> listOf(value, value.substringAfterLast('/')) }
        .map { it.normalizedScheduleToken() }
        .filterTo(mutableSetOf()) { it.isNotBlank() }
}

internal fun List<OfflineAnimeEntry>.toOfflineFilterCatalog(): FilterCatalog {
    fun List<String>.toFilterOptions(): List<FilterOption> {
        return asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
            .map { FilterOption(title = it, value = it) }
            .toList()
    }
    fun List<FilterOption>.toDistinctFilterOptions(): List<FilterOption> {
        return asSequence()
            .filter { it.title.isNotBlank() && it.value.isNotBlank() }
            .distinctBy { it.value }
            .toList()
            .sortedByTitle()
    }

    return FilterCatalog(
        genres = flatMap { entry ->
            entry.details.genreTags.map { it.title }.ifEmpty { entry.anime.genres }
        }.toFilterOptions(),
        types = map { entry -> entry.details.type.ifBlank { entry.anime.type } }.toFilterOptions(),
        studios = flatMap { entry -> entry.details.studios }.toDistinctFilterOptions(),
        creators = flatMap { entry -> entry.details.creators }.toDistinctFilterOptions(),
    )
}

internal fun mergedFilterOptions(
    catalogOptions: List<FilterOption>,
    selectedValues: Set<String>,
    selectedTitles: Map<String, String>,
): List<FilterOption> {
    return (catalogOptions + selectedValues.map { value ->
        FilterOption(title = selectedTitles[value] ?: value, value = value)
    })
        .filter { it.title.isNotBlank() && it.value.isNotBlank() }
        .distinctBy { it.value }
        .sortedByTitle()
}

internal fun BrowseFilters.toggleStudioFilter(value: String, title: String?): BrowseFilters {
    return if (value in studios) {
        copy(studios = studios - value, studioTitles = studioTitles - value)
    } else {
        copy(
            studios = studios + value,
            studioTitles = studioTitles + (value to (title?.takeIf { it.isNotBlank() } ?: value)),
        )
    }
}

internal fun BrowseFilters.toggleCreatorFilter(value: String, title: String?): BrowseFilters {
    return if (value in creators) {
        copy(creators = creators - value, creatorTitles = creatorTitles - value)
    } else {
        copy(
            creators = creators + value,
            creatorTitles = creatorTitles + (value to (title?.takeIf { it.isNotBlank() } ?: value)),
        )
    }
}

internal fun ScheduleAnime.matchesScheduleFilters(
    filters: BrowseFilters,
    genreInclude: Set<String>,
    genreExclude: Set<String>,
    typeInclude: Set<String>,
): Boolean {
    val anime = anime
    val year = anime.year?.takeIf { it > 0 }
    val fromYear = filters.fromYear
    val toYear = filters.toYear
    val minRating = filters.minRating
    val maxRating = filters.maxRating
    val episodeFrom = filters.episodeFrom
    val episodeTo = filters.episodeTo
    val rating = anime.rating
    if (fromYear != null && (year == null || year < fromYear)) return false
    if (toYear != null && (year == null || year > toYear)) return false
    if (minRating != null && (rating == null || rating < minRating)) return false
    if (maxRating != null && (rating == null || rating > maxRating)) return false
    if (episodeFrom != null && airedEpisodes < episodeFrom) return false
    if (episodeTo != null && airedEpisodes > episodeTo) return false
    if (filters.offlineOnly) return false

    if (filters.statuses.isNotEmpty() && filters.statuses.none { anime.status.matchesScheduleStatus(it) }) {
        return false
    }
    if (typeInclude.isNotEmpty() && anime.type.normalizedScheduleToken() !in typeInclude) {
        return false
    }

    val animeGenres = anime.genres.mapTo(mutableSetOf()) { it.normalizedScheduleToken() }
    if (genreInclude.isNotEmpty() && animeGenres.none { genre -> genreInclude.any { genre.matchesScheduleToken(it) } }) {
        return false
    }
    if (genreExclude.isNotEmpty() && animeGenres.any { genre -> genreExclude.any { genre.matchesScheduleToken(it) } }) {
        return false
    }

    return true
}

internal fun List<ScheduleAnime>.sortedForSchedule(sort: AnimeSort): List<ScheduleAnime> {
    val collator = Collator.getInstance(Locale.forLanguageTag("ru-RU")).apply {
        strength = Collator.PRIMARY
    }
    return when (sort) {
        AnimeSort.Title -> sortedWith { first, second ->
            collator.compare(first.anime.title, second.anime.title)
        }
        AnimeSort.Views -> sortedByDescending { it.anime.views }
        AnimeSort.Year -> sortedWith(
            compareByDescending<ScheduleAnime> { it.anime.year ?: 0 }
                .thenBy { it.nextEpisodeAtSeconds.takeIf { next -> next > 0L } ?: Long.MAX_VALUE },
        )
        AnimeSort.Id -> sortedByDescending { it.anime.id }
        AnimeSort.Random -> sortedBy { ((it.anime.id * 1103515245L) + 12345L) and 0x7fffffffL }
        AnimeSort.Rating,
        AnimeSort.RatingCounters,
        AnimeSort.Top -> sortedWith(
            compareByDescending<ScheduleAnime> { it.anime.rating ?: -1.0 }
                .thenByDescending { it.anime.views }
                .thenBy { it.nextEpisodeAtSeconds.takeIf { next -> next > 0L } ?: Long.MAX_VALUE },
        )
    }
}

internal fun String.matchesScheduleStatus(selected: String): Boolean {
    val status = normalizedScheduleToken()
    return when (selected.normalizedScheduleToken()) {
        "ongoing" -> status.contains("онго") || status.contains("ongo")
        "released" -> status.contains("выш") || status.contains("релиз") || status.contains("released")
        "announcement" -> status.contains("анонс") || status.contains("announce")
        else -> status.matchesScheduleToken(selected.normalizedScheduleToken())
    }
}

internal fun String.matchesScheduleToken(token: String): Boolean {
    if (isBlank() || token.isBlank()) return false
    return this == token || contains(token) || token.contains(this)
}

internal fun String.normalizedScheduleToken(): String {
    return trim()
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(Regex("[^a-zа-я0-9]+"), " ")
        .trim()
}

internal fun upcomingScheduleItems(
    items: List<ScheduleAnime>,
    nowSeconds: Long = System.currentTimeMillis() / 1000L,
): List<ScheduleAnime> {
    return items.filter { it.nextEpisodeAtSeconds > nowSeconds }
}
