package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import me.yummydroid.app.InputAction

@Composable
internal fun SearchDialogInteractionContent(
    query: String,
    isTelevision: Boolean,
    remoteInputAction: InputAction?,
    remoteInputActionRequest: Long,
    visibleHistory: List<String>,
    historyFocusRequesters: List<FocusRequester>,
    focusState: SearchDialogFocusState,
    actions: SearchDialogActions,
    onQueryChange: (String) -> Unit,
    onHistorySelected: (String) -> Unit,
    onLaunchVoiceSearch: () -> Unit,
) {
    SearchDialogRemoteInputEffect(
        request = remoteInputActionRequest,
        action = remoteInputAction,
        focusedHistoryIndex = focusState.focusedHistoryIndex,
        visibleHistory = visibleHistory,
        inputFocused = focusState.inputFocused,
        micFocused = focusState.micFocused,
        historyFocusRequesters = historyFocusRequesters,
        onFocusInput = actions::focusInput,
        onFocusHistoryOrExit = actions::focusHistoryOrExit,
        onExitDown = actions::exitDownFromSearch,
        onHideKeyboard = actions::hideKeyboard,
        onFocusMic = { focusState.micFocusRequester.requestFocusSafely() },
        onHistorySelected = onHistorySelected,
        onLaunchVoiceSearch = onLaunchVoiceSearch,
        onSubmitCurrentQuery = actions::submitCurrentQuery,
    )
    SearchDialogPanel(
        query = query,
        isTelevision = isTelevision,
        visibleHistory = visibleHistory,
        historyFocusRequesters = historyFocusRequesters,
        focusState = focusState,
        actions = actions,
        onQueryChange = onQueryChange,
        onHistorySelected = { historyQuery ->
            onHistorySelected(historyQuery)
            actions.focusInput()
        },
        onLaunchVoiceSearch = onLaunchVoiceSearch,
    )
}
