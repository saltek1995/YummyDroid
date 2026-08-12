package me.yummydroid.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import kotlinx.coroutines.delay
import me.yummydroid.app.ui.LocalUiControlCoordinator
import me.yummydroid.app.ui.UiControlOperation

// DpadClickable
fun Modifier.dpadClickable(
    shape: Shape,
    onClick: () -> Unit,
): Modifier = dpadClickable(shape, enabled = true, onClick = onClick)

fun Modifier.dpadClickable(
    shape: Shape,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = if (enabled) {
    focusRing(shape).clickable(onClick = onClick)
} else {
    clip(shape)
}

// TouchFocus
private const val TOUCH_FOCUS_CLEAR_DELAY_MS = 80L

@Composable
private fun rememberTouchFocusClearer(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val inputModeManager = LocalInputModeManager.current
    val scope = rememberCoroutineScope()
    val uiControls = LocalUiControlCoordinator.current
    val controlOwner = remember { Any() }
    return remember(focusManager, inputModeManager, scope, uiControls, controlOwner) {
        {
            inputModeManager.requestInputMode(InputMode.Touch)
            focusManager.clearFocus(force = true)
            uiControls.launch(scope, controlOwner, UiControlOperation.InputModeLatest) {
                delay(TOUCH_FOCUS_CLEAR_DELAY_MS)
                if (inputModeManager.inputMode == InputMode.Touch) {
                    focusManager.clearFocus(force = true)
                }
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
