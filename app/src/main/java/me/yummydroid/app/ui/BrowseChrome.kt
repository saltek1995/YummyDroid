package me.yummydroid.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

internal val BrowseSectionTabsHeight = 32.dp

internal fun Modifier.consumeUnhandledPointerInput(key: Any = Unit): Modifier {
    return pointerInput(key) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                event.changes.forEach { change ->
                    if (!change.isConsumed) {
                        change.consume()
                    }
                }
            }
        }
    }
}
