package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadEpisodeRangeTest {
    @Test
    fun episodeRangeParserAcceptsCommaSeparatedRanges() {
        val parsed = parseDownloadEpisodeSelection("1-3, 7, 10-11")

        assertNull(parsed.error)
        assertTrue(parsed.selection.allows(1.0))
        assertTrue(parsed.selection.allows(3.0))
        assertTrue(parsed.selection.allows(7.0))
        assertTrue(parsed.selection.allows(11.0))
        assertFalse(parsed.selection.allows(9.0))
    }

    @Test
    fun episodeRangeParserNormalizesUnicodeDashesAndSemicolons() {
        val parsed = parseDownloadEpisodeSelection("1 \u2013 2; 4\u20145")

        assertNull(parsed.error)
        assertEquals(listOf(1..2, 4..5), parsed.selection.ranges)
    }

    @Test
    fun episodeRangeValidationRejectsEpisodesMissingFromVoice() {
        val parsed = validateDownloadEpisodeSelection(
            input = "1-4, 8",
            availableRanges = listOf(1..2, 4..4),
        )

        assertEquals(DownloadEpisodeSelectionError.MissingEpisodes("3, 8"), parsed.error)
        assertTrue(parsed.selection.allows(1.0))
        assertTrue(parsed.selection.allows(8.0))
    }

    @Test
    fun episodeRangeParserReturnsStructuredErrors() {
        assertEquals(
            DownloadEpisodeSelectionError.InvalidEpisodeNumber("0"),
            parseDownloadEpisodeSelection("0").error,
        )
        assertEquals(
            DownloadEpisodeSelectionError.InvalidEpisodeRange("4-2"),
            parseDownloadEpisodeSelection("4-2").error,
        )
        assertEquals(
            DownloadEpisodeSelectionError.InvalidEpisodeRange("1-2-3"),
            parseDownloadEpisodeSelection("1-2-3").error,
        )
    }

    @Test
    fun episodeRangeParserKeepsValidPrefixWhenLaterTokenFails() {
        val parsed = parseDownloadEpisodeSelection("1-2, 5-3")

        assertEquals(listOf(1..2), parsed.selection.ranges)
        assertEquals(DownloadEpisodeSelectionError.InvalidEpisodeRange("5-3"), parsed.error)
    }
}
