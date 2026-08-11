package me.yummydroid.app.ui

import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent
import me.yummydroid.app.R

@Suppress("UNCHECKED_CAST")
internal fun PlayerView.requestPlayCallback(): (() -> Unit)? {
    return getTag(R.id.yummy_player_request_play_callback) as? (() -> Unit)
}

@Suppress("UNCHECKED_CAST")
internal fun PlayerView.pausePlaybackCallback(): (() -> Unit)? {
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
    if (activateFocusedSkipControl(action, skipButton, watchButton)) return true
    if (action == InputAction.Back) {
        hideVisiblePlayerControls()
        return true
    }
    val movedInsideSkipPrompt = moveInsideSkipPrompt(action, skipButton, watchButton)
    cancelSkipAutoCountdown()
    if (movedInsideSkipPrompt) return true

    restoreStandardControlsFromSkipMode(action, skipButton, watchButton)
    return true
}

private fun activateFocusedSkipControl(
    action: InputAction,
    skipButton: View?,
    watchButton: View?,
): Boolean {
    if (action != InputAction.Confirm) return false
    val focusedButton = when {
        skipButton?.hasFocus() == true -> skipButton
        watchButton?.hasFocus() == true -> watchButton
        else -> null
    }
    focusedButton ?: return false
    focusedButton.performClick()
    return true
}

private fun moveInsideSkipPrompt(
    action: InputAction,
    skipButton: View?,
    watchButton: View?,
): Boolean {
    return when (action) {
        InputAction.Right -> {
            if (skipButton?.hasFocus() == true) watchButton?.requestFocus() == true else false
        }
        InputAction.Left -> {
            if (watchButton?.hasFocus() == true) skipButton?.requestFocus() == true else false
        }
        else -> false
    }
}

private fun PlayerView.restoreStandardControlsFromSkipMode(
    action: InputAction,
    skipButton: View?,
    watchButton: View?,
) {
    val timeBar = findViewById<View>(Media3R.id.exo_progress)
    setSkipOnlyControllerMode(false)
    showPlayerControls()
    post {
        if (requestTimelineFocusFromSkipPrompt(action, skipButton, watchButton, timeBar)) {
            return@post
        }
        requestDefaultPlayerControlFocus()
    }
}

private fun requestTimelineFocusFromSkipPrompt(
    action: InputAction,
    skipButton: View?,
    watchButton: View?,
    timeBar: View?,
): Boolean {
    if (action != InputAction.Down) return false
    val skipPromptFocused = skipButton?.hasFocus() == true || watchButton?.hasFocus() == true
    if (!skipPromptFocused) return false
    return timeBar?.requestFocus() == true
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
    if (action == InputAction.Confirm && timelineHasFocus()) {
        return confirmTimelineScrubOrTogglePlayback(
            onRequestPlay = requestPlay,
            onPausePlayback = pausePlayback,
        )
    }
    return handleDirectionalPlayerInput(event)
        ?: handlePlaybackPlayerInput(action, requestPlay, pausePlayback)
}

private fun PlayerView.timelineHasFocus(): Boolean {
    return findViewById<View>(Media3R.id.exo_progress)?.hasFocus() == true
}

private fun PlayerView.handleDirectionalPlayerInput(event: InputActionEvent): Boolean? {
    return when (event.action) {
        InputAction.Back -> hideVisiblePlayerControls()
        InputAction.Up,
        InputAction.Down,
        InputAction.Confirm -> preparePlayerControlsForNavigation()
        InputAction.Left,
        InputAction.Right -> handleHorizontalPlayerInput(event)
        else -> null
    }
}

private fun PlayerView.handleHorizontalPlayerInput(event: InputActionEvent): Boolean {
    if (preparePlayerControlsForNavigation()) return true
    return seekTimelineIfFocused(
        forward = event.action == InputAction.Right,
        repeatedInput = event.isRepeated,
    )
}

private fun PlayerView.handlePlaybackPlayerInput(
    action: InputAction,
    requestPlay: (() -> Unit)?,
    pausePlayback: (() -> Unit)?,
): Boolean {
    return when (action) {
        InputAction.Play -> {
            requestPlay?.invoke() ?: player?.play()
            true
        }
        InputAction.Pause -> {
            pausePlayback?.invoke() ?: player?.pause()
            true
        }
        InputAction.PlayPause -> togglePlayerPlayback(requestPlay, pausePlayback)
        else -> false
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

internal fun PlayerView.togglePlayerPlayback(
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
