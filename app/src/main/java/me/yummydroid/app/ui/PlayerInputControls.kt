package me.yummydroid.app.ui

import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import androidx.media3.ui.TimeBar
import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent
import me.yummydroid.app.R
import me.yummydroid.app.formatPlaybackTime

@Suppress("UNCHECKED_CAST")
private fun PlayerView.requestPlayCallback(): (() -> Unit)? {
    return getTag(R.id.yummy_player_request_play_callback) as? (() -> Unit)
}

@Suppress("UNCHECKED_CAST")
private fun PlayerView.pausePlaybackCallback(): (() -> Unit)? {
    return getTag(R.id.yummy_player_pause_callback) as? (() -> Unit)
}

@OptIn(UnstableApi::class)
internal fun PlayerView.handleRemoteInputAction(
    event: InputActionEvent,
    onRequestPlay: (() -> Unit)? = null,
    onPausePlayback: (() -> Unit)? = null,
): Boolean {
    val requestPlay = onRequestPlay ?: requestPlayCallback()
    val pausePlayback = onPausePlayback ?: pausePlaybackCallback()
    if (!useController) return false
    if (isSkipOnlyControllerMode()) {
        return handleSkipOnlyInputAction(event.action)
    }
    return handleStandardPlayerInput(event, requestPlay, pausePlayback)
}

private fun PlayerView.handleSkipOnlyInputAction(action: InputAction): Boolean {
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
    if (movedInsideSkipPrompt) return true

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

private fun PlayerView.handleStandardPlayerInput(
    event: InputActionEvent,
    requestPlay: (() -> Unit)?,
    pausePlayback: (() -> Unit)?,
): Boolean {
    val action = event.action
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
        InputAction.Confirm -> preparePlayerControlsForNavigation()
        InputAction.Left,
        InputAction.Right -> {
            if (preparePlayerControlsForNavigation()) {
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
        InputAction.PlayPause -> togglePlayerPlayback(requestPlay, pausePlayback)
        InputAction.PreviousEpisode,
        InputAction.NextEpisode -> false
    }
}

private fun PlayerView.preparePlayerControlsForNavigation(): Boolean {
    if (!hasVisiblePlayerControls()) {
        showPlayerControls()
        post { requestDefaultPlayerControlFocus() }
        return true
    }
    if (!hasFocusedPlayerControl()) {
        requestDefaultPlayerControlFocus()
        return true
    }
    return false
}

private fun PlayerView.togglePlayerPlayback(
    requestPlay: (() -> Unit)?,
    pausePlayback: (() -> Unit)?,
): Boolean {
    return player?.let { currentPlayer ->
        if (currentPlayer.isPlaying) {
            pausePlayback?.invoke() ?: currentPlayer.pause()
        } else {
            requestPlay?.invoke() ?: currentPlayer.play()
        }
        true
    } ?: (findViewById<View>(Media3R.id.exo_play_pause)?.performClick() == true)
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
