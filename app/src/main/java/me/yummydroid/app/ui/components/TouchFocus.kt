package me.yummydroid.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TOUCH_FOCUS_CLEAR_DELAY_MS = 80L

@Composable
private fun rememberTouchFocusClearer(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val inputModeManager = LocalInputModeManager.current
    val scope = rememberCoroutineScope()
    return remember(focusManager, inputModeManager, scope) {
        {
            inputModeManager.requestInputMode(InputMode.Touch)
            focusManager.clearFocus(force = true)
            scope.launch {
                delay(TOUCH_FOCUS_CLEAR_DELAY_MS)
                focusManager.clearFocus(force = true)
            }
        }
    }
}

fun Modifier.clearFocusAfterTouch(): Modifier = composed {
    val clearFocusAfterTouch = rememberTouchFocusClearer()
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any { change -> change.changedToDownIgnoreConsumed() }) {
                    clearFocusAfterTouch()
                }
            }
        }
    }
}
