package me.yummydroid.app.data

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubtitleCacheValidationTest {
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
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun playerDiscoveryBridgeOnlyObservesNativePlayerState() {
        val script = STREAM_PLAYER_DISCOVERY_BRIDGE_SCRIPT

        assertTrue("window.player && window.player.currentSource" in script)
        assertTrue("bridge.captureResponse" in script)
        assertFalse("XMLHttpRequest.prototype" in script)
        assertFalse("window.fetch =" in script)
        assertFalse(".click()" in script)
        assertFalse(".play()" in script)
    }
}
