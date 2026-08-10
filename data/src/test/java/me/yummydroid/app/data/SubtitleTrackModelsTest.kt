package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleTrackModelsTest {
    @Test
    fun readableMetadataWinsOverOpaqueTechnicalLabels() {
        val normalized = listOf(
            ResolvedSubtitleTrack(uri = "https://example.test/sub.vtt", label = "a19f"),
            ResolvedSubtitleTrack(uri = "https://example.test/sub.vtt", label = "subtitle_1234567890abcdef123456"),
            ResolvedSubtitleTrack(
                uri = "https://example.test/sub.vtt",
                label = "Russian signs",
                language = "ru",
            ),
        ).normalizedSubtitleTracks()

        assertEquals(1, normalized.size)
        assertEquals("Russian signs", normalized.single().label)
        assertEquals("ru", normalized.single().language)
    }

    @Test
    fun embeddedTracksMergeTrimmedIdentityAndReadableMetadata() {
        val normalized = listOf(
            ResolvedEmbeddedSubtitleTrack(id = " CC1 ", label = "8219"),
            ResolvedEmbeddedSubtitleTrack(id = "CC1", label = " Signs ", language = " ru "),
        ).normalizedEmbeddedSubtitleTracks()

        assertEquals(
            listOf(ResolvedEmbeddedSubtitleTrack(id = "CC1", label = "Signs", language = "ru")),
            normalized,
        )
    }
}
