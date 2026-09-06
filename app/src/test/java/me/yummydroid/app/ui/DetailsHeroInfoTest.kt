package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import me.yummydroid.app.downloadPlanTestVideo

class DetailsHeroInfoTest {
    @Test
    fun downloadedEpisodesUseRangesWithoutLosingZeroSpecialsOrGaps() {
        val video = downloadPlanTestVideo(1, "CVH", "Voice", "1", 720)
            .copy(localPlaybackUrl = "file:///episode.mp4")
        val downloaded = listOf("0", "1", "2", "4", "5", "7", "7.5", "OVA")
            .mapIndexed { index, episode -> video.copy(id = index.toLong() + 1, episode = episode, index = index) }
        val duplicate = downloaded.first().copy(id = 100, player = "Kodik")
        val missing = video.copy(id = 101, episode = "3", localPlaybackUrl = "")
        assertEquals(listOf("0-2", "4-5", "7", "7.5", "OVA"), (downloaded.reversed() + duplicate + missing).downloadedEpisodeRanges())
        assertTrue(listOf(missing).downloadedEpisodeRanges().isEmpty())
        assertEquals(listOf("1"), listOf(video).downloadedEpisodeRanges())
    }

    @Test
    fun presentFactRejectsEmptyAndPlaceholderValues() {
        listOf("", " ", "unknown", "NULL", "-", "\u2014", "\u0432\u0402\u201d").forEach { value ->
            assertFalse(value.isPresentFactValue(), value)
        }
    }

    @Test
    fun presentFactAcceptsActualMetadata() {
        assertTrue("Studio Trigger".isPresentFactValue())
        assertTrue("0".isPresentFactValue())
    }
}
