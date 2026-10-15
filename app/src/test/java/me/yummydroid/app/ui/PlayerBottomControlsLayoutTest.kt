package me.yummydroid.app.ui

import android.app.Activity
import android.app.Application
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.yummydroid.app.R
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerBottomControlsLayoutTest {
    @Test
    @Config(qualifiers = "w360dp-h640dp-port-mdpi")
    fun portraitActionsStayCompactAndReachable() = verifyLayout(360, 640)

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-mdpi")
    fun landscapeActionsStayCompactAndReachable() = verifyLayout(640, 360)

    @Test
    @Config(qualifiers = "sw800dp-w800dp-h1280dp-port-mdpi")
    fun tabletActionsStayGrouped() = verifyLayout(800, 1280)

    @Test
    @Config(qualifiers = "w360dp-h640dp-port-mdpi")
    fun restoringFromSmallViewportDoesNotAddStockTimelineSpacing() {
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = activityController.get()
            val context = ContextThemeWrapper(activity, R.style.Theme_YummyDroid_Player)
            val root = FrameLayout(context)
            val player = LayoutInflater.from(context).inflate(R.layout.yummy_player_view, root, false) as PlayerView
            root.addView(player)
            activity.setContentView(root)
            player.setControllerAnimationEnabled(false)
            player.showController()
            for ((width, height) in listOf(320 to 120, 360 to 640)) {
                root.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                )
                root.layout(0, 0, width, height)
                shadowOf(Looper.getMainLooper()).idle()
            }
            val timeline = player.findViewById<View>(androidx.media3.ui.R.id.exo_progress)
            assertEquals(0, (timeline.layoutParams as android.view.ViewGroup.MarginLayoutParams).bottomMargin)
            val bar = player.findViewById<View>(androidx.media3.ui.R.id.exo_bottom_bar)
            val scroll = player.findViewById<View>(R.id.yummy_player_bottom_scroll)
            assertTrue(scroll.bottom <= bar.height)
        } finally {
            activityController.pause().stop().destroy()
        }
    }

    private fun verifyLayout(widthDp: Int, heightDp: Int) {
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = activityController.get()
        val context = ContextThemeWrapper(activity, R.style.Theme_YummyDroid_Player)
        val density = context.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).roundToInt()
        val root = FrameLayout(context)
        val player = LayoutInflater.from(context).inflate(R.layout.yummy_player_view, root, false) as PlayerView
        root.addView(player)
        activity.setContentView(root)
        player.setControllerAnimationEnabled(false)
        player.showController()
        val actionIds = listOf(
            R.id.yummy_player_quality, R.id.yummy_player_source, R.id.yummy_player_voice,
            R.id.yummy_player_subtitles, R.id.yummy_player_subscription, R.id.yummy_player_speed,
            R.id.yummy_player_cast, R.id.yummy_player_pip,
        )
        val actions = actionIds.map { player.findViewById<View>(it).apply { visibility = View.VISIBLE } }
        player.findViewById<TextView>(androidx.media3.ui.R.id.exo_position).text = "00:22"
        player.findViewById<TextView>(androidx.media3.ui.R.id.exo_duration).text = "23:03"
        player.findViewById<TextView>(R.id.yummy_player_info).text = "CVH"
        root.measure(
            View.MeasureSpec.makeMeasureSpec(px(widthDp), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(px(heightDp), View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        shadowOf(Looper.getMainLooper()).idle()

        actions.forEach {
            assertEquals(px(48), it.width)
            assertEquals(px(48), it.height)
        }
        actions.zipWithNext().forEach { (left, right) ->
            assertEquals(px(4), right.left - left.right, "Actions must stay grouped as the viewport grows")
        }
        val position = player.findViewById<View>(androidx.media3.ui.R.id.exo_position)
        val duration = player.findViewById<View>(androidx.media3.ui.R.id.exo_duration)
        assertEquals(position.top, duration.top)
        assertTrue(duration.left >= position.right, "Time labels must share a horizontal row")

        val bar = player.findViewById<View>(androidx.media3.ui.R.id.exo_bottom_bar)
        val scroll = player.findViewById<View>(R.id.yummy_player_bottom_content).parent as HorizontalScrollView
        assertTrue(scroll.bottom <= bar.height, "Actions must fit below the timeline")
        scroll.isSmoothScrollingEnabled = false
        if (widthDp == 360) {
            player.installVideoZoomGestures("bottom-scroll-test")
            val bounds = Rect(0, 0, scroll.width, scroll.height)
            player.offsetDescendantRectToMyCoords(scroll, bounds)
            val downTime = SystemClock.uptimeMillis()
            listOf(
                MotionEvent.ACTION_DOWN to 120,
                MotionEvent.ACTION_MOVE to 80,
                MotionEvent.ACTION_MOVE to 20,
                MotionEvent.ACTION_UP to 20,
            ).forEachIndexed { index, (action, xDp) ->
                val event = MotionEvent.obtain(
                    downTime, downTime + index * 30L, action,
                    px(xDp).toFloat(), bounds.exactCenterY(), 0,
                )
                player.dispatchTouchEvent(event)
                event.recycle()
            }
            assertTrue(scroll.scrollX > 0, "Dragging the time labels must scroll actions instead of moving the video")
        }
        actions.forEach { action ->
            action.requestRectangleOnScreen(Rect(0, 0, action.width, action.height), true)
            val bounds = Rect(0, 0, action.width, action.height)
            player.offsetDescendantRectToMyCoords(action, bounds)
            assertTrue(bounds.left >= 0 && bounds.right <= player.width, "Action must be reachable: $bounds")
        }
        activityController.pause().stop().destroy()
    }
}
