package me.yummydroid.app.ui

import android.content.res.ColorStateList
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Pause
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.view.isVisible
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import androidx.media3.ui.TimeBar
import kotlin.math.abs
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoSkipSegment
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.formatPlaybackTime
import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent
import me.yummydroid.app.R
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.normalizedSkipSegments
import me.yummydroid.app.playbackSourceKey

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

    val touchListener = View.OnTouchListener { _, event ->
        handleVideoGesture(event)
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
    return playerInteractiveControlIds.any { id ->
        findViewById<View>(id)?.let { control ->
            control.isVisible && control.isEnabled && control.containsRawPoint(event.rawX, event.rawY)
        } == true
    }
}

private val playerInteractiveControlIds = intArrayOf(
    R.id.yummy_player_back,
    R.id.yummy_episode_previous,
    Media3R.id.exo_play_pause,
    R.id.yummy_episode_next,
    R.id.yummy_skip_skip,
    R.id.yummy_skip_watch,
    Media3R.id.exo_progress,
    R.id.yummy_player_quality,
    R.id.yummy_player_source,
    R.id.yummy_player_voice,
    R.id.yummy_player_subtitles,
    R.id.yummy_player_subscription,
    R.id.yummy_player_speed,
    R.id.yummy_player_pip,
)

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

@OptIn(UnstableApi::class)
internal fun PlayerView.applyPictureInPictureControllerMode(enabled: Boolean) {
    useController = !enabled
    controllerAutoShow = false
    if (enabled) {
        hidePlayerControls()
    }
    requestLayout()
    invalidate()
}

