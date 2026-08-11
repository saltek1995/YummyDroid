package me.yummydroid.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import me.yummydroid.app.AuthUiState

@Composable
internal fun BrowseTopBarActions(
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    auth: AuthUiState,
    activeFilters: Int,
    activeSearch: Boolean,
    modifier: Modifier = Modifier,
    activeFiltersPanel: Boolean = false,
    activeSettings: Boolean = false,
    activeDownloads: Boolean = false,
    activeProfile: Boolean = false,
    activeDownloadCount: Int,
    searchEnabled: Boolean = true,
    filtersEnabled: Boolean = true,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
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
            auth = auth,
            activeFilters = activeFilters,
            activeSearch = activeSearch,
            activeFiltersPanel = activeFiltersPanel,
            activeSettings = activeSettings,
            activeDownloads = activeDownloads,
            activeProfile = activeProfile,
            activeDownloadCount = activeDownloadCount,
            searchEnabled = searchEnabled,
            filtersEnabled = filtersEnabled,
        ),
        callbacks = BrowseActionCallbacks(
            onOpenSearch = onOpenSearch,
            onOpenFilters = onOpenFilters,
            onOpenSettings = onOpenSettings,
            onOpenDownloads = onOpenDownloads,
            onOpenLogin = onOpenLogin,
            onOpenProfile = onOpenProfile,
        ),
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

@Composable
internal fun BrowseDownloadsActionButton(
    activeDownloadCount: Int,
    activeDownloads: Boolean,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    BrowseActionIconButton(
        icon = Icons.Default.Download,
        contentDescription = uiText(UiStringKey.Downloads),
        onClick = onOpenDownloads,
        modifier = modifier,
        active = activeDownloadCount > 0 || activeDownloads,
        badgeText = activeDownloadCount.takeIf { it > 0 }?.let { count ->
            if (count > 9) "9+" else count.toString()
        },
        focusLinks = focusLinks,
    )
}

@Composable
internal fun BrowseProfileActionButton(
    auth: AuthUiState,
    activeProfile: Boolean,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    focusLinks: BrowseActionFocusLinks = BrowseActionFocusLinks(),
) {
    val unreadNotifications = auth.profile?.unreadNotifications ?: 0
    BrowseActionIconButton(
        icon = Icons.Default.AccountCircle,
        contentDescription = if (auth.profile == null) uiText(UiStringKey.SignIn) else uiText(UiStringKey.Profile),
        onClick = if (auth.profile == null) onOpenLogin else onOpenProfile,
        modifier = modifier,
        active = activeProfile,
        badgeText = unreadNotifications.notificationBadgeText(),
        focusLinks = focusLinks,
    )
}
