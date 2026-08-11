package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.statusFilterOptions
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor
import me.yummydroid.app.ui.theme.YummySpacing

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
