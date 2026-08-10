package me.yummydroid.app.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class DirectDownloadSessionTest {
    @Test
    fun progressUsesCumulativeSessionSpeedAndResponseTotal() {
        var now = 2_000L
        val session = DirectDownloadSession(
            target = File("video.mp4"),
            qualityTitle = "1080p",
            voiceTitle = "Voice",
            startedAtMs = 1_000L,
            nowMs = { now },
        )

        val first = session.progressAfterRead(readBytes = 500L, readTotal = 600L, totalBytes = 1_000L)
        now = 3_000L
        val second = session.progressAfterRead(readBytes = 500L, readTotal = 1_100L, totalBytes = 1_000L)

        assertEquals(0.6f, first.fraction)
        assertEquals(500L, first.bytesPerSecond)
        assertEquals(1f, second.fraction)
        assertEquals(500L, second.bytesPerSecond)
        assertEquals("1080p", second.qualityTitle)
        assertEquals("Voice", second.voiceTitle)
    }

    @Test
    fun unknownTotalHasZeroFraction() {
        val session = DirectDownloadSession(
            target = File("video.mp4"),
            qualityTitle = "Auto",
            voiceTitle = "Voice",
            startedAtMs = 0L,
            nowMs = { 1_000L },
        )

        val progress = session.progressAfterRead(readBytes = 250L, readTotal = 250L, totalBytes = -1L)

        assertEquals(0f, progress.fraction)
        assertEquals(-1L, progress.totalBytes)
    }
}
