package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class HlsDownloadProgressTest {
    @Test
    fun progressUsesCompletedSegmentFractionAndSessionSpeed() {
        val progress = hlsSegmentDownloadProgress(
            nextSegmentIndex = 2,
            segmentCount = 4,
            downloadedBytes = 8_000L,
            sessionDownloadedBytes = 3_000L,
            elapsedMs = 2_000L,
            qualityTitle = "1080p",
            voiceTitle = "Voice",
        )

        assertEquals(0.5f, progress.fraction)
        assertEquals(8_000L, progress.downloadedBytes)
        assertEquals(-1L, progress.totalBytes)
        assertEquals(1_500L, progress.bytesPerSecond)
        assertEquals("1080p", progress.qualityTitle)
        assertEquals("Voice", progress.voiceTitle)
    }

    @Test
    fun fractionAndElapsedTimeAreSafelyBounded() {
        assertEquals(1f, hlsSegmentDownloadProgress(5, 4, 0, 10, 0, "Auto", "Voice").fraction)
        assertEquals(10_000L, hlsSegmentDownloadProgress(1, 0, 0, 10, 0, "Auto", "Voice").bytesPerSecond)
        assertEquals(0f, hlsSegmentDownloadProgress(1, 0, 0, 10, 0, "Auto", "Voice").fraction)
    }
}
