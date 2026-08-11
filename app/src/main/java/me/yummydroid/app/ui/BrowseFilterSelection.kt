package me.yummydroid.app.ui

import me.yummydroid.app.data.BrowseFilters

private data class NamedFilterSelection(
    val values: Set<String>,
    val titles: Map<String, String>,
)

internal fun BrowseFilters.toggleStudioFilter(value: String, title: String?): BrowseFilters {
    val selection = toggleNamedFilter(studios, studioTitles, value, title)
    return copy(studios = selection.values, studioTitles = selection.titles)
}

internal fun BrowseFilters.toggleCreatorFilter(value: String, title: String?): BrowseFilters {
    val selection = toggleNamedFilter(creators, creatorTitles, value, title)
    return copy(creators = selection.values, creatorTitles = selection.titles)
}

private fun toggleNamedFilter(
    values: Set<String>,
    titles: Map<String, String>,
    value: String,
    title: String?,
): NamedFilterSelection {
    return if (value in values) {
        NamedFilterSelection(values - value, titles - value)
    } else {
        val resolvedTitle = title?.takeIf(String::isNotBlank) ?: value
        NamedFilterSelection(values + value, titles + (value to resolvedTitle))
    }
}

internal fun BrowseFilters.advancedFilterCount(isAuthorized: Boolean): Int {
    val commonCount = excludedGenres.size +
        seasons.size +
        types.size +
        studios.size +
        creators.size +
        translates.size +
        ageRatings.size +
        listOfNotNull(fromYear, toYear, minRating, maxRating, episodeFrom, episodeTo).size
    val markCount = if (isAuthorized) userMarks.size + excludedUserMarks.size else 0
    return commonCount + markCount + if (offlineOnly) 1 else 0
}

internal fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value
