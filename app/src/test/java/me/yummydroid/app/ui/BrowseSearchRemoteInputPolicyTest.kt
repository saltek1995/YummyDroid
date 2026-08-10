package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.InputAction

class BrowseSearchRemoteInputPolicyTest {
    @Test
    fun upMovesThroughHistoryThenReturnsToInput() {
        assertEquals(
            SearchRemoteInputCommand.FocusHistory(index = 1, hideKeyboard = false),
            command(InputAction.Up, focusedHistoryIndex = 2),
        )
        assertEquals(
            SearchRemoteInputCommand.FocusInput,
            command(InputAction.Up, focusedHistoryIndex = 0),
        )
        assertEquals(
            SearchRemoteInputCommand.FocusInput,
            command(InputAction.Up, focusedHistoryIndex = -1),
        )
        assertEquals(
            SearchRemoteInputCommand.None,
            command(InputAction.Up, focusedHistoryIndex = -1, inputFocused = true),
        )
    }

    @Test
    fun downMovesThroughHistoryAndExitsAfterLastItem() {
        assertEquals(
            SearchRemoteInputCommand.FocusHistoryOrExit,
            command(InputAction.Down, focusedHistoryIndex = -1),
        )
        assertEquals(
            SearchRemoteInputCommand.FocusHistory(index = 2, hideKeyboard = true),
            command(InputAction.Down, focusedHistoryIndex = 1),
        )
        assertEquals(
            SearchRemoteInputCommand.ExitDown,
            command(InputAction.Down, focusedHistoryIndex = 2),
        )
    }

    @Test
    fun horizontalActionsMoveOnlyBetweenInputAndMic() {
        assertEquals(
            SearchRemoteInputCommand.FocusMic,
            command(InputAction.Left, inputFocused = true),
        )
        assertEquals(
            SearchRemoteInputCommand.FocusMic,
            command(InputAction.Left),
        )
        assertEquals(
            SearchRemoteInputCommand.None,
            command(InputAction.Left, micFocused = true),
        )
        assertEquals(
            SearchRemoteInputCommand.FocusInput,
            command(InputAction.Right, micFocused = true),
        )
    }

    @Test
    fun confirmPrioritizesHistoryThenMicThenInput() {
        assertEquals(
            SearchRemoteInputCommand.SelectHistory(1),
            command(InputAction.Confirm, focusedHistoryIndex = 1, inputFocused = true, micFocused = true),
        )
        assertEquals(
            SearchRemoteInputCommand.LaunchVoiceSearch,
            command(InputAction.Confirm, micFocused = true),
        )
        assertEquals(
            SearchRemoteInputCommand.SubmitCurrentQuery,
            command(InputAction.Confirm, inputFocused = true),
        )
        assertEquals(
            SearchRemoteInputCommand.FocusInput,
            command(InputAction.Confirm),
        )
    }

    @Test
    fun playbackAndBackActionsAreIgnored() {
        listOf(
            InputAction.Play,
            InputAction.Pause,
            InputAction.PlayPause,
            InputAction.PreviousEpisode,
            InputAction.NextEpisode,
            InputAction.Back,
            null,
        ).forEach { action ->
            assertEquals(SearchRemoteInputCommand.None, command(action))
        }
    }

    private fun command(
        action: InputAction?,
        focusedHistoryIndex: Int = -1,
        inputFocused: Boolean = false,
        micFocused: Boolean = false,
    ): SearchRemoteInputCommand {
        return resolveSearchRemoteInputCommand(
            action = action,
            focusedHistoryIndex = focusedHistoryIndex,
            visibleHistoryCount = 3,
            historyFocusRequesterCount = 3,
            inputFocused = inputFocused,
            micFocused = micFocused,
        )
    }
}
