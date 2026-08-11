package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import me.yummydroid.app.data.ageRatingFilterOptions
import me.yummydroid.app.data.seasonFilterOptions
import me.yummydroid.app.data.translateFilterOptions

@Composable
internal fun AdvancedRangeFilterSections(
    state: FiltersDialogContentState,
    callbacks: FiltersDialogContentCallbacks,
) {
    val filters = state.filters
    FiltersDialogRangeSection(
        id = "years",
        title = uiText(UiStringKey.Year),
        summary = rangeSummary(filters.fromYear, filters.toYear),
        startText = filters.fromYear?.toString().orEmpty(),
        endText = filters.toYear?.toString().orEmpty(),
        keyboardType = KeyboardType.Number,
        sanitizeInput = ::integerInput,
        state = state,
        callbacks = callbacks,
        onStartChange = { filters.copy(fromYear = it.yearFilterValue()) },
        onEndChange = { filters.copy(toYear = it.yearFilterValue()) },
    )
    FiltersDialogSelectionSection(
        id = "seasons",
        title = uiText(UiStringKey.Season),
        options = seasonFilterOptions,
        selected = filters.seasons,
        state = state,
        callbacks = callbacks,
        onToggle = { filters.copy(seasons = filters.seasons.toggle(it)) },
    )
    FiltersDialogSelectionSection(
        id = "translates",
        title = uiText(UiStringKey.Voice),
        options = translateFilterOptions,
        selected = filters.translates,
        state = state,
        callbacks = callbacks,
        onToggle = { filters.copy(translates = filters.translates.toggle(it)) },
    )
    FiltersDialogSelectionSection(
        id = "age",
        title = uiText(UiStringKey.Age),
        options = ageRatingFilterOptions,
        selected = filters.ageRatings,
        state = state,
        callbacks = callbacks,
        onToggle = { filters.copy(ageRatings = filters.ageRatings.toggle(it)) },
    )
    FiltersDialogRangeSection(
        id = "rating_range",
        title = uiText(UiStringKey.Rating5709e2),
        summary = rangeSummary(filters.minRating, filters.maxRating),
        startText = filters.minRating.filterText(),
        endText = filters.maxRating.filterText(),
        keyboardType = KeyboardType.Decimal,
        sanitizeInput = ::decimalInput,
        state = state,
        callbacks = callbacks,
        onStartChange = { filters.copy(minRating = it.ratingFilterValue()) },
        onEndChange = { filters.copy(maxRating = it.ratingFilterValue()) },
    )
    FiltersDialogRangeSection(
        id = "episodes",
        title = uiText(UiStringKey.Episodes),
        summary = rangeSummary(filters.episodeFrom, filters.episodeTo),
        startText = filters.episodeFrom?.toString().orEmpty(),
        endText = filters.episodeTo?.toString().orEmpty(),
        keyboardType = KeyboardType.Number,
        sanitizeInput = ::integerInput,
        state = state,
        callbacks = callbacks,
        onStartChange = { filters.copy(episodeFrom = it.episodeFilterValue()) },
        onEndChange = { filters.copy(episodeTo = it.episodeFilterValue()) },
    )
}

@Composable
private fun FiltersDialogRangeSection(
    id: String,
    title: String,
    summary: String,
    startText: String,
    endText: String,
    keyboardType: KeyboardType,
    sanitizeInput: (String) -> String,
    state: FiltersDialogContentState,
    callbacks: FiltersDialogContentCallbacks,
    onStartChange: (String) -> me.yummydroid.app.data.BrowseFilters,
    onEndChange: (String) -> me.yummydroid.app.data.BrowseFilters,
) {
    RangeAccordionSection(
        id = id,
        title = title,
        summary = summary,
        expandedSection = state.expandedSection,
        onExpandedChange = callbacks.onExpandedSectionChange,
        startLabel = uiText(UiStringKey.From),
        endLabel = uiText(UiStringKey.To),
        startText = startText,
        endText = endText,
        keyboardType = keyboardType,
        sanitizeInput = sanitizeInput,
        onStartChange = { callbacks.onFiltersChange(onStartChange(it)) },
        onEndChange = { callbacks.onFiltersChange(onEndChange(it)) },
        onSideExit = callbacks.onSideExit,
    )
}
