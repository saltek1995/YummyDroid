package me.yummydroid.app.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import me.yummydroid.app.AppRoute
import me.yummydroid.app.YummyDroidUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppLayerTransitionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun exitingDetailsKeepTheirCompositionAndContentUntilTheFadeFinishes() {
        val closing = mutableStateOf(false)
        val removed = mutableStateOf(false)
        val layer = AppScreenLayer(AppScreenKey.Details(10), YummyDroidUiState(route = AppRoute.Details(10)))
        var mounts = 0
        var disposals = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalAppNavigationController provides remember { AppNavigationController() }) {
            Box(Modifier.fillMaxSize().background(Color.Blue).testTag("screen")) {
                AppScreenLayers(
                    renderedLayers = if (closing.value) emptyList() else listOf(layer),
                    exitingLayers = if (closing.value && !removed.value) listOf(layer) else emptyList(),
                ) { _, active, zIndex, visible ->
                    DisposableEffect(Unit) { mounts++; onDispose { disposals++ } }
                    AppLayerContainer(zIndex, visible, active, scaleFrom = 1f) {
                        Box(Modifier.fillMaxSize().background(Color.Red).testTag("outgoing"))
                    }
                }
            }
            }
        }
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { closing.value = true }
        compose.mainClock.advanceTimeBy(112)
        compose.onNodeWithTag("outgoing").assertIsDisplayed()
        assertEquals(1, mounts)
        assertEquals(0, disposals)
        compose.mainClock.advanceTimeBy(250)
        compose.runOnIdle { removed.value = true }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("outgoing").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, disposals) }
    }
}
