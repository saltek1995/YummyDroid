package me.yummydroid.app

import android.app.Application
import android.view.KeyEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MainActivityInputRouterTest {
    @Test
    fun lostPhysicalKeyUpCannotBlockTheNextSystemBack() {
        for (key in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_NAVIGATE_OUT,
            KeyEvent.KEYCODE_BUTTON_B)) {
            val events = mutableListOf<InputActionEvent>()
            val router = MainActivityInputRouter().apply { setHandler { events += it; true } }
            assertEquals(true, router.interceptKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key)))
            router.handleBackPressed()
            assertEquals(listOf(InputAction.Back, InputAction.Back), events.map { it.action })
            assertEquals(true, router.interceptKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key)))
            assertEquals(2, events.size)
        }
    }

    @Test
    fun consumedPhysicalBackPairDispatchesOnlyOnceAndNextPressStillWorks() {
        val events = mutableListOf<InputActionEvent>()
        val router = MainActivityInputRouter().apply { setHandler { events += it; true } }
        repeat(2) {
            assertTrue(router.interceptKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)) == true)
            assertTrue(router.interceptKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)) == true)
        }
        assertEquals(2, events.size)
    }
}
