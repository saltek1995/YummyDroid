package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent

class DpadFocusPolicyTest {
    @Test
    fun keyboardDpadInputUsesExistingPlatformFocusBeforeRecovery() {
        val event = InputActionEvent(InputAction.Down)

        assertFalse(
            event.shouldInitializeFocusBeforePlatformDispatch(
                layerHadPointerInput = false,
                touchInputMode = false,
            ),
        )
    }

    @Test
    fun firstDpadInputAfterPointerInitializesFocus() {
        val event = InputActionEvent(InputAction.Down, followsPointerInput = true)

        assertTrue(
            event.shouldInitializeFocusBeforePlatformDispatch(
                layerHadPointerInput = false,
                touchInputMode = false,
            ),
        )
    }

    @Test
    fun playbackKeysNeverInitializeContentFocus() {
        val event = InputActionEvent(InputAction.PlayPause, followsPointerInput = true)

        assertFalse(
            event.shouldInitializeFocusBeforePlatformDispatch(
                layerHadPointerInput = true,
                touchInputMode = true,
            ),
        )
    }
}
