package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import me.yummydroid.app.InputAction

@Composable
internal fun SearchDialogRemoteInputEffect(
    request: Long,
    action: InputAction?,
    focusedHistoryIndex: Int,
    visibleHistory: List<String>,
    inputFocused: Boolean,
    micFocused: Boolean,
    historyFocusRequesters: List<FocusRequester>,
    onFocusInput: () -> Unit,
    onFocusHistoryOrExit: () -> Boolean,
    onExitDown: () -> Unit,
    onHideKeyboard: () -> Unit,
    onFocusMic: () -> Unit,
    onHistorySelected: (String) -> Unit,
    onLaunchVoiceSearch: () -> Unit,
    onSubmitCurrentQuery: () -> Unit,
) {
    LaunchedEffect(request) {
        if (request <= 0L) return@LaunchedEffect
        when (
            val command = resolveSearchRemoteInputCommand(
                action = action,
                focusedHistoryIndex = focusedHistoryIndex,
                visibleHistoryCount = visibleHistory.size,
                historyFocusRequesterCount = historyFocusRequesters.size,
                inputFocused = inputFocused,
                micFocused = micFocused,
            )
        ) {
            SearchRemoteInputCommand.None -> Unit
            SearchRemoteInputCommand.FocusInput -> onFocusInput()
            SearchRemoteInputCommand.FocusHistoryOrExit -> onFocusHistoryOrExit()
            SearchRemoteInputCommand.ExitDown -> onExitDown()
            SearchRemoteInputCommand.FocusMic -> onFocusMic()
            SearchRemoteInputCommand.LaunchVoiceSearch -> onLaunchVoiceSearch()
            SearchRemoteInputCommand.SubmitCurrentQuery -> {
                onSubmitCurrentQuery()
                onHideKeyboard()
            }
            is SearchRemoteInputCommand.FocusHistory -> {
                if (command.hideKeyboard) onHideKeyboard()
                historyFocusRequesters.getOrNull(command.index)?.requestFocusSafely()
            }
            is SearchRemoteInputCommand.SelectHistory -> {
                onHistorySelected(visibleHistory[command.index])
                onFocusInput()
            }
        }
    }
}
