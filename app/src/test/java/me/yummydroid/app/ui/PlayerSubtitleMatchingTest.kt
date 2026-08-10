package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import androidx.media3.common.Format

class PlayerSubtitleMatchingTest {
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
    fun materializedMedia3SubtitleMatchesByResolvedLabelWhenMedia3DropsStableId() {
        val reference = ResolvedSubtitleTrackReference(
            media3Id = "external-subtitle:file:///cache/real.vtt::(Russian) Надписи",
            label = "(Russian) Надписи",
        )
        val format = Format.Builder()
            .setId("1")
            .setLabel("(Russian) Надписи")
            .build()

        assertEquals(
            reference,
            format.matchingResolvedSubtitleReference(
                resolvedSubtitles = listOf(reference),
            ),
        )
    }

    @Test
    fun materializedMedia3SubtitleDoesNotMatchOnlyByGenericRenderedLabel() {
        val reference = ResolvedSubtitleTrackReference(
            media3Id = "external-subtitle:file:///cache/real.vtt::Alloha signs",
            label = "Alloha signs",
        )
        val format = Format.Builder()
            .setId("8219")
            .setLabel("Subtitles 1")
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
}
