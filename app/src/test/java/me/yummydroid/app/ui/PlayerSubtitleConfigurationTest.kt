package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import me.yummydroid.app.data.ResolvedSubtitleTrack

class PlayerSubtitleConfigurationTest {
    @Test
    fun subtitleLabelIgnoresTechnicalCacheFileName() {
        assertEquals(
            "",
            subtitleLabelForMedia3(
                label = "",
                uri = "file:///data/user/0/me.yummydroid.app/cache/subtitles/subtitle_abcdef1234567890abcdef1234567890.vtt",
            ),
        )
    }

    @Test
    fun subtitleLabelPrefersResolvedTrackLabel() {
        assertEquals(
            "Alloha ru 2",
            subtitleLabelForMedia3(
                label = "Alloha ru 2",
                uri = "file:///data/user/0/me.yummydroid.app/cache/subtitles/subtitle_original_hash.vtt",
            ),
        )
    }

    @Test
    fun subtitleLabelKeepsReadableSourceFileName() {
        assertEquals(
            "alloha ru",
            subtitleLabelForMedia3(
                label = "",
                uri = "https://example.com/subtitles/alloha_ru.vtt",
            ),
        )
    }

    @Test
    fun formatSubtitleLabelIgnoresCacheIdentifierBeforeGenericFallback() {
        val format = Format.Builder()
            .setId("subtitle_abcdef1234567890abcdef1234567890")
            .build()

        assertEquals("Субтитры 1", format.subtitleLabel(defaultPlayerControlTexts, trackIndex = 0))
    }

    @Test
    fun subtitleIdentifierLabelIgnoresOpaquePlayerIds() {
        assertEquals("", "1".subtitleIdentifierLabel())
    }

    @Test
    fun subtitleUserVisibleLabelRejectsOnlyTechnicalCacheHash() {
        assertNull("subtitle_abcdef1234567890abcdef1234567890".subtitleUserVisibleLabel())
        assertEquals("subtitle_materialized", "subtitle_materialized".subtitleUserVisibleLabel())
    }

    @Test
    fun subtitleUserVisibleLabelRejectsNumericTrackIds() {
        assertNull("8219".subtitleUserVisibleLabel())
        assertNull("0f31a9".subtitleUserVisibleLabel())
    }

    @Test
    fun subtitleDisplayLabelUsesResolvedLabelInsteadOfTechnicalTrackId() {
        assertEquals(
            "(Russian) Надписи",
            "8219".subtitleDisplayLabel(
                texts = defaultPlayerControlTexts,
                trackIndex = 0,
                resolvedSubtitleLabel = "(Russian) Надписи",
            ),
        )
    }

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

    @Test
    fun genericMedia3SubtitleDoesNotMatchMaterializedSubtitleWithoutStableId() {
        val reference = ResolvedSubtitleTrackReference(
            media3Id = "external-subtitle:file:///cache/real.vtt::Alloha signs",
            label = "Alloha signs",
        )
        val format = Format.Builder()
            .setId("8219")
            .build()

        assertNull(
            format.matchingResolvedSubtitleReference(
                resolvedSubtitles = listOf(reference),
            ),
        )
    }

    @Test
    fun materializedMedia3SubtitleMatchesByStableIdWithGenericMedia3Label() {
        val reference = ResolvedSubtitleTrackReference(
            media3Id = "external-subtitle:file:///cache/real.vtt::Alloha signs",
            label = "Alloha signs",
        )
        val format = Format.Builder()
            .setId(reference.media3Id)
            .build()

        assertEquals(
            reference,
            format.matchingResolvedSubtitleReference(
                resolvedSubtitles = listOf(reference),
            ),
        )
        assertEquals(
            "Alloha signs",
            "Subtitles 1".subtitleDisplayLabel(
                texts = defaultPlayerControlTexts,
                trackIndex = 0,
                resolvedSubtitleLabel = reference.label,
            ),
        )
    }

    @Test
    fun materializedAssSubtitleMapsToMedia3SsaMimeType() {
        assertEquals(
            MimeTypes.TEXT_SSA,
            subtitleMimeTypeForMedia3(
                uri = "file:///data/user/0/me.yummydroid.app/cache/subtitle_streams/subtitle_abcdef.ass",
                mimeType = "text/x-ssa",
            ),
        )
    }
}
