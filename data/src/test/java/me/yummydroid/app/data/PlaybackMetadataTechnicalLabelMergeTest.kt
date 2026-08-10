package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackMetadataTechnicalLabelMergeTest {
    @Test
    fun normalizesDuplicateSubtitleUrlsAndRejectsNumericTechnicalLabel() {
        val subtitles = listOf(
            ResolvedSubtitleTrack(
                uri = "https://example.test/subtitles/8219.ass",
                label = "8219",
                mimeType = "text/x-ssa",
            ),
            ResolvedSubtitleTrack(
                uri = "https://example.test/subtitles/8219.ass",
                label = "Alloha signs",
                language = "rus",
                mimeType = "text/x-ssa",
            ),
        ).normalizedSubtitleTracks()

        assertEquals(1, subtitles.size)
        assertEquals("Alloha signs", subtitles.single().label)
    }
}
