package me.yummydroid.app

import android.view.KeyEvent

internal fun inputActionForKeyCode(keyCode: Int): InputAction? {
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> InputAction.Up
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> InputAction.Down
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT,
        KeyEvent.KEYCODE_NAVIGATE_PREVIOUS -> InputAction.Left
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT,
        KeyEvent.KEYCODE_NAVIGATE_NEXT -> InputAction.Right
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
        KeyEvent.KEYCODE_CHANNEL_DOWN,
        KeyEvent.KEYCODE_BUTTON_L1 -> InputAction.PreviousEpisode
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
        KeyEvent.KEYCODE_CHANNEL_UP,
        KeyEvent.KEYCODE_BUTTON_R1 -> InputAction.NextEpisode
        KeyEvent.KEYCODE_MEDIA_PLAY -> InputAction.Play
        KeyEvent.KEYCODE_MEDIA_PAUSE -> InputAction.Pause
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK -> InputAction.PlayPause
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_NAVIGATE_IN -> InputAction.Confirm
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_NAVIGATE_OUT,
        KeyEvent.KEYCODE_BUTTON_B -> InputAction.Back
        else -> null
    }
}
