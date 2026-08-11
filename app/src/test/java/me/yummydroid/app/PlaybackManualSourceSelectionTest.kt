package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackManualSourceSelectionTest {
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

    @Test
    fun manualSourceIgnoresBufferingTimeoutFallback() {
        val cvh = cvhSourceVideo()

        assertFalse(
            shouldUseAutomaticPlaybackFallback(
                currentVideo = cvh,
                failedVideo = cvh,
                manualSourceKey = cvh.sourceSelectionKey,
                failure = PlaybackFailure(PlaybackFailureKind.BufferingTimeout),
            ),
        )
    }

    @Test
    fun manualSourceAllowsPlayerErrorFallback() {
        val cvh = cvhSourceVideo()

        assertTrue(
            shouldUseAutomaticPlaybackFallback(
                currentVideo = cvh,
                failedVideo = cvh,
                manualSourceKey = cvh.sourceSelectionKey,
                failure = PlaybackFailure(PlaybackFailureKind.PlayerError, "HTTP 500"),
            ),
        )
    }

    @Test
    fun staleSourceFailureIsIgnored() {
        val kodik = kodikSourceVideo()
        val cvh = cvhSourceVideo()

        assertFalse(
            shouldUseAutomaticPlaybackFallback(
                currentVideo = cvh,
                failedVideo = kodik,
                manualSourceKey = cvh.sourceSelectionKey,
                failure = PlaybackFailure(PlaybackFailureKind.PlayerError, "HTTP 500"),
            ),
        )
    }
}
