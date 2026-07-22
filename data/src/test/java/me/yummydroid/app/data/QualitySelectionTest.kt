package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class QualitySelectionTest {
    @Test
    fun preferredQualitySelectsBestHeightWithoutExceedingWhenAvailable() {
        val selected = listOf(360, 720, 1080).selectForPreferredQuality(
            preferredQuality = PreferredQuality.P720,
            height = { it },
        )

        assertEquals(720, selected)
    }

    @Test
    fun preferredQualityFallsBackToNearestHigherHeightWhenNeeded() {
        val selected = listOf(1080, 1440).selectForPreferredQuality(
            preferredQuality = PreferredQuality.P720,
            height = { it },
        )

        assertEquals(1080, selected)
    }

    @Test
    fun autoQualitySelectsHighestHeight() {
        val selected = listOf(720, 1080, 480).selectForPreferredQuality(
            preferredQuality = PreferredQuality.Auto,
            height = { it },
        )

        assertEquals(1080, selected)
    }

    @Test
    fun bitrateBreaksTiesWithinSameHeight() {
        val selected = listOf(
            QualityCandidate(height = 720, bitrate = 900),
            QualityCandidate(height = 720, bitrate = 1_800),
        ).selectForPreferredQuality(
            preferredQuality = PreferredQuality.P720,
            height = { it.height },
            bitrate = { it.bitrate },
        )

        assertEquals(1_800, selected?.bitrate)
    }
}

private data class QualityCandidate(
    val height: Int,
    val bitrate: Int,
)
