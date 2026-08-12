package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.AnimeSort
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.ageRatingFilterOptions
import me.yummydroid.app.data.seasonFilterOptions
import me.yummydroid.app.data.statusFilterOptions
import me.yummydroid.app.data.translateFilterOptions
import me.yummydroid.app.data.userMarkFilterOptions
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceColor

// BrowseFilterAccordionComponents
@Composable
internal fun AccordionHeader(
    title: String,
    modifier: Modifier = Modifier,
    summary: String = "",
    expanded: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    centerTitle: Boolean = false,
) {
    val shape = RoundedCornerShape(8.dp)
    val contentColor = yummyActionContentColor(selected = active)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .background(yummyActionSurfaceColor(selected = active), shape)
            .border(yummyActionBorder(selected = active), shape)
            .dpadClickable(shape, onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        AccordionHeaderText(title, summary, active, centerTitle)
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun BoxScope.AccordionHeaderText(
    title: String,
    summary: String,
    active: Boolean,
    centered: Boolean,
) {
    val alignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start
    val textPadding = if (centered) Modifier.padding(horizontal = 34.dp) else Modifier.padding(end = 34.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.Center)
            .then(textPadding),
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = yummyActionContentColor(selected = active),
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
        )
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = accordionSummaryColor(active),
                textAlign = textAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun accordionSummaryColor(active: Boolean) = if (active) {
    YummyColors.focus.copy(alpha = 0.82f)
} else {
    MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun SelectableFilterRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    onSideExit: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .onHorizontalFilterExit(onSideExit)
            .dpadClickable(RoundedCornerShape(8.dp), onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(checked = selected, onCheckedChange = { onClick() })
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// BrowseFilterAccordionSections
@Composable
internal fun SortAccordionSection(
    expanded: Boolean,
    selected: AnimeSort,
    onToggleExpanded: () -> Unit,
    onSelected: (AnimeSort) -> Unit,
    onSideExit: () -> Unit,
) {
    AccordionHeader(
        title = uiText(UiStringKey.Sorting),
        summary = selected.localizedTitle(),
        expanded = expanded,
        active = selected != AnimeSort.Rating,
        onClick = onToggleExpanded,
    )
    if (!expanded) return

    FilterOptionsColumn {
        AnimeSort.entries.forEach { sort ->
            SelectableFilterRow(
                title = sort.localizedTitle(),
                selected = selected == sort,
                onClick = { onSelected(sort) },
                onSideExit = onSideExit,
            )
        }
    }
}

@Composable
internal fun FilterAccordionSection(
    id: String,
    title: String,
    options: List<FilterOption>,
    selected: Set<String>,
    expandedSection: String,
    onExpandedChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onSideExit: () -> Unit,
    searchable: Boolean = false,
) {
    if (options.isEmpty()) return

    val uiLocale = LocalUiLanguage.current.uiLocale()
    val sortedOptions = remember(options, uiLocale) { options.sortedByTitle(uiLocale) }
    val expanded = expandedSection == id
    var query by remember(id, expanded) { mutableStateOf("") }
    val visibleOptions = remember(sortedOptions, query, searchable) {
        visibleFilterOptions(sortedOptions, query, searchable)
    }
    AccordionHeader(
        title = title,
        summary = selectedFilterSummary(sortedOptions, selected),
        expanded = expanded,
        active = selected.isNotEmpty(),
        onClick = { onExpandedChange(if (expanded) "" else id) },
    )
    if (!expanded) return

    FilterOptionsColumn {
        if (searchable) FilterSearchField(query, { query = it }, onSideExit)
        visibleOptions.forEach { option ->
            SelectableFilterRow(
                title = option.localizedTitle(),
                selected = option.value in selected,
                onClick = { onToggle(option.value) },
                onSideExit = onSideExit,
            )
        }
    }
}

@Composable
private fun FilterOptionsColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
    }
}

@Composable
private fun FilterSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSideExit: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(uiText(UiStringKey.Search)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .onHorizontalFilterExit(onSideExit),
    )
}

private fun visibleFilterOptions(
    options: List<FilterOption>,
    query: String,
    searchable: Boolean,
): List<FilterOption> {
    if (!searchable || query.isBlank()) return options
    val trimmedQuery = query.trim()
    return options.filter { option ->
        option.title.contains(trimmedQuery, ignoreCase = true) ||
            option.value.contains(trimmedQuery, ignoreCase = true)
    }
}

// BrowseFilterInput
internal fun Number?.filterText(): String = when (this) {
    null -> ""
    is Double -> if (this % 1.0 == 0.0) toInt().toString() else toString()
    else -> toString()
}

internal fun integerInput(value: String): String = value.filter(Char::isDigit).take(5)

internal fun decimalInput(value: String): String {
    val builder = StringBuilder()
    var dotSeen = false
    value.replace(',', '.').forEach { char ->
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

internal fun String.yearFilterValue(): Int? =
    toIntOrNull()?.takeIf { value -> value in 1900..2100 }

internal fun String.episodeFilterValue(): Int? =
    toIntOrNull()?.takeIf { value -> value in 0..10000 }

internal fun String.ratingFilterValue(): Double? =
    toDoubleOrNull()?.takeIf { value -> value in 0.0..10.0 }

// BrowseFilterNavigation
internal fun Modifier.onHorizontalFilterExit(onExit: (() -> Unit)?): Modifier {
    if (onExit == null) return this
    return onPreviewKeyEvent { event ->
        if (!event.isHorizontalFilterExit()) return@onPreviewKeyEvent false
        onExit()
        true
    }
}

internal fun KeyEvent.isHorizontalFilterExit(): Boolean {
    return type == KeyEventType.KeyDown && (key == Key.DirectionLeft || key == Key.DirectionRight)
}

internal fun Modifier.horizontalEdgeFocusHints(
    index: Int,
    total: Int,
    leftExit: FocusRequester? = null,
    rightExit: FocusRequester? = null,
): Modifier {
    if (total <= 0 || index < 0) return this
    val isFirst = index == 0
    val isLast = index >= total - 1
    return focusProperties {
        if (isFirst && leftExit != null) left = leftExit
        if (isLast && rightExit != null) right = rightExit
    }
}

@Composable
internal fun rangeSummary(from: Number?, to: Number?): String {
    val start = from.filterText()
    val end = to.filterText()
    return when {
        start.isBlank() && end.isBlank() -> uiText(UiStringKey.All)
        start.isNotBlank() && end.isNotBlank() -> "$start - $end"
        start.isNotBlank() -> "${uiText(UiStringKey.FromDba126)} $start"
        else -> "${uiText(UiStringKey.To7618b0)} $end"
    }
}

@Composable
internal fun selectedFilterSummary(
    options: List<FilterOption>,
    selected: Set<String>,
): String {
    if (selected.isEmpty()) return uiText(UiStringKey.All)
    val titles = options
        .filter { it.value in selected }
        .map { it.localizedTitle() }
    return when {
        titles.isEmpty() -> "${selected.size} ${uiText(UiStringKey.Selected)}"
        titles.size <= 2 -> titles.joinToString(", ")
        else -> titles.take(2).joinToString(", ") + " +${titles.size - 2}"
    }
}

// BrowseFiltersDialog
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
        mutableStateOf(filters.normalizedForFiltersDialog(isAuthorized, forcedOfflineMode))
    }
    var expandedSection by remember { mutableStateOf("") }
    var advancedVisible by remember(filters) { mutableStateOf(false) }
    val catalog = rememberFiltersDialogCatalog(catalogState, offlineEntries, forcedOfflineMode)
    val options = rememberFiltersDialogOptions(catalog, draft)
    val hiddenActiveCount = remember(draft, isAuthorized) { draft.advancedFilterCount(isAuthorized) }
    val applyFocusRequester = remember { FocusRequester() }
    val moveFocusToActions: () -> Unit = remember {
        { applyFocusRequester.requestFocusSafely() }
    }
    val contentState = FiltersDialogContentState(
        filters = draft,
        catalog = catalog,
        options = options,
        expandedSection = expandedSection,
        advancedVisible = advancedVisible,
        hiddenActiveCount = hiddenActiveCount,
        isAuthorized = isAuthorized,
        forcedOfflineMode = forcedOfflineMode,
        errorMessage = (catalogState as? LoadState.Error)?.message,
    )
    val callbacks = FiltersDialogContentCallbacks(
        onFiltersChange = { draft = it },
        onExpandedSectionChange = { expandedSection = it },
        onShowAdvanced = { advancedVisible = true },
        onSideExit = moveFocusToActions,
    )

    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.Filters)) },
        text = { FiltersDialogContent(contentState, callbacks) },
        confirmButton = {
            FiltersDialogActions(
                applyFocusRequester = applyFocusRequester,
                onReset = {
                    draft = BrowseFilters().normalizedForFiltersDialog(isAuthorized, forcedOfflineMode)
                    onReset()
                    onDismiss()
                },
                onCancel = onDismiss,
                onApply = {
                    onApply(draft.normalizedForFiltersDialog(isAuthorized, forcedOfflineMode))
                    onDismiss()
                },
            )
        },
    )
}

