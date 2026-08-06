package me.yummydroid.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import me.yummydroid.app.ui.components.clearFocusAfterTouch

internal fun Modifier.animeCardTouchHold(
    enabled: Boolean,
    onTouchHeldChange: (Boolean) -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(Unit) {
        try {
            awaitEachGesture {
                val down = awaitPointerEvent(PointerEventPass.Initial)
                    .changes
                    .firstOrNull { it.pressed }
                    ?: return@awaitEachGesture
                onTouchHeldChange(true)
                var pointerId = down.id
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val tracked = event.changes.firstOrNull { it.id == pointerId }
                    when {
                        tracked == null -> {
                            val replacement = event.changes.firstOrNull { it.pressed }
                            if (replacement == null) {
                                onTouchHeldChange(false)
                                break
                            }
                            pointerId = replacement.id
                        }
                        tracked.changedToUpIgnoreConsumed() || !tracked.pressed -> {
                            onTouchHeldChange(false)
                            break
                        }
                    }
                }
            }
        } finally {
            onTouchHeldChange(false)
        }
    }.clearFocusAfterTouch()
}
