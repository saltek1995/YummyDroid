package me.yummydroid.app

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class InputActionMappingTest {
    @Test
    fun tvBackKeysMapToBackAction() {
        assertEquals(InputAction.Back, inputActionForKeyCode(KeyEvent.KEYCODE_BACK))
        assertEquals(InputAction.Back, inputActionForKeyCode(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(InputAction.Back, inputActionForKeyCode(KeyEvent.KEYCODE_NAVIGATE_OUT))
        assertEquals(InputAction.Back, inputActionForKeyCode(KeyEvent.KEYCODE_BUTTON_B))
    }

    @Test
    fun dpadKeysMapToNavigationActions() {
        assertEquals(InputAction.Up, inputActionForKeyCode(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(InputAction.Down, inputActionForKeyCode(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(InputAction.Left, inputActionForKeyCode(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(InputAction.Right, inputActionForKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    @Test
    fun selectKeysMapToConfirmAction() {
        assertEquals(InputAction.Confirm, inputActionForKeyCode(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(InputAction.Confirm, inputActionForKeyCode(KeyEvent.KEYCODE_ENTER))
        assertEquals(InputAction.Confirm, inputActionForKeyCode(KeyEvent.KEYCODE_BUTTON_A))
    }
}
