package me.yummydroid.app.ui

import java.util.Locale
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.ScheduleAnime

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

internal fun upcomingScheduleItems(
    items: List<ScheduleAnime>,
    nowSeconds: Long = System.currentTimeMillis() / 1000L,
): List<ScheduleAnime> {
    return items.filter { it.nextEpisodeAtSeconds > nowSeconds }
}