@OptIn(UnstableApi::class)
internal fun PlayerView.restoreControllerAfterPictureInPicture() {
    useController = true
    controllerAutoShow = false
    hidePlayerControls()
    requestLayout()
    post {
        requestLayout()
        invalidate()
        postDelayed({ showPlayerControls() }, 220L)
    }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.showPlayerControls() {
    if (!useController) return
    removeTaggedRunnable(R.id.yummy_player_controls_hide_runnable)
    removeTaggedRunnable(R.id.yummy_player_controls_auto_hide_runnable)
    val hadDisplayedChrome = hasDisplayedPlayerControlChrome()
    setTag(R.id.yummy_player_controls_visible, true)
    setControllerShowTimeoutMs(0)
    showController()
    fadePlayerControlChrome(visible = true, fromHidden = !hadDisplayedChrome)
    schedulePlayerControlsAutoHide()
}

@OptIn(UnstableApi::class)
private fun PlayerView.keepVisiblePlayerControlsAwake() {
    if (hasVisiblePlayerControls()) {
        showPlayerControls()
    }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.hasVisiblePlayerControls(): Boolean {
    if (isControllerFullyVisible || hasDisplayedPlayerControlChrome()) return true
    tagValue<Boolean>(R.id.yummy_player_controls_visible)?.let { knownVisible ->
        return knownVisible
    }
    return listOf(
        Media3R.id.exo_controls_background,
        R.id.yummy_player_top_bar,
        R.id.yummy_player_episode_controls,
        R.id.yummy_skip_controls,
        Media3R.id.exo_bottom_bar,
    ).any { id ->
        findViewById<View>(id)?.let { view ->
            view.visibility == View.VISIBLE && view.isShown
        } == true
    }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.hidePlayerControls() {
    cancelSkipAutoCountdown()
    clearActiveSkipPrompt(markDismissed = true)
    setTag(R.id.yummy_player_controls_visible, false)
    removeTaggedRunnable(R.id.yummy_player_controls_hide_runnable)
    removeTaggedRunnable(R.id.yummy_player_controls_auto_hide_runnable)
    if (!useController) {
        hideController()
        setPlayerControlChromeAlpha(0f)
        return
    }
    fadePlayerControlChrome(visible = false, fromHidden = false)
    val hideRunnable = Runnable {
        clearTagValue(R.id.yummy_player_controls_hide_runnable)
        hideController()
        setPlayerControlChromeAlpha(0f)
    }
    setTag(R.id.yummy_player_controls_hide_runnable, hideRunnable)
    postDelayed(hideRunnable, PLAYER_CONTROLS_FADE_OUT_MS)
}

@OptIn(UnstableApi::class)
internal fun PlayerView.hideVisiblePlayerControls(): Boolean {
    if (!hasVisiblePlayerControls()) return false
    hidePlayerControls()
    return true
}

internal fun PlayerView.applyPlayerControlIconColors() {
    listOf(
        R.id.yummy_player_back,
        R.id.yummy_episode_previous,
        Media3R.id.exo_play_pause,
        R.id.yummy_episode_next,
    ).forEach { id ->
        findViewById<ImageButton>(id)?.imageTintList = playerControlContentColors(active = false)
    }
}

internal fun View?.playerFocusableTarget(): View? {
    return this?.takeIf { it.isVisible && it.isShown && it.isEnabled && it.isFocusable && it.width > 0 && it.height > 0 }
}

private val PlayerControlChromeInterpolator = LinearInterpolator()
private const val PLAYER_CONTROLS_FADE_IN_MS = 340L
private const val PLAYER_CONTROLS_FADE_OUT_MS = 340L

private val playerControlChromeIds = intArrayOf(
    Media3R.id.exo_controls_background,
    R.id.yummy_player_top_bar,
    R.id.yummy_player_episode_controls,
    R.id.yummy_skip_controls,
    Media3R.id.exo_bottom_bar,
)

private fun PlayerView.playerControlChromeViews(): List<View> {
    val views = ArrayList<View>(playerControlChromeIds.size)
    playerControlChromeIds.forEach { id ->
        findViewById<View>(id)?.let(views::add)
    }
    return views.distinctBy { view -> view.id }
}

private fun PlayerView.hasDisplayedPlayerControlChrome(): Boolean {
    return playerControlChromeViews().any { control ->
        control.visibility == View.VISIBLE && control.isShown && control.alpha > 0.01f
    }
}

private fun PlayerView.fadePlayerControlChrome(
    visible: Boolean,
    fromHidden: Boolean,
) {
    val targetAlpha = if (visible) 1f else 0f
    playerControlChromeViews().forEach { control ->
        control.animate().cancel()
        if (visible && fromHidden) {
            control.alpha = 0f
        }
        control.animate()
            .alpha(targetAlpha)
            .setDuration(if (visible) PLAYER_CONTROLS_FADE_IN_MS else PLAYER_CONTROLS_FADE_OUT_MS)
            .setInterpolator(PlayerControlChromeInterpolator)
            .withLayer()
            .start()
    }
}

internal fun PlayerView.setPlayerControlChromeAlpha(alpha: Float) {
    playerControlChromeViews().forEach { control ->
        control.animate().cancel()
        control.alpha = alpha
    }
}

private fun PlayerView.schedulePlayerControlsAutoHide() {
    removeTaggedRunnable(R.id.yummy_player_controls_auto_hide_runnable)
    if (player == null || isSkipOnlyControllerMode()) return
    val hideRunnable = Runnable {
        clearTagValue(R.id.yummy_player_controls_auto_hide_runnable)
        hidePlayerControls()
    }
    setTag(R.id.yummy_player_controls_auto_hide_runnable, hideRunnable)
    postDelayed(hideRunnable, PLAYER_CONTROLS_AUTO_HIDE_MS)
}

@OptIn(UnstableApi::class)
internal fun PlayerView.requestDefaultPlayerControlFocus(): Boolean {
    val timeBar = findViewById<View>(Media3R.id.exo_progress)?.apply {
        isFocusable = true
        isFocusableInTouchMode = false
    }
    return timeBar.playerFocusableTarget()?.requestFocus() == true ||
        findViewById<View>(Media3R.id.exo_play_pause).playerFocusableTarget()?.requestFocus() == true ||
        requestFocus()
}

@OptIn(UnstableApi::class)
internal fun PlayerView.hasFocusedPlayerControl(): Boolean {
    return listOf(
        R.id.yummy_player_back,
        R.id.yummy_episode_previous,
        Media3R.id.exo_play_pause,
        R.id.yummy_episode_next,
        R.id.yummy_skip_skip,
        R.id.yummy_skip_watch,
        Media3R.id.exo_progress,
        R.id.yummy_player_quality,
        R.id.yummy_player_source,
        R.id.yummy_player_voice,
        R.id.yummy_player_subtitles,
        R.id.yummy_player_subscription,
        R.id.yummy_player_speed,
        R.id.yummy_player_pip,
    ).any { id -> findViewById<View>(id)?.hasFocus() == true }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.clearPlayerControlFocus() {
    listOf(
        R.id.yummy_player_back,
        R.id.yummy_episode_previous,
        Media3R.id.exo_play_pause,
        R.id.yummy_episode_next,
        R.id.yummy_skip_skip,
        R.id.yummy_skip_watch,
        Media3R.id.exo_progress,
        R.id.yummy_player_quality,
        R.id.yummy_player_source,
        R.id.yummy_player_voice,
        R.id.yummy_player_subtitles,
        R.id.yummy_player_subscription,
        R.id.yummy_player_speed,
        R.id.yummy_player_pip,
    ).forEach { id ->
        findViewById<View>(id)?.clearFocus()
    }
    clearFocus()
}

@OptIn(UnstableApi::class)
internal fun PlayerView.clearPlayerControlFocusAfterTouch() {
    post {
        if (isInTouchMode) {
            clearPlayerControlFocus()
        }
    }
    postDelayed(
        {
            if (isInTouchMode) {
                clearPlayerControlFocus()
            }
        },
        PLAYER_TOUCH_FOCUS_CLEAR_DELAY_MS,
    )
}

internal fun PlayerView.hasRecentPlayerTouch(): Boolean {
    val lastTouchAt = tagValue<Long>(R.id.yummy_player_last_touch_down_at) ?: return false
    return SystemClock.uptimeMillis() - lastTouchAt <= PLAYER_TOUCH_FOCUS_CLEAR_WINDOW_MS
}

@Suppress("UNCHECKED_CAST")
private fun PlayerView.requestPlayCallback(): (() -> Unit)? {
    return getTag(R.id.yummy_player_request_play_callback) as? (() -> Unit)
}

@Suppress("UNCHECKED_CAST")
private fun PlayerView.pausePlaybackCallback(): (() -> Unit)? {
    return getTag(R.id.yummy_player_pause_callback) as? (() -> Unit)
}

@OptIn(UnstableApi::class)
internal fun PlayerView.installPlayerControlsVisibilitySync() {
    setControllerVisibilityListener(
        PlayerView.ControllerVisibilityListener { visibility ->
            val visible = visibility == View.VISIBLE
            val wasVisible = tagValue<Boolean>(R.id.yummy_player_controls_visible) == true
            setTag(R.id.yummy_player_controls_visible, visible)
            if (visible && hasRecentPlayerTouch()) {
                clearPlayerControlFocusAfterTouch()
            }
            if (visible && !wasVisible) {
                removeTaggedRunnable(R.id.yummy_player_controls_hide_runnable)
                fadePlayerControlChrome(visible = true, fromHidden = !hasDisplayedPlayerControlChrome())
                schedulePlayerControlsAutoHide()
            }
            if (!visible) {
                removeTaggedRunnable(R.id.yummy_player_controls_hide_runnable)
                removeTaggedRunnable(R.id.yummy_player_controls_auto_hide_runnable)
                cancelSkipAutoCountdown()
                if (isSkipOnlyControllerMode()) {
                    setSkipOnlyControllerMode(false)
                }
                setPlayerControlChromeAlpha(0f)
            }
        },
    )
}

@OptIn(UnstableApi::class)
internal fun PlayerView.handleRemoteInputAction(
    event: InputActionEvent,
    onRequestPlay: (() -> Unit)? = null,
    onPausePlayback: (() -> Unit)? = null,
): Boolean {
    val requestPlay = onRequestPlay ?: requestPlayCallback()
    val pausePlayback = onPausePlayback ?: pausePlaybackCallback()
    val action = event.action
    if (!useController) return false
    if (isSkipOnlyControllerMode()) {
        val skipButton = findViewById<View>(R.id.yummy_skip_skip)
        val watchButton = findViewById<View>(R.id.yummy_skip_watch)
        val timeBar = findViewById<View>(Media3R.id.exo_progress)
        if (action == InputAction.Confirm && skipButton?.hasFocus() == true) {
            skipButton.performClick()
            return true
        }
        if (action == InputAction.Confirm && watchButton?.hasFocus() == true) {
            watchButton.performClick()
            return true
        }
        if (action == InputAction.Back) {
            hideVisiblePlayerControls()
            return true
        }
        val movedInsideSkipPrompt = when {
            action == InputAction.Right && skipButton?.hasFocus() == true -> watchButton?.requestFocus() == true
            action == InputAction.Left && watchButton?.hasFocus() == true -> skipButton.requestFocus()
            else -> false
        }
        cancelSkipAutoCountdown()
        if (movedInsideSkipPrompt) {
            return true
        }
        setSkipOnlyControllerMode(false)
        showPlayerControls()
        post {
            if (
                action == InputAction.Down &&
                (skipButton?.hasFocus() == true || watchButton?.hasFocus() == true) &&
                timeBar?.requestFocus() == true
            ) {
                return@post
            }
            requestDefaultPlayerControlFocus()
        }
        return true
    }
    if (action != InputAction.Back) {
        keepVisiblePlayerControlsAwake()
    }
    cancelSkipAutoCountdown()
    if (action == InputAction.Confirm && findViewById<View>(Media3R.id.exo_progress)?.hasFocus() == true) {
        return confirmTimelineScrubOrTogglePlayback(
            onRequestPlay = requestPlay,
            onPausePlayback = pausePlayback,
        )
    }
    return when (action) {
        InputAction.Back -> hideVisiblePlayerControls()
        InputAction.Up,
        InputAction.Down,
        InputAction.Confirm -> {
            if (!hasVisiblePlayerControls()) {
                showPlayerControls()
                post { requestDefaultPlayerControlFocus() }
                true
            } else if (!hasFocusedPlayerControl()) {
                requestDefaultPlayerControlFocus()
                true
            } else {
                false
            }
        }
        InputAction.Left,
        InputAction.Right -> {
            if (!hasVisiblePlayerControls()) {
                showPlayerControls()
                post { requestDefaultPlayerControlFocus() }
                true
            } else if (!hasFocusedPlayerControl()) {
                requestDefaultPlayerControlFocus()
                true
            } else {
                seekTimelineIfFocused(
                    forward = action == InputAction.Right,
                    repeatedInput = event.isRepeated,
                )
            }
        }
        InputAction.Play -> {
            requestPlay?.invoke() ?: player?.play()
            true
        }
        InputAction.Pause -> {
            pausePlayback?.invoke() ?: player?.pause()
            true
        }
        InputAction.PlayPause -> {
            player?.let { currentPlayer ->
                if (currentPlayer.isPlaying) {
                    pausePlayback?.invoke() ?: currentPlayer.pause()
                } else {
                    requestPlay?.invoke() ?: currentPlayer.play()
                }
                true
            } ?: (findViewById<View>(Media3R.id.exo_play_pause)?.performClick() == true)
        }
        InputAction.PreviousEpisode,
        InputAction.NextEpisode -> false
    }
}

internal fun PlayerView.isSkipOnlyControllerMode(): Boolean {
    return tagValue<Boolean>(R.id.yummy_player_skip_only_mode) == true
}

@OptIn(UnstableApi::class)
internal fun PlayerView.setSkipOnlyControllerMode(enabled: Boolean) {
    setTag(R.id.yummy_player_skip_only_mode, enabled)
    setControllerShowTimeoutMs(0)
    if (enabled) {
        removeTaggedRunnable(R.id.yummy_player_controls_auto_hide_runnable)
    } else if (hasVisiblePlayerControls()) {
        schedulePlayerControlsAutoHide()
    }
    findViewById<View>(Media3R.id.exo_controls_background)?.visibility = if (enabled) View.GONE else View.VISIBLE
    findViewById<View>(R.id.yummy_player_top_bar)?.visibility = if (enabled) View.GONE else View.VISIBLE
    findViewById<View>(R.id.yummy_player_episode_controls)?.visibility = if (enabled) View.GONE else View.VISIBLE
    findViewById<View>(Media3R.id.exo_bottom_bar)?.visibility = if (enabled) View.GONE else View.VISIBLE
}

@OptIn(UnstableApi::class)
internal fun PlayerView.seekTimelineIfFocused(
    forward: Boolean,
    repeatedInput: Boolean,
): Boolean {
    val timeBarView = findViewById<View>(Media3R.id.exo_progress) ?: return false
    if (!timeBarView.hasFocus()) return false

    val currentPlayer = player ?: return false
    val duration = currentPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return false
    val now = SystemClock.uptimeMillis()
    val direction = if (forward) 1 else -1
    val state = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
        ?: TimelineScrubState(pendingPositionMs = currentPlayer.currentPosition.coerceIn(0L, duration))
    state.clearRunnable?.let(::removeCallbacks)
    state.clearRunnable = null

    val keepsHoldingSameDirection = repeatedInput && state.lastDirection == direction
    state.repeatedInputCount = if (keepsHoldingSameDirection) state.repeatedInputCount + 1 else 1
    state.lastDirection = direction
    state.lastInputAtMs = now
    state.generation += 1
    state.pendingPositionMs = (state.pendingPositionMs + direction.toLong() * state.stepMs(duration)).coerceIn(0L, duration)
    setTag(R.id.yummy_player_timeline_manual_until, now + PLAYER_TIMELINE_MANUAL_FREEZE_MS)

    state.commitRunnable?.let(::removeCallbacks)
    val commitGeneration = state.generation
    val commitRunnable = object : Runnable {
        override fun run() {
            val latestState = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
                ?: return
            if (latestState.generation != commitGeneration) return

            val elapsedSinceInputMs = SystemClock.uptimeMillis() - latestState.lastInputAtMs
            if (elapsedSinceInputMs < PLAYER_TIMELINE_SCRUB_COMMIT_DELAY_MS) {
                postDelayed(this, PLAYER_TIMELINE_SCRUB_COMMIT_DELAY_MS - elapsedSinceInputMs)
                return
            }

            val targetPositionMs = latestState.pendingPositionMs.coerceIn(0L, duration)
            currentPlayer.seekTo(targetPositionMs)
            latestState.pendingPositionMs = targetPositionMs
            latestState.repeatedInputCount = 0
            latestState.commitRunnable = null
            renderTimelineScrubPosition(latestState)
            val freezeUntil = SystemClock.uptimeMillis() + PLAYER_TIMELINE_MANUAL_FREEZE_MS
            setTag(R.id.yummy_player_timeline_manual_until, freezeUntil)
            val clearRunnable = object : Runnable {
                override fun run() {
                    val currentState = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
                    if (currentState !== latestState) return
                    if (isTimelineManuallyControlled()) {
                        postDelayed(this, 50L)
                        return
                    }
                    clearTimelineScrubState()
                }
            }
            latestState.clearRunnable = clearRunnable
            postDelayed(clearRunnable, PLAYER_TIMELINE_MANUAL_FREEZE_MS)
        }
    }
    state.commitRunnable = commitRunnable
    setTag(R.id.yummy_player_timeline_scrub_state, state)
    renderTimelineScrubPosition(state)
    post {
        val latestState = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
        if (latestState?.generation == commitGeneration) {
            renderTimelineScrubPosition(latestState)
        }
    }
    holdTimelineScrubPosition()
    postDelayed(commitRunnable, PLAYER_TIMELINE_SCRUB_COMMIT_DELAY_MS)
    return true
}

@OptIn(UnstableApi::class)
internal fun PlayerView.confirmTimelineScrubOrTogglePlayback(
    onRequestPlay: (() -> Unit)? = null,
    onPausePlayback: (() -> Unit)? = null,
): Boolean {
    val requestPlay = onRequestPlay ?: requestPlayCallback()
    val pausePlayback = onPausePlayback ?: pausePlaybackCallback()
    val currentPlayer = player
    val state = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
    if (currentPlayer != null && state != null && isTimelineManuallyControlled()) {
        state.commitRunnable?.let(::removeCallbacks)
        state.clearRunnable?.let(::removeCallbacks)
        state.commitRunnable = null
        state.clearRunnable = null
        val duration = currentPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            ?: currentPlayer.contentDuration.takeIf { it != C.TIME_UNSET && it > 0L }
        val targetPositionMs = duration?.let { state.pendingPositionMs.coerceIn(0L, it) }
            ?: state.pendingPositionMs.coerceAtLeast(0L)
        state.pendingPositionMs = targetPositionMs
        currentPlayer.seekTo(targetPositionMs)
        renderTimelineScrubPosition(state)
        clearTimelineScrubState()
        return true
    }

    return currentPlayer?.let { playback ->
        if (playback.isPlaying) {
            pausePlayback?.invoke() ?: playback.pause()
        } else {
            requestPlay?.invoke() ?: playback.play()
        }
        true
    } ?: (findViewById<View>(Media3R.id.exo_play_pause)?.performClick() == true)
}

@OptIn(UnstableApi::class)
internal fun PlayerView.renderTimelineScrubPosition(state: TimelineScrubState) {
    (findViewById<View>(Media3R.id.exo_progress) as? TimeBar)?.setPosition(state.pendingPositionMs)
    findViewById<TextView>(Media3R.id.exo_position)?.text = formatPlaybackTime(state.pendingPositionMs)
}

internal fun PlayerView.isTimelineManuallyControlled(): Boolean {
    val until = tagValue<Long>(R.id.yummy_player_timeline_manual_until) ?: return false
    return SystemClock.uptimeMillis() < until
}

@OptIn(UnstableApi::class)
internal fun PlayerView.holdTimelineScrubPosition() {
    if (tagValue<Runnable>(R.id.yummy_player_timeline_hold_runnable) != null) return
    val runnable = object : Runnable {
        override fun run() {
            val latestState = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
            if (latestState == null || !isTimelineManuallyControlled()) {
                clearTagValue(R.id.yummy_player_timeline_hold_runnable)
                return
            }
            renderTimelineScrubPosition(latestState)
            postOnAnimation(this)
        }
    }
    setTag(R.id.yummy_player_timeline_hold_runnable, runnable)
    postOnAnimation(runnable)
}

internal fun PlayerView.clearTimelineScrubState() {
    tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)?.commitRunnable?.let(::removeCallbacks)
    tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)?.clearRunnable?.let(::removeCallbacks)
    removeTaggedRunnable(R.id.yummy_player_timeline_hold_runnable)
    clearTagValue(R.id.yummy_player_timeline_scrub_state)
    clearTagValue(R.id.yummy_player_timeline_manual_until)
}

internal data class TimelineScrubState(
    var pendingPositionMs: Long,
    var repeatedInputCount: Int = 0,
    var lastDirection: Int = 0,
    var generation: Int = 0,
    var lastInputAtMs: Long = 0L,
    var commitRunnable: Runnable? = null,
    var clearRunnable: Runnable? = null,
) {
    fun stepMs(durationMs: Long): Long {
        val requestedStep = when {
            repeatedInputCount <= 3 -> PLAYER_TIMELINE_BASE_STEP_MS
            repeatedInputCount <= 7 -> 10_000L
            repeatedInputCount <= 13 -> 30_000L
            else -> 60_000L
        }
        val maxStep = (durationMs / PLAYER_TIMELINE_MAX_STEP_DIVISOR).coerceAtLeast(1_000L)
        return requestedStep.coerceAtMost(maxStep)
    }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.bindYummyController(
    player: ExoPlayer,
    animeTitle: String,
    currentVideo: VideoVariant,
    isLocalPlayback: Boolean,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    sourceOptions: List<SourceOption>,
    selectedSourceKey: String?,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    allowSubscription: Boolean,
    subscriptionActive: Boolean,
    onToggleSubscription: () -> Unit,
    qualityOptions: List<QualityOption>,
    selectedQualityKey: String?,
    onSelectedQualityKeyChange: (String) -> Unit,
    subtitleOptions: List<SubtitleOption>,
    subtitlesLoading: Boolean,
    selectedSubtitleKey: String,
    onSelectedSubtitleKeyChange: (String) -> Unit,
    onSelectLocalQuality: (OfflineVideoFile) -> Unit,
    onSelectPreferredQuality: (PreferredQuality) -> Unit,
    onSelectGroup: (String, VideoVariant?, Long) -> Unit,
    onSelectSource: (VideoVariant, Long) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    canUsePictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    settings: AppSettings,
    texts: PlayerControlTexts,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onRequestPlay: () -> Unit = { player.play() },
    onPausePlayback: () -> Unit = { player.pause() },
    onRememberPlayerControlFocus: (Int) -> Unit = {},
) {
    setTag(R.id.yummy_player_request_play_callback, onRequestPlay)
    setTag(R.id.yummy_player_pause_callback, onPausePlayback)
    applyPlayerControlIconColors()
    findViewById<TextView>(R.id.yummy_player_title)?.text = animeTitle.ifBlank { texts.title }
    findViewById<TextView>(R.id.yummy_player_subtitle)?.text =
        currentVideo.playbackSubtitle(texts, groups.values.flatten())
    findViewById<TextView>(R.id.yummy_player_info)?.text =
        currentVideo.playbackSourceLabel(isLocalPlayback)

    findViewById<View>(Media3R.id.exo_settings)?.visibility = View.GONE
    findViewById<View>(R.id.yummy_player_back)?.setOnClickListener { onBack() }
    findViewById<View>(Media3R.id.exo_play_pause)?.setOnClickListener {
        if (player.isPlaying) {
            onPausePlayback()
        } else {
            onRequestPlay()
        }
    }

    findViewById<View>(R.id.yummy_episode_previous)?.apply {
        visibility = if (previousVideo != null) View.VISIBLE else View.GONE
        setOnClickListener {
            previousVideo?.let {
                showVoiceFallbackToast(context, currentVideo, it)
                onPausePlayback()
                onPlayVideoAt(it, 0L)
            }
        }
    }

    findViewById<View>(R.id.yummy_episode_next)?.apply {
        visibility = if (nextVideo != null) View.VISIBLE else View.GONE
        setOnClickListener {
            nextVideo?.let {
                showVoiceFallbackToast(context, currentVideo, it)
                onPausePlayback()
                onPlayVideoAt(it, 0L)
            }
        }
    }

    findViewById<ImageButton>(R.id.yummy_player_voice)?.apply {
        applyPlayerIconControl(R.drawable.ic_player_voice, texts.voice)
        visibility = View.VISIBLE
        setPlayerControlEnabled(groups.size > 1)
        setOnClickListener {
            if (groups.size <= 1) return@setOnClickListener
            showPlayerControls()
            showVoicePopup(
                anchor = this,
                groups = groups,
                selectedKey = selectedKey,
                preferredGroupKey = currentVideo.groupKey,
                currentVideo = currentVideo,
                texts = texts,
                onRememberPlayerControlFocus = onRememberPlayerControlFocus,
                onSelectGroup = { groupKey, replacement ->
                    onPausePlayback()
                    onSelectGroup(groupKey, replacement, player.currentPosition.coerceAtLeast(0L))
                },
            )
        }
    }

    findViewById<ImageButton>(R.id.yummy_player_source)?.apply {
        applyPlayerIconControl(R.drawable.ic_player_source, texts.source)
        visibility = View.VISIBLE
        setPlayerControlEnabled(sourceOptions.size > 1)
        setOnClickListener {
            if (sourceOptions.size <= 1) return@setOnClickListener
            showPlayerControls()
            showSourcePopup(
                anchor = this,
                options = sourceOptions,
                selectedSourceKey = selectedSourceKey,
                onRememberPlayerControlFocus = onRememberPlayerControlFocus,
                onSelectSource = { source ->
                    onPausePlayback()
                    onSelectSource(source, player.currentPosition.coerceAtLeast(0L))
                },
            )
        }
    }

    findViewById<TextView>(R.id.yummy_player_quality)?.apply {
        val qualityTitle = qualityOptions.selectedQualityControlText(selectedQualityKey)
        applyPlayerQualityControl(qualityTitle, "${texts.quality}: $qualityTitle")
        visibility = View.VISIBLE
        setPlayerControlEnabled(qualityOptions.isNotEmpty())
        setOnClickListener {
            if (qualityOptions.isEmpty()) return@setOnClickListener
            showPlayerControls()
            showQualityPopup(
                anchor = this,
                player = player,
                options = qualityOptions,
                selectedQualityKey = selectedQualityKey,
                onSelectedQualityKeyChange = onSelectedQualityKeyChange,
                onSelectLocalQuality = onSelectLocalQuality,
                onSelectPreferredQuality = onSelectPreferredQuality,
                onRememberPlayerControlFocus = onRememberPlayerControlFocus,
            )
        }
    }

    findViewById<ImageButton>(R.id.yummy_player_subtitles)?.apply {
        val label = if (subtitlesLoading && subtitleOptions.isEmpty()) "${texts.subtitles}..." else texts.subtitles
        applyPlayerIconControl(
            iconResId = R.drawable.ic_player_subtitles,
            label = label,
            active = selectedSubtitleKey != SUBTITLE_OFF_KEY && subtitleOptions.isNotEmpty(),
        )
        visibility = View.VISIBLE
        setPlayerControlEnabled(subtitleOptions.isNotEmpty())
        setOnClickListener {
            if (subtitleOptions.isEmpty()) return@setOnClickListener
            showPlayerControls()
            showSubtitlePopup(
                anchor = this,
                player = player,
                options = subtitleOptions,
                selectedSubtitleKey = selectedSubtitleKey,
                texts = texts,
                onRememberPlayerControlFocus = onRememberPlayerControlFocus,
                onSelectedSubtitleKeyChange = onSelectedSubtitleKeyChange,
            )
        }
    }

    findViewById<ImageButton>(R.id.yummy_player_subscription)?.apply {
        applyPlayerIconControl(
            iconResId = R.drawable.ic_player_subscription,
            label = if (subscriptionActive) texts.subscribed else texts.subscription,
            active = subscriptionActive,
        )
        visibility = View.VISIBLE
        setPlayerControlEnabled(allowSubscription)
        setOnClickListener {
            if (!allowSubscription) return@setOnClickListener
            showPlayerControls()
            onToggleSubscription()
        }
    }

    findViewById<ImageButton>(R.id.yummy_player_speed)?.apply {
        applyPlayerIconControl(
            iconResId = R.drawable.ic_player_speed,
            label = "${context.getString(R.string.player_speed)}: ${settings.playerSpeed.title}",
        )
        visibility = View.VISIBLE
        setOnClickListener {
            showPlayerControls()
            showSpeedPopup(
                anchor = this,
                selected = settings.playerSpeed,
                onRememberPlayerControlFocus = onRememberPlayerControlFocus,
                onSelected = { onSettingsChange(settings.copy(playerSpeed = it)) },
            )
        }
    }

    findViewById<ImageButton>(R.id.yummy_player_pip)?.apply {
        applyPlayerIconControl(R.drawable.ic_player_pip, context.getString(R.string.player_pip))
        visibility = if (canUsePictureInPicture) View.VISIBLE else View.GONE
        setOnClickListener {
            hidePlayerControls()
            postDelayed({ onEnterPictureInPicture() }, PIP_ENTER_DELAY_MS)
        }
    }

    if (settings.skipOpeningsAndEndings) {
        bindSkipControls(player = player, currentVideo = currentVideo, texts = texts)
    } else {
        unbindSkipControls()
    }
    bindSkipTimelineMarkers(player = player, currentVideo = currentVideo)
    configurePlayerFocusNavigation()
}

internal fun PlayerView.restorePlayerControlFocus(controlId: Int?): Boolean {
    if (controlId == null || isInTouchMode) return false
    removeTaggedRunnable(R.id.yummy_player_focus_restore_runnable)
    showPlayerControls()
    return findViewById<View>(controlId)
        .playerFocusableTarget()
        ?.requestFocus() == true
}

internal fun PlayerView.restorePlayerControlFocusWhenReady(
    controlId: Int?,
    onRestored: () -> Unit,
) {
    if (controlId == null || isInTouchMode) return
    if (restorePlayerControlFocus(controlId)) {
        onRestored()
        return
    }
    val restoreRunnable = Runnable {
        clearTagValue(R.id.yummy_player_focus_restore_runnable)
        if (restorePlayerControlFocus(controlId)) {
            onRestored()
        }
    }
    setTag(R.id.yummy_player_focus_restore_runnable, restoreRunnable)
    post(restoreRunnable)
}

internal fun PlayerView.configurePlayerFocusNavigation() {
    findViewById<View>(Media3R.id.exo_progress)?.apply {
        isFocusable = true
        isFocusableInTouchMode = false
        applyPlayerTimelineFocusColors()
    }
    installDynamicPlayerFocusNavigation()
}

internal enum class PlayerFocusDirection {
    Left,
    Right,
    Up,
    Down,
}

internal data class PlayerFocusBounds(
    val id: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

private val playerFocusControlIds = intArrayOf(
    R.id.yummy_player_back,
    R.id.yummy_episode_previous,
    Media3R.id.exo_play_pause,
    R.id.yummy_episode_next,
    Media3R.id.exo_progress,
    R.id.yummy_skip_skip,
    R.id.yummy_skip_watch,
    R.id.yummy_player_quality,
    R.id.yummy_player_source,
    R.id.yummy_player_voice,
    R.id.yummy_player_subtitles,
    R.id.yummy_player_subscription,
    R.id.yummy_player_speed,
    R.id.yummy_player_pip,
)

private fun PlayerView.installDynamicPlayerFocusNavigation() {
    val controls = playerFocusTargets()
    val timeBar = findViewById<View>(Media3R.id.exo_progress)
    controls.forEach { control ->
        control.setOnKeyListener { view: View, keyCode: Int, event: KeyEvent ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                return@setOnKeyListener false
            }
            if (view.id == Media3R.id.exo_progress) {
                val isHorizontalSeekKey = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
                    keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                val isConfirmKey = keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                    keyCode == KeyEvent.KEYCODE_ENTER
                if (isHorizontalSeekKey) {
                    seekTimelineIfFocused(
                        forward = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT,
                        repeatedInput = event.repeatCount > 0,
                    )
                    return@setOnKeyListener true
                }
                if (isConfirmKey) {
                    confirmTimelineScrubOrTogglePlayback()
                    return@setOnKeyListener true
                }
            }
            val direction = keyCode.playerFocusDirection() ?: return@setOnKeyListener false
            requestDynamicPlayerFocus(from = view, direction = direction)
        }
    }
    if (timeBar?.playerFocusableTarget() == null) {
        timeBar?.setOnKeyListener(null)
    }
}

private fun PlayerView.playerFocusTargets(): List<View> {
    return playerFocusControlIds
        .asSequence()
        .mapNotNull { id: Int -> findViewById<View>(id).playerFocusableTarget() }
        .distinctBy { view: View -> view.id }
        .toList()
}

private fun Int.playerFocusDirection(): PlayerFocusDirection? {
    return when (this) {
        KeyEvent.KEYCODE_DPAD_LEFT -> PlayerFocusDirection.Left
        KeyEvent.KEYCODE_DPAD_RIGHT -> PlayerFocusDirection.Right
        KeyEvent.KEYCODE_DPAD_UP -> PlayerFocusDirection.Up
        KeyEvent.KEYCODE_DPAD_DOWN -> PlayerFocusDirection.Down
        else -> null
    }
}

private fun PlayerView.requestDynamicPlayerFocus(
    from: View,
    direction: PlayerFocusDirection,
): Boolean {
    val controls = playerFocusTargets()
    val bounds = controls.mapNotNull { view -> view.playerVisibleFocusBounds() }
    val targetId = playerFocusDirectionalTarget(
        bounds = bounds,
        sourceId = from.id,
        direction = direction,
    )
    val target = controls.firstOrNull { view -> view.id == targetId }
    return if (target != null) {
        target.requestFocus()
        true
    } else {
        true
    }
}

internal fun playerFocusDirectionalTarget(
    bounds: Collection<PlayerFocusBounds>,
    sourceId: Int,
    direction: PlayerFocusDirection,
): Int? {
    return visualFocusDirectionalTarget(
        bounds = bounds.map { item ->
            VisualFocusBounds(
                index = item.id,
                left = item.left.toFloat(),
                top = item.top.toFloat(),
                right = item.right.toFloat(),
                bottom = item.bottom.toFloat(),
            )
        },
        sourceIndex = sourceId,
        direction = direction.toVisualGridDirection(),
        allowLoosePerpendicularMatch = true,
    )
}

private fun PlayerFocusDirection.toVisualGridDirection(): VisualGridDirection {
    return when (this) {
        PlayerFocusDirection.Left -> VisualGridDirection.Left
        PlayerFocusDirection.Right -> VisualGridDirection.Right
        PlayerFocusDirection.Up -> VisualGridDirection.Up
        PlayerFocusDirection.Down -> VisualGridDirection.Down
    }
}

private fun View.playerVisibleFocusBounds(): PlayerFocusBounds? {
    val rect = Rect()
    if (!getGlobalVisibleRect(rect)) return null
    if (rect.width() <= 0 || rect.height() <= 0) return null
    return PlayerFocusBounds(
        id = id,
        left = rect.left,
        top = rect.top,
        right = rect.right,
        bottom = rect.bottom,
    )
}

@OptIn(UnstableApi::class)
internal fun View.applyPlayerTimelineFocusColors() {
    val timeBar = this as? DefaultTimeBar ?: return
    timeBar.defaultFocusHighlightEnabled = false
    fun update(focused: Boolean) {
        val accent = if (focused) PLAYER_ACCENT_COLOR else android.graphics.Color.WHITE
        timeBar.setScrubberColor(accent)
        timeBar.setPlayedColor(accent)
    }
    update(hasFocus())
    setOnFocusChangeListener { _, focused -> update(focused) }
}

internal fun PlayerView.configureSkipFocusNavigation(active: Boolean) {
    findViewById<View>(Media3R.id.exo_progress)?.apply {
        isFocusable = true
        isFocusableInTouchMode = false
    }
    setSkipControlsActive(active)
    installDynamicPlayerFocusNavigation()
}

internal fun PlayerView.setSkipControlsActive(active: Boolean) {
    findViewById<View>(R.id.yummy_skip_controls)?.visibility = if (active) View.VISIBLE else View.GONE
    listOf(R.id.yummy_skip_skip, R.id.yummy_skip_watch).forEach { id ->
        findViewById<View>(id)?.apply {
            isEnabled = active
            isFocusable = active
            isClickable = active
            if (!active && hasFocus()) {
                clearFocus()
            }
        }
    }
}

internal fun TextView.applyPlayerSubscriptionState(active: Boolean) {
    applyPlayerToggleState(active)
}

internal fun ImageButton.applyPlayerIconControl(
    @DrawableRes iconResId: Int,
    label: CharSequence,
    active: Boolean = false,
) {
    contentDescription = label
    backgroundTintList = null
    setBackgroundResource(R.drawable.player_center_control_background)
    setImageResource(iconResId)
    imageTintList = playerControlContentColors(active)
}

internal fun TextView.applyPlayerToggleState(active: Boolean) {
    backgroundTintList = null
    setBackgroundResource(R.drawable.player_center_control_background)
    val colors = playerControlContentColors(active)
    setTextColor(colors)
    compoundDrawableTintList = colors
}

internal fun TextView.applyPlayerQualityControl(
    title: String,
    label: CharSequence,
) {
    text = title
    contentDescription = label
    applyPlayerToggleState(active = false)
}

internal fun playerControlContentColors(active: Boolean): ColorStateList {
    return ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused),
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_pressed),
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(),
        ),
        intArrayOf(
            PLAYER_ACCENT_CONTENT_COLOR,
            PLAYER_ACCENT_CONTENT_COLOR,
            0x66F3F6FA,
            if (active) PLAYER_ACCENT_COLOR else PLAYER_CONTROL_CONTENT_COLOR,
        ),
    )
}

