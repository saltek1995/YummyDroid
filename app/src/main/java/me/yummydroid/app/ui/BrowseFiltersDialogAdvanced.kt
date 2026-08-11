package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import me.yummydroid.app.data.FilterOption

@Composable
internal fun AdvancedFiltersDialogSections(
    state: FiltersDialogContentState,
    callbacks: FiltersDialogContentCallbacks,
) {
    AdvancedCatalogFilterSections(state, callbacks)
    AdvancedRangeFilterSections(state, callbacks)
    AdvancedAccountFilterSections(state, callbacks)
}

@Composable
internal fun FiltersDialogSelectionSection(
    id: String,
    title: String,
    options: List<FilterOption>,
    selected: Set<String>,
    state: FiltersDialogContentState,
    callbacks: FiltersDialogContentCallbacks,
    searchable: Boolean = false,
    onToggle: (String) -> me.yummydroid.app.data.BrowseFilters,
) {
    FilterAccordionSection(
        id = id,
        title = title,
        options = options,
        selected = selected,
        expandedSection = state.expandedSection,
        onExpandedChange = callbacks.onExpandedSectionChange,
        onToggle = { callbacks.onFiltersChange(onToggle(it)) },
        onSideExit = callbacks.onSideExit,
        searchable = searchable,
    )
}

@Composable
private fun AdvancedCatalogFilterSections(
    state: FiltersDialogContentState,
    callbacks: FiltersDialogContentCallbacks,
) {
    val filters = state.filters
    FiltersDialogSelectionSection(
        id = "excluded_genres",
        title = uiText(UiStringKey.ExcludeGenres),
        options = state.catalog.genres,
        selected = filters.excludedGenres,
        state = state,
        callbacks = callbacks,
        searchable = true,
        onToggle = { filters.copy(excludedGenres = filters.excludedGenres.toggle(it)) },
    )
    FiltersDialogSelectionSection(
        id = "types",
        title = uiText(UiStringKey.Type),
        options = state.catalog.types,
        selected = filters.types,
        state = state,
        callbacks = callbacks,
        onToggle = { filters.copy(types = filters.types.toggle(it)) },
    )
    FiltersDialogSelectionSection(
        id = "studios",
        title = uiText(UiStringKey.Studio),
        options = state.options.studios,
        selected = filters.studios,
        state = state,
        callbacks = callbacks,
        searchable = true,
        onToggle = { filters.toggleStudioFilter(it, state.options.studioTitles[it]) },
    )
    FiltersDialogSelectionSection(
        id = "creators",
        title = uiText(UiStringKey.Director),
        options = state.options.creators,
        selected = filters.creators,
        state = state,
        callbacks = callbacks,
        searchable = true,
        onToggle = { filters.toggleCreatorFilter(it, state.options.creatorTitles[it]) },
    )
}
