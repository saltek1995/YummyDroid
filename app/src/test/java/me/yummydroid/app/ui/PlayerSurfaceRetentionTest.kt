package me.yummydroid.app.ui

import android.app.Application
import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.ui.PlayerView
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlayerSurfaceRetentionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun displaySizeAndOrientationChangesKeepTheSameVideoSurface() {
        val configuration = mutableStateOf(Configuration().apply {
            orientation = Configuration.ORIENTATION_LANDSCAPE
            screenWidthDp = 960
            screenHeightDp = 540
            smallestScreenWidthDp = 540
            densityDpi = 160
            fontScale = 1f
        })
        var currentView: PlayerView? = null
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides configuration.value) {
                val content = rememberPlayerViewContent()
                content(Modifier.fillMaxSize()) { currentView = it }
            }
        }
        compose.waitForIdle()
        val initialView = requireNotNull(currentView)
        val initialSurface = initialView.videoSurfaceView
        assertNotNull(initialSurface)
        compose.runOnIdle {
            configuration.value = Configuration(configuration.value).apply {
                screenWidthDp = 1920
                screenHeightDp = 1080
                smallestScreenWidthDp = 1080
            }
        }
        compose.runOnIdle {
            assertSame(initialView, currentView)
            assertSame(initialSurface, currentView!!.videoSurfaceView)
            configuration.value = Configuration(configuration.value).apply {
                orientation = Configuration.ORIENTATION_PORTRAIT
                screenWidthDp = 540
                screenHeightDp = 960
                smallestScreenWidthDp = 540
            }
        }
        compose.runOnIdle {
            assertSame(initialView, currentView)
            assertSame(initialSurface, currentView!!.videoSurfaceView)
        }
    }
}
