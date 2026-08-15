package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DialogActionButtonTest {
    @Test
    fun loadingButtonRetainsFocusWithoutAllowingActivation() {
        val availability = resolveDialogActionAvailability(enabled = true, loading = true)

        assertTrue(availability.focusable)
        assertFalse(availability.actionable)
    }

    @Test
    fun readyButtonCanReceiveFocusAndActivate() {
        val availability = resolveDialogActionAvailability(enabled = true, loading = false)

        assertTrue(availability.focusable)
        assertTrue(availability.actionable)
    }

    @Test
    fun disabledButtonCannotReceiveFocusOrActivate() {
        val availability = resolveDialogActionAvailability(enabled = false, loading = false)

        assertFalse(availability.focusable)
        assertFalse(availability.actionable)
    }
}
