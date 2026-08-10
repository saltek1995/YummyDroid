package me.yummydroid.app.ui

import me.yummydroid.app.InputAction

internal sealed interface SearchRemoteInputCommand {
    data object None : SearchRemoteInputCommand
    data object FocusInput : SearchRemoteInputCommand
    data object FocusHistoryOrExit : SearchRemoteInputCommand
    data object ExitDown : SearchRemoteInputCommand
    data object FocusMic : SearchRemoteInputCommand
    data object LaunchVoiceSearch : SearchRemoteInputCommand
    data object SubmitCurrentQuery : SearchRemoteInputCommand
    data class FocusHistory(val index: Int, val hideKeyboard: Boolean) : SearchRemoteInputCommand
    data class SelectHistory(val index: Int) : SearchRemoteInputCommand
}

internal fun resolveSearchRemoteInputCommand(
    action: InputAction?,
    focusedHistoryIndex: Int,
    visibleHistoryCount: Int,
    historyFocusRequesterCount: Int,
    inputFocused: Boolean,
    micFocused: Boolean,
): SearchRemoteInputCommand = when (action) {
    InputAction.Up -> when {
        focusedHistoryIndex > 0 -> SearchRemoteInputCommand.FocusHistory(
            index = focusedHistoryIndex - 1,
            hideKeyboard = false,
        )
        focusedHistoryIndex == 0 -> SearchRemoteInputCommand.FocusInput
        !inputFocused && !micFocused -> SearchRemoteInputCommand.FocusInput
        else -> SearchRemoteInputCommand.None
    }
    InputAction.Down -> when {
        focusedHistoryIndex < 0 -> SearchRemoteInputCommand.FocusHistoryOrExit
        focusedHistoryIndex + 1 >= historyFocusRequesterCount -> SearchRemoteInputCommand.ExitDown
        else -> SearchRemoteInputCommand.FocusHistory(
            index = focusedHistoryIndex + 1,
            hideKeyboard = true,
        )
    }
    InputAction.Left -> when {
        inputFocused -> SearchRemoteInputCommand.FocusMic
        !micFocused && focusedHistoryIndex < 0 -> SearchRemoteInputCommand.FocusMic
        else -> SearchRemoteInputCommand.None
    }
    InputAction.Right -> if (micFocused) {
        SearchRemoteInputCommand.FocusInput
    } else {
        SearchRemoteInputCommand.None
    }
    InputAction.Confirm -> when {
        focusedHistoryIndex in 0 until visibleHistoryCount -> {
            SearchRemoteInputCommand.SelectHistory(focusedHistoryIndex)
        }
        micFocused -> SearchRemoteInputCommand.LaunchVoiceSearch
        inputFocused -> SearchRemoteInputCommand.SubmitCurrentQuery
        else -> SearchRemoteInputCommand.FocusInput
    }
    InputAction.Play,
    InputAction.Pause,
    InputAction.PlayPause,
    InputAction.PreviousEpisode,
    InputAction.NextEpisode,
    InputAction.Back,
    null -> SearchRemoteInputCommand.None
}
