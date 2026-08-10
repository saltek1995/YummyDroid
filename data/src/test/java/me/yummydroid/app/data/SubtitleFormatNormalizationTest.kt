package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SubtitleFormatNormalizationTest {
    @Test
    fun mislabeledVttWithSrtTimingIsConvertedToPlayableWebVtt() {
        val subtitles = """
            1
            00:00:01,500 --> 00:00:02,750
            Hello.
        """.trimIndent()

        val playable = assertNotNull(subtitles.toPlayableSubtitleBody(mimeType = "text/vtt", uri = "alloha.vtt"))

        assertEquals("text/vtt", playable.mimeType)
        assertTrue(playable.text.startsWith("WEBVTT"))
        assertTrue("00:00:01.500 --> 00:00:02.750" in playable.text)
        assertTrue(playable.text.hasSubtitleCues(mimeType = playable.mimeType))
    }

    @Test
    fun assDialogueKeepsNativeSubtitleFormat() {
        val subtitles = """
            [Script Info]
            ScriptType: v4.00+

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:02.50,Default,,0,0,0,,{\an8}Hello\Nworld
        """.trimIndent()

        val playable = assertNotNull(subtitles.toPlayableSubtitleBody(uri = "https://example.test/subtitle?id=1"))

        assertEquals("text/x-ssa", playable.mimeType)
        assertEquals("ass", playable.fileExtension)
        assertTrue(playable.text.contains("{\\an8}Hello\\Nworld"))
        assertTrue(playable.text.hasSubtitleCues(mimeType = playable.mimeType, uri = "subtitle.ass"))
    }

    @Test
    fun assDialogueWithoutHeaderStillKeepsNativeSubtitleFormat() {
        val subtitles = "Dialogue: 0,0:00:01.00,0:00:02.50,Default,,0,0,0,,{\\an8}Hello\\Nworld"

        val playable = assertNotNull(subtitles.toPlayableSubtitleBody(uri = "https://example.test/subtitle?id=1"))

        assertEquals("text/x-ssa", playable.mimeType)
        assertEquals("ass", playable.fileExtension)
        assertTrue(playable.text.contains("[Events]"))
        assertTrue(playable.text.contains("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text"))
        assertTrue(playable.text.contains("{\\an8}Hello\\Nworld"))
    }

    @Test
    fun webVttCueSettingsArePreserved() {
        val subtitles = """
            WEBVTT

            00:00:01.000 --> 00:00:02.500 line:10% position:80% align:start
            Sign text
        """.trimIndent()

        val playable = assertNotNull(subtitles.toPlayableSubtitleBody(uri = "subtitle.vtt"))

        assertEquals("text/vtt", playable.mimeType)
        assertEquals("vtt", playable.fileExtension)
        assertTrue("line:10% position:80% align:start" in playable.text)
        assertTrue(playable.text.hasSubtitleCues(mimeType = playable.mimeType))
    }
}