// BrowseFiltersDialogAccount
@Composable
internal fun AdvancedAccountFilterSections(
    state: FiltersDialogContentState,
    callbacks: FiltersDialogContentCallbacks,
) {
    val filters = state.filters
    if (state.isAuthorized) {
        FiltersDialogSelectionSection(
            id = "user_marks",
            title = uiText(UiStringKey.Marks),
            options = userMarkFilterOptions,
            selected = filters.userMarks,
            state = state,
            callbacks = callbacks,
            onToggle = { filters.copy(userMarks = filters.userMarks.toggle(it)) },
        )
        FiltersDialogSelectionSection(
            id = "excluded_user_marks",
            title = uiText(UiStringKey.ExcludeMarks),
            options = userMarkFilterOptions,
            selected = filters.excludedUserMarks,
            state = state,
            callbacks = callbacks,
            onToggle = { filters.copy(excludedUserMarks = filters.excludedUserMarks.toggle(it)) },
        )
    }
    if (state.forcedOfflineMode) {
        OfflineFilterNotice()
    } else {
        SettingsSwitchRow(
            title = uiText(UiStringKey.AvailableOffline),
            checked = filters.offlineOnly,
            onCheckedChange = { callbacks.onFiltersChange(filters.copy(offlineOnly = it)) },
        )
    }
}

