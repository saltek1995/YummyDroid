package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import me.yummydroid.app.InputActionEvent

/** Connect the Compose error status to the native player's spatial focus graph. */
internal class PlayerShellFocusBridge(private val retryFocus: FocusRequester) {
    var retryBounds: PlayerFocusBounds? = null
    var messageBounds: PlayerFocusBounds? = null
    var retryFocused = false
    var messageFocused = false
    var messageScrollable = false
    var canScroll: (PlayerFocusDirection) -> Boolean = { false }

    @OptIn(UnstableApi::class)
    fun handle(player: PlayerView?, event: InputActionEvent): Boolean {
        if (player == null || player.hasPlayerPopupMenu()) return false
        val direction = event.action.toVisualGridDirectionOrNull() ?: return false
        // Let Compose enter/scroll the message before crossing into the native controls.
        if (retryFocused && messageScrollable && direction == PlayerFocusDirection.Up) return false
        if (messageFocused && (canScroll(direction) || direction == PlayerFocusDirection.Down)) return false
        val statusFocused = retryFocused || messageFocused
        if (!statusFocused && !player.hasFocus()) return false
        if (statusFocused && !player.hasVisiblePlayerControls()) {
            player.showPlayerControls()
            player.post {
                if (player.isAttachedToWindow && (retryFocused || messageFocused)) handle(player, event)
            }
            return true
        }
        val external = (if (messageFocused) messageBounds else retryBounds) ?: return false
        val controls = player.playerFocusTargets()
        val sourceId = if (statusFocused) external.id else player.findFocus()?.id ?: return false
        val target = playerFocusDirectionalTarget(
            controls.mapNotNull { it.playerVisibleFocusBounds() } + external,
            sourceId,
            direction,
        ) ?: return false
        if (target == external.id) return retryFocus.requestFocusSafely()
        if (!statusFocused) return false // Ordinary native navigation, including scrolling rows.
        return controls.firstOrNull { it.id == target }?.requestFocus() == true
    }
}

internal fun Modifier.shellStatusFocusTarget(bridge: PlayerShellFocusBridge?, message: Boolean): Modifier {
    if (bridge == null) return this
    return onFocusChanged {
        if (message) bridge.messageFocused = it.hasFocus else bridge.retryFocused = it.hasFocus
    }.onGloballyPositioned { coordinates ->
        val origin = coordinates.localToScreen(Offset.Zero)
        val bounds = PlayerFocusBounds(
            id = if (message) -3 else -2,
            left = origin.x.toInt(),
            top = origin.y.toInt(),
            right = origin.x.toInt() + coordinates.size.width,
            bottom = origin.y.toInt() + coordinates.size.height,
        )
        if (message) bridge.messageBounds = bounds else bridge.retryBounds = bounds
    }
}
