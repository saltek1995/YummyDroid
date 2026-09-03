package me.yummydroid.app.ui

import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.AdapterView
import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.InputAction

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
    fun measuredPopupLabelsAreBoundedButKeepLongestLabel() {
        val labels = (1..40).map { index -> "Option $index" } + "Longest option label"

        assertEquals(
            listOf("Option 1", "Option 2", "Option 3", "Longest option label"),
            playerPopupMeasuredLabels(labels, limit = 4),
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
            PlayerPopupKeyAction.Click,
            playerPopupKeyAction(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.ACTION_DOWN),
        )
        assertEquals(
            PlayerPopupKeyAction.Dismiss,
            playerPopupKeyAction(KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN),
        )
        assertEquals(
            PlayerPopupKeyAction.Dismiss,
            playerPopupKeyAction(KeyEvent.KEYCODE_ESCAPE, KeyEvent.ACTION_DOWN),
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
    fun inputActionsMapToPopupActions() {
        assertEquals(PlayerPopupKeyAction.Click, playerPopupInputAction(InputAction.Confirm))
        assertEquals(PlayerPopupKeyAction.Dismiss, playerPopupInputAction(InputAction.Back))
        assertEquals(PlayerPopupKeyAction.Previous, playerPopupInputAction(InputAction.Up))
        assertEquals(PlayerPopupKeyAction.Next, playerPopupInputAction(InputAction.Down))
        assertEquals(PlayerPopupKeyAction.Ignore, playerPopupInputAction(InputAction.Left))
        assertEquals(PlayerPopupKeyAction.Ignore, playerPopupInputAction(InputAction.Right))
        assertEquals(PlayerPopupKeyAction.Ignore, playerPopupInputAction(InputAction.PlayPause))
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
    fun popupSelectionMovementClampsInsideItems() {
        assertEquals(1, playerPopupMovedSelectionIndex(itemCount = 3, selectedIndex = 0, delta = 1))
        assertEquals(0, playerPopupMovedSelectionIndex(itemCount = 3, selectedIndex = 0, delta = -1))
        assertEquals(2, playerPopupMovedSelectionIndex(itemCount = 3, selectedIndex = 2, delta = 1))
        assertEquals(0, playerPopupMovedSelectionIndex(itemCount = 3, selectedIndex = AdapterView.INVALID_POSITION, delta = -1))
        assertEquals(AdapterView.INVALID_POSITION, playerPopupMovedSelectionIndex(itemCount = 0, selectedIndex = 0, delta = 1))
    }

    @Test
    fun visiblePopupSelectionDoesNotForceListScroll() {
        assertEquals(false, playerPopupSelectionRequiresScroll(1, firstVisiblePosition = 0, lastVisiblePosition = 5))
        assertEquals(true, playerPopupSelectionRequiresScroll(6, firstVisiblePosition = 0, lastVisiblePosition = 5))
        assertEquals(true, playerPopupSelectionRequiresScroll(0, firstVisiblePosition = 1, lastVisiblePosition = 5))
    }

    @Test
    fun popupTouchPassesInsideEventsAndDismissesOutsideRelease() {
        assertEquals(
            PlayerPopupTouchAction.PassToPopup,
            playerPopupTouchAction(
                actionMasked = MotionEvent.ACTION_DOWN,
                x = 50f,
                y = 60f,
                popupLeft = 10,
                popupTop = 20,
                popupRight = 100,
                popupBottom = 120,
            ),
        )
        assertEquals(
            PlayerPopupTouchAction.ConsumeOutside,
            playerPopupTouchAction(
                actionMasked = MotionEvent.ACTION_DOWN,
                x = 5f,
                y = 60f,
                popupLeft = 10,
                popupTop = 20,
                popupRight = 100,
                popupBottom = 120,
            ),
        )
        assertEquals(
            PlayerPopupTouchAction.Dismiss,
            playerPopupTouchAction(
                actionMasked = MotionEvent.ACTION_UP,
                x = 5f,
                y = 60f,
                popupLeft = 10,
                popupTop = 20,
                popupRight = 100,
                popupBottom = 120,
            ),
        )
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
