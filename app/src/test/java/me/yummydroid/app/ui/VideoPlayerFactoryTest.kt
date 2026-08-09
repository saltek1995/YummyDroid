package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.data.SourceQuality

class VideoPlayerFactoryTest {
    @Test
    fun missingBitrateUsesHighDefinitionFallback() {
        assertEquals(12_000_000L, initialVideoBitrateEstimate(emptyList()))
        assertEquals(12_000_000L, initialVideoBitrateEstimate(listOf(SourceQuality(height = 1080))))
    }

    @Test
    fun declaredBitrateGetsSelectionHeadroom() {
        val qualities = listOf(
            SourceQuality(height = 720, bitrate = 3_000_000),
            SourceQuality(height = 1080, bitrate = 8_000_000),
        )

        assertEquals(16_000_000L, initialVideoBitrateEstimate(qualities))
    }

    @Test
    fun estimateIsBoundedForExtremeMetadata() {
        assertEquals(
            50_000_000L,
            initialVideoBitrateEstimate(listOf(SourceQuality(height = 2160, bitrate = Int.MAX_VALUE))),
        )
    }
}
