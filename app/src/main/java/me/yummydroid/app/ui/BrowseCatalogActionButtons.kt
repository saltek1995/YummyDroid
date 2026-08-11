package me.yummydroid.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun BrowseSettingsActionButton(
    activeSettings: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    BrowseActionIconButton(
        icon = Icons.Default.Settings,
        contentDescription = uiText(UiStringKey.Settings),
        onClick = onOpenSettings,
        modifier = modifier,
        active = activeSettings,
        focusLinks = focusLinks,
    )
}

@Composable
internal fun BrowseSearchActionButton(
    activeSearch: Boolean,
    enabled: Boolean,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    BrowseActionIconButton(
        icon = Icons.Default.Search,
        contentDescription = uiText(UiStringKey.Search),
        onClick = onOpenSearch,
        modifier = modifier,
        active = activeSearch,
        enabled = enabled,
        focusLinks = focusLinks,
    )
}

@Composable
internal fun BrowseFiltersActionButton(
    activeFilters: Int,
    activeFiltersPanel: Boolean,
    enabled: Boolean,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    BrowseActionIconButton(
        icon = Icons.Default.FilterList,
        contentDescription = uiText(UiStringKey.Filters),
        onClick = onOpenFilters,
        modifier = modifier,
        active = activeFilters > 0 || activeFiltersPanel,
        enabled = enabled,
        badgeText = activeFilters.takeIf { it > 0 }?.coerceAtMost(9)?.toString(),
        focusLinks = focusLinks,
    )
}
