package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.ageRatingFilterOptions
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.seasonFilterOptions
import me.yummydroid.app.data.statusFilterOptions
import me.yummydroid.app.data.translateFilterOptions
import me.yummydroid.app.data.userMarkFilterOptions
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.ui.theme.YummySpacing

@Composable
internal fun FiltersDialogAccordion(
    filters: BrowseFilters,
    auth: AuthUiState,
    catalogState: LoadState<FilterCatalog>,
    offlineEntries: List<OfflineAnimeEntry>,
    forcedOfflineMode: Boolean,
    onApply: (BrowseFilters) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isAuthorized = auth.profile != null && !forcedOfflineMode
    var draft by remember(filters, isAuthorized, forcedOfflineMode) {
        val baseFilters = if (isAuthorized) {
            filters
        } else {
            filters.copy(userMarks = emptySet(), excludedUserMarks = emptySet())
        }
        mutableStateOf(
            if (forcedOfflineMode) {
                baseFilters.copy(offlineOnly = true, userMarks = emptySet(), excludedUserMarks = emptySet())
            } else {
                baseFilters
            },
        )
    }
    var expandedSection by remember { mutableStateOf("") }
    var advancedVisible by remember(filters) { mutableStateOf(false) }
    val catalog = remember(catalogState, offlineEntries, forcedOfflineMode) {
        if (forcedOfflineMode) {
            offlineEntries.toOfflineFilterCatalog()
        } else {
            catalogState.readyDataOrNull() ?: FilterCatalog.Empty
        }
    }
    val studioOptions = remember(catalog.studios, draft.studios, draft.studioTitles) {
        mergedFilterOptions(catalog.studios, draft.studios, draft.studioTitles)
    }
    val creatorOptions = remember(catalog.creators, draft.creators, draft.creatorTitles) {
        mergedFilterOptions(catalog.creators, draft.creators, draft.creatorTitles)
    }
    val studioOptionTitles = remember(studioOptions) {
        studioOptions.associate { it.value to it.title }
    }
    val creatorOptionTitles = remember(creatorOptions) {
        creatorOptions.associate { it.value to it.title }
    }
    val hiddenActiveCount = remember(draft, isAuthorized) { draft.advancedFilterCount(isAuthorized) }
    val containerScrollState = rememberScrollState()
    val applyFocusRequester = remember { FocusRequester() }
    val moveFocusToActions: () -> Unit = remember {
        {
            applyFocusRequester.requestFocusSafely()
            Unit
        }
    }
    fun resetAndDismiss() {
        draft = if (forcedOfflineMode) BrowseFilters(offlineOnly = true) else BrowseFilters()
        onReset()
        onDismiss()
    }

    fun applyAndDismiss() {
        onApply(
            when {
                forcedOfflineMode -> draft.copy(
                    offlineOnly = true,
                    userMarks = emptySet(),
                    excludedUserMarks = emptySet(),
                )
                isAuthorized -> draft
                else -> draft.copy(userMarks = emptySet(), excludedUserMarks = emptySet())
            },
        )
        onDismiss()
    }

    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.Filters)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(state = containerScrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SortAccordionSection(
                    expanded = expandedSection == "sort",
                    selected = draft.sort,
                    onToggleExpanded = {
                        expandedSection = if (expandedSection == "sort") "" else "sort"
                    },
                    onSelected = { draft = draft.copy(sort = it) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "status",
                    title = uiText(UiStringKey.Status),
                    options = statusFilterOptions,
                    selected = draft.statuses,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(statuses = draft.statuses.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "genres",
                    title = uiText(UiStringKey.Genres),
                    options = catalog.genres,
                    selected = draft.genres,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(genres = draft.genres.toggle(value)) },
                    onSideExit = moveFocusToActions,
                    searchable = true,
                )

                if (!advancedVisible) {
                    AdvancedFiltersButton(
                        activeCount = hiddenActiveCount,
                        onClick = { advancedVisible = true },
                    )
                }

                if (advancedVisible) {
                FilterAccordionSection(
                    id = "excluded_genres",
                    title = uiText(UiStringKey.ExcludeGenres),
                    options = catalog.genres,
                    selected = draft.excludedGenres,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(excludedGenres = draft.excludedGenres.toggle(value)) },
                    onSideExit = moveFocusToActions,
                    searchable = true,
                )

                FilterAccordionSection(
                    id = "types",
                    title = uiText(UiStringKey.Type),
                    options = catalog.types,
                    selected = draft.types,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(types = draft.types.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "studios",
                    title = uiText(UiStringKey.Studio),
                    options = studioOptions,
                    selected = draft.studios,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value ->
                        draft = draft.toggleStudioFilter(value, studioOptionTitles[value])
                    },
                    onSideExit = moveFocusToActions,
                    searchable = true,
                )

                FilterAccordionSection(
                    id = "creators",
                    title = uiText(UiStringKey.Director),
                    options = creatorOptions,
                    selected = draft.creators,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value ->
                        draft = draft.toggleCreatorFilter(value, creatorOptionTitles[value])
                    },
                    onSideExit = moveFocusToActions,
                    searchable = true,
                )

                RangeAccordionSection(
                    id = "years",
                    title = uiText(UiStringKey.Year),
                    summary = rangeSummary(draft.fromYear, draft.toYear),
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    startLabel = uiText(UiStringKey.From),
                    endLabel = uiText(UiStringKey.To),
                    startText = draft.fromYear?.toString().orEmpty(),
                    endText = draft.toYear?.toString().orEmpty(),
                    keyboardType = KeyboardType.Number,
                    sanitizeInput = ::integerInput,
                    onStartChange = { value -> draft = draft.copy(fromYear = value.yearFilterValue()) },
                    onEndChange = { value -> draft = draft.copy(toYear = value.yearFilterValue()) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "seasons",
                    title = uiText(UiStringKey.Season),
                    options = seasonFilterOptions,
                    selected = draft.seasons,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(seasons = draft.seasons.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "translates",
                    title = uiText(UiStringKey.Voice),
                    options = translateFilterOptions,
                    selected = draft.translates,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(translates = draft.translates.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                FilterAccordionSection(
                    id = "age",
                    title = uiText(UiStringKey.Age),
                    options = ageRatingFilterOptions,
                    selected = draft.ageRatings,
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    onToggle = { value -> draft = draft.copy(ageRatings = draft.ageRatings.toggle(value)) },
                    onSideExit = moveFocusToActions,
                )

                RangeAccordionSection(
                    id = "rating_range",
                    title = uiText(UiStringKey.Rating5709e2),
                    summary = rangeSummary(draft.minRating, draft.maxRating),
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    startLabel = uiText(UiStringKey.From),
                    endLabel = uiText(UiStringKey.To),
                    startText = draft.minRating.filterText(),
                    endText = draft.maxRating.filterText(),
                    keyboardType = KeyboardType.Decimal,
                    sanitizeInput = ::decimalInput,
                    onStartChange = { value -> draft = draft.copy(minRating = value.ratingFilterValue()) },
                    onEndChange = { value -> draft = draft.copy(maxRating = value.ratingFilterValue()) },
                    onSideExit = moveFocusToActions,
                )

                RangeAccordionSection(
                    id = "episodes",
                    title = uiText(UiStringKey.Episodes),
                    summary = rangeSummary(draft.episodeFrom, draft.episodeTo),
                    expandedSection = expandedSection,
                    onExpandedChange = { expandedSection = it },
                    startLabel = uiText(UiStringKey.From),
                    endLabel = uiText(UiStringKey.To),
                    startText = draft.episodeFrom?.toString().orEmpty(),
                    endText = draft.episodeTo?.toString().orEmpty(),
                    keyboardType = KeyboardType.Number,
                    sanitizeInput = ::integerInput,
                    onStartChange = { value -> draft = draft.copy(episodeFrom = value.episodeFilterValue()) },
                    onEndChange = { value -> draft = draft.copy(episodeTo = value.episodeFilterValue()) },
                    onSideExit = moveFocusToActions,
                )

                if (isAuthorized) {
                    FilterAccordionSection(
                        id = "user_marks",
                        title = uiText(UiStringKey.Marks),
                        options = userMarkFilterOptions,
                        selected = draft.userMarks,
                        expandedSection = expandedSection,
                        onExpandedChange = { expandedSection = it },
                        onToggle = { value -> draft = draft.copy(userMarks = draft.userMarks.toggle(value)) },
                        onSideExit = moveFocusToActions,
                    )
                    FilterAccordionSection(
                        id = "excluded_user_marks",
                        title = uiText(UiStringKey.ExcludeMarks),
                        options = userMarkFilterOptions,
                        selected = draft.excludedUserMarks,
                        expandedSection = expandedSection,
                        onExpandedChange = { expandedSection = it },
                        onToggle = { value -> draft = draft.copy(excludedUserMarks = draft.excludedUserMarks.toggle(value)) },
                        onSideExit = moveFocusToActions,
                    )
                }

                if (forcedOfflineMode) {
                    OfflineFilterNotice()
                } else {
                    SettingsSwitchRow(
                        title = uiText(UiStringKey.AvailableOffline),
                        checked = draft.offlineOnly,
                        onCheckedChange = { checked -> draft = draft.copy(offlineOnly = checked) },
                    )
                }
                }

                if (!forcedOfflineMode && catalogState is LoadState.Error) {
                    InlineErrorMessage(
                        message = catalogState.message,
                        modifier = Modifier.padding(top = YummySpacing.xs),
                    )
                }
            }
        },
        confirmButton = {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (maxWidth < 300.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DialogActionButton(
                                text = uiText(UiStringKey.Reset),
                                modifier = Modifier.weight(1f),
                                compact = true,
                                onClick = { resetAndDismiss() },
                            )
                            DialogActionButton(
                                text = uiText(UiStringKey.Cancel),
                                modifier = Modifier.weight(1f),
                                compact = true,
                                onClick = onDismiss,
                            )
                        }
                        DialogActionButton(
                            text = uiText(UiStringKey.Apply),
                            primary = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(applyFocusRequester),
                            onClick = { applyAndDismiss() },
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DialogActionButton(
                            text = uiText(UiStringKey.Reset),
                            modifier = Modifier.weight(1f),
                            compact = true,
                            onClick = { resetAndDismiss() },
                        )
                        DialogActionButton(
                            text = uiText(UiStringKey.Cancel),
                            modifier = Modifier.weight(1f),
                            compact = true,
                            onClick = onDismiss,
                        )
                        DialogActionButton(
                            text = uiText(UiStringKey.Apply),
                            primary = true,
                            compact = true,
                            modifier = Modifier
                                .weight(1.25f)
                                .focusRequester(applyFocusRequester),
                            onClick = { applyAndDismiss() },
                        )
                    }
                }
            }
        },
    )
}
