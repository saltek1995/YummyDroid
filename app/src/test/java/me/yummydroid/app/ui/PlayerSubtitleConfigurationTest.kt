package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.media3.common.Format

class PlayerSubtitleConfigurationTest {
    @Test
    fun subtitleLabelFallsBackToCacheFileNameWithoutExtension() {
        assertEquals(
            "subtitle_abcdef",
            subtitleLabelForMedia3(
                label = "",
                uri = "file:///data/user/0/me.yummydroid.app/cache/subtitles/subtitle_abcdef.vtt",
            ),
        )
    }

    @Test
    fun subtitleLabelPrefersResolvedTrackLabel() {
        assertEquals(
            "subtitle_materialized",
            subtitleLabelForMedia3(
                label = "subtitle_materialized",
                uri = "file:///data/user/0/me.yummydroid.app/cache/subtitles/subtitle_original.vtt",
            ),
        )
    }

    @Test
    fun subtitleLabelKeepsExtensionlessCacheFileName() {
        assertEquals(
            "subtitle_abcdef",
            subtitleLabelForMedia3(
                label = "",
                uri = "file:///data/user/0/me.yummydroid.app/cache/subtitles/subtitle_abcdef",
            ),
        )
    }

    @Test
    fun formatSubtitleLabelUsesCacheIdentifierBeforeGenericFallback() {
        val format = Format.Builder()
            .setId("subtitle_abcdef")
            .build()

        assertEquals("subtitle_abcdef", format.subtitleLabel(defaultPlayerControlTexts, trackIndex = 0))
    }

    @Test
    fun subtitleIdentifierLabelIgnoresOpaquePlayerIds() {
        assertEquals("", "1".subtitleIdentifierLabel())
    }
}
