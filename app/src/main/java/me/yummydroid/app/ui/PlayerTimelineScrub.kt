package me.yummydroid.app.ui

import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import androidx.media3.ui.TimeBar
import me.yummydroid.app.R
import me.yummydroid.app.formatPlaybackTime

@OptIn(UnstableApi::class)
internal fun PlayerView.seekTimelineIfFocused(
    forward: Boolean,
    repeatedInput: Boolean,
): Boolean {
    val timeBarView = findViewById<View>(Media3R.id.exo_progress) ?: return false
    if (!timeBarView.hasFocus()) return false
    val currentPlayer = player ?: return false
    val duration = currentPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return false
    val direction = if (forward) 1 else -1
    val state = updateTimelineScrubState(
        currentPositionMs = currentPlayer.currentPosition,
        durationMs = duration,
        direction = direction,
        repeatedInput = repeatedInput,
    )
    state.commitRunnable?.let(::removeCallbacks)
    val commitGeneration = state.generation
    val commitRunnable = createTimelineCommitRunnable(
        currentPlayer = currentPlayer,
        durationMs = duration,
        commitGeneration = commitGeneration,
    )
    state.commitRunnable = commitRunnable
    setTag(R.id.yummy_player_timeline_scrub_state, state)
    renderTimelineScrubPosition(state)
    postTimelineScrubRender(commitGeneration)
    holdTimelineScrubPosition()
    postDelayed(commitRunnable, PLAYER_TIMELINE_SCRUB_COMMIT_DELAY_MS)
    return true
}

private fun PlayerView.updateTimelineScrubState(
    currentPositionMs: Long,
    durationMs: Long,
    direction: Int,
    repeatedInput: Boolean,
): TimelineScrubState {
    val now = SystemClock.uptimeMillis()
    val state = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
        ?: TimelineScrubState(pendingPositionMs = currentPositionMs.coerceIn(0L, durationMs))
    state.clearRunnable?.let(::removeCallbacks)
    state.clearRunnable = null
    val keepsHoldingSameDirection = repeatedInput && state.lastDirection == direction
    state.repeatedInputCount = if (keepsHoldingSameDirection) state.repeatedInputCount + 1 else 1
    state.lastDirection = direction
    state.lastInputAtMs = now
    state.generation += 1
    state.pendingPositionMs = (
        state.pendingPositionMs + direction.toLong() * state.stepMs(durationMs)
        ).coerceIn(0L, durationMs)
    setTag(R.id.yummy_player_timeline_manual_until, now + PLAYER_TIMELINE_MANUAL_FREEZE_MS)
    return state
}

private fun PlayerView.createTimelineCommitRunnable(
    currentPlayer: Player,
    durationMs: Long,
    commitGeneration: Int,
): Runnable {
    return object : Runnable {
        override fun run() {
            val latestState = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
                ?: return
            if (latestState.generation != commitGeneration) return
            val elapsedSinceInputMs = SystemClock.uptimeMillis() - latestState.lastInputAtMs
            if (elapsedSinceInputMs < PLAYER_TIMELINE_SCRUB_COMMIT_DELAY_MS) {
                postDelayed(this, PLAYER_TIMELINE_SCRUB_COMMIT_DELAY_MS - elapsedSinceInputMs)
                return
            }
            commitTimelinePosition(currentPlayer, durationMs, latestState)
        }
    }
}

private fun PlayerView.commitTimelinePosition(
    currentPlayer: Player,
    durationMs: Long,
    state: TimelineScrubState,
) {
    val targetPositionMs = state.pendingPositionMs.coerceIn(0L, durationMs)
    currentPlayer.seekTo(targetPositionMs)
    state.pendingPositionMs = targetPositionMs
    state.repeatedInputCount = 0
    state.commitRunnable = null
    renderTimelineScrubPosition(state)
    val freezeUntil = SystemClock.uptimeMillis() + PLAYER_TIMELINE_MANUAL_FREEZE_MS
    setTag(R.id.yummy_player_timeline_manual_until, freezeUntil)
    val clearRunnable = createTimelineClearRunnable(state)
    state.clearRunnable = clearRunnable
    postDelayed(clearRunnable, PLAYER_TIMELINE_MANUAL_FREEZE_MS)
}

private fun PlayerView.createTimelineClearRunnable(expectedState: TimelineScrubState): Runnable {
    return object : Runnable {
        override fun run() {
            val currentState = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
            if (currentState !== expectedState) return
            if (isTimelineManuallyControlled()) {
                postDelayed(this, 50L)
                return
            }
            clearTimelineScrubState()
        }
    }
}

private fun PlayerView.postTimelineScrubRender(commitGeneration: Int) {
    post {
        val latestState = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state)
        if (latestState?.generation == commitGeneration) {
            renderTimelineScrubPosition(latestState)
        }
    }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.confirmTimelineScrubOrTogglePlayback(
    onRequestPlay: (() -> Unit)? = null,
    onPausePlayback: (() -> Unit)? = null,
): Boolean {
    val requestPlay = onRequestPlay ?: requestPlayCallback()
    val pausePlayback = onPausePlayback ?: pausePlaybackCallback()
    val currentPlayer = player
    if (currentPlayer != null && commitPendingTimelineScrub(currentPlayer)) return true
    return togglePlayerPlayback(requestPlay, pausePlayback)
}

private fun PlayerView.commitPendingTimelineScrub(currentPlayer: Player): Boolean {
    val state = tagValue<TimelineScrubState>(R.id.yummy_player_timeline_scrub_state) ?: return false
    if (!isTimelineManuallyControlled()) return false
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
