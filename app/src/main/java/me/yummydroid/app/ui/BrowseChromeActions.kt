package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester

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
