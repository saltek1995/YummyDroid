package me.yummydroid.app.ui

import androidx.media3.common.C
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.data.PlayerBufferPreset

class PlayerBufferFallbackTest {
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
}
