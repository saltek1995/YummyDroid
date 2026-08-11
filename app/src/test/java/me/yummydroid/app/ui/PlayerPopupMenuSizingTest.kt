package me.yummydroid.app.ui

import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerPopupMenuSizingTest {
    @Test
    fun contentWidthMatchesLongestLabelAndStructuralInsets() {
        assertEquals(
            155,
            playerMenuContentWidthPx(
                longestLabelWidth = 100,
                listHorizontalPadding = 16,
                rowHorizontalPadding = 24,
                markerWidthWithMargin = 15,
            ),
        )
    }

    @Test
    fun keyDownEventsMapToPopupActions() {
        assertEquals(
            PlayerPopupKeyAction.Click,
            playerPopupKeyAction(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_DOWN),
        )
        assertEquals(
            PlayerPopupKeyAction.Click,
            playerPopupKeyAction(KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_DOWN),
        )
        assertEquals(
            PlayerPopupKeyAction.Click,
            playerPopupKeyAction(KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.ACTION_DOWN),
        )
        assertEquals(
            PlayerPopupKeyAction.Dismiss,
            playerPopupKeyAction(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN),
        )
        assertEquals(
            PlayerPopupKeyAction.Previous,
            playerPopupKeyAction(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.ACTION_DOWN),
        )
        assertEquals(
            PlayerPopupKeyAction.Next,
            playerPopupKeyAction(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.ACTION_DOWN),
        )
    }

    @Test
    fun unsupportedOrReleasedKeysAreIgnored() {
        assertEquals(
            PlayerPopupKeyAction.Ignore,
            playerPopupKeyAction(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.ACTION_DOWN),
        )
        assertEquals(
            PlayerPopupKeyAction.Ignore,
            playerPopupKeyAction(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.ACTION_UP),
        )
    }
}
