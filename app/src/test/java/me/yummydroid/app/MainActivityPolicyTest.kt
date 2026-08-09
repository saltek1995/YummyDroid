package me.yummydroid.app

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityPolicyTest {
    @Test
    fun `motion axes preserve horizontal priority and dead zones`() {
        assertEquals(
            InputAction.Left,
            MainActivityInputPolicy.actionForAxes(hatX = -0.5f, hatY = 1f, x = 0f, y = 0f),
        )
        assertEquals(
            InputAction.Right,
            MainActivityInputPolicy.actionForAxes(hatX = 0f, hatY = 0f, x = 0.65f, y = 0f),
        )
        assertEquals(
            InputAction.Up,
            MainActivityInputPolicy.actionForAxes(hatX = 0f, hatY = -0.5f, x = 0f, y = 0f),
        )
        assertEquals(
            InputAction.Down,
            MainActivityInputPolicy.actionForAxes(hatX = 0f, hatY = 0f, x = 0f, y = 0.65f),
        )
        assertNull(
            MainActivityInputPolicy.actionForAxes(hatX = 0.49f, hatY = 0f, x = 0.64f, y = 0f),
        )
    }

    @Test
    fun `pointer reset and focus recovery use the original action sets`() {
        assertTrue(MainActivityInputPolicy.resetsPointerInputNavigation(InputAction.Confirm))
        assertFalse(MainActivityInputPolicy.resetsPointerInputNavigation(InputAction.PlayPause))
        assertTrue(MainActivityInputPolicy.usesDpadFocusRecovery(InputAction.Left))
        assertFalse(MainActivityInputPolicy.usesDpadFocusRecovery(InputAction.Confirm))
        assertFalse(MainActivityInputPolicy.usesDpadFocusRecovery(null))
    }

    @Test
    fun `pip entry requires an active player route outside pip`() {
        assertTrue(
            MainActivityPipPolicy.canEnter(
                isPlayerRoute = true,
                isInPictureInPictureMode = false,
                hasPlayer = true,
            ),
        )
        assertFalse(
            MainActivityPipPolicy.canEnter(
                isPlayerRoute = true,
                isInPictureInPictureMode = true,
                hasPlayer = true,
            ),
        )
        assertFalse(MainActivityPipPolicy.shouldAutoEnter(isPlayerRoute = false, hasPlayer = true))
    }

    @Test
    fun `manual leave hint is only used before android twelve`() {
        assertTrue(
            MainActivityPipPolicy.shouldEnterOnUserLeaveHint(
                sdkInt = Build.VERSION_CODES.R,
                isPlayerRoute = true,
                hasPlayer = true,
            ),
        )
        assertFalse(
            MainActivityPipPolicy.shouldEnterOnUserLeaveHint(
                sdkInt = Build.VERSION_CODES.S,
                isPlayerRoute = true,
                hasPlayer = true,
            ),
        )
    }
}
