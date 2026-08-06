package me.yummydroid.app.ui

import android.graphics.Rect
import android.view.KeyEvent
import android.view.View
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import me.yummydroid.app.R

internal fun View?.playerFocusableTarget(): View? {
    return this?.takeIf { it.isVisible && it.isShown && it.isEnabled && it.isFocusable && it.width > 0 && it.height > 0 }
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
    return playerControlIds
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
