package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebVttCueValidationTest {
    @Test
    fun headerOnlyIsInvalid() {
        assertFalse("WEBVTT\n\n".hasSubtitleCues(mimeType = "text/vtt"))
    }

    @Test
    fun timingWithoutTextIsInvalid() {
        val subtitles = """
            WEBVTT

            00:00:01.000 --> 00:00:02.000
        """.trimIndent()

        assertFalse(subtitles.hasSubtitleCues(mimeType = "text/vtt"))
    }

    @Test
    fun cueWithOnlyTagsAndSpacesIsInvalid() {
        val subtitles = """
            WEBVTT

            00:00:01.000 --> 00:00:02.000
            <c>&nbsp;</c>
        """.trimIndent()

        assertFalse(subtitles.hasSubtitleCues(mimeType = "text/vtt"))
    }

    @Test
    fun cueWithTextIsValid() {
        val subtitles = """
            WEBVTT

            00:00:01.000 --> 00:00:02.000
            Привет.
        """.trimIndent()

        assertTrue(subtitles.hasSubtitleCues(mimeType = "text/vtt"))
    }

    @Test
    fun unnamedCueWithTextIsPlayable() {
        val subtitles = """
            WEBVTT

            00:00:01.000 --> 00:00:02.000
            Hello.
        """.trimIndent()

        assertNotNull(subtitles.toPlayableSubtitleBody(mimeType = "text/vtt", uri = ""))
    }

    @Test
    fun placeholderWithoutCueTextIsNotPlayable() {
        val subtitles = """
            WEBVTT

            NOTE subtitles are available

            STYLE
            ::cue { color: white; }
        """.trimIndent()

        assertNull(subtitles.toPlayableSubtitleBody(mimeType = "text/vtt", uri = ""))
    }
}
