package me.yummydroid.app.ui

import android.graphics.Color
import android.graphics.Typeface
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import me.yummydroid.app.R

@OptIn(UnstableApi::class)
@Composable
internal fun NativePlayerView(
    player: ExoPlayer,
    videoToken: String,
    interactive: Boolean,
    isInPictureInPicture: Boolean,
    controllerBinding: PlayerControllerBinding,
    playerControlFocusToRestoreId: Int?,
    onPlayerViewChanged: (PlayerView) -> Unit,
    onPlayerControlFocusRestored: () -> Unit,
    modifier: Modifier,
) {
    val configuration = LocalConfiguration.current
    val windowSize = currentWindowSizeDp()
    key(
        configuration.orientation,
        windowSize.width,
        windowSize.height,
        configuration.smallestScreenWidthDp,
    ) {
        AndroidView(
            factory = { viewContext ->
                val playerContext = ContextThemeWrapper(viewContext, R.style.Theme_YummyDroid_Player)
                val parent = FrameLayout(playerContext)
                LayoutInflater.from(playerContext).inflate(R.layout.yummy_player_view, parent, false) as PlayerView
            },
            update = { view ->
                onPlayerViewChanged(view)
                view.bindPlayer(
                    player = player,
                    videoToken = videoToken,
                    interactive = interactive,
                    isInPictureInPicture = isInPictureInPicture,
                    controllerBinding = controllerBinding,
                    playerControlFocusToRestoreId = playerControlFocusToRestoreId,
                    onPlayerControlFocusRestored = onPlayerControlFocusRestored,
                )
            },
            modifier = modifier,
        )
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.bindPlayer(
    player: ExoPlayer,
    videoToken: String,
    interactive: Boolean,
    isInPictureInPicture: Boolean,
    controllerBinding: PlayerControllerBinding,
    playerControlFocusToRestoreId: Int?,
    onPlayerControlFocusRestored: () -> Unit,
) {
    attachPlayer(player)
    configurePlayerView(videoToken)
    requestInitialPlayerFocus(interactive, playerControlFocusToRestoreId)
    val restoreAfterPictureInPicture = updatePictureInPictureMode(isInPictureInPicture)
    bindPlayerInteraction(
        interactive = interactive,
        isInPictureInPicture = isInPictureInPicture,
        controllerBinding = controllerBinding,
        playerControlFocusToRestoreId = playerControlFocusToRestoreId,
        onPlayerControlFocusRestored = onPlayerControlFocusRestored,
        restoreAfterPictureInPicture = restoreAfterPictureInPicture,
    )
}

private fun PlayerView.attachPlayer(player: ExoPlayer) {
    if (this.player === player) return
    unbindSkipControls()
    this.player = player
}

@OptIn(UnstableApi::class)
private fun PlayerView.configurePlayerView(videoToken: String) {
    controllerAutoShow = false
    setControllerAnimationEnabled(false)
    setControllerShowTimeoutMs(0)
    installPlayerControlsVisibilitySync()
    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    (this as? YummyPlayerView)?.updateControllerViewport()
    applyYummySubtitleStyle()
    installVideoZoomGestures(token = videoToken)
    keepScreenOn = true
    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
}

private fun PlayerView.requestInitialPlayerFocus(
    interactive: Boolean,
    playerControlFocusToRestoreId: Int?,
) {
    if (!interactive) return
    if (isInTouchMode) return
    if (playerControlFocusToRestoreId != null) return
    if (hasFocusedPlayerControl()) return
    requestFocus()
}

private fun PlayerView.updatePictureInPictureMode(isInPictureInPicture: Boolean): Boolean {
    val previousPictureInPictureMode = tagValue<Boolean>(R.id.yummy_player_view)
    if (previousPictureInPictureMode != isInPictureInPicture) {
        setTag(R.id.yummy_player_view, isInPictureInPicture)
        applyPictureInPictureControllerMode(isInPictureInPicture)
    }
    return previousPictureInPictureMode != false
}

private fun PlayerView.bindPlayerInteraction(
    interactive: Boolean,
    isInPictureInPicture: Boolean,
    controllerBinding: PlayerControllerBinding,
    playerControlFocusToRestoreId: Int?,
    onPlayerControlFocusRestored: () -> Unit,
    restoreAfterPictureInPicture: Boolean,
) {
    when {
        !interactive -> {
            unbindSkipControls()
            hidePlayerControls()
            clearFocus()
        }

        isInPictureInPicture -> hidePlayerControls()
        else -> bindInteractiveController(
            controllerBinding = controllerBinding,
            playerControlFocusToRestoreId = playerControlFocusToRestoreId,
            onPlayerControlFocusRestored = onPlayerControlFocusRestored,
            restoreAfterPictureInPicture = restoreAfterPictureInPicture,
        )
    }
}

private fun PlayerView.bindInteractiveController(
    controllerBinding: PlayerControllerBinding,
    playerControlFocusToRestoreId: Int?,
    onPlayerControlFocusRestored: () -> Unit,
    restoreAfterPictureInPicture: Boolean,
) {
    bindYummyController(binding = controllerBinding)
    restorePlayerControlFocusWhenReady(
        controlId = playerControlFocusToRestoreId,
        onRestored = onPlayerControlFocusRestored,
    )
    if (restoreAfterPictureInPicture) {
        restoreControllerAfterPictureInPicture()
    }
}

@OptIn(UnstableApi::class)
private fun PlayerView.applyYummySubtitleStyle() {
    subtitleView?.apply {
        setApplyEmbeddedStyles(true)
        setApplyEmbeddedFontSizes(true)
        setStyle(
            CaptionStyleCompat(
                Color.WHITE,
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                Color.BLACK,
                Typeface.DEFAULT_BOLD,
            ),
        )
    }
}
