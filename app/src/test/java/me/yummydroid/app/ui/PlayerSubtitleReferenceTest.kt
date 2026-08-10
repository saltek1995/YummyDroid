package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.media3.common.Format
import me.yummydroid.app.data.ResolvedEmbeddedSubtitleTrack
import me.yummydroid.app.data.ResolvedSubtitleTrack

class PlayerSubtitleReferenceTest {
    @Test
    fun remoteSubtitleCandidateIsNotMedia3SubtitleConfiguration() {
        val track = ResolvedSubtitleTrack(
            uri = "https://example.test/subtitles/real.vtt",
            label = "Alloha signs",
            mimeType = "text/vtt",
        )

        assertNull(track.toMedia3SubtitleConfiguration())
        assertNull(track.toMedia3SubtitleReference())
    }

    @Test
    fun remoteSubtitleCandidateStillCreatesDisplayReference() {
        val track = ResolvedSubtitleTrack(
            uri = "https://example.test/subtitles/real.vtt",
            label = "Alloha signs",
            language = "ru",
            mimeType = "text/vtt",
        )

        val reference = assertNotNull(track.toSubtitleDisplayReference(sourceIndex = 3))

        assertEquals("", reference.media3Id)
        assertEquals("Alloha signs", reference.label)
        assertEquals("ru", reference.language)
        assertEquals(3, reference.sourceIndex)
    }

    @Test
    fun embeddedSubtitleReferenceNamesGenericMedia3TrackByManifestId() {
        val reference = assertNotNull(
            ResolvedEmbeddedSubtitleTrack(
                id = "CC1",
                label = "Signs",
                language = "ru",
            ).toSubtitleDisplayReference(sourceIndex = 0),
        )
        val format = Format.Builder()
            .setId("CC1")
            .build()

        assertEquals(reference, format.matchingResolvedSubtitleReference(listOf(reference)))
        assertEquals(
            "Signs",
            "Subtitles 1".subtitleDisplayLabel(
                texts = defaultPlayerControlTexts,
                trackIndex = 0,
                resolvedSubtitleLabel = reference.label,
            ),
        )
    }

    @Test
    fun materializedSubtitleCreatesStableMedia3Reference() {
        val track = ResolvedSubtitleTrack(
            uri = "file:///data/user/0/me.yummydroid.app/cache/subtitles/subtitle_abcdef1234567890.vtt",
            label = "Alloha signs",
            mimeType = "text/vtt",
        )

        val reference = assertNotNull(track.toMedia3SubtitleReference())

        assertEquals("Alloha signs", reference.label)
        assertTrue(reference.media3Id.startsWith("external-subtitle:file:///data/user/0/"))
        assertTrue(reference.media3Id.endsWith(":Alloha signs"))
    }
}
