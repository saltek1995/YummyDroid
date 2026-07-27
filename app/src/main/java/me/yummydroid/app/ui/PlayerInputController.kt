package me.yummydroid.app.ui

import me.yummydroid.app.InputActionEvent

internal class PlayerInputController(
    private val controlsVisible: () -> Boolean,
    private val hideControls: () -> Boolean,
    private val handle: (InputActionEvent) -> Boolean,
) {
    fun hasVisibleControls(): Boolean = controlsVisible()

    fun hideVisibleControls(): Boolean = hideControls()

    fun handleInput(event: InputActionEvent): Boolean = handle(event)
}
