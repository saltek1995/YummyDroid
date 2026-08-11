package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import me.yummydroid.app.PlaybackFailure
import me.yummydroid.app.PlaybackFailureKind
import me.yummydroid.app.PlayerPipController
import me.yummydroid.app.R
import me.yummydroid.app.localizedString

@Composable
internal fun BindNativeVideoPlayerRuntimeEffects(
    binding: NativeVideoPlayerRuntimeBinding,
    session: NativeVideoPlayerRuntimeSession,
) {
    val player = session.player
    LaunchedEffect(binding.previousVideo?.id, binding.nextVideo?.id, player) {
        PlayerPipController.notifyPlayingChanged()
    }
    LaunchedEffect(player, binding.settings.playerSpeed) {
        player.setPlaybackSpeed(binding.settings.playerSpeed.value)
    }
    LaunchedEffect(player) {
        while (player.playbackState != Player.STATE_READY && player.playbackState != Player.STATE_ENDED) {
            delay(24)
        }
        if (player.playbackState == Player.STATE_READY) {
            if (binding.keepControlsVisibleAfterReady) {
                session.playerView.value?.showPlayerControls()
            } else {
                session.playerView.value?.hidePlayerControls()
            }
            if (binding.keepControlsVisibleAfterReady) {
                binding.onControlsKeptVisibleAfterReady()
            }
            player.play()
        }
    }
    LaunchedEffect(player, binding.settings.matchDisplayModeToVideo, session.selection.tracks) {
        session.activity?.applyVideoDisplayMode(
            enabled = binding.settings.matchDisplayModeToVideo,
            video = player.currentVideoDisplayInfo(),
        )
    }
    LaunchedEffect(player, binding.currentVideo.id) {
        while (true) {
            delay(PLAYBACK_PROGRESS_SAVE_INTERVAL_MS)
            if (player.playbackState != Player.STATE_IDLE) {
                session.currentProgressCallback.value(
                    session.currentProgressVideo.value,
                    player.currentPosition.coerceAtLeast(0L),
                    player.duration.normalizedDurationMs(),
                )
            }
        }
    }
    NativePlayerLifecycle(createNativePlayerLifecycleBinding(binding, session))
}

private fun createNativePlayerLifecycleBinding(
    binding: NativeVideoPlayerRuntimeBinding,
    session: NativeVideoPlayerRuntimeSession,
): NativePlayerLifecycleBinding {
    return NativePlayerLifecycleBinding(
        player = session.player,
        pipPlayerHandle = session.pipPlayerHandle,
        metadataDurationSeconds = binding.currentVideo.durationSeconds,
        state = createNativePlayerEventState(session),
        callbacks = createNativePlayerEventCallbacks(binding, session),
    )
}

private fun createNativePlayerEventState(
    session: NativeVideoPlayerRuntimeSession,
): NativePlayerEventState {
    return NativePlayerEventState(
        playerView = { session.playerView.value },
        settings = { session.currentSettings.value },
        qualityOptions = { session.latestQualityOptions.value },
        playbackPreferredQuality = { session.latestPlaybackPreferredQuality.value },
        streamSelectedQualityKey = { session.latestStreamSelectedQualityKey.value },
        fallbackSuppressedUntilMs = { session.fallbackSuppressedUntilMs.longValue },
        onFallbackSuppressedUntilChanged = { session.fallbackSuppressedUntilMs.longValue = it },
        skipControlsTimelineReady = { session.skipControlsTimelineReady.value },
        onSkipControlsTimelineReady = { session.skipControlsTimelineReady.value = true },
        onTracksChanged = session.selection.onTracksChanged,
        onSelectedSubtitleKeyChanged = session.selection.onSelectedSubtitleKeyChanged,
        onSelectedQualityKeyChanged = session.selection.onSelectedQualityKeyChanged,
    )
}

private fun createNativePlayerEventCallbacks(
    binding: NativeVideoPlayerRuntimeBinding,
    session: NativeVideoPlayerRuntimeSession,
): NativePlayerEventCallbacks {
    return NativePlayerEventCallbacks(
        onPlaybackStarted = { binding.onPlaybackStarted(binding.currentVideo) },
        onPlaybackEnded = { binding.onPlaybackEnded(binding.currentVideo) },
        onBufferingTimeout = { positionMs ->
            binding.onPlaybackFailed(
                binding.currentVideo,
                positionMs,
                PlaybackFailure(
                    kind = PlaybackFailureKind.BufferingTimeout,
                    message = session.context.localizedString(
                        R.string.ui_playback_buffer_not_filling,
                        session.currentSettings.value.contentLanguage,
                    ),
                ),
            )
        },
        onAutoAdvance = {
            binding.nextVideo?.let { next ->
                showVoiceFallbackToast(session.context, binding.currentVideo, next)
                session.currentProgressCallback.value(next, 1_000L, 0L)
                session.playerView.value?.hidePlayerControls()
                binding.onPlayVideoAt(next, 0L)
            }
        },
        onPlaybackError = { positionMs, error ->
            binding.onPlaybackFailed(
                binding.currentVideo,
                positionMs,
                PlaybackFailure(
                    kind = PlaybackFailureKind.PlayerError,
                    message = error.playbackFailureMessage(),
                ),
            )
        },
        onProgressSnapshot = { positionMs, durationMs ->
            session.currentProgressCallback.value(
                session.currentProgressVideo.value,
                positionMs,
                durationMs,
            )
        },
        onDisplayModeUpdate = { videoSize ->
            (session.playerView.value as? YummyPlayerView)?.updateControllerViewport()
            session.activity?.applyVideoDisplayMode(
                enabled = session.currentSettings.value.matchDisplayModeToVideo,
                video = session.player.currentVideoDisplayInfo() ?: videoSize?.toVideoDisplayInfo(),
            )
        },
        onDispose = {
            session.playerView.value?.clearTimelineScrubState()
            session.playerView.value?.unbindSkipControls()
            session.activity?.clearPreferredDisplayMode()
        },
    )
}