internal fun List<QualityOption>.selectedQualityControlText(selectedQualityKey: String?): String {
    val selected = firstOrNull { it.matchesSelectedQualityKey(selectedQualityKey) }
    val height = selected?.height?.takeIf { it > 0 }
    if (height != null) return "${height}p"
    return selected?.label?.compactQualityControlText()
        ?: selectedQualityKey?.compactQualityControlText()
        ?: PLAYER_AUTO_QUALITY_LABEL
}

private fun String.compactQualityControlText(): String? {
    val explicitHeight = Regex("""(?i)(2160|1440|1080|720|576|540|480|360|240|144)\s*p""")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
    if (explicitHeight != null) return "${explicitHeight}p"
    if (contains("auto", ignoreCase = true)) return PLAYER_AUTO_QUALITY_LABEL
    if (contains("adaptive", ignoreCase = true)) return PLAYER_AUTO_QUALITY_LABEL
    return null
}

internal val PLAYER_ACCENT_COLOR: Int = 0xFFFFB454.toInt()
internal val PLAYER_ACCENT_CONTENT_COLOR: Int = 0xFF1B1305.toInt()
internal val PLAYER_CONTROL_CONTENT_COLOR: Int = 0xFFF3F6FA.toInt()
internal const val PLAYER_AUTO_QUALITY_LABEL = "AUTO"

