package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.file.Files
import kotlinx.serialization.json.jsonObject

class SourceQualityNormalizationTest {
    @Test
    fun savingPrunesExpiredQualitiesButKeepsTheInclusiveTtlBoundary() {
        val directory = Files.createTempDirectory("source-quality-expiration").toFile()
        val file = directory.resolve("qualities.json")
        var now = 1_000_000L
        val storage = SourceQualityCacheStorage(file, nowMs = { now })
        val otherStorage = SourceQualityCacheStorage(file, nowMs = { now })
        val video = VideoVariant(
            id = 1, animeId = 1, player = "CVH", dubbing = "Voice", episode = "1",
            url = "https://example.test/1", index = 0, durationSeconds = null, views = 0,
        )
        val boundary = video.copy(id = 2)
        val newest = video.copy(id = 3)
        val stream = ResolvedVideoStream("https://example.test/video.mp4", "video/mp4", emptyMap(), maxVideoHeight = 720)
        try {
            storage.save(video, stream)
            now++
            storage.save(boundary, stream)
            now += 14L * 24 * 60 * 60 * 1_000
            otherStorage.save(newest, stream)

            assertEquals(setOf("2", "3"), AppJson.parseToJsonElement(file.readText()).jsonObject.keys)
            val restored = storage.applyTo(listOf(video, boundary, newest))
            assertEquals(emptyList(), restored[0].sourceQualities)
            assertEquals(listOf(SourceQuality(720)), restored[1].sourceQualities)
            assertEquals(listOf(SourceQuality(720)), restored[2].sourceQualities)
            now++
            assertEquals(emptyList(), storage.applyTo(listOf(boundary)).single().sourceQualities)
            storage.save(newest, stream)
            assertEquals(setOf("3"), AppJson.parseToJsonElement(file.readText()).jsonObject.keys)
        } finally {
            storage.clear()
            directory.deleteRecursively()
        }
    }

    @Test
    fun bestSourceQualitiesKeepUniqueKnownHeightsInDescendingOrder() {
        assertEquals(
            listOf(SourceQuality(1080), SourceQuality(720)),
            listOf(SourceQuality(720), SourceQuality(null, 500), SourceQuality(1080), SourceQuality(720, 100))
                .bestSourceQualityPerHeight(),
        )
    }

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
