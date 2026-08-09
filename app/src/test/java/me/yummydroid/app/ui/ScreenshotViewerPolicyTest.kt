package me.yummydroid.app.ui

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.InputAction

class ScreenshotViewerPolicyTest {
    @Test
    fun remoteActionsMapToViewerCommands() {
        assertEquals(ScreenshotViewerCommand.Close, InputAction.Back.toScreenshotViewerCommand())
        assertEquals(ScreenshotViewerCommand.Close, InputAction.Up.toScreenshotViewerCommand())
        assertEquals(ScreenshotViewerCommand.Close, InputAction.Down.toScreenshotViewerCommand())
        assertEquals(ScreenshotViewerCommand.Previous, InputAction.Left.toScreenshotViewerCommand())
        assertEquals(ScreenshotViewerCommand.Next, InputAction.Right.toScreenshotViewerCommand())
        assertEquals(ScreenshotViewerCommand.Ignore, InputAction.Confirm.toScreenshotViewerCommand())
        assertEquals(ScreenshotViewerCommand.Ignore, InputAction.PlayPause.toScreenshotViewerCommand())
    }

    @Test
    fun keyboardKeysMapToViewerCommands() {
        assertEquals(ScreenshotViewerCommand.Close, Key.DirectionUp.toScreenshotViewerCommand())
        assertEquals(ScreenshotViewerCommand.Close, Key.DirectionDown.toScreenshotViewerCommand())
        assertEquals(ScreenshotViewerCommand.Close, Key.Escape.toScreenshotViewerCommand())
        assertEquals(ScreenshotViewerCommand.Close, Key.NavigateOut.toScreenshotViewerCommand())
        assertEquals(ScreenshotViewerCommand.Previous, Key.DirectionLeft.toScreenshotViewerCommand())
        assertEquals(ScreenshotViewerCommand.Next, Key.DirectionRight.toScreenshotViewerCommand())
        assertEquals(ScreenshotViewerCommand.Ignore, Key.Enter.toScreenshotViewerCommand())
    }

    @Test
    fun targetPageStopsAtViewerBoundaries() {
        assertEquals(1, screenshotViewerTargetPage(ScreenshotViewerCommand.Previous, 2, 4))
        assertEquals(3, screenshotViewerTargetPage(ScreenshotViewerCommand.Next, 2, 4))
        assertNull(screenshotViewerTargetPage(ScreenshotViewerCommand.Previous, 0, 4))
        assertNull(screenshotViewerTargetPage(ScreenshotViewerCommand.Next, 4, 4))
        assertNull(screenshotViewerTargetPage(ScreenshotViewerCommand.Close, 2, 4))
    }

    @Test
    fun verticalDismissRequiresDragBeyondThreshold() {
        assertFalse(shouldDismissScreenshotViewer(120f))
        assertFalse(shouldDismissScreenshotViewer(-120f))
        assertTrue(shouldDismissScreenshotViewer(120.1f))
        assertTrue(shouldDismissScreenshotViewer(-120.1f))
    }
}