@OptIn(UnstableApi::class)
internal fun PlayerView.bindSkipControls(
    player: ExoPlayer,
    currentVideo: VideoVariant,
    texts: PlayerControlTexts,
) {
    val bindingKey = currentVideo.skipPromptBindingKey()
    if (tagValue<String>(R.id.yummy_player_skip_binding_key) != bindingKey) {
        unbindSkipControls()
        setTag(R.id.yummy_player_skip_binding_key, bindingKey)
        setTag(R.id.yummy_player_skip_dismissed_keys, mutableSetOf<String>())
    }

    val container = findViewById<View>(R.id.yummy_skip_controls) ?: return
    val skipButton = findViewById<TextView>(R.id.yummy_skip_skip) ?: return
    val watchButton = findViewById<TextView>(R.id.yummy_skip_watch) ?: return
    setTag(R.id.yummy_player_skip_text_tag, texts.skip)
    watchButton.text = texts.watch
    removeTaggedRunnable(R.id.yummy_player_skip_poll_runnable)
    if (currentVideo.skipSegments.isEmpty()) {
        clearActiveSkipPrompt(markDismissed = false)
        setSkipControlsActive(false)
        return
    }

    fun dismissActivePrompt() {
        hidePlayerControls()
    }

    fun skipActivePrompt() {
        val prompt = tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment) ?: return
        val targetEndMs = prompt.targetEndMs
        clearActiveSkipPrompt(markDismissed = true)
        hidePlayerControls()
        if (player.currentPosition.coerceAtLeast(0L) < targetEndMs) {
            player.seekTo(targetEndMs)
        }
    }

    fun updateSkipButtonText(state: SkipCountdownState, nowMs: Long = SystemClock.elapsedRealtime()) {
        val remainingSeconds = (((state.deadlineMs - nowMs).coerceAtLeast(0L) + 999L) / 1_000L)
            .toInt()
            .coerceIn(0, SKIP_PROMPT_COUNTDOWN_SECONDS)
        skipButton.text = if (state.autoSkipEnabled) {
            context.getString(R.string.player_skip_countdown, texts.skip, remainingSeconds)
        } else {
            texts.skip
        }
    }

    fun scheduleCountdown(prompt: ActiveSkipPrompt) {
        val startedAtMs = SystemClock.elapsedRealtime()
        val state = SkipCountdownState(
            startedAtMs = startedAtMs,
            deadlineMs = startedAtMs + SKIP_PROMPT_COUNTDOWN_SECONDS * 1_000L,
            autoSkipEnabled = true,
        )
        setTag(R.id.yummy_player_skip_auto_cancelled, state)
        updateSkipButtonText(state)

        fun tick() {
            val activeKey = tagValue<String>(R.id.yummy_player_active_skip_key)
            if (activeKey != prompt.key || !state.autoSkipEnabled) return
            val playerPositionMs = player.currentPosition.coerceAtLeast(0L)
            if (!prompt.hasUsefulSkipAt(playerPositionMs)) {
                clearActiveSkipPrompt(markDismissed = true)
                return
            }
            val nowMs = SystemClock.elapsedRealtime()
            val remainingMs = state.deadlineMs - nowMs
            if (remainingMs <= 0L) {
                updateSkipButtonText(state, state.deadlineMs)
                val finishCountdown = Runnable {
                    val currentKey = tagValue<String>(R.id.yummy_player_active_skip_key)
                    if (currentKey == prompt.key && state.autoSkipEnabled) {
                        skipActivePrompt()
                    }
                }
                setTag(R.id.yummy_player_skip_countdown_runnable, finishCountdown)
                postDelayed(finishCountdown, SKIP_PROMPT_ZERO_DISPLAY_MS)
            } else {
                updateSkipButtonText(state, nowMs)
                val nextTick = Runnable { tick() }
                setTag(R.id.yummy_player_skip_countdown_runnable, nextTick)
                val elapsedMs = (nowMs - state.startedAtMs).coerceAtLeast(0L)
                val nextSecondMs = ((elapsedMs / 1_000L) + 1L) * 1_000L
                val delayMs = (nextSecondMs - elapsedMs).coerceIn(16L, remainingMs)
                postDelayed(nextTick, delayMs)
            }
        }

        val firstTick = Runnable { tick() }
        setTag(R.id.yummy_player_skip_countdown_runnable, firstTick)
        postDelayed(firstTick, 1_000L)
    }

    fun showPrompt(segment: VideoSkipSegment) {
        val key = segment.key
        if (tagValue<String>(R.id.yummy_player_active_skip_key) == key) return
        val cluster = currentVideo.skipSegments.skipPromptCluster(segment)
        val prompt = ActiveSkipPrompt(
            key = key,
            segment = segment,
            dismissKeys = cluster.mapTo(mutableSetOf()) { clusterSegment -> clusterSegment.key },
            activeStartMs = cluster.minOfOrNull { clusterSegment -> clusterSegment.startMs } ?: segment.startMs,
            targetEndMs = cluster.maxOfOrNull { clusterSegment -> clusterSegment.endMs } ?: segment.endMs,
        )
        setTag(R.id.yummy_player_active_skip_key, key)
        setTag(R.id.yummy_player_active_skip_segment, prompt)
        setSkipControlsActive(true)
        showPlayerControls()
        setSkipOnlyControllerMode(true)
        skipButton.setOnClickListener { skipActivePrompt() }
        watchButton.setOnClickListener { dismissActivePrompt() }
        configureSkipFocusNavigation(active = true)
        scheduleCountdown(prompt)
        if (isInTouchMode) {
            clearPlayerControlFocusAfterTouch()
        } else {
            post { skipButton.requestFocus() }
        }
    }

    val pollRunnable = object : Runnable {
        override fun run() {
            val position = player.currentPosition.coerceAtLeast(0L)
            val activePrompt = tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment)
            val countdownState = tagValue<SkipCountdownState>(R.id.yummy_player_skip_auto_cancelled)
            if (
                activePrompt != null &&
                countdownState?.autoSkipEnabled != true &&
                !activePrompt.hasUsefulSkipAt(position)
            ) {
                clearActiveSkipPrompt(markDismissed = true)
            }
            if (container.visibility != View.VISIBLE) {
                val segment = currentVideo.skipSegments.firstOrNull { segment ->
                    segment.key !in dismissedSkipKeys() &&
                        segment.hasUsefulSkipAt(position)
                }
                if (segment != null) {
                    showPrompt(segment)
                }
            }
            postDelayed(this, SKIP_PROMPT_POLL_MS)
        }
    }

    setTag(R.id.yummy_player_skip_poll_runnable, pollRunnable)
    post(pollRunnable)
}

