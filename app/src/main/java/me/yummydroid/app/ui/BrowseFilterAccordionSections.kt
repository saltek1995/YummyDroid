package me.yummydroid.app.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.yummydroid.app.data.AnimeSort
import me.yummydroid.app.data.FilterOption

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
