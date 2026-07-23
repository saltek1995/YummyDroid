package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import androidx.media3.common.Format

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
}
