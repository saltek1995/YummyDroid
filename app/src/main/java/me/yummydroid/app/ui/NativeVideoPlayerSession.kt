package me.yummydroid.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.yummydroid.app.PipPlayerHandle
import me.yummydroid.app.data.VideoVariant

internal class NativePlayerPlaybackActions(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
) {
    private var pendingPlaybackStartJob: Job? = null

    fun pause() {
        pendingPlaybackStartJob?.cancel()
        pendingPlaybackStartJob = null
        player.pause()
    }

    fun requestStart() {
        if (player.isPlaying || pendingPlaybackStartJob?.isActive == true) return
        pendingPlaybackStartJob = scope.launch {
            try {
                while (
                    player.playbackState != Player.STATE_READY &&
                    player.playbackState != Player.STATE_ENDED &&
                    player.playbackState != Player.STATE_IDLE
                ) {
                    delay(24)
                }
                if (player.playbackState == Player.STATE_IDLE) return@launch
                player.play()
            } finally {
                pendingPlaybackStartJob = null
            }
        }
    }
}

@Composable
internal fun rememberNativePlayerPlaybackActions(
    player: ExoPlayer,
    scope: CoroutineScope,
): NativePlayerPlaybackActions {
    return remember(player, scope) { NativePlayerPlaybackActions(player, scope) }
}

@Composable
internal fun RegisterNativePlayerInputController(
    player: ExoPlayer,
    playerView: () -> PlayerView?,
    isInPictureInPicture: Boolean,
    playbackActions: NativePlayerPlaybackActions,
    onRegisterPlayerInputActionHandler: (PlayerInputController?) -> Unit,
) {
    DisposableEffect(player, isInPictureInPicture, onRegisterPlayerInputActionHandler) {
        onRegisterPlayerInputActionHandler(
            PlayerInputController(
                controlsVisible = {
                    !isInPictureInPicture && playerView()?.hasVisiblePlayerControls() == true
                },
                hideControls = {
                    val view = playerView()
                    if (view == null || isInPictureInPicture) {
                        false
                    } else {
                        view.hidePlayerControls()
                        true
                    }
                },
                handle = { event ->
                    val view = playerView()
                    if (view == null || isInPictureInPicture) {
                        false
                    } else {
                        view.handleRemoteInputAction(
                            event = event,
                            onRequestPlay = playbackActions::requestStart,
                            onPausePlayback = playbackActions::pause,
                        )
                    }
                },
            ),
        )
        onDispose { onRegisterPlayerInputActionHandler(null) }
    }
}

@Composable
internal fun rememberNativePipPlayerHandle(
    context: Context,
    player: ExoPlayer,
    playerView: () -> PlayerView?,
    playbackActions: NativePlayerPlaybackActions,
    currentVideo: VideoVariant,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
): PipPlayerHandle {
    val latestCurrentVideo = rememberUpdatedState(currentVideo)
    val latestPreviousVideo = rememberUpdatedState(previousVideo)
    val latestNextVideo = rememberUpdatedState(nextVideo)
    val latestPlayVideoAt = rememberUpdatedState(onPlayVideoAt)
    return remember(player) {
        object : PipPlayerHandle {
            override val isPlaying: Boolean
                get() = player.isPlaying

            override val canPlayPreviousEpisode: Boolean
                get() = latestPreviousVideo.value != null

            override val canPlayNextEpisode: Boolean
                get() = latestNextVideo.value != null

            override fun play() = playbackActions.requestStart()

            override fun pause() = playbackActions.pause()

            override fun playPreviousEpisode() {
                latestPreviousVideo.value?.let { previous ->
                    showVoiceFallbackToast(context, latestCurrentVideo.value, previous)
                    playbackActions.pause()
                    latestPlayVideoAt.value(previous, 0L)
                }
            }

            override fun playNextEpisode() {
                latestNextVideo.value?.let { next ->
                    showVoiceFallbackToast(context, latestCurrentVideo.value, next)
                    playbackActions.pause()
                    latestPlayVideoAt.value(next, 0L)
                }
            }

            override fun setPictureInPictureMode(enabled: Boolean) {
                playerView()?.applyPictureInPictureControllerMode(enabled)
            }

            override fun hideAppControls() {
                playerView()?.hidePlayerControls()
            }
        }
    }
}
