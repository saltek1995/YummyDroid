package me.yummydroid.app.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

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
}
