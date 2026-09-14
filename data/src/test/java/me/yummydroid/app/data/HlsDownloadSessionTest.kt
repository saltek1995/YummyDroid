package me.yummydroid.app.data

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class HlsDownloadSessionTest {
    private val plan = HlsSingleFilePlan(
        mediaSequence = 0, initUrl = "https://example.test/init", outputExtension = "mp4", variantBandwidth = 0,
        segments = (0..2).map { HlsMediaSegment("https://example.test/$it", null, 6.0) },
    )

    private fun withTarget(action: (File) -> Unit) {
        val root = Files.createTempDirectory("hls-checkpoint").toFile()
        try { action(File(root, "episode.mp4")) } finally { root.deleteRecursively() }
    }

    private fun checkpoint(target: File): HlsDownloadSession {
        val session = HlsDownloadSession(target, plan, "720p", "Voice")
        session.prepareResume()
        session.temp.writeText("init")
        session.recordInit(4)
        session.temp.appendText("segment")
        session.recordSegment(0, 7)
        return session
    }

    @Test
    fun resumeTruncatesUncommittedTailAndRetainsCompletedSegments() = withTarget { target ->
        checkpoint(target).temp.appendText("uncommitted partial segment")
        val resumed = HlsDownloadSession(target, plan, "720p", "Voice")
        resumed.prepareResume()
        assertEquals("initsegment", resumed.temp.readText())
        assertEquals(1, resumed.nextSegmentIndex)
        assertEquals(null, resumed.pendingInitUrl())
        resumed.temp.appendText("second")
        resumed.recordSegment(1, 6)
        resumed.complete()
        assertEquals("initsegmentsecond", target.readText())
        assertFalse(resumed.temp.hlsStateFile().exists())
    }

    @Test
    fun missingOrTruncatedPayloadRestartsInsteadOfSkippingSegments() {
        for (missing in listOf(false, true)) withTarget { target ->
            val initial = checkpoint(target)
            if (missing) assertTrue(initial.temp.delete()) else initial.temp.writeText("init")
            val resumed = HlsDownloadSession(target, plan, "720p", "Voice")
            resumed.prepareResume()
            assertEquals(0, resumed.nextSegmentIndex)
            assertEquals(plan.initUrl, resumed.pendingInitUrl())
            assertEquals(0L, resumed.temp.length())
        }
    }

    @Test
    fun legacyInvalidAndOutOfRangeCheckpointsRestartSafely() {
        for (state in listOf("true\n1", "true\n-1\n11", "true\n99\n11", "false\n1\n11", "true\n1\n-1",
            "true\n0\n0", "false\n0\n11")) {
            withTarget { target ->
                val initial = checkpoint(target)
                initial.temp.hlsStateFile().writeText("${plan.signature()}\n$state")
                val resumed = HlsDownloadSession(target, plan, "720p", "Voice")
                resumed.prepareResume()
                assertEquals(0, resumed.nextSegmentIndex, state)
                assertEquals(plan.initUrl, resumed.pendingInitUrl(), state)
                assertEquals(0L, resumed.temp.length(), state)
            }
        }
    }

    @Test
    fun failedPublicationKeepsCheckpointForRetry() = withTarget { target ->
        val session = checkpoint(target)
        target.mkdirs()
        File(target, "block-replacement").writeText("occupied")
        assertFailsWith<IOException> { session.complete() }
        assertTrue(session.temp.hlsStateFile().exists())
        assertEquals("initsegment", session.temp.readText())
    }

    @Test
    fun encryptionIvChangesInvalidateCheckpointIdentity() {
        val first = plan.copy(segments = listOf(plan.segments[0].copy(
            encryption = HlsEncryption("AES-128", "https://example.test/key", byteArrayOf(1)),
        )))
        val second = first.copy(segments = listOf(first.segments[0].copy(
            encryption = first.segments[0].encryption!!.copy(iv = byteArrayOf(2)),
        )))
        assertNotEquals(first.signature(), second.signature())
    }
}
