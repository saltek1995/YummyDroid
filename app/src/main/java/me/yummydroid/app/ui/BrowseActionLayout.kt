package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
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

private data class BrowseActionPresentation(
    val icon: ImageVector,
    val contentDescription: String,
    val onClick: () -> Unit,
    val active: Boolean,
    val enabled: Boolean = true,
    val badgeText: String? = null,
)

private val StackedBrowseActionRows = listOf(
    listOf(BrowseAction.Search, BrowseAction.Filters, BrowseAction.Downloads),
    listOf(BrowseAction.Settings, BrowseAction.Profile),
)

@Composable
internal fun BrowseChromeActions(
    state: BrowseHomeChromeState,
    callbacks: BrowseActionCallbacks,
    modifier: Modifier = Modifier,
    spreadActions: Boolean = false,
    stackActions: Boolean = false,
    entryFocusRequester: FocusRequester? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    consumeUpWhenNoRequester: Boolean = false,
    consumeDownWhenNoRequester: Boolean = false,
    consumeHorizontalEdgesWhenNoRequester: Boolean = false,
) {
    BrowseTopBarActionsContent(
        state = BrowseActionBarState(
            auth = state.auth,
            activeFilters = state.activeFilters,
            activeSearch = state.activeSearch,
            activeFiltersPanel = state.activeFiltersPanel,
            activeSettings = state.activeSettings,
            activeDownloads = state.activeDownloads,
            activeProfile = state.activeProfile,
            activeDownloadCount = state.activeDownloadCount,
            searchEnabled = state.catalogActionsEnabled,
            filtersEnabled = state.catalogActionsEnabled,
        ),
        callbacks = callbacks,
        layout = BrowseActionLayout(
            modifier = modifier,
            spreadActions = spreadActions,
            stackActions = stackActions,
        ),
        focus = BrowseActionFocusOptions(
            entryFocusRequester = entryFocusRequester,
            upFocusRequester = upFocusRequester,
            downFocusRequester = downFocusRequester,
            consumeUpWhenNoRequester = consumeUpWhenNoRequester,
            consumeDownWhenNoRequester = consumeDownWhenNoRequester,
            consumeHorizontalEdgesWhenNoRequester = consumeHorizontalEdgesWhenNoRequester,
        ),
    )
}

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
    val presentation = browseActionPresentation(action, state, callbacks)
    BrowseActionIconButton(
        icon = presentation.icon,
        contentDescription = presentation.contentDescription,
        onClick = presentation.onClick,
        modifier = navigation.actionModifier(action),
        active = presentation.active,
        enabled = presentation.enabled,
        badgeText = presentation.badgeText,
        focusLinks = navigation.focusLinks(action),
    )
}

@Composable
private fun browseActionPresentation(
    action: BrowseAction,
    state: BrowseActionBarState,
    callbacks: BrowseActionCallbacks,
): BrowseActionPresentation = when (action) {
    BrowseAction.Search -> BrowseActionPresentation(
        icon = Icons.Default.Search,
        contentDescription = uiText(UiStringKey.Search),
        onClick = callbacks.onOpenSearch,
        active = state.searchEnabled && state.activeSearch,
        enabled = state.searchEnabled,
    )
    BrowseAction.Filters -> BrowseActionPresentation(
        icon = Icons.Default.FilterList,
        contentDescription = uiText(UiStringKey.Filters),
        onClick = callbacks.onOpenFilters,
        active = state.filtersEnabled && (state.activeFilters > 0 || state.activeFiltersPanel),
        enabled = state.filtersEnabled,
        badgeText = state.activeFilters
            .takeIf { state.filtersEnabled && it > 0 }
            ?.coerceAtMost(9)
            ?.toString(),
    )
    BrowseAction.Downloads -> BrowseActionPresentation(
        icon = Icons.Default.Download,
        contentDescription = uiText(UiStringKey.Downloads),
        onClick = callbacks.onOpenDownloads,
        active = state.activeDownloadCount > 0 || state.activeDownloads,
        badgeText = state.activeDownloadCount.takeIf { it > 0 }?.let { count ->
            if (count > 9) "9+" else count.toString()
        },
    )
    BrowseAction.Settings -> BrowseActionPresentation(
        icon = Icons.Default.Settings,
        contentDescription = uiText(UiStringKey.Settings),
        onClick = callbacks.onOpenSettings,
        active = state.activeSettings,
    )
    BrowseAction.Profile -> BrowseActionPresentation(
        icon = Icons.Default.AccountCircle,
        contentDescription = if (state.auth.profile == null) {
            uiText(UiStringKey.SignIn)
        } else {
            uiText(UiStringKey.Profile)
        },
        onClick = if (state.auth.profile == null) callbacks.onOpenLogin else callbacks.onOpenProfile,
        active = state.activeProfile,
        badgeText = state.auth.profile?.unreadNotifications?.notificationBadgeText(),
    )
}