@Composable
private fun OfflineFilterNotice() {
    Surface(
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = uiText(UiStringKey.OfflineOnlyDownloadedAnimeAreShown),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// BrowseFiltersDialogActions
@Composable
internal fun FiltersDialogActions(
    applyFocusRequester: FocusRequester,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 300.dp) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
            ) {
                SecondaryFiltersDialogActions(onReset, onCancel)
                DialogActionButton(
                    text = uiText(UiStringKey.Apply),
                    primary = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(applyFocusRequester),
                    onClick = onApply,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryFiltersDialogAction(uiText(UiStringKey.Reset), onReset)
                SecondaryFiltersDialogAction(uiText(UiStringKey.Cancel), onCancel)
                DialogActionButton(
                    text = uiText(UiStringKey.Apply),
                    primary = true,
                    compact = true,
                    modifier = Modifier
                        .weight(1.25f)
                        .focusRequester(applyFocusRequester),
                    onClick = onApply,
                )
            }
        }
    }
}

@Composable
private fun SecondaryFiltersDialogActions(
    onReset: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryFiltersDialogAction(uiText(UiStringKey.Reset), onReset)
        SecondaryFiltersDialogAction(uiText(UiStringKey.Cancel), onCancel)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SecondaryFiltersDialogAction(
    text: String,
    onClick: () -> Unit,
) {
    DialogActionButton(
        text = text,
        modifier = Modifier.weight(1f),
        compact = true,
        onClick = onClick,
    )
}

// BrowseFiltersDialogAdvanced
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

// BrowseFiltersDialogContent
internal data class FiltersDialogContentState(
    val filters: BrowseFilters,
    val catalog: FilterCatalog,
    val options: FiltersDialogOptions,
    val expandedSection: String,
    val advancedVisible: Boolean,
    val hiddenActiveCount: Int,
    val isAuthorized: Boolean,
    val forcedOfflineMode: Boolean,
    val errorMessage: String?,
)

internal data class FiltersDialogContentCallbacks(
    val onFiltersChange: (BrowseFilters) -> Unit,
    val onExpandedSectionChange: (String) -> Unit,
    val onShowAdvanced: () -> Unit,
    val onSideExit: () -> Unit,
)

@Composable
internal fun FiltersDialogContent(
    state: FiltersDialogContentState,
    callbacks: FiltersDialogContentCallbacks,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .verticalScroll(state = rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PrimaryFiltersDialogSections(state, callbacks)
        if (!state.advancedVisible) {
            AdvancedFiltersButton(
                activeCount = state.hiddenActiveCount,
                onClick = callbacks.onShowAdvanced,
            )
        } else {
            AdvancedFiltersDialogSections(state, callbacks)
        }
        state.errorMessage?.let { message ->
            InlineErrorMessage(
                message = message,
                modifier = Modifier.padding(top = YummySpacing.xs),
            )
        }
    }
}

@Composable
private fun PrimaryFiltersDialogSections(
    state: FiltersDialogContentState,
    callbacks: FiltersDialogContentCallbacks,
) {
    val filters = state.filters
    SortAccordionSection(
        expanded = state.expandedSection == "sort",
        selected = filters.sort,
        onToggleExpanded = {
            callbacks.onExpandedSectionChange(if (state.expandedSection == "sort") "" else "sort")
        },
        onSelected = { callbacks.onFiltersChange(filters.copy(sort = it)) },
        onSideExit = callbacks.onSideExit,
    )
    FiltersDialogSelectionSection(
        id = "status",
        title = uiText(UiStringKey.Status),
        options = statusFilterOptions,
        selected = filters.statuses,
        state = state,
        callbacks = callbacks,
        onToggle = { filters.copy(statuses = filters.statuses.toggle(it)) },
    )
    FiltersDialogSelectionSection(
        id = "genres",
        title = uiText(UiStringKey.Genres),
        options = state.catalog.genres,
        selected = filters.genres,
        state = state,
        callbacks = callbacks,
        searchable = true,
        onToggle = { filters.copy(genres = filters.genres.toggle(it)) },
    )
}

@Composable
private fun AdvancedFiltersButton(activeCount: Int, onClick: () -> Unit) {
    val title = if (activeCount > 0) {
        "${uiText(UiStringKey.AdvancedMode)} • $activeCount"
    } else {
        uiText(UiStringKey.AdvancedMode)
    }
    val shape = RoundedCornerShape(8.dp)
    val selected = activeCount > 0
    val contentColor = yummyActionContentColor(selected = selected)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .then(selectedFilterModifier(selected, shape))
            .dpadClickable(shape, onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = contentColor)
    }
}

