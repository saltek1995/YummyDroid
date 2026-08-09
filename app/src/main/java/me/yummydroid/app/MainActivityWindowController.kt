package me.yummydroid.app

import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal class MainActivityWindowController(
    private val activity: ComponentActivity,
) {
    private var appStatusBarColor = Color.BLACK
    private var appNavigationBarColor = Color.BLACK

    val isTelevisionDevice: Boolean
        get() = (activity.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION

    @Suppress("DEPRECATION")
    fun captureSystemBarColors() {
        appStatusBarColor = activity.window.statusBarColor
        appNavigationBarColor = activity.window.navigationBarColor
    }

    fun applyCurrentMode(
        isPlayerRoute: Boolean,
        isInPictureInPictureMode: Boolean,
    ) {
        if (isPlayerRoute && !isInPictureInPictureMode) {
            setPlayerFullscreen()
        } else {
            configureForAppContent()
        }
    }

    @Suppress("DEPRECATION")
    fun configureForAppContent() {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, !isTelevisionDevice)
        applyPreferredAppFrameRate()
        window.statusBarColor = appStatusBarColor
        window.navigationBarColor = appNavigationBarColor
        setCutoutMode(fullscreen = false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.show(WindowInsetsCompat.Type.systemBars())
        if (shouldHideAppStatusBar) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    @Suppress("DEPRECATION")
    private fun setPlayerFullscreen() {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setPreferredWindowRefreshRate(0f)
        setCutoutMode(fullscreen = true)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    @Suppress("DEPRECATION")
    private fun applyPreferredAppFrameRate() {
        if (isTelevisionDevice) {
            setPreferredWindowRefreshRate(APP_CONTENT_FRAME_RATE)
        }
    }

    @Suppress("DEPRECATION")
    private fun setPreferredWindowRefreshRate(refreshRate: Float) {
        activity.window.attributes = activity.window.attributes.apply {
            preferredRefreshRate = refreshRate
        }
    }

    @Suppress("DEPRECATION")
    private fun setCutoutMode(fullscreen: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        activity.window.attributes = activity.window.attributes.apply {
            layoutInDisplayCutoutMode = if (fullscreen) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
        }
    }

    private val shouldHideAppStatusBar: Boolean
        get() = isTelevisionDevice ||
            activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}

private const val APP_CONTENT_FRAME_RATE = 60f
