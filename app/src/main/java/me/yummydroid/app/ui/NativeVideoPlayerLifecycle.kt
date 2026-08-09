package me.yummydroid.app.ui

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.yummydroid.app.AppLog
import me.yummydroid.app.PipPlayerHandle
import me.yummydroid.app.PlayerPipController
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.PreferredQuality

internal data class PlaybackQualitySelection(
    val key: String?,
    val shouldUpdateDisplayMode: Boolean,
)

internal fun resolvePlaybackQualitySelection(
    resolvedSourceKey: String?,
    qualityOptions: List<QualityOption>,
    trackOptions: List<QualityOption>,
    playbackPreferredQuality: PreferredQuality,
    defaultQuality: PreferredQuality,
    actualQualityKey: String?,
): PlaybackQualitySelection {
    if (resolvedSourceKey != null && qualityOptions.any { it.matchesSelectedQualityKey(resolvedSourceKey) }) {
        return PlaybackQualitySelection(resolvedSourceKey, shouldUpdateDisplayMode = false)
    }
    val explicitPreferredQuality = playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto }
        ?: defaultQuality.takeUnless { it == PreferredQuality.Auto }
    explicitPreferredQuality
        ?.let(qualityOptions::preferredOption)
        ?.let { preferredOption ->
            return PlaybackQualitySelection(preferredOption.qualityOptionIdentity(), shouldUpdateDisplayMode = false)
        }
    val selectedKey = trackOptions
        .firstOrNull { it.matchesSelectedQualityKey(actualQualityKey) }
        ?.qualityOptionIdentity()
        ?: actualQualityKey?.qualityIdentityFromLabel()
        ?: actualQualityKey
    return PlaybackQualitySelection(selectedKey, shouldUpdateDisplayMode = true)
}

internal class NativePlayerEventState(
    val playerView: () -> PlayerView?,
    val settings: () -> AppSettings,
    val qualityOptions: () -> List<QualityOption>,
    val playbackPreferredQuality: () -> PreferredQuality,
    val streamSelectedQualityKey: () -> String?,
    val fallbackSuppressedUntilMs: () -> Long,
    val onFallbackSuppressedUntilChanged: (Long) -> Unit,
    val skipControlsTimelineReady: () -> Boolean,
    val onSkipControlsTimelineReady: () -> Unit,
    val onTracksChanged: (Tracks) -> Unit,
    val onSelectedSubtitleKeyChanged: (String) -> Unit,
    val onSelectedQualityKeyChanged: (String?) -> Unit,
)

internal class NativePlayerEventCallbacks(
    val onPlaybackStarted: () -> Unit,
    val onPlaybackEnded: () -> Unit,
    val onBufferingTimeout: (Long) -> Unit,
    val onAutoAdvance: () -> Unit,
    val onPlaybackError: (Long, PlaybackException) -> Unit,
    val onProgressSnapshot: (Long, Long) -> Unit,
    val onDisplayModeUpdate: (VideoSize?) -> Unit,
    val onDispose: () -> Unit,
)

internal class NativePlayerLifecycleBinding(
    val player: ExoPlayer,
    val pipPlayerHandle: PipPlayerHandle,
    val metadataDurationSeconds: Int?,
    val state: NativePlayerEventState,
    val callbacks: NativePlayerEventCallbacks,
)

@Composable
internal fun NativePlayerLifecycle(binding: NativePlayerLifecycleBinding) {
    val fallbackScope = rememberCoroutineScope()
    DisposableEffect(binding.player) {
        val listener = NativePlayerEventListener(binding, fallbackScope)
        PlayerPipController.registerPlayer(binding.pipPlayerHandle)
        binding.player.addListener(listener)
        onDispose {
            listener.dispose()
            binding.callbacks.onProgressSnapshot(
                binding.player.currentPosition.coerceAtLeast(0L),
                binding.player.duration.normalizedDurationMs(),
            )
            binding.player.removeListener(listener)
            PlayerPipController.unregisterPlayer(binding.pipPlayerHandle)
            binding.callbacks.onDispose()
            binding.player.release()
        }
    }
}

