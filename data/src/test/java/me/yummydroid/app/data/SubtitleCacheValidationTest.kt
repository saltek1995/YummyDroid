package me.yummydroid.app.data

import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubtitleCacheValidationTest {
    @Test
    fun concurrentSubtitleReadersNeverSeeMissingOrPartialReplacement() {
        val directory = createTempDirectory("yummy-subtitle-replacement").toFile()
        val executor = Executors.newFixedThreadPool(3)
        try {
            val file = File(directory, "subtitle.vtt")
            val bodies = (1..2).map { "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nSubtitle $it\n" }
            assertTrue(file.writeVerifiedSubtitleCacheFile(bodies.first(), "text/vtt"))
            val jobs = listOf(
                Callable { repeat(50) { assertTrue(file.writeVerifiedSubtitleCacheFile(bodies[it % 2], "text/vtt")) } },
                Callable { repeat(200) { assertTrue(file.subtitleTextOrNull() in bodies) } },
                Callable { repeat(200) { assertTrue(file.subtitleTextOrNull() in bodies) } },
            )
            executor.invokeAll(jobs).forEach { it.get() }
            assertFalse(directory.hasTemporarySubtitleFiles())
        } finally {
            executor.shutdownNow()
            directory.deleteRecursively()
        }
    }

    @Test
    fun subtitleCacheWriteIsVerifiedByReadingTheFullFileBack() {
        val directory = createTempDirectory("yummy-subtitle-cache").toFile()
        try {
            val file = File(directory, "subtitle.vtt")
            val subtitles = """
                WEBVTT

                00:00:01.000 --> 00:00:02.000
                Hello.

                00:00:03.000 --> 00:00:04.000
                Again.
            """.trimIndent()

            assertTrue(file.writeVerifiedSubtitleCacheFile(subtitles, "text/vtt"))
            assertEquals(subtitles, file.readText(Charsets.UTF_8))
            assertFalse(directory.hasTemporarySubtitleFiles())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun invalidSubtitleCacheWriteDoesNotReplaceExistingFile() {
        val directory = createTempDirectory("yummy-subtitle-cache").toFile()
        try {
            val file = File(directory, "subtitle.vtt")
            val validSubtitles = """
                WEBVTT

                00:00:01.000 --> 00:00:02.000
                Hello.
            """.trimIndent()
            file.writeText(validSubtitles, Charsets.UTF_8)

            assertFalse(file.writeVerifiedSubtitleCacheFile("WEBVTT\n\n", "text/vtt"))
            assertEquals(validSubtitles, file.readText(Charsets.UTF_8))
            assertFalse(directory.hasTemporarySubtitleFiles())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun playerDiscoveryBridgeOnlyObservesPlayerState() {
        val script = STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT

        assertTrue("currentSource" in script)
        assertTrue("textTracks" in script)
        assertTrue("bridge.captureResponse" in script)
        assertFalse("XMLHttpRequest.prototype" in script)
        assertFalse("window.fetch =" in script)
        assertFalse(".click()" in script)
        assertFalse(".play()" in script)
    }
}

private fun File.hasTemporarySubtitleFiles(): Boolean {
    return listFiles().orEmpty().any { it.extension == "tmp" }
}
