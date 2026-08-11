package me.yummydroid.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import me.yummydroid.app.R
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
internal class YummyPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : PlayerView(context, attrs, defStyleAttr) {
    var videoGestureHandler: ((MotionEvent) -> Boolean)? = null

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (videoGestureHandler?.invoke(event) == true) {
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        updateControllerViewport(width, height)
    }

    fun updateControllerViewport() {
        updateControllerViewport(width, height)
    }

    private fun updateControllerViewport(width: Int, height: Int) {
        val viewport = findViewById<FrameLayout>(R.id.yummy_player_controls_viewport) ?: return
        val layoutParams = viewport.layoutParams as? FrameLayout.LayoutParams ?: return
        val targetHeight = if (width > 0 && height > width) {
            val videoSize = player?.videoSize
            val videoWidth = videoSize?.let { it.width * it.pixelWidthHeightRatio } ?: 0f
            val videoHeight = videoSize?.height ?: 0
            val videoAspectRatio = if (videoWidth > 0f && videoHeight > 0) {
                videoWidth / videoHeight
            } else {
                DEFAULT_LANDSCAPE_VIDEO_ASPECT_RATIO
            }
            val fittedVideoHeight = (width / videoAspectRatio).roundToInt()
            val minimumHeight = resources.getDimensionPixelSize(
                R.dimen.yummy_player_compact_viewport_min_height,
            )
            maxOf(fittedVideoHeight, minimumHeight).coerceAtMost(height)
        } else {
            ViewGroup.LayoutParams.MATCH_PARENT
        }
        if (layoutParams.height == targetHeight && layoutParams.gravity == Gravity.CENTER) return
        layoutParams.height = targetHeight
        layoutParams.gravity = Gravity.CENTER
        viewport.layoutParams = layoutParams
    }

    private companion object {
        const val DEFAULT_LANDSCAPE_VIDEO_ASPECT_RATIO = 16f / 9f
    }
}
