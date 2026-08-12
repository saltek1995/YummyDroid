package me.yummydroid.app.ui

import androidx.media3.common.Format
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubtitleTrackLabelTest {
    @Test
    fun cacheIdentifierFallsBackToGenericFormatLabel() {
        val format = Format.Builder()
            .setId("subtitle_abcdef1234567890abcdef1234567890")
            .build()

        assertEquals("Subtitles 1", format.subtitleLabel(defaultPlayerControlTexts, trackIndex = 0))
    }

    @Test
    fun opaquePlayerIdHasNoIdentifierLabel() {
        assertEquals("", "1".subtitleIdentifierLabel())
    }

    @Test
    fun onlyTechnicalCacheHashesAreRejectedFromNamedLabels() {
        assertNull("subtitle_abcdef1234567890abcdef1234567890".subtitleUserVisibleLabel())
        assertEquals("subtitle_materialized", "subtitle_materialized".subtitleUserVisibleLabel())
    }

    @Test
    fun numericAndOpaqueHexTrackIdsAreRejected() {
        assertNull("8219".subtitleUserVisibleLabel())
        assertNull("0f31a9".subtitleUserVisibleLabel())
    }

    @Test
    fun readableHexLikeLabelsRemainVisibleOutsideOpaqueIdRules() {
        assertEquals("face", " face ".subtitleUserVisibleLabel())
        assertEquals("0f3", "0f3".subtitleUserVisibleLabel())
        assertEquals("123g", "123g".subtitleUserVisibleLabel())
        assertEquals("0f31a9deadbeef1234", "0f31a9deadbeef1234".subtitleUserVisibleLabel())
        assertEquals("subtitle_abcdef", "subtitle_abcdef".subtitleUserVisibleLabel())
        assertEquals(
            "subtitle_abcdef1234567890abcdefg",
            "subtitle_abcdef1234567890abcdefg".subtitleUserVisibleLabel(),
        )
    }

    @Test
    fun resolvedLabelTakesPriorityOverTechnicalTrackId() {
        assertEquals(
            "(Russian) Надписи",
            "8219".subtitleDisplayLabel(
                texts = defaultPlayerControlTexts,
                trackIndex = 0,
                resolvedSubtitleLabel = "(Russian) Надписи",
            ),
        )
    }
}