internal fun PlayerView.cancelSkipAutoCountdown() {
    val state = tagValue<SkipCountdownState>(R.id.yummy_player_skip_auto_cancelled) ?: return
    if (!state.autoSkipEnabled) return
    state.autoSkipEnabled = false
    val skipText = tagValue<String>(R.id.yummy_player_skip_text_tag) ?: defaultPlayerControlTexts.skip
    findViewById<TextView>(R.id.yummy_skip_skip)?.text = skipText
    removeTaggedRunnable(R.id.yummy_player_skip_countdown_runnable)
}

internal fun PlayerView.unbindSkipControls() {
    removeTaggedRunnable(R.id.yummy_player_skip_poll_runnable)
    clearActiveSkipPrompt(markDismissed = false)
    clearTagValue(R.id.yummy_player_skip_binding_key)
    clearTagValue(R.id.yummy_player_skip_dismissed_keys)
}

internal fun VideoVariant.skipPromptBindingKey(): String {
    return listOf(
        animeId.toString(),
        matchingEpisodeKey,
        matchingVoiceKey,
        playbackSourceKey,
        skipSegments.skipPromptSignature(),
    ).joinToString("|")
}

internal fun List<VideoSkipSegment>.skipPromptSignature(): String {
    return normalizedSkipSegments()
        .joinToString(";") { segment -> segment.key }
}

internal fun Long.normalizedDurationMs(): Long {
    return takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
}

internal fun View.setPlayerControlEnabled(enabled: Boolean) {
    isEnabled = enabled
    isFocusable = enabled
    alpha = if (enabled) 1f else 0.45f
}
