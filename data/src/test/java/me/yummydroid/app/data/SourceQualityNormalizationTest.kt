package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.file.Files

class SourceQualityNormalizationTest {
    @Test
    fun clearingSourceQualitiesInvalidatesEveryStorageInstanceAndCannotResurrectOldEntries() {
        val directory = Files.createTempDirectory("source-quality-cache").toFile()
        val first = SourceQualityCacheStorage(directory.resolve("qualities.json"))
        val second = SourceQualityCacheStorage(directory.resolve("qualities.json"))
        try {
            val video = VideoVariant(
                id = 1L, animeId = 1L, player = "CVH", dubbing = "Voice", episode = "1",
                url = "https://example.test/1", index = 0, durationSeconds = null, views = 0L,
            )
            val other = video.copy(id = 2L, episode = "2", url = "https://example.test/2")
            val stream = ResolvedVideoStream("https://example.test/video.mp4", "video/mp4", emptyMap(), maxVideoHeight = 720)
            first.save(video, stream)
            assertEquals(listOf(SourceQuality(720)), second.applyTo(listOf(video)).single().sourceQualities)

            first.clear()
            assertEquals(emptyList(), second.applyTo(listOf(video)).single().sourceQualities)
            second.save(other, stream)
            val restored = first.applyTo(listOf(video, other))
            assertEquals(emptyList(), restored[0].sourceQualities)
            assertEquals(listOf(SourceQuality(720)), restored[1].sourceQualities)
        } finally {
            first.clear()
            directory.deleteRecursively()
        }
    }

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
