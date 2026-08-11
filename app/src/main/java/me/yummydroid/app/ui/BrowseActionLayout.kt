package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AuthUiState

internal data class BrowseActionBarState(
    val auth: AuthUiState,
    val activeFilters: Int,
    val activeSearch: Boolean,
    val activeFiltersPanel: Boolean,
    val activeSettings: Boolean,
    val activeDownloads: Boolean,
    val activeProfile: Boolean,
    val activeDownloadCount: Int,
    val searchEnabled: Boolean,
    val filtersEnabled: Boolean,
)

internal data class BrowseActionCallbacks(
    val onOpenSearch: () -> Unit,
    val onOpenFilters: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenDownloads: () -> Unit,
    val onOpenLogin: () -> Unit,
    val onOpenProfile: () -> Unit,
)

internal data class BrowseActionLayout(
    val modifier: Modifier,
    val spreadActions: Boolean,
    val stackActions: Boolean,
)

private val StackedBrowseActionRows = listOf(
    listOf(BrowseAction.Search, BrowseAction.Filters, BrowseAction.Downloads),
    listOf(BrowseAction.Settings, BrowseAction.Profile),
)

@Composable
internal fun BrowseTopBarActionsContent(
    state: BrowseActionBarState,
    callbacks: BrowseActionCallbacks,
    layout: BrowseActionLayout,
    focus: BrowseActionFocusOptions,
) {
    val requesters = remember { List(BrowseAction.entries.size) { FocusRequester() } }
    val focusedActionIndex = remember { mutableIntStateOf(-1) }
    val enabledActions = remember(state.searchEnabled, state.filtersEnabled) {
        BrowseAction.entries.filter { action ->
            action != BrowseAction.Search || state.searchEnabled
        }.filter { action ->
            action != BrowseAction.Filters || state.filtersEnabled
        }
    }
    val navigation = BrowseActionNavigation(requesters, enabledActions, focus, focusedActionIndex)

    if (layout.stackActions) {
        StackedBrowseActions(state, callbacks, layout.modifier, navigation)
    } else {
        InlineBrowseActions(state, callbacks, layout, navigation)
    }
}

@Composable
private fun StackedBrowseActions(
    state: BrowseActionBarState,
    callbacks: BrowseActionCallbacks,
    modifier: Modifier,
    navigation: BrowseActionNavigation,
) {
    Column(
        modifier = navigation.containerModifier(modifier),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StackedBrowseActionRows.forEach { actions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BrowseActionItems(actions, state, callbacks, navigation)
            }
        }
    }
}

@Composable
private fun InlineBrowseActions(
    state: BrowseActionBarState,
    callbacks: BrowseActionCallbacks,
    layout: BrowseActionLayout,
    navigation: BrowseActionNavigation,
) {
    Row(
        modifier = navigation.containerModifier(layout.modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (layout.spreadActions) Arrangement.SpaceBetween else Arrangement.spacedBy(10.dp),
    ) {
        BrowseActionItems(BrowseAction.entries, state, callbacks, navigation)
    }
}

@Composable
private fun BrowseActionItems(
    actions: List<BrowseAction>,
    state: BrowseActionBarState,
    callbacks: BrowseActionCallbacks,
    navigation: BrowseActionNavigation,
) {
    actions.forEach { action -> BrowseActionItem(action, state, callbacks, navigation) }
}

@Composable
private fun BrowseActionItem(
    action: BrowseAction,
    state: BrowseActionBarState,
    callbacks: BrowseActionCallbacks,
    navigation: BrowseActionNavigation,
) {
    val modifier = navigation.actionModifier(action)
    val focusLinks = navigation.focusLinks(action)
    when (action) {
        BrowseAction.Search -> BrowseSearchActionButton(
            activeSearch = state.searchEnabled && state.activeSearch,
            enabled = state.searchEnabled,
            onOpenSearch = callbacks.onOpenSearch,
            modifier = modifier,
            focusLinks = focusLinks,
        )
        BrowseAction.Filters -> BrowseFiltersActionButton(
            activeFilters = state.activeFilters.takeIf { state.filtersEnabled } ?: 0,
            activeFiltersPanel = state.filtersEnabled && state.activeFiltersPanel,
            enabled = state.filtersEnabled,
            onOpenFilters = callbacks.onOpenFilters,
            modifier = modifier,
            focusLinks = focusLinks,
        )
        BrowseAction.Downloads -> BrowseDownloadsActionButton(
            activeDownloadCount = state.activeDownloadCount,
            activeDownloads = state.activeDownloads,
            onOpenDownloads = callbacks.onOpenDownloads,
            modifier = modifier,
            focusLinks = focusLinks,
        )
        BrowseAction.Settings -> BrowseSettingsActionButton(
            activeSettings = state.activeSettings,
            onOpenSettings = callbacks.onOpenSettings,
            modifier = modifier,
            focusLinks = focusLinks,
        )
        BrowseAction.Profile -> BrowseProfileActionButton(
            auth = state.auth,
            activeProfile = state.activeProfile,
            onOpenLogin = callbacks.onOpenLogin,
            onOpenProfile = callbacks.onOpenProfile,
            modifier = modifier,
            focusLinks = focusLinks,
        )
    }
}
