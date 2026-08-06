package me.yummydroid.app.ui

import java.text.Collator
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

internal fun Number?.filterText(): String {
    return when (this) {
        null -> ""
        is Double -> if (this % 1.0 == 0.0) toInt().toString() else toString()
        else -> toString()
    }
}

internal fun BrowseFilters.advancedFilterCount(isAuthorized: Boolean): Int {
    return excludedGenres.size +
        seasons.size +
        types.size +
        studios.size +
        creators.size +
        translates.size +
        ageRatings.size +
        listOfNotNull(fromYear, toYear, minRating, maxRating, episodeFrom, episodeTo).size +
        (if (isAuthorized) userMarks.size + excludedUserMarks.size else 0) +
        if (offlineOnly) 1 else 0
}

internal fun integerInput(value: String): String {
    return value.filter { it.isDigit() }.take(5)
}

internal fun decimalInput(value: String): String {
    val normalized = value.replace(',', '.')
    val builder = StringBuilder()
    var dotSeen = false
    normalized.forEach { char ->
        when {
            char.isDigit() -> builder.append(char)
            char == '.' && !dotSeen -> {
                builder.append(char)
                dotSeen = true
            }
        }
    }
    return builder.toString().take(4)
}

internal fun String.yearFilterValue(): Int? {
    return toIntOrNull()?.takeIf { it in 1900..2100 }
}

internal fun String.episodeFilterValue(): Int? {
    return toIntOrNull()?.takeIf { it in 0..10000 }
}

internal fun String.ratingFilterValue(): Double? {
    return toDoubleOrNull()?.takeIf { it in 0.0..10.0 }
}

internal fun List<FilterOption>.sortedByTitle(locale: Locale = Locale.getDefault()): List<FilterOption> {
    val collator = Collator.getInstance(locale).apply {
        strength = Collator.PRIMARY
    }
    return sortedWith { first, second ->
        val titleCompare = collator.compare(first.title, second.title)
        if (titleCompare != 0) titleCompare else first.value.compareTo(second.value)
    }
}

internal fun Set<String>.toggle(value: String): Set<String> {
    return if (value in this) this - value else this + value
}
