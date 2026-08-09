package me.yummydroid.app

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

internal class MainActivityInputRouter(
    private val uptimeMillis: () -> Long = SystemClock::uptimeMillis,
) {
    private var handler: ((InputActionEvent) -> Boolean)? = null
    private var lastMotionNavigationAt = 0L
    private var hadPointerInputSinceNavigation = false
    private var handledBackKeyDown = false

    fun setHandler(updatedHandler: ((InputActionEvent) -> Boolean)?) {
        handler = updatedHandler
    }

    fun interceptKeyEvent(event: KeyEvent): Boolean? {
        val action = inputActionForKeyCode(event.keyCode)
        return when {
            action == InputAction.Back -> interceptBackEvent(event)
            event.action == KeyEvent.ACTION_DOWN && action != null -> interceptActionEvent(event, action)
            else -> null
        }
    }

    fun recoverAfterSystemDispatch(event: KeyEvent, handledBySystem: Boolean): Boolean {
        if (handledBySystem || event.action != KeyEvent.ACTION_DOWN) return handledBySystem
        val action = inputActionForKeyCode(event.keyCode) ?: return false
        if (!MainActivityInputPolicy.usesDpadFocusRecovery(action)) return false
        return handler?.invoke(
            InputActionEvent(
                action = action,
                repeatCount = event.repeatCount,
                focusRecovery = true,
            ),
        ) == true
    }

    fun recordTouchEvent(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            hadPointerInputSinceNavigation = true
        }
    }

    fun consumeGenericMotionEvent(event: MotionEvent): Boolean {
        val action = motionAction(event) ?: return false
        return handler?.invoke(InputActionEvent(action)) == true
    }

    fun handleBackPressed() {
        if (!handledBackKeyDown) {
            handler?.invoke(InputActionEvent(InputAction.Back))
        }
    }

    private fun interceptBackEvent(event: KeyEvent): Boolean? {
        if (event.action == KeyEvent.ACTION_UP && handledBackKeyDown) {
            handledBackKeyDown = false
            return true
        }
        if (event.action != KeyEvent.ACTION_DOWN) return null
        val handled = dispatchAction(event, InputAction.Back)
        handledBackKeyDown = handled
        return true.takeIf { handled }
    }

    private fun interceptActionEvent(event: KeyEvent, action: InputAction): Boolean? {
        val handled = dispatchAction(event, action)
        if (MainActivityInputPolicy.resetsPointerInputNavigation(action)) {
            hadPointerInputSinceNavigation = false
        }
        return true.takeIf { handled }
    }

    private fun dispatchAction(event: KeyEvent, action: InputAction): Boolean {
        return handler?.invoke(
            InputActionEvent(
                action = action,
                repeatCount = event.repeatCount,
                followsPointerInput = hadPointerInputSinceNavigation,
            ),
        ) == true
    }

    private fun motionAction(event: MotionEvent): InputAction? {
        if (event.action != MotionEvent.ACTION_MOVE || !event.hasNavigationSource()) return null
        val now = uptimeMillis()
        if (now - lastMotionNavigationAt < MOTION_NAVIGATION_THROTTLE_MILLIS) return null
        val inputAction = MainActivityInputPolicy.actionForAxes(
            hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X),
            hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y),
            x = event.getAxisValue(MotionEvent.AXIS_X),
            y = event.getAxisValue(MotionEvent.AXIS_Y),
        )
        if (inputAction != null) {
            lastMotionNavigationAt = now
        }
        return inputAction
    }

    private fun MotionEvent.hasNavigationSource(): Boolean {
        return (source and InputDevice.SOURCE_CLASS_JOYSTICK) != 0 ||
            (source and InputDevice.SOURCE_DPAD) != 0 ||
            (source and InputDevice.SOURCE_GAMEPAD) != 0
    }
}

internal object MainActivityInputPolicy {
    fun actionForAxes(
        hatX: Float,
        hatY: Float,
        x: Float,
        y: Float,
    ): InputAction? {
        return when {
            hatX <= -HAT_AXIS_THRESHOLD || x <= -STICK_AXIS_THRESHOLD -> InputAction.Left
            hatX >= HAT_AXIS_THRESHOLD || x >= STICK_AXIS_THRESHOLD -> InputAction.Right
            hatY <= -HAT_AXIS_THRESHOLD || y <= -STICK_AXIS_THRESHOLD -> InputAction.Up
            hatY >= HAT_AXIS_THRESHOLD || y >= STICK_AXIS_THRESHOLD -> InputAction.Down
            else -> null
        }
    }

    fun resetsPointerInputNavigation(action: InputAction): Boolean {
        return action in pointerResetActions
    }

    fun usesDpadFocusRecovery(action: InputAction?): Boolean {
        return action in focusRecoveryActions
    }

    private val pointerResetActions = setOf(
        InputAction.Up,
        InputAction.Down,
        InputAction.Left,
        InputAction.Right,
        InputAction.Confirm,
    )
    private val focusRecoveryActions = setOf(
        InputAction.Up,
        InputAction.Down,
        InputAction.Left,
        InputAction.Right,
    )
}

private const val MOTION_NAVIGATION_THROTTLE_MILLIS = 180L
private const val HAT_AXIS_THRESHOLD = 0.5f
private const val STICK_AXIS_THRESHOLD = 0.65f
