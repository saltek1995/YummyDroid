package me.yummydroid.app.ui

import android.app.Activity
import android.app.Application
import android.graphics.Rect
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import kotlin.math.roundToInt
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.R
import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent
import me.yummydroid.app.data.VideoSkipKind
import me.yummydroid.app.data.VideoSkipSegment
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "w360dp-h640dp-port-mdpi")
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerControlsLayoutTest {
    private val windows = listOf(
        360 to 800, 800 to 360, 320 to 320, 800 to 800, 240 to 640, 640 to 240,
        492 to 246, 1280 to 360, 360 to 1280, 1280 to 960, 960 to 1280,
    )

    @Test
    fun resizingKeepsControlSizesSpacingAndEdgeAnchors() = verifyGeometry()

    @Test
    @Config(qualifiers = "sw600dp-w800dp-h1280dp-port-xhdpi")
    fun tabletDensityKeepsTheSameDpGeometry() = verifyGeometry()

    @Test
    @Config(qualifiers = "w492dp-h246dp-land-260dpi")
    fun fractionalDensityKeepsGeometryStable() = verifyGeometry()

    private fun verifyGeometry() = withPlayer { player, resize, px ->
        for ((width, height) in windows) {
            resize(width, height)
            val play = player.findViewById<View>(Media3R.id.exo_play_pause)
            val previous = player.findViewById<View>(R.id.yummy_episode_previous)
            val next = player.findViewById<View>(R.id.yummy_episode_next)
            assertEquals(px(52), play.width)
            assertEquals(px(52), play.height)
            for (button in listOf(previous, next, player.findViewById(R.id.yummy_player_back))) {
                assertEquals(px(48), button.width)
                assertEquals(px(48), button.height)
            }
            val playBounds = player.bounds(play)
            assertTrue(abs(px(72) - (playBounds.centerX() - player.bounds(previous).centerX())) <= 1)
            assertTrue(abs(px(72) - (player.bounds(next).centerX() - playBounds.centerX())) <= 1)
            assertTrue(abs(player.width / 2 - playBounds.centerX()) <= 1)
            val header = player.bounds(player.findViewById(R.id.yummy_player_top_bar))
            val footer = player.bounds(player.findViewById(Media3R.id.exo_bottom_bar))
            assertEquals(0, header.top)
            assertTrue(header.height() >= px(56))
            assertEquals(player.height, footer.bottom)
            assertTrue(footer.height() >= px(88))
            val title = player.findViewById<TextView>(R.id.yummy_player_title)
            val subtitle = player.findViewById<TextView>(R.id.yummy_player_subtitle)
            assertTrue(title.lineCount in 1..2)
            if (width <= 360) assertTrue(title.lineCount > 1,
                "Long title must wrap at ${width}x$height")
            assertTrue(player.bounds(title).top >= header.top && player.bounds(title).bottom <= header.bottom)
            assertTrue(player.bounds(subtitle).top >= header.top && player.bounds(subtitle).bottom <= header.bottom)
            assertTrue(title.height < header.height(), "Title must keep its natural height")
            for (button in listOf(previous, play, next)) {
                val bounds = player.bounds(button)
                assertTrue(bounds.left >= 0 && bounds.right <= player.width)
                assertTrue(bounds.top >= header.bottom && bounds.bottom <= footer.top)
            }
        }
    }

    @Test
    fun fullControlsReplaceWatchWithSkipInTheSameRightHandSlot() = verifySkipPresentation()

    @Test
    @Config(qualifiers = "w492dp-h246dp-land-260dpi")
    fun fractionalDensityKeepsSkipInTheWatchSlot() = verifySkipPresentation()

    private fun verifySkipPresentation() = withPlayer { player, resize, px ->
        val skip = player.findViewById<TextView>(R.id.yummy_skip_skip)
        val watch = player.findViewById<TextView>(R.id.yummy_skip_watch)
        skip.text = "Пропустить (5)"
        watch.text = "Смотреть"
        player.setSkipControlsActive(true)
        for ((width, height) in listOf(320 to 360, 492 to 246, 640 to 320)) {
            player.setSkipOnlyControllerMode(true)
            resize(width, height)
            assertEquals(View.VISIBLE, skip.visibility)
            assertEquals(View.VISIBLE, watch.visibility)
            val watchBounds = player.bounds(watch)
            val floatingSkipBounds = player.bounds(skip)
            assertFalse(Rect.intersects(floatingSkipBounds, watchBounds))
            assertTrue(floatingSkipBounds.left >= 0 && floatingSkipBounds.top >= 0)
            assertEquals(player.width - px(14), watchBounds.right)

            player.setSkipOnlyControllerMode(false)
            resize(width, height)
            assertEquals(View.GONE, watch.visibility)
            assertFalse(watch.isEnabled || watch.isFocusable || watch.isClickable)
            val skipBounds = player.bounds(skip)
            assertEquals(watchBounds.right, skipBounds.right,
                "Skip must occupy Watch's right-hand slot at ${width}x$height")
            assertEquals(watchBounds.bottom, skipBounds.bottom)
            assertEquals(px(48), skip.height)
            for (id in listOf(R.id.yummy_episode_previous, Media3R.id.exo_play_pause, R.id.yummy_episode_next,
                R.id.yummy_player_top_bar, Media3R.id.exo_bottom_bar)) {
                assertFalse(Rect.intersects(skipBounds, player.bounds(player.findViewById(id))),
                    "Skip overlaps $id at ${width}x$height: $skipBounds")
            }
        }
    }

    @Test
    fun hidingWatchTransfersItsFocusToSkip() = withPlayer { player, resize, _ ->
        player.setSkipControlsActive(true)
        player.setSkipOnlyControllerMode(true)
        resize(640, 320)
        val watch = player.findViewById<View>(R.id.yummy_skip_watch)
        val skip = player.findViewById<View>(R.id.yummy_skip_skip)
        watch.isFocusableInTouchMode = true
        skip.isFocusableInTouchMode = true
        assertTrue(watch.requestFocus())
        player.setSkipOnlyControllerMode(false)
        assertTrue(skip.hasFocus())
        assertFalse(watch.hasFocus())
        player.setSkipControlsActive(false)
        assertEquals(View.GONE, player.findViewById<View>(R.id.yummy_skip_controls).visibility)
    }

    @Test
    fun stackedFloatingActionsFollowVerticalDpadNavigation() = withPlayer { player, resize, _ ->
        player.setSkipControlsActive(true)
        player.setSkipOnlyControllerMode(true)
        resize(240, 640)
        val skip = player.findViewById<View>(R.id.yummy_skip_skip)
        val watch = player.findViewById<View>(R.id.yummy_skip_watch)
        skip.isFocusableInTouchMode = true
        watch.isFocusableInTouchMode = true
        assertTrue(skip.requestFocus())
        assertTrue(player.handleRemoteInputAction(InputActionEvent(InputAction.Down)))
        assertTrue(watch.hasFocus())
        assertTrue(player.isSkipOnlyControllerMode())
        assertTrue(player.handleRemoteInputAction(InputActionEvent(InputAction.Up)))
        assertTrue(skip.hasFocus())

        resize(640, 320)
        assertTrue(player.handleRemoteInputAction(InputActionEvent(InputAction.Down)))
        assertFalse(player.isSkipOnlyControllerMode())
        assertEquals(View.GONE, watch.visibility)
    }

    @Test
    fun openingAndEndingKeepOnlySkipWhenControlsReopenAfterWatching() = withPlayer { player, resize, _ ->
        for (kind in listOf(VideoSkipKind.Opening, VideoSkipKind.Ending)) {
            val segment = VideoSkipSegment(kind, 0L, 90_000L)
            val prompt = ActiveSkipPrompt(segment.key, segment)
            player.setTag(R.id.yummy_player_active_skip_key, prompt.key)
            player.setTag(R.id.yummy_player_active_skip_segment, prompt)
            player.setSkipControlsActive(true)
            player.setSkipOnlyControllerMode(true)
            player.showPlayerControls()
            resize(640, 320)
            assertEquals(View.VISIBLE, player.findViewById<View>(R.id.yummy_skip_watch).visibility)

            player.hidePlayerControls()
            player.showPlayerControls()
            resize(640, 320)
            assertEquals(View.GONE, player.findViewById<View>(R.id.yummy_skip_watch).visibility)
            assertEquals(View.VISIBLE, player.findViewById<View>(R.id.yummy_skip_skip).visibility)
            assertTrue(player.findViewById<View>(R.id.yummy_skip_skip).isClickable)
            assertEquals(prompt.key, player.tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment)?.key)
        }
    }

    @Test
    fun automaticHidingRestoresBothFloatingActions() = withPlayer { player, resize, _ ->
        val segment = VideoSkipSegment(VideoSkipKind.Opening, 0L, 90_000L)
        val prompt = ActiveSkipPrompt(segment.key, segment)
        player.setTag(R.id.yummy_player_active_skip_key, prompt.key)
        player.setTag(R.id.yummy_player_active_skip_segment, prompt)
        player.setSkipControlsActive(true)
        player.setSkipOnlyControllerMode(false)
        resize(640, 320)
        assertEquals(View.GONE, player.findViewById<View>(R.id.yummy_skip_watch).visibility)
        player.autoHidePlayerControls()
        resize(640, 320)
        assertTrue(player.isSkipOnlyControllerMode())
        assertEquals(View.VISIBLE, player.findViewById<View>(R.id.yummy_skip_watch).visibility)
    }

    private fun PlayerView.bounds(view: View): Rect = Rect(0, 0, view.width, view.height).also {
        val playerLocation = IntArray(2)
        val viewLocation = IntArray(2)
        getLocationOnScreen(playerLocation)
        view.getLocationOnScreen(viewLocation)
        it.offset(viewLocation[0] - playerLocation[0], viewLocation[1] - playerLocation[1])
    }

    private fun withPlayer(block: (PlayerView, (Int, Int) -> Unit, (Int) -> Int) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val themed = ContextThemeWrapper(activity, R.style.Theme_YummyDroid_Player)
            val player = LayoutInflater.from(themed).inflate(R.layout.yummy_player_view, null) as PlayerView
            activity.setContentView(player)
            player.setControllerAnimationEnabled(false)
            player.showPlayerControls()
            player.findViewById<TextView>(R.id.yummy_player_title).text = "Длинное название аниме ".repeat(10)
            player.findViewById<TextView>(R.id.yummy_player_subtitle).text = "AniLibria · Серия 1 из 12"
            val density = themed.resources.displayMetrics.density
            val px: (Int) -> Int = { (it * density).roundToInt() }
            val resize: (Int, Int) -> Unit = { width, height ->
                repeat(2) {
                    shadowOf(Looper.getMainLooper()).idle()
                    player.measure(View.MeasureSpec.makeMeasureSpec(px(width), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(px(height), View.MeasureSpec.EXACTLY))
                    player.layout(0, 0, px(width), px(height))
                }
            }
            block(player, resize, px)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