@OptIn(UnstableApi::class)
private class NativePlayerEventListener(
    private val binding: NativePlayerLifecycleBinding,
    private val fallbackScope: CoroutineScope,
) : Player.Listener {
    private var fallbackReported = false
    private var autoAdvanceReported = false
    private var playbackStartedReported = false
    private var playbackEndedReported = false
    private var bufferingFallbackJob: Job? = null

    override fun onEvents(player: Player, events: Player.Events) {
        if (!binding.state.skipControlsTimelineReady() && player.hasReadyTimeline()) {
            binding.state.onSkipControlsTimelineReady()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        PlayerPipController.notifyPlayingChanged()
        if (isPlaying && !playbackStartedReported) {
            playbackStartedReported = true
            binding.callbacks.onPlaybackStarted()
        }
    }

    override fun onTracksChanged(currentTracks: Tracks) {
        binding.state.onTracksChanged(currentTracks)
        val subtitleKey = currentTracks.currentSubtitleKey() ?: SUBTITLE_OFF_KEY
        binding.state.onSelectedSubtitleKeyChanged(subtitleKey)
        binding.state.playerView()
            ?.findViewById<android.view.View>(me.yummydroid.app.R.id.yummy_player_subtitles)
            ?.setTag(me.yummydroid.app.R.id.yummy_player_subtitles, subtitleKey)

        val selection = resolvePlaybackQualitySelection(
            resolvedSourceKey = binding.state.streamSelectedQualityKey(),
            qualityOptions = binding.state.qualityOptions(),
            trackOptions = currentTracks.videoQualityOptions(),
            playbackPreferredQuality = binding.state.playbackPreferredQuality(),
            defaultQuality = binding.state.settings().defaultQuality,
            actualQualityKey = binding.player.currentQualityKey(),
        )
        binding.state.onSelectedQualityKeyChanged(selection.key)
        binding.state.playerView()
            ?.findViewById<android.view.View>(me.yummydroid.app.R.id.yummy_player_quality)
            ?.setTag(me.yummydroid.app.R.id.yummy_player_quality, selection.key)
        if (selection.shouldUpdateDisplayMode) {
            binding.callbacks.onDisplayModeUpdate(null)
        }
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        binding.callbacks.onDisplayModeUpdate(videoSize)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        updateBufferingFallback(playbackState)
        if (playbackState == Player.STATE_ENDED && !playbackEndedReported) {
            playbackEndedReported = true
            binding.callbacks.onPlaybackEnded()
        }
        if (
            playbackState == Player.STATE_ENDED &&
            binding.state.settings().autoplayNextEpisode &&
            !autoAdvanceReported
        ) {
            autoAdvanceReported = true
            binding.callbacks.onAutoAdvance()
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        binding.state.onFallbackSuppressedUntilChanged(
            SystemClock.elapsedRealtime() + PLAYBACK_SEEK_BUFFER_GRACE_MS,
        )
    }

    override fun onPlayerError(error: PlaybackException) {
        logPlaybackError(error)
        if (!fallbackReported) {
            bufferingFallbackJob?.cancel()
            bufferingFallbackJob = null
            fallbackReported = true
            binding.callbacks.onPlaybackError(
                binding.player.currentPosition.coerceAtLeast(0L),
                error,
            )
        }
    }

    fun dispose() {
        bufferingFallbackJob?.cancel()
    }

    private fun updateBufferingFallback(playbackState: Int) {
        if (playbackState == Player.STATE_BUFFERING && playbackStartedReported && !fallbackReported) {
            bufferingFallbackJob?.cancel()
            bufferingFallbackJob = fallbackScope.launch {
                reportBufferingTimeoutIfNeeded()
            }
        } else if (playbackState != Player.STATE_BUFFERING) {
            bufferingFallbackJob?.cancel()
            bufferingFallbackJob = null
        }
    }

    private suspend fun reportBufferingTimeoutIfNeeded() {
        val delayMs = maxOf(
            PLAYBACK_BUFFERING_FALLBACK_DELAY_MS,
            binding.state.fallbackSuppressedUntilMs() - SystemClock.elapsedRealtime(),
        )
        delay(delayMs.coerceAtLeast(0L))
        if (SystemClock.elapsedRealtime() < binding.state.fallbackSuppressedUntilMs()) return
        if (binding.player.playbackState != Player.STATE_BUFFERING || fallbackReported) return
        val settings = binding.state.settings()
        if (
            isPlaybackEndCloseOrBuffered(
                positionMs = binding.player.currentPosition.coerceAtLeast(0L),
                bufferedPositionMs = binding.player.bufferedPosition.coerceAtLeast(0L),
                durationMs = resolvedPlaybackDurationMs(
                    playerDurationMs = binding.player.duration,
                    contentDurationMs = binding.player.contentDuration,
                    metadataDurationSeconds = binding.metadataDurationSeconds,
                ),
                switchFallbackThresholdMs = settings.playerBufferPreset.switchFallbackThresholdMs,
            )
        ) {
            return
        }
        fallbackReported = true
        binding.callbacks.onBufferingTimeout(binding.player.currentPosition.coerceAtLeast(0L))
    }

    private fun logPlaybackError(error: PlaybackException) {
        val httpError = error.cause as? HttpDataSource.InvalidResponseCodeException
        if (httpError != null) {
            val uri = httpError.dataSpec.uri
            AppLog.w(
                "YummyDroidPlayer",
                "Playback HTTP ${httpError.responseCode}: host=${uri.host}, file=${uri.lastPathSegment}, headers=${httpError.headerFields.keys}",
            )
        } else {
            AppLog.w("YummyDroidPlayer", "Playback failed: ${error.errorCodeName}", error)
        }
    }
}

internal fun PlaybackException.playbackFailureMessage(): String {
    val httpError = cause as? HttpDataSource.InvalidResponseCodeException
    if (httpError != null) return "HTTP ${httpError.responseCode}"
    return errorCodeName.takeIf { it.isNotBlank() }
        ?: localizedMessage?.takeIf { it.isNotBlank() }
        ?: message?.takeIf { it.isNotBlank() }
        ?: "playback error"
}
