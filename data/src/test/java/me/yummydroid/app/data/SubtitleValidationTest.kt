package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SubtitleValidationTest {
    @Test
    fun webVttHeaderOnlyIsInvalid() {
        assertFalse("WEBVTT\n\n".hasSubtitleCues(mimeType = "text/vtt"))
    }

    @Test
    fun webVttTimingWithoutTextIsInvalid() {
        val subtitles = """
            WEBVTT

            00:00:01.000 --> 00:00:02.000
        """.trimIndent()

        assertFalse(subtitles.hasSubtitleCues(mimeType = "text/vtt"))
    }

    @Test
    fun webVttCueWithOnlyTagsAndSpacesIsInvalid() {
        val subtitles = """
            WEBVTT

            00:00:01.000 --> 00:00:02.000
            <c>&nbsp;</c>
        """.trimIndent()

        assertFalse(subtitles.hasSubtitleCues(mimeType = "text/vtt"))
    }

    @Test
    fun webVttCueWithTextIsValid() {
        val subtitles = """
            WEBVTT

            00:00:01.000 --> 00:00:02.000
            Привет.
        """.trimIndent()

        assertTrue(subtitles.hasSubtitleCues(mimeType = "text/vtt"))
    }

    @Test
    fun srtCueWithCommaTimingIsValid() {
        val subtitles = """
            1
            00:00:01,000 --> 00:00:02,000
            Привет.
        """.trimIndent()

        assertTrue(subtitles.hasSubtitleCues(mimeType = "application/x-subrip"))
    }

    @Test
    fun assDialogueWithoutVisibleTextIsInvalid() {
        val subtitles = "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,{\\an8}\\N"

        assertFalse(subtitles.hasSubtitleCues(uri = "subtitle.ass"))
    }

    @Test
    fun assDialogueWithVisibleTextIsValid() {
        val subtitles = "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,{\\an8}Привет, мир"

        assertTrue(subtitles.hasSubtitleCues(uri = "subtitle.ass"))
    }

    @Test
    fun ttmlEmptyParagraphIsInvalid() {
        val subtitles = """<tt><body><div><p begin="00:00:01.000" end="00:00:02.000"></p></div></body></tt>"""

        assertFalse(subtitles.hasSubtitleCues(mimeType = "application/ttml+xml"))
    }

    @Test
    fun ttmlParagraphWithTextIsValid() {
        val subtitles = """<tt><body><div><p begin="00:00:01.000" end="00:00:02.000">Привет.</p></div></body></tt>"""

        assertTrue(subtitles.hasSubtitleCues(mimeType = "application/ttml+xml"))
    }

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
    fun assDialogueIsConvertedToPlayableWebVtt() {
        val subtitles = "Dialogue: 0,0:00:01.00,0:00:02.50,Default,,0,0,0,,{\\an8}Hello\\Nworld"

        val playable = assertNotNull(subtitles.toPlayableSubtitleBody(uri = "subtitle.ass"))

        assertEquals("text/vtt", playable.mimeType)
        assertTrue("00:00:01.000 --> 00:00:02.500" in playable.text)
        assertTrue("Hello\nworld" in playable.text)
        assertTrue(playable.text.hasSubtitleCues(mimeType = playable.mimeType))
    }
}
