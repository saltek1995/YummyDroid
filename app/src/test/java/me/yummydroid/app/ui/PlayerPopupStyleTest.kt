package me.yummydroid.app.ui

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent
import me.yummydroid.app.R
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], application = Application::class, qualifiers = "w640dp-h360dp-land-mdpi")
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerPopupStyleTest {
    @Test fun longListReservesScrollbarGutterAndCompactSelectionMarker() = verifyStyle()

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-260dpi")
    fun fractionalDensityKeepsLabelsInsideFocusAndScrollbarBounds() = verifyStyle()

    private fun verifyStyle() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val themed = ContextThemeWrapper(activity, R.style.Theme_YummyDroid_Player)
            val player = LayoutInflater.from(themed).inflate(R.layout.yummy_player_view, null) as PlayerView
            activity.setContentView(player)
            val density = themed.resources.displayMetrics.density
            val width = (640 * density).toInt()
            val height = (360 * density).toInt()
            fun layout() {
                repeat(2) {
                    shadowOf(Looper.getMainLooper()).idle()
                    player.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    player.layout(0, 0, width, height)
                }
            }
            player.setControllerAnimationEnabled(false)
            player.showPlayerControls()
            layout()
            val anchor = player.findViewById<View>(R.id.yummy_player_source)
            val popup = PopupMenu(themed, anchor)
            repeat(40) { index -> popup.menu.add(0, index, index, "Вариант ${index.toString().padStart(2, '0')}").isChecked = index == 0 }
            var clicked: Int? = null
            popup.setOnMenuItemClickListener { clicked = it.itemId; true }
            popup.show()
            layout()
            val host = player.ensurePlayerPopupHost()
            val list = (0 until host.childCount).map { host.getChildAt(it) }.filterIsInstance<ListView>().single()
            assertEquals(View.SCROLLBARS_INSIDE_INSET, list.scrollBarStyle)
            assertTrue(list.isVerticalScrollBarEnabled)
            assertTrue(list.childCount > 1 && list.childCount < list.count)
            val listInset = (8 * density).toInt()
            assertTrue(list.paddingRight >= listInset + list.scrollBarSize)
            for (index in 0 until list.childCount) {
                val row = list.getChildAt(index) as FrameLayout
                val marker = row.getChildAt(0)
                val label = row.getChildAt(1) as TextView
                assertTrue(row.right <= list.width - list.paddingRight)
                assertEquals((12 * density).toInt(), label.left)
                assertTrue(marker.right < label.left, "Selection marker must share the existing row inset")
                assertEquals(0, label.layout.getEllipsisCount(0), "Width calculation must preserve the complete label")
            }
            val checked = list.getChildAt(0) as FrameLayout
            fun markerPixel(): Int {
                val marker = checked.getChildAt(0)
                val bitmap = Bitmap.createBitmap(checked.width, checked.height, Bitmap.Config.ARGB_8888)
                checked.draw(Canvas(bitmap))
                return bitmap.getPixel(marker.left + marker.width / 2, marker.top + marker.height / 2)
                    .also { bitmap.recycle() }
            }
            assertEquals(1f, checked.getChildAt(0).alpha)
            assertEquals(PLAYER_ACCENT_CONTENT_COLOR, markerPixel(), "Checked marker must be drawn inside the compact inset")
            assertEquals(0f, (list.getChildAt(1) as FrameLayout).getChildAt(0).alpha)
            assertTrue(player.handleRemoteInputAction(InputActionEvent(InputAction.Down)))
            assertEquals(1f, checked.getChildAt(0).alpha, "Committed choice stays marked after focus moves")
            assertEquals(PLAYER_ACCENT_COLOR, markerPixel())
            assertEquals(0f, (list.getChildAt(1) as FrameLayout).getChildAt(0).alpha)
            assertTrue(player.handleRemoteInputAction(InputActionEvent(InputAction.Confirm)))
            assertEquals(1, clicked)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
