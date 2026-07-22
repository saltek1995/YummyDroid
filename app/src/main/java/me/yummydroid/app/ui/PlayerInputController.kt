package me.yummydroid.app.ui

import me.yummydroid.app.InputActionEvent

internal class PlayerInputController(
    private val controlsVisible: () -> Boolean,
    private val handle: (InputActionEvent) -> Boolean,
) {
    fun hasVisibleControls(): Boolean = controlsVisible()

    fun handleInput(event: InputActionEvent): Boolean = handle(event)
}