private fun selectedFilterModifier(selected: Boolean, shape: RoundedCornerShape): Modifier {
    if (!selected) return Modifier
    return Modifier
        .background(yummyActionSurfaceColor(selected = true), shape)
        .border(yummyActionBorder(selected = true), shape)
}

// BrowseFiltersDialogRanges
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

// BrowseFiltersDialogState
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

// BrowseFilterSelection
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

// BrowseRangeFilterSection
@Composable
internal fun RangeAccordionSection(
    id: String,
    title: String,
    summary: String,
    expandedSection: String,
    onExpandedChange: (String) -> Unit,
    startLabel: String,
    endLabel: String,
    startText: String,
    endText: String,
    keyboardType: KeyboardType,
    sanitizeInput: (String) -> String,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    onSideExit: () -> Unit,
) {
    val expanded = expandedSection == id
    var localStart by remember(id, startText) { mutableStateOf(startText) }
    var localEnd by remember(id, endText) { mutableStateOf(endText) }
    AccordionHeader(
        title = title,
        summary = summary,
        expanded = expanded,
        active = startText.isNotBlank() || endText.isNotBlank(),
        onClick = { onExpandedChange(if (expanded) "" else id) },
    )
    if (!expanded) return

    RangeFilterFields(
        startValue = localStart,
        endValue = localEnd,
        startLabel = startLabel,
        endLabel = endLabel,
        keyboardType = keyboardType,
        sanitizeInput = sanitizeInput,
        onStartValueChange = { sanitized ->
            localStart = sanitized
            onStartChange(sanitized)
        },
        onEndValueChange = { sanitized ->
            localEnd = sanitized
            onEndChange(sanitized)
        },
        onSideExit = onSideExit,
    )
}

@Composable
private fun RangeFilterFields(
    startValue: String,
    endValue: String,
    startLabel: String,
    endLabel: String,
    keyboardType: KeyboardType,
    sanitizeInput: (String) -> String,
    onStartValueChange: (String) -> Unit,
    onEndValueChange: (String) -> Unit,
    onSideExit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RangeFilterField(
            value = startValue,
            onValueChange = { value -> onStartValueChange(sanitizeInput(value)) },
            label = startLabel,
            keyboardType = keyboardType,
            onSideExit = onSideExit,
        )
        RangeFilterField(
            value = endValue,
            onValueChange = { value -> onEndValueChange(sanitizeInput(value)) },
            label = endLabel,
            keyboardType = keyboardType,
            onSideExit = onSideExit,
        )
    }
}

@Composable
private fun RowScope.RangeFilterField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    onSideExit: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .weight(1f)
            .padding(2.dp)
            .defaultMinSize(minWidth = 0.dp)
            .onHorizontalFilterExit(onSideExit),
    )
}
