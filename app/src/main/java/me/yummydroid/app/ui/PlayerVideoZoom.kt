package me.yummydroid.app.ui

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import kotlin.math.abs
import me.yummydroid.app.R

@OptIn(UnstableApi::class)
internal fun PlayerView.installVideoZoomGestures(token: String) {
    val currentToken = tagValue<String>(R.id.yummy_video_zoom_token_tag)
    val currentState = tagValue<VideoZoomGestureState>(R.id.yummy_video_zoom_state_tag)
    val state = if (currentToken == token && currentState != null) {
        currentState
    } else {
        resetVideoZoom()
        VideoZoomGestureState().also { newState ->
            setTag(R.id.yummy_video_zoom_token_tag, token)
            setTag(R.id.yummy_video_zoom_state_tag, newState)
        }
    }

    val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val previousScale = state.scale
                state.scale = (state.scale * detector.scaleFactor).coerceIn(1f, 4f)
                if (state.scale <= 1.01f) {
                    state.scale = 1f
                    state.offsetX = 0f
                    state.offsetY = 0f
                } else if (previousScale > 0f) {
                    val scaleRatio = state.scale / previousScale
                    state.offsetX = (state.offsetX * scaleRatio) + ((detector.focusX - width / 2f) * (1f - scaleRatio))
                    state.offsetY = (state.offsetY * scaleRatio) + ((detector.focusY - height / 2f) * (1f - scaleRatio))
                }
                applyVideoZoom(state)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                if (state.scale <= 1.01f) {
                    state.scale = 1f
                    state.offsetX = 0f
                    state.offsetY = 0f
                    applyVideoZoom(state)
                }
            }
        },
    )

    fun handleVideoGesture(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && isInsideInteractivePlayerControl(event)) {
            state.handlingTouch = false
            return false
        }
        if (event.actionMasked != MotionEvent.ACTION_DOWN && !state.handlingTouch) {
            return false
        }

        scaleDetector.onTouchEvent(event)
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                state.handlingTouch = true
                setTag(R.id.yummy_player_last_touch_down_at, SystemClock.uptimeMillis())
                clearPlayerControlFocusAfterTouch()
                state.lastX = event.x
                state.lastY = event.y
                state.moved = false
                true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                hidePlayerControls()
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1 || scaleDetector.isInProgress) {
                    true
                } else if (state.scale > 1f) {
                    val dx = event.x - state.lastX
                    val dy = event.y - state.lastY
                    state.lastX = event.x
                    state.lastY = event.y
                    if (abs(dx) > 0.5f || abs(dy) > 0.5f) {
                        state.offsetX += dx
                        state.offsetY += dy
                        state.moved = state.moved || abs(dx) > 6f || abs(dy) > 6f
                        applyVideoZoom(state)
                    }
                    true
                } else {
                    val dx = event.x - state.lastX
                    val dy = event.y - state.lastY
                    state.moved = state.moved || abs(dx) > 6f || abs(dy) > 6f
                    true
                }
            }
            MotionEvent.ACTION_UP -> {
                state.handlingTouch = false
                if (state.scale > 1f && !state.moved) {
                    showPlayerControls()
                    clearPlayerControlFocusAfterTouch()
                    true
                } else if (state.scale <= 1f && !state.moved) {
                    if (hasVisiblePlayerControls()) {
                        hidePlayerControls()
                    } else {
                        showPlayerControls()
                        clearPlayerControlFocusAfterTouch()
                    }
                    true
                } else {
                    state.scale > 1f
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                state.handlingTouch = false
                true
            }
            else -> event.pointerCount > 1 || state.scale > 1f
        }
    }

    val touchListener = View.OnTouchListener { view, event ->
        val clickDetected = event.actionMasked == MotionEvent.ACTION_UP && !state.moved
        val handled = handleVideoGesture(event)
        if (handled && clickDetected) {
            view.performClick()
        }
        handled
    }

    (this as? YummyPlayerView)?.videoGestureHandler = ::handleVideoGesture

    if (this is YummyPlayerView) {
        post { applyVideoZoom(state) }
        return
    }

    fun installTouchListenerTargets() {
        setOnTouchListener(touchListener)
        findViewById<View>(Media3R.id.exo_content_frame)?.setTouchListenerRecursively(touchListener)
        findViewById<View>(Media3R.id.exo_overlay)?.setTouchListenerRecursively(touchListener)
        findViewById<View>(Media3R.id.exo_subtitles)?.setTouchListenerRecursively(touchListener)
        videoSurfaceView?.setOnTouchListener(touchListener)
    }
    installTouchListenerTargets()
    post {
        installTouchListenerTargets()
        applyVideoZoom(state)
    }
    postDelayed({ installTouchListenerTargets() }, 250L)
    postDelayed({ installTouchListenerTargets() }, 1_000L)
}

private fun PlayerView.isInsideInteractivePlayerControl(event: MotionEvent): Boolean {
    if (!hasVisiblePlayerControls()) return false
    return playerControlIds.any { id ->
        findViewById<View>(id)?.let { control ->
            control.isVisible && control.isEnabled && control.containsRawPoint(event.rawX, event.rawY)
        } == true
    }
}

private fun View.containsRawPoint(rawX: Float, rawY: Float): Boolean {
    val location = IntArray(2)
    getLocationOnScreen(location)
    return rawX >= location[0] &&
        rawX <= location[0] + width &&
        rawY >= location[1] &&
        rawY <= location[1] + height
}

private fun View.setTouchListenerRecursively(listener: View.OnTouchListener) {
    setOnTouchListener(listener)
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).setTouchListenerRecursively(listener)
        }
    }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.applyVideoZoom(state: VideoZoomGestureState) {
    val surface = videoSurfaceView ?: return
    val scale = state.scale.coerceIn(1f, 4f)
    val maxOffsetX = surface.width * (scale - 1f) / 2f
    val maxOffsetY = surface.height * (scale - 1f) / 2f
    state.offsetX = if (maxOffsetX > 0f) state.offsetX.coerceIn(-maxOffsetX, maxOffsetX) else 0f
    state.offsetY = if (maxOffsetY > 0f) state.offsetY.coerceIn(-maxOffsetY, maxOffsetY) else 0f

    surface.pivotX = surface.width / 2f
    surface.pivotY = surface.height / 2f
    surface.scaleX = scale
    surface.scaleY = scale
    surface.translationX = state.offsetX
    surface.translationY = state.offsetY
}

@OptIn(UnstableApi::class)
internal fun PlayerView.resetVideoZoom() {
    tagValue<VideoZoomGestureState>(R.id.yummy_video_zoom_state_tag)?.let { state ->
        state.scale = 1f
        state.offsetX = 0f
        state.offsetY = 0f
        state.moved = false
        state.handlingTouch = false
    }
    videoSurfaceView?.apply {
        scaleX = 1f
        scaleY = 1f
        translationX = 0f
        translationY = 0f
    }
}
