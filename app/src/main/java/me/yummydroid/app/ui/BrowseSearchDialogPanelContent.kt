package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import me.yummydroid.app.ui.theme.YummySpacing

@Composable
internal fun SearchDialogPanelContent(
    query: String,
    visibleHistory: List<String>,
    historyFocusRequesters: List<FocusRequester>,
    focusState: SearchDialogFocusState,
    actions: SearchDialogActions,
    onQueryChange: (String) -> Unit,
    onHistorySelected: (String) -> Unit,
    onLaunchVoiceSearch: () -> Unit,
) {
    val firstHistoryFocusRequester = historyFocusRequesters.firstOrNull() ?: FocusRequester.Default
    Column(
        modifier = Modifier
            .padding(YummySpacing.sm)
            .searchDialogPanelNavigation(focusState, actions),
        verticalArrangement = Arrangement.spacedBy(YummySpacing.xs),
    ) {
        SearchDialogInputRow(
            query = query,
            focusState = focusState,
            firstHistoryFocusRequester = firstHistoryFocusRequester,
            actions = actions,
            onQueryChange = onQueryChange,
            onLaunchVoiceSearch = onLaunchVoiceSearch,
        )
        if (visibleHistory.isNotEmpty()) {
            SearchHistoryDropdown(
                history = visibleHistory,
                focusRequesters = historyFocusRequesters,
                inputFocusRequester = focusState.inputFocusRequester,
                onSelect = onHistorySelected,
                onFocusedIndexChange = focusState::setHistoryFocused,
                onFocusInput = actions::focusInput,
                onExitDown = actions::exitDownFromSearch,
            )
        }
    }
}
