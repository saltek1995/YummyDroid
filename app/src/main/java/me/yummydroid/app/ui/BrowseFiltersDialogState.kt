package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.readyDataOrNull

internal data class FiltersDialogOptions(
    val studios: List<FilterOption>,
    val studioTitles: Map<String, String>,
    val creators: List<FilterOption>,
    val creatorTitles: Map<String, String>,
)

internal fun BrowseFilters.normalizedForFiltersDialog(
    isAuthorized: Boolean,
    forcedOfflineMode: Boolean,
): BrowseFilters {
    val authorizedFilters = if (isAuthorized) {
        this
    } else {
        copy(userMarks = emptySet(), excludedUserMarks = emptySet())
    }
    return if (forcedOfflineMode) {
        authorizedFilters.copy(
            offlineOnly = true,
            userMarks = emptySet(),
            excludedUserMarks = emptySet(),
        )
    } else {
        authorizedFilters
    }
}

@Composable
internal fun rememberFiltersDialogCatalog(
    catalogState: LoadState<FilterCatalog>,
    offlineEntries: List<OfflineAnimeEntry>,
    forcedOfflineMode: Boolean,
): FilterCatalog {
    return remember(catalogState, offlineEntries, forcedOfflineMode) {
        if (forcedOfflineMode) {
            offlineEntries.toOfflineFilterCatalog()
        } else {
            catalogState.readyDataOrNull() ?: FilterCatalog.Empty
        }
    }
}

@Composable
internal fun rememberFiltersDialogOptions(
    catalog: FilterCatalog,
    filters: BrowseFilters,
): FiltersDialogOptions {
    val studios = remember(catalog.studios, filters.studios, filters.studioTitles) {
        mergedFilterOptions(catalog.studios, filters.studios, filters.studioTitles)
    }
    val creators = remember(catalog.creators, filters.creators, filters.creatorTitles) {
        mergedFilterOptions(catalog.creators, filters.creators, filters.creatorTitles)
    }
    return FiltersDialogOptions(
        studios = studios,
        studioTitles = remember(studios) { studios.associate { it.value to it.title } },
        creators = creators,
        creatorTitles = remember(creators) { creators.associate { it.value to it.title } },
    )
}

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
