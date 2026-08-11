package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

@OptIn(UnstableApi::class)
@Composable
internal fun NativeVideoPlayerRuntime(binding: NativeVideoPlayerRuntimeBinding) {
    val session = rememberNativeVideoPlayerRuntimeSession(binding)
    BindNativeVideoPlayerRuntimeEffects(binding, session)
    NativePlayerView(
        player = session.player,
        videoToken = "${binding.currentVideo.id}:${binding.stream.url}",
        interactive = binding.interactive,
        isInPictureInPicture = binding.isInPictureInPicture,
        controllerBinding = createNativeVideoPlayerControllerBinding(binding, session),
        playerControlFocusToRestoreId = binding.playerControlFocusToRestoreId,
        onPlayerViewChanged = { session.playerView.value = it },
        onPlayerControlFocusRestored = binding.onPlayerControlFocusRestored,
        modifier = binding.modifier,
    )
}

internal fun ExoPlayer.prepareCurrentMediaItemIfSameVideo(mediaItem: MediaItem): Boolean {
    val currentUri = currentMediaItem?.localConfiguration?.uri ?: return false
    val replacementUri = mediaItem.localConfiguration?.uri ?: return false
    if (currentUri != replacementUri) return false
    val positionMs = currentPosition.coerceAtLeast(0L)
    val shouldPlay = playWhenReady
    setMediaItem(mediaItem, positionMs)
    prepare()
    playWhenReady = shouldPlay
    return true
}
