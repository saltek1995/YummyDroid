package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackSourceSelectionTest {
    @Test
    fun providerPriorityWinsOverSiteOrderWithoutManualChoice() {
        val kodik = kodikSourceVideo()
        val cvh = cvhSourceVideo()

        val ordered = listOf(kodik, cvh).sortedForPlaybackSource(
            requested = kodik,
            manualSourceKey = null,
        )

        assertEquals(cvh.id, ordered.first().id)
    }

    @Test
    fun providerPriorityOverridesRequestedCachedAndHigherQualityAutomaticSources() {
        val providers = listOf("CVH", "Aksor", "Kodik", "VK", "Alloha", "Sibnet")
        val videos = providers.mapIndexed { index, player ->
            playbackSourceVideo(index + 1L, player, 100 - index, "https://example.test/$player/${240 + index * 240}p")
                .copy(sourceQualities = listOf(me.yummydroid.app.data.SourceQuality(240 + index * 240)))
        }
        assertEquals(providers, videos.reversed().sortedForPlaybackSource(
            requested = videos.last(), manualSourceKey = null, cachedSourceKey = videos.last().sourceSelectionKey,
        ).map { it.player })
        assertEquals(videos.last(), videos.sortedForPlaybackSource(
            requested = videos.first(), manualSourceKey = videos.last().sourceSelectionKey,
        ).first())
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
