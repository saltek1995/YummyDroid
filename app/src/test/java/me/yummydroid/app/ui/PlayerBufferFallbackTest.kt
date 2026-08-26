package me.yummydroid.app.ui

import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.data.PlayerBufferPreset

class PlayerBufferFallbackTest {
    @Test
    fun remoteRebufferingUsesFullBufferProfileTimeout() {
        assertEquals(
            PlayerBufferPreset.Standard.prepareFallbackThresholdMs,
            playbackBufferingFallbackDelayMs(
                playbackStartedReported = true,
                playerBufferPreset = PlayerBufferPreset.Standard,
                fallbackSuppressedUntilMs = 0L,
                nowMs = 1_000L,
                playbackType = DeviceInfo.PLAYBACK_TYPE_REMOTE,
            ),
        )
    }

    @Test
    fun resolvesDurationFromPlayerBeforeMetadata() {
        assertEquals(
            1_200_000L,
            resolvedPlaybackDurationMs(
                playerDurationMs = 1_200_000L,
                contentDurationMs = 1_300_000L,
                metadataDurationSeconds = 1_400,
            ),
        )
    }

    @Test
    fun resolvesDurationFromMetadataWhenPlayerDurationIsUnknown() {
        assertEquals(
            1_440_000L,
            resolvedPlaybackDurationMs(
                playerDurationMs = C.TIME_UNSET,
                contentDurationMs = C.TIME_UNSET,
                metadataDurationSeconds = 1_440,
            ),
        )
    }

    @Test
    fun endOfEpisodeIsNotAStalledBufferWhenOnlyMetadataDurationIsKnown() {
        assertTrue(
            isPlaybackEndCloseOrBuffered(
                positionMs = 1_430_000L,
                bufferedPositionMs = 1_432_000L,
                durationMs = resolvedPlaybackDurationMs(
                    playerDurationMs = C.TIME_UNSET,
                    contentDurationMs = C.TIME_UNSET,
                    metadataDurationSeconds = 1_440,
                ),
                switchFallbackThresholdMs = PlayerBufferPreset.Standard.switchFallbackThresholdMs,
            ),
        )
    }

    @Test
    fun middleOfEpisodeCanStillBeInspectedForFallback() {
        assertFalse(
            isPlaybackEndCloseOrBuffered(
                positionMs = 600_000L,
                bufferedPositionMs = 603_000L,
                durationMs = 1_440_000L,
                switchFallbackThresholdMs = PlayerBufferPreset.Standard.switchFallbackThresholdMs,
            ),
        )
    }

    @Test
    fun fullyBufferedTailIsTreatedAsNaturalEnd() {
        assertTrue(
            isPlaybackEndCloseOrBuffered(
                positionMs = 1_380_000L,
                bufferedPositionMs = 1_439_500L,
                durationMs = 1_440_000L,
                switchFallbackThresholdMs = PlayerBufferPreset.Standard.switchFallbackThresholdMs,
            ),
        )
    }

    @Test
    fun startupBufferingIsObservedBeforeFirstFrame() {
        assertTrue(
            shouldSchedulePlaybackBufferingFallback(
                playbackState = Player.STATE_BUFFERING,
                fallbackReported = false,
            ),
        )
    }

    @Test
    fun bufferingIsIgnoredAfterFailureWasReported() {
        assertFalse(
            shouldSchedulePlaybackBufferingFallback(
                playbackState = Player.STATE_BUFFERING,
                fallbackReported = true,
            ),
        )
    }

    @Test
    fun startupFallbackIsScheduledBeforePlaybackReallyStarts() {
        assertTrue(
            shouldSchedulePlaybackStartupFallback(
                playbackState = Player.STATE_IDLE,
                playbackStartedReported = false,
                fallbackReported = false,
            ),
        )
        assertTrue(
            shouldSchedulePlaybackStartupFallback(
                playbackState = Player.STATE_BUFFERING,
                playbackStartedReported = false,
                fallbackReported = false,
            ),
        )
        assertTrue(
            shouldSchedulePlaybackStartupFallback(
                playbackState = Player.STATE_READY,
                playbackStartedReported = false,
                fallbackReported = false,
            ),
        )
    }

    @Test
    fun startupFallbackIsNotScheduledAfterFirstFrameOrFailure() {
        assertFalse(
            shouldSchedulePlaybackStartupFallback(
                playbackState = Player.STATE_BUFFERING,
                playbackStartedReported = true,
                fallbackReported = false,
            ),
        )
        assertFalse(
            shouldSchedulePlaybackStartupFallback(
                playbackState = Player.STATE_BUFFERING,
                playbackStartedReported = false,
                fallbackReported = true,
            ),
        )
    }

    @Test
    fun startupFallbackReportWaitsForPlaybackRequest() {
        assertFalse(
            shouldReportPlaybackStartupFallback(
                playbackState = Player.STATE_READY,
                playbackStartedReported = false,
                fallbackReported = false,
                playWhenReady = false,
            ),
        )
        assertTrue(
            shouldReportPlaybackStartupFallback(
                playbackState = Player.STATE_READY,
                playbackStartedReported = false,
                fallbackReported = false,
                playWhenReady = true,
            ),
        )
    }

    @Test
    fun startupBufferingUsesPrepareThreshold() {
        assertEquals(
            PlayerBufferPreset.Standard.prepareFallbackThresholdMs,
            playbackStartupFallbackDelayMs(PlayerBufferPreset.Standard),
        )
        assertEquals(
            PlayerBufferPreset.Standard.prepareFallbackThresholdMs,
            playbackBufferingFallbackDelayMs(
                playbackStartedReported = false,
                playerBufferPreset = PlayerBufferPreset.Standard,
                fallbackSuppressedUntilMs = 0L,
                nowMs = 1_000L,
                playbackType = DeviceInfo.PLAYBACK_TYPE_LOCAL,
            ),
        )
    }

    @Test
    fun rebufferingUsesBufferProfileSwitchDelayAfterPlaybackStarted() {
        assertEquals(
            PlayerBufferPreset.Standard.switchFallbackThresholdMs,
            playbackBufferingFallbackDelayMs(
                playbackStartedReported = true,
                playerBufferPreset = PlayerBufferPreset.Standard,
                fallbackSuppressedUntilMs = 0L,
                nowMs = 1_000L,
                playbackType = DeviceInfo.PLAYBACK_TYPE_LOCAL,
            ),
        )
    }

}
