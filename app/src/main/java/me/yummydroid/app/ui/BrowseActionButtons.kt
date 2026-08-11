package me.yummydroid.app.ui

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
