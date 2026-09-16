package me.yummydroid.app.ui

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent
import me.yummydroid.app.R
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.ui.theme.YummyDroidTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Regression coverage for focus crossing between the error overlay and native player controls. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class, UnstableApi::class)
class PlayerShellErrorFocusTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun errorRetryBridgesToNativeSourceAndBackAndSourceConfirmOpensMenu() {
        val fixture = setErrorShell()
        val retry = compose.onNodeWithTag(PLAYER_SHELL_ERROR_RETRY_TAG)
        compose.waitUntil { fixture.navigation.playerInputController != null }
        compose.waitUntil { retry.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true }
        retry.assertIsFocused()

        // Confirm belongs to the Compose button; the player adapter deliberately leaves it unhandled.
        compose.runOnIdle {
            assertFalse(fixture.navigation.playerInputController!!.handleInput(InputActionEvent(InputAction.Confirm)))
        }
        retry.performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        compose.runOnIdle { assertTrue(fixture.retryCount == 1) }

        compose.runOnIdle {
            assertTrue(fixture.navigation.playerInputController!!.handleInput(InputActionEvent(InputAction.Down)))
            assertTrue("Down from Retry reaches a native selector", fixture.source.hasFocus() || fixture.voice.hasFocus())
            assertTrue(fixture.navigation.playerInputController!!.handleInput(InputActionEvent(InputAction.Up)))
        }
        retry.assertIsFocused()

        compose.runOnIdle {
            assertTrue(fixture.source.requestFocus())
            assertTrue(fixture.navigation.playerInputController!!.handleInput(InputActionEvent(InputAction.Confirm)))
            assertTrue("Source Confirm opens its native popup", fixture.player.hasPlayerPopupMenu())
        }
    }

    @Test
    fun downFromRetryRestoresHiddenNativeControllerBeforeCrossingFocusBoundary() {
        val fixture = setErrorShell()
        val retry = compose.onNodeWithTag(PLAYER_SHELL_ERROR_RETRY_TAG)
        compose.waitUntil { fixture.navigation.playerInputController != null }
        compose.waitUntil { retry.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true }
        compose.runOnIdle {
            assertTrue(fixture.navigation.playerInputController!!.hideVisibleControls())
        }
        compose.waitUntil { !fixture.player.isControllerFullyVisible }
        compose.runOnIdle {
            assertTrue(fixture.navigation.playerInputController!!.handleInput(InputActionEvent(InputAction.Down)))
        }
        compose.waitUntil { fixture.source.hasFocus() || fixture.voice.hasFocus() }
        compose.runOnIdle {
            assertTrue(fixture.navigation.playerInputController!!.handleInput(InputActionEvent(InputAction.Up)))
        }
        retry.assertIsFocused()
    }

    @Test
    fun replacingErrorMessageKeepsRetryFocused() {
        val fixture = setErrorShell()
        val retry = compose.onNodeWithTag(PLAYER_SHELL_ERROR_RETRY_TAG)
        compose.waitUntil { fixture.navigation.playerInputController != null }
        compose.waitUntil { retry.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true }
        compose.runOnIdle { fixture.message.value = "The fallback source also failed." }
        compose.waitForIdle()
        retry.assertIsFocused()
        compose.runOnIdle {
            assertTrue(fixture.navigation.playerInputController!!.handleInput(InputActionEvent(InputAction.Down)))
            assertTrue(fixture.source.hasFocus() || fixture.voice.hasFocus())
        }
    }

    private fun setErrorShell(): ErrorShellFixture {
        val fixture = ErrorShellFixture()
        compose.setContent {
            val inputMode = LocalInputModeManager.current
            LaunchedEffect(Unit) { inputMode.requestInputMode(InputMode.Keyboard) }
            val playerContent: PlayerViewContent = remember {
                { modifier, update ->
                    AndroidView(
                        modifier = modifier,
                        factory = { context ->
                            val themed = ContextThemeWrapper(context, R.style.Theme_YummyDroid_Player)
                            LayoutInflater.from(themed).inflate(
                                R.layout.yummy_player_view,
                                FrameLayout(themed),
                                false,
                            ) as PlayerView
                        },
                        update = { view ->
                            update(view)
                            fixture.player = view
                            fixture.source = view.findViewById(R.id.yummy_player_source)
                            fixture.voice = view.findViewById(R.id.yummy_player_voice)
                        },
                    )
                }
            }
            CompositionLocalProvider(
                LocalAppNavigationController provides fixture.navigation,
                LocalPlayerViewContent provides playerContent,
            ) {
                YummyDroidTheme {
                    PlayerShellPane(
                        model = fixture.model,
                        actions = PlayerShellActions(
                            onToggleSubscription = {}, onSelectGroup = { _, _ -> }, onSelectSource = {},
                            onPlayVideo = {}, onRetry = { fixture.retryCount++ }, onBack = {},
                        ),
                        modifier = Modifier.fillMaxSize(),
                        message = fixture.message.value,
                    )
                }
            }
        }
        return fixture
    }

    private class ErrorShellFixture {
        val navigation = AppNavigationController()
        val message = mutableStateOf("The selected video source is temporarily unavailable.")
        var retryCount = 0
        lateinit var player: PlayerView
        lateinit var source: View
        lateinit var voice: View
        private val current = video(id = 1, player = "Primary")
        private val alternate = video(id = 2, player = "Mirror")
        val model = PlayerShellModel(
            animeTitle = "Focus test", currentVideo = current, settings = AppSettings(),
            groups = mapOf(current.groupKey to listOf(current)), selectedKey = current.groupKey,
            sourceOptions = listOf(
                SourceOption("primary", "Primary", current),
                SourceOption("mirror", "Mirror", alternate),
            ),
            selectedSourceKey = "primary", previousVideo = null, nextVideo = null,
            allowSubscription = false, subscriptionActive = false, canUsePictureInPicture = false,
        )
    }

    private companion object {
        fun video(id: Long, player: String) = VideoVariant(
            id = id, animeId = 1L, player = player, dubbing = "Voice", episode = "1",
            url = "https://example.invalid/$id", index = 1, durationSeconds = 1, views = 0,
        )
    }
}
