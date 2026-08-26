package me.yummydroid.app.ui

import android.view.KeyEvent
import android.widget.AdapterView
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

    @Test
    fun popupInitialSelectionFallsBackToFirstItem() {
        assertEquals(2, playerPopupInitialSelectionIndex(itemCount = 5, checkedIndex = 2))
        assertEquals(0, playerPopupInitialSelectionIndex(itemCount = 5, checkedIndex = -1))
        assertEquals(0, playerPopupInitialSelectionIndex(itemCount = 5, checkedIndex = 8))
        assertEquals(AdapterView.INVALID_POSITION, playerPopupInitialSelectionIndex(itemCount = 0, checkedIndex = 0))
    }

    @Test
    fun popupPlacementClampsInsidePlayerWidthAndPrefersAboveAnchor() {
        assertEquals(
            PlayerPopupPlacement(x = 36, y = 200),
            playerPopupPlacement(
                playerWidth = 200,
                playerHeight = 400,
                anchorLeft = 180,
                anchorTop = 300,
                anchorWidth = 40,
                anchorHeight = 48,
                popupWidth = 150,
                popupHeight = 90,
                margin = 14,
                gap = 10,
            ),
        )
    }

    @Test
    fun popupPlacementFallsBelowAnchorWhenAboveDoesNotFit() {
        assertEquals(
            PlayerPopupPlacement(x = 45, y = 88),
            playerPopupPlacement(
                playerWidth = 240,
                playerHeight = 400,
                anchorLeft = 70,
                anchorTop = 30,
                anchorWidth = 50,
                anchorHeight = 48,
                popupWidth = 100,
                popupHeight = 90,
                margin = 14,
                gap = 10,
            ),
        )
    }
}
