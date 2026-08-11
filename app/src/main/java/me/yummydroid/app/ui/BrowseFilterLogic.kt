package me.yummydroid.app.ui

import me.yummydroid.app.data.FilterOption

internal fun mergedFilterOptions(
    catalogOptions: List<FilterOption>,
    selectedValues: Set<String>,
    selectedTitles: Map<String, String>,
): List<FilterOption> {
    val selectedOptions = selectedValues.map { value ->
        FilterOption(title = selectedTitles[value] ?: value, value = value)
    }
    return (catalogOptions + selectedOptions)
        .filter { it.title.isNotBlank() && it.value.isNotBlank() }
        .distinctBy { it.value }
        .sortedByTitle()
}
