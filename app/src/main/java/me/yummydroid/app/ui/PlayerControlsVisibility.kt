package me.yummydroid.app.ui

import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import me.yummydroid.app.R

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
internal fun PlayerView.keepVisiblePlayerControlsAwake() {
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
    return playerChromeIds.any { id ->
        findViewById<View>(id)?.let { view ->
            view.isVisible && view.isShown
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

private val PlayerControlChromeInterpolator = LinearInterpolator()
private const val PLAYER_CONTROLS_FADE_IN_MS = 340L
private const val PLAYER_CONTROLS_FADE_OUT_MS = 340L

private fun PlayerView.playerControlChromeViews(): List<View> {
    val views = ArrayList<View>(playerChromeIds.size)
    playerChromeIds.forEach { id ->
        findViewById<View>(id)?.let(views::add)
    }
    return views.distinctBy { view -> view.id }
}

private fun PlayerView.hasDisplayedPlayerControlChrome(): Boolean {
    return playerControlChromeViews().any { control ->
        control.isVisible && control.isShown && control.alpha > 0.01f
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
    return playerControlIds.any { id -> findViewById<View>(id)?.hasFocus() == true }
}

@OptIn(UnstableApi::class)
internal fun PlayerView.clearPlayerControlFocus() {
    playerControlIds.forEach { id ->
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
