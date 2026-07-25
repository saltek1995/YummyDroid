package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class SourceQualityNormalizationTest {
    @Test
    fun normalizedSourceQualitiesCompareOnlyByResolutionHeight() {
        val qualities = listOf(
            SourceQuality(height = 1080, bitrate = 6_000_000),
            SourceQuality(height = 1080, bitrate = 2_500_000),
            SourceQuality(height = 720, bitrate = 1_500_000),
        ).normalizedSourceQualities()

        assertEquals(
            listOf(
                SourceQuality(height = 1080, bitrate = 0),
                SourceQuality(height = 720, bitrate = 0),
            ),
            qualities,
        )
    }

    @Test
    fun sourceResolutionHeightUsesAvailableQualitiesWhenMaxHeightIsMissing() {
        val stream = ResolvedVideoStream(
            url = "https://example.com/master.m3u8",
            mimeType = "application/x-mpegURL",
            headers = emptyMap(),
            maxVideoHeight = null,
            selectedVideoHeight = 720,
            availableQualities = listOf(
                SourceQuality(height = 1080),
                SourceQuality(height = 720),
            ),
        )

        assertEquals(1080, stream.sourceResolutionHeight())
    }
}
