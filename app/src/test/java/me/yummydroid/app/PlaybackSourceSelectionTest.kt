package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackSourceSelectionTest {
    @Test
    fun higherEstimatedSourceQualityWinsOverSiteOrderWithoutManualChoice() {
        val kodik = kodikSourceVideo()
        val cvh = cvhSourceVideo()

        val ordered = listOf(kodik, cvh).sortedForPlaybackSource(
            requested = kodik,
            manualSourceKey = null,
        )

        assertEquals(cvh.id, ordered.first().id)
    }

    @Test
    fun manualSourceWinsOverHigherEstimatedSourceQuality() {
        val kodik = kodikSourceVideo()
        val cvh = cvhSourceVideo()

        val ordered = listOf(cvh, kodik).sortedForPlaybackSource(
            requested = cvh,
            manualSourceKey = kodik.sourceSelectionKey,
            cachedSourceKey = cvh.sourceSelectionKey,
        )

        assertEquals(kodik.id, ordered.first().id)
    }
}
