package me.yummydroid.app.ui

import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent

internal fun InputActionEvent.shouldInitializeFocusBeforePlatformDispatch(
    layerHadPointerInput: Boolean,
    touchInputMode: Boolean,
): Boolean {
    if (action !in DpadFocusActions) return false
    return followsPointerInput || layerHadPointerInput || touchInputMode
}

private val DpadFocusActions = setOf(
    InputAction.Up,
    InputAction.Down,
    InputAction.Left,
    InputAction.Right,
    InputAction.Confirm,
)
