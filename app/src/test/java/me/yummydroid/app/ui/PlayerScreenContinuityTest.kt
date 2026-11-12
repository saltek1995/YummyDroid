package me.yummydroid.app.ui

import android.app.Application
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.ui.PlayerView
import me.yummydroid.app.LoadState
import me.yummydroid.app.R
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.ui.theme.YummyDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlayerScreenContinuityTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun pendingAndFailedSelectionKeepTheControlTreeMenusAndSettingsUsable() {
        val first = video(1, "CVH")
        val second = video(2, "Kodik")
        val third = video(3, "Alloha")
        val selected = mutableStateOf(state(first, listOf(first, second, third)))
        val intents = mutableListOf<Pair<Long, Boolean>>()
        var retries = 0
        val actions = PlayerScreenActions(
            onSelectGroup = {}, onPlayVideoAtQuality = { _, _, _ -> },
            onSelectPlaybackSource = { _, _ -> }, onChooseResumePosition = {},
            onToggleVideoSubscription = {}, onRetry = { retries++ },
            onPlaybackFailed = { _, _, _ -> }, onPlaybackStarted = {}, onPlaybackEnded = {},
            onPlaybackProgress = { _, _, _ -> }, onEnterPictureInPicture = {},
            onSettingsChange = {}, onBack = {}, onRegisterModalInputActionHandler = {},
            onPlayerControlIntentChanged = { target, play -> intents += target.id to play },
        )
        compose.setContent {
            CompositionLocalProvider(LocalAppNavigationController provides remember { AppNavigationController() }) {
                YummyDroidTheme { PlayerScreen(selected.value, actions) }
            }
        }
        compose.waitForIdle()
        var original: PlayerView? = null
        compose.runOnIdle { original = findPlayer(compose.activity.window.decorView) }
        val view = assertNotNull(original)
        val source = assertNotNull(view.findViewById<View>(R.id.yummy_player_source))
        val speed = assertNotNull(view.findViewById<View>(R.id.yummy_player_speed))
        val surface = assertNotNull(view.videoSurfaceView)
        compose.runOnIdle {
            assertTrue(source.isEnabled)
            source.performClick()
            assertTrue(view.hasPlayerPopupMenu())
            selected.value = selected.value.copy(video = second, streamState = LoadState.Loading)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(view, findPlayer(compose.activity.window.decorView))
            assertSame(surface, view.videoSurfaceView)
            assertSame(source, view.findViewById(R.id.yummy_player_source))
            assertSame(speed, view.findViewById(R.id.yummy_player_speed))
            assertTrue(source.isEnabled)
            assertTrue(speed.isEnabled)
            assertTrue(view.hasPlayerPopupMenu())
            selected.value = selected.value.copy(video = third, streamState = LoadState.Error("Fixture replacement failed"))
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(view, findPlayer(compose.activity.window.decorView))
            assertSame(surface, view.videoSurfaceView)
            assertTrue(source.isEnabled)
            assertTrue(speed.isEnabled)
            assertTrue(view.hasPlayerPopupMenu())
            view.requestPlayCallback()?.invoke()
            assertEquals(3L to true, intents.last())
            assertEquals(1, retries)
        }
    }

    @Test fun displayPreparationMaskLivesUnderTheControllerAndKeepsTheSurfaceAttached() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val view = android.view.LayoutInflater.from(android.view.ContextThemeWrapper(context, R.style.Theme_YummyDroid_Player))
            .inflate(R.layout.yummy_player_view, android.widget.FrameLayout(context), false) as PlayerView
        val surface = assertNotNull(view.videoSurfaceView)
        val control = assertNotNull(view.findViewById<View>(R.id.yummy_player_source))
        view.setVideoPreparationMaskVisible(true)
        val mask = assertNotNull(view.findViewById<View>(R.id.yummy_player_video_preparation_mask))
        assertSame<Any?>(view.findViewById<View>(androidx.media3.ui.R.id.exo_content_frame), mask.parent)
        assertSame(surface, view.videoSurfaceView)
        assertTrue(mask.parent !== control.parent)
        assertEquals(View.VISIBLE, mask.visibility)
        view.setVideoPreparationMaskVisible(false)
        assertEquals(View.GONE, mask.visibility)
        assertSame(surface, view.videoSurfaceView)
    }

    private fun findPlayer(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findPlayer(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun video(id: Long, source: String) = VideoVariant(
        id = id, animeId = 10, player = source, dubbing = "Fixture voice", episode = "1",
        url = "", index = 1, durationSeconds = 1440, views = 0,
    )

    private fun state(video: VideoVariant, videos: List<VideoVariant>) = PlayerScreenState(
        animeTitle = "Fixture anime", video = video, interactive = true, settings = AppSettings(),
        startPositionMs = 0, preferredQuality = PreferredQuality.Auto, allVideos = videos,
        selectedGroup = video.groupKey,
        streamState = LoadState.Ready(ResolvedVideoStream("file:///fixture-does-not-exist.mp4", "video/mp4", emptyMap())),
        playbackMetadataLoading = false, resumeChoicePositionMs = null, playWhenReady = false,
        isInPictureInPicture = false, forcedOfflineMode = false, allowSubscriptions = false,
        canUsePictureInPicture = false,
    )
}
