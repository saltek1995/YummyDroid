package me.yummydroid.app.ui

import java.util.Locale
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.OfflineAnimeEntry

internal fun List<OfflineAnimeEntry>.toOfflineFilterCatalog(): FilterCatalog = FilterCatalog(
    genres = flatMap { entry ->
        entry.details.genreTags.map { it.title }.ifEmpty { entry.anime.genres }
    }.toFilterOptions(),
    types = map { entry -> entry.details.type.ifBlank { entry.anime.type } }.toFilterOptions(),
    studios = flatMap { entry -> entry.details.studios }.toDistinctFilterOptions(),
    creators = flatMap { entry -> entry.details.creators }.toDistinctFilterOptions(),
)

private fun List<String>.toFilterOptions(): List<FilterOption> {
    return asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { value -> value.lowercase(Locale.ROOT) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { value -> value })
        .map { value -> FilterOption(title = value, value = value) }
        .toList()
}

private fun List<FilterOption>.toDistinctFilterOptions(): List<FilterOption> {
    return asSequence()
        .filter { option -> option.title.isNotBlank() && option.value.isNotBlank() }
        .distinctBy(FilterOption::value)
        .toList()
        .sortedByTitle()
}
