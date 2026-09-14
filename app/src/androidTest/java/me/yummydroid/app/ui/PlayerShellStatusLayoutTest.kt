package me.yummydroid.app.ui

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import kotlin.math.roundToInt
import me.yummydroid.app.R
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.ui.theme.YummyDroidTheme
import me.yummydroid.app.ui.theme.YummySizes
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PlayerShellStatusLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun shortLandscape() = verifyLayout(DpSize(640.dp, 320.dp))
    @Test fun landscapeAt130PercentInterfaceScale() = verifyLayout(DpSize((640f / 1.3f).dp, (320f / 1.3f).dp))
    @Test fun portraitPhone() = verifyLayout(DpSize(360.dp, 640.dp))
    @Test fun portraitTablet() = verifyLayout(DpSize(800.dp, 1280.dp))
    @Test fun televisionDpad() = verifyLayout(DpSize(960.dp, 540.dp), true)

    @Test
    @androidx.annotation.OptIn(UnstableApi::class)
    fun nativeDirectionalNavigationReachesOffscreenActions() {
        lateinit var player: PlayerView
        val ids = listOf(R.id.yummy_player_quality, R.id.yummy_player_source, R.id.yummy_player_voice,
            R.id.yummy_player_subtitles, R.id.yummy_player_subscription, R.id.yummy_player_speed, R.id.yummy_player_pip)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(492.dp, 246.dp))) {
                val base = LocalContext.current
                val density = LocalDensity.current.density
                val currentConfiguration = LocalConfiguration.current
                val context = remember(base, density, currentConfiguration) {
                    base.createConfigurationContext(Configuration(currentConfiguration).apply {
                        orientation = Configuration.ORIENTATION_LANDSCAPE
                        screenWidthDp = 492; screenHeightDp = 246; smallestScreenWidthDp = 246
                        densityDpi = (density * 160).roundToInt()
                    })
                }
                androidx.compose.ui.viewinterop.AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        val themed = ContextThemeWrapper(context, R.style.Theme_YummyDroid_Player)
                        (LayoutInflater.from(themed).inflate(R.layout.yummy_player_view, FrameLayout(themed), false) as PlayerView).also {
                            player = it
                            it.setControllerAnimationEnabled(false)
                            it.showController()
                            (it.findViewById<View>(R.id.yummy_player_bottom_content).parent as android.widget.HorizontalScrollView).isSmoothScrollingEnabled = false
                            ids.forEach { id -> it.findViewById<View>(id).isFocusableInTouchMode = true }
                        }
                    },
                )
            }
        }
        compose.runOnIdle {
            var source = player.findViewById<View>(ids.first())
            assertTrue(source.requestFocus())
            for (id in ids.drop(1)) {
                assertTrue(player.requestDynamicPlayerFocus(source, PlayerFocusDirection.Right))
                val next = player.findViewById<View>(id)
                assertTrue("D-pad reached ${player.resources.getResourceEntryName(id)}", next.hasFocus())
                val bounds = android.graphics.Rect(0, 0, next.width, next.height)
                player.offsetDescendantRectToMyCoords(next, bounds)
                assertTrue("Focused action is on screen: $bounds / ${player.width}", bounds.left >= 0 && bounds.right <= player.width)
                source = next
            }
        }
    }

    private fun verifyLayout(size: DpSize, television: Boolean = false) {
        var clicks = 0
        var density = 1f
        lateinit var measuredContext: Context
        val error = List(80) { "Источник временно недоступен. Проверьте соединение и повторите попытку." }.joinToString("\n")
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
                val base = LocalContext.current
                val config = LocalConfiguration.current
                val currentDensity = LocalDensity.current.density
                val configuration = remember(config, size, currentDensity) {
                    Configuration(config).apply {
                        densityDpi = (currentDensity * 160).roundToInt()
                        fontScale = 1f
                        screenWidthDp = size.width.value.roundToInt()
                        screenHeightDp = size.height.value.roundToInt()
                        smallestScreenWidthDp = minOf(screenWidthDp, screenHeightDp)
                        orientation = if (screenWidthDp > screenHeightDp) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                        uiMode = (uiMode and Configuration.UI_MODE_TYPE_MASK.inv()) or
                            if (television) Configuration.UI_MODE_TYPE_TELEVISION else Configuration.UI_MODE_TYPE_NORMAL
                    }
                }
                val context = remember(base, configuration) { base.createConfigurationContext(configuration) }
                val navigation = remember { AppNavigationController() }
                val focus = remember { FocusRequester() }
                SideEffect { measuredContext = context; density = currentDensity }
                CompositionLocalProvider(
                    LocalContext provides context,
                    LocalConfiguration provides context.resources.configuration,
                    LocalUiLanguage provides ContentLanguage.Russian,
                    LocalAppNavigationController provides navigation,
                ) {
                    YummyDroidTheme {
                        Box(Modifier.fillMaxSize().navigationFocusBoundary().testTag("status-root")) {
                            PlayerShellStatus(error, focus) { clicks++ }
                        }
                    }
                }
            }
        }
        val root = compose.onNodeWithTag("status-root").fetchSemanticsNode().boundsInRoot
        val retry = compose.onNodeWithTag(PLAYER_SHELL_ERROR_RETRY_TAG).assertIsDisplayed()
        val before = retry.fetchSemanticsNode().boundsInRoot
        val message = compose.onNodeWithTag(PLAYER_SHELL_ERROR_MESSAGE_TAG)
        val messageBounds = message.fetchSemanticsNode().boundsInRoot
        val bars = compose.runOnIdle { nativeBars(measuredContext, root.width.roundToInt(), root.height.roundToInt()) }
        assertTrue("Retry minimum height: $before", before.height >= YummySizes.dialogButtonHeight.value * density - 2)
        assertTrue("Top clearance: $messageBounds, bars=$bars", messageBounds.top >= root.top + bars.first + 8 * density - 2)
        assertTrue("Visible message viewport", messageBounds.height > 0)
        assertTrue("Message and Retry separated", messageBounds.bottom <= before.top + 2)
        assertTrue("Bottom clearance: $before, bars=$bars", before.bottom <= root.bottom - bars.second - 8 * density + 2)
        assertTrue(before.left >= root.left - 2 && before.right <= root.right + 2)
        val scroller = compose.onNode(hasScrollAction())
        val range = scroller.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(range.maxValue() > 0)
        if (television) {
            message.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            message.assertIsFocused().performKeyInput { keyDown(Key.DirectionDown); keyUp(Key.DirectionDown) }
            compose.waitForIdle()
            assertTrue("D-pad scroll", compose.runOnIdle { range.value() > 0 })
            message.assertIsFocused()
        }
        scroller.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1_000_000f) }
        compose.waitForIdle()
        assertTrue("Error end reachable", compose.runOnIdle { abs(range.value() - range.maxValue()) <= 2 })
        assertEquals(before, retry.fetchSemanticsNode().boundsInRoot)
        retry.performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun nativeBars(context: Context, width: Int, height: Int): Pair<Int, Int> {
        val themed = ContextThemeWrapper(context, R.style.Theme_YummyDroid_Player)
        val parent = FrameLayout(themed)
        val player = LayoutInflater.from(themed).inflate(R.layout.yummy_player_view, parent, false) as PlayerView
        parent.addView(player)
        player.setControllerAnimationEnabled(false)
        player.showController()
        parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, width, height)
        val scroll = player.findViewById<View>(R.id.yummy_player_bottom_content).parent as? android.widget.HorizontalScrollView
        if (scroll != null) {
            scroll.isSmoothScrollingEnabled = false
            val actions = listOf(R.id.yummy_player_quality, R.id.yummy_player_source, R.id.yummy_player_voice,
                R.id.yummy_player_subtitles, R.id.yummy_player_subscription, R.id.yummy_player_speed,
                R.id.yummy_player_pip)
            for (id in actions) {
                val action = player.findViewById<View>(id)
                assertEquals(View.VISIBLE, action.visibility)
                action.isFocusableInTouchMode = true
                assertTrue("Native action can receive focus: $id", action.requestFocus())
                action.requestRectangleOnScreen(android.graphics.Rect(0, 0, action.width, action.height), true)
                val bounds = android.graphics.Rect(0, 0, action.width, action.height)
                player.offsetDescendantRectToMyCoords(action, bounds)
                assertTrue("Focused native action must be fully reachable: $id $bounds / $width", bounds.left >= 0 && bounds.right <= width)
                assertTrue(action.width > 0 && action.height > 0)
            }
        }
        val top = player.findViewById<View>(R.id.yummy_player_top_bar).height
        val bottom = player.findViewById<View>(androidx.media3.ui.R.id.exo_bottom_bar).height
        assertTrue(top > 0 && bottom > 0)
        return top to bottom
    }
}
