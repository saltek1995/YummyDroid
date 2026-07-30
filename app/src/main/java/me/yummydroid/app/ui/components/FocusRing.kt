package me.yummydroid.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.yummydroid.app.ui.theme.YummyColors

private const val TOUCH_FOCUS_CLEAR_DELAY_MS = 80L
private val FocusFillColor = YummyColors.focusOverlay

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.focusRing(shape: Shape): Modifier = composed {
    val inputModeManager = LocalInputModeManager.current
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var focused by remember { mutableStateOf(false) }
    val focusVisible = focused && inputModeManager.inputMode != InputMode.Touch
    val focusProgress by animateFloatAsState(if (focusVisible) 1f else 0f, label = "focus-fill")
    val focusColor = FocusFillColor.copy(alpha = 0.22f * focusProgress)

    clearFocusAfterTouch()
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusChanged { focusState ->
            focused = focusState.isFocused
            if (focusState.isFocused && inputModeManager.inputMode != InputMode.Touch) {
                scope.launch {
                    withFrameNanos { }
                    bringIntoViewRequester.bringIntoView()
                }
            }
        }
        .clip(shape)
        .drawWithContent {
            drawContent()
            if (focusProgress > 0f) {
                drawRect(focusColor)
            }
        }
}

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
