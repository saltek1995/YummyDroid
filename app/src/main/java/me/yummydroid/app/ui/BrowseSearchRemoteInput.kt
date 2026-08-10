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
        when (action) {
            InputAction.Up -> {
                when {
                    focusedHistoryIndex > 0 -> {
                        historyFocusRequesters.getOrNull(focusedHistoryIndex - 1)?.requestFocusSafely()
                    }
                    focusedHistoryIndex == 0 -> onFocusInput()
                    !inputFocused && !micFocused -> onFocusInput()
                }
            }
            InputAction.Down -> {
                if (focusedHistoryIndex >= 0) {
                    val nextHistoryFocus = historyFocusRequesters.getOrNull(focusedHistoryIndex + 1)
                    if (nextHistoryFocus == null) {
                        onExitDown()
                    } else {
                        onHideKeyboard()
                        nextHistoryFocus.requestFocusSafely()
                    }
                } else {
                    onFocusHistoryOrExit()
                }
            }
            InputAction.Left -> {
                when {
                    inputFocused -> onFocusMic()
                    !micFocused && focusedHistoryIndex < 0 -> onFocusMic()
                }
            }
            InputAction.Right -> {
                if (micFocused) {
                    onFocusInput()
                }
            }
            InputAction.Confirm -> {
                when {
                    focusedHistoryIndex in visibleHistory.indices -> {
                        onHistorySelected(visibleHistory[focusedHistoryIndex])
                        onFocusInput()
                    }
                    micFocused -> onLaunchVoiceSearch()
                    inputFocused -> {
                        onSubmitCurrentQuery()
                        onHideKeyboard()
                    }
                    else -> onFocusInput()
                }
            }
            InputAction.Play,
            InputAction.Pause,
            InputAction.PlayPause,
            InputAction.PreviousEpisode,
            InputAction.NextEpisode,
            InputAction.Back,
            null -> Unit
        }
    }
}
