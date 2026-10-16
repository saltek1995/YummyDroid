package me.yummydroid.app.ui

import android.app.Activity
import android.app.Application
import android.graphics.Rect
import android.os.Looper
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.yummydroid.app.R
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerBottomControlsLayoutTest {
    private val actionIds = listOf(
        R.id.yummy_player_quality, R.id.yummy_player_source, R.id.yummy_player_voice,
        R.id.yummy_player_subtitles, R.id.yummy_player_subscription, R.id.yummy_player_speed,
        R.id.yummy_player_cast, R.id.yummy_player_pip,
    )

    @Test
    @Config(qualifiers = "w360dp-h640dp-port-mdpi")
    fun sixCommonActionsAreVisibleWithoutScrolling() = verifyActions(
        windows = listOf(360 to 640),
        visible = actionIds.filterNot { it in setOf(R.id.yummy_player_subtitles, R.id.yummy_player_subscription) },
    )

    @Test
    @Config(qualifiers = "w360dp-h640dp-port-mdpi")
    fun allActionsWrapInsidePortraitViewports() = verifyActions(
        windows = listOf(240 to 640, 320 to 640, 360 to 640),
        visible = actionIds,
    )

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-mdpi")
    fun allActionsFitInWideLandscapeWithoutScrolling() = verifyActions(
        windows = listOf(640 to 360),
        visible = actionIds,
    )

    @Test
    @Config(qualifiers = "w492dp-h246dp-land-260dpi")
    fun fractionalDensityKeepsWrappedActionsReachableAfterRepeatedResizes() = verifyActions(
        windows = listOf(492 to 246, 240 to 640, 360 to 640, 492 to 246),
        visible = actionIds,
    )

    private fun verifyActions(windows: List<Pair<Int, Int>>, visible: List<Int>) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val context = ContextThemeWrapper(activity, R.style.Theme_YummyDroid_Player)
            val density = context.resources.displayMetrics.density
            fun px(dp: Int) = (dp * density).roundToInt()
            val root = FrameLayout(context)
            val player = LayoutInflater.from(context).inflate(R.layout.yummy_player_view, root, false) as PlayerView
            root.addView(player)
            activity.setContentView(root)
            player.setControllerAnimationEnabled(false)
            player.showController()
            actionIds.forEach { id -> player.findViewById<View>(id).visibility = if (id in visible) View.VISIBLE else View.GONE }
            player.findViewById<TextView>(Media3R.id.exo_position).text = "123:45"
            player.findViewById<TextView>(Media3R.id.exo_duration).text = "678:90"

            for ((width, height) in windows) {
                root.measure(View.MeasureSpec.makeMeasureSpec(px(width), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(px(height), View.MeasureSpec.EXACTLY))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                shadowOf(Looper.getMainLooper()).idle()

                val footer = player.bounds(player.findViewById(Media3R.id.exo_bottom_bar))
                assertEquals(player.height, footer.bottom)
                assertTrue(footer.height() >= px(88), "Footer must grow for wrapped rows at ${width}x$height")
                val actions = visible.map { player.findViewById<View>(it) }
                actions.forEach { action ->
                    assertEquals(px(48), action.width)
                    assertEquals(px(48), action.height)
                    val bounds = player.bounds(action)
                    assertTrue(bounds.left >= 0 && bounds.right <= player.width && bounds.top >= 0 && bounds.bottom <= player.height,
                        "Action must already be fully visible at ${width}x$height: $bounds")
                }
                actions.forEachIndexed { index, action ->
                    actions.drop(index + 1).forEach { other ->
                        assertTrue(!Rect.intersects(player.bounds(action), player.bounds(other)), "Action touch rectangles must not overlap")
                    }
                }
                val rows = actions.groupBy { player.bounds(it).top }.values
                rows.forEach { unsortedRow ->
                    val row = unsortedRow.sortedBy { player.bounds(it).left }
                    row.zipWithNext().forEach { (left, right) ->
                        assertEquals(px(4), player.bounds(right).left - player.bounds(left).right)
                    }
                    val rowBounds = row.map { player.bounds(it) }.reduce { left, right ->
                        Rect(minOf(left.left, right.left), minOf(left.top, right.top),
                            maxOf(left.right, right.right), maxOf(left.bottom, right.bottom))
                    }
                    assertTrue(kotlin.math.abs(rowBounds.centerX() - player.width / 2) <= 1,
                        "Every action row must be centered at ${width}x$height")
                }
                val expectedRows = if (actions.size == 6 || width >= 492) 1 else 2
                assertEquals(expectedRows, rows.size, "Action grid row count at ${width}x$height")
                val position = player.findViewById<TextView>(Media3R.id.exo_position)
                val duration = player.findViewById<TextView>(Media3R.id.exo_duration)
                val progress = player.findViewById<View>(Media3R.id.exo_progress)
                val timeline = player.findViewById<View>(R.id.yummy_player_timeline_row)
                val positionBounds = player.bounds(position)
                val durationBounds = player.bounds(duration)
                val progressBounds = player.bounds(progress)
                assertEquals(px(12), positionBounds.left)
                assertEquals(player.width - px(12), durationBounds.right)
                assertTrue(position.isShown && duration.isShown)
                assertEquals(1, position.lineCount)
                assertEquals(1, duration.lineCount)
                assertTrue(positionBounds.right <= progressBounds.left && progressBounds.right <= durationBounds.left)
                assertTrue(progressBounds.width() >= px(48))
                assertTrue(kotlin.math.abs(positionBounds.centerY() - progressBounds.centerY()) <= 1)
                assertTrue(kotlin.math.abs(durationBounds.centerY() - progressBounds.centerY()) <= 1)
                assertTrue(position.layout.getEllipsisCount(0) == 0 && duration.layout.getEllipsisCount(0) == 0)
                assertTrue(position.paint.measureText(position.text.toString()) <= position.width - position.paddingLeft - position.paddingRight)
                assertTrue(duration.paint.measureText(duration.text.toString()) <= duration.width - duration.paddingLeft - duration.paddingRight)
                val firstActionTop = actions.minOf { player.bounds(it).top }
                assertTrue(positionBounds.bottom <= firstActionTop && durationBounds.bottom <= firstActionTop)
                assertTrue(player.bounds(timeline).bottom <= firstActionTop)
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun PlayerView.bounds(view: View): Rect = Rect(0, 0, view.width, view.height).also {
        val playerLocation = IntArray(2)
        val viewLocation = IntArray(2)
        getLocationOnScreen(playerLocation)
        view.getLocationOnScreen(viewLocation)
        it.offset(viewLocation[0] - playerLocation[0], viewLocation[1] - playerLocation[1])
    }
}
