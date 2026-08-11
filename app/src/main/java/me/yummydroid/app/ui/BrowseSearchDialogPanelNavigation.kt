package me.yummydroid.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

internal fun Modifier.searchDialogPanelNavigation(
    focusState: SearchDialogFocusState,
    actions: SearchDialogActions,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when {
        focusState.micFocused && event.key == Key.DirectionRight -> {
            actions.focusInput()
            true
        }
        focusState.micFocused && event.key == Key.DirectionDown -> actions.focusHistoryOrExit()
        focusState.inputFocused && event.key == Key.DirectionLeft -> {
            focusState.micFocusRequester.requestFocusSafely()
            true
        }
        focusState.inputFocused && event.key == Key.DirectionDown -> actions.focusHistoryOrExit()
        else -> false
    }
}

internal fun Modifier.searchDialogMicNavigation(
    focusState: SearchDialogFocusState,
    actions: SearchDialogActions,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionRight -> {
            actions.focusInput()
            true
        }
        Key.DirectionDown -> actions.focusHistoryOrExit()
        else -> false
    }
}

internal fun Modifier.searchDialogInputNavigation(
    focusState: SearchDialogFocusState,
    actions: SearchDialogActions,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionLeft -> {
            focusState.micFocusRequester.requestFocusSafely()
            true
        }
        Key.DirectionDown -> actions.focusHistoryOrExit()
        else -> false
    }
}
