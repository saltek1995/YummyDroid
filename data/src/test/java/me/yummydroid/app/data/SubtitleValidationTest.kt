package me.yummydroid.app.data

import java.io.File
import kotlin.io.path.createTempDirectory
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

    @Test
    fun jsonCueListIsConvertedToPlayableWebVtt() {
        val subtitles = """
            {
              "captions": [
                {"start": 1.25, "end": 2.5, "text": "Hello<br>world"},
                {"startMs": 3000, "durationMs": 1250, "caption": "Again"}
              ]
            }
        """.trimIndent()

        val playable = assertNotNull(subtitles.toPlayableSubtitleBody(uri = "https://example.test/captions?id=1"))

        assertEquals("text/vtt", playable.mimeType)
        assertTrue("00:00:01.250 --> 00:00:02.500" in playable.text)
        assertTrue("Hello\nworld" in playable.text)
        assertTrue("00:00:03.000 --> 00:00:04.250" in playable.text)
        assertTrue(playable.text.hasSubtitleCues(mimeType = playable.mimeType))
    }

    @Test
    fun jsonCueSettingsAreConvertedToWebVtt() {
        val subtitles = """
            {
              "captions": [
                {
                  "start": 1.25,
                  "end": 2.5,
                  "text": "Sign",
                  "line": "10%",
                  "position": "80%",
                  "align": "middle",
                  "size": "35%"
                }
              ]
            }
        """.trimIndent()

        val playable = assertNotNull(subtitles.toPlayableSubtitleBody(uri = "https://example.test/captions?id=1"))

        assertTrue("00:00:01.250 --> 00:00:02.500 line:10% position:80% align:center size:35%" in playable.text)
        assertTrue(playable.text.hasSubtitleCues(mimeType = playable.mimeType))
    }

    @Test
    fun webVttCueBodyKeepsTimestampMapLocalTimeSeparately() {
        val segment = """
            WEBVTT
            X-TIMESTAMP-MAP=LOCAL:01:00:00.000,MPEGTS:324000000

            01:00:01.000 --> 01:00:02.000
            Hello.
        """.trimIndent()

        val body = segment.webVttCueBody()

        assertEquals(3_600_000L, body.localMapMs)
        assertFalse("X-TIMESTAMP-MAP" in body.text)
        assertTrue("01:00:01.000 --> 01:00:02.000" in body.text)
    }

    @Test
    fun webVttCueBodyKeepsStyleAndRegionBlocksSeparately() {
        val segment = """
            WEBVTT
            X-TIMESTAMP-MAP=LOCAL:00:00:00.000,MPEGTS:0

            STYLE
            ::cue(.sign) { position: absolute; }

            REGION
            id:top
            lines:3

            00:00:01.000 --> 00:00:02.000 region:top line:10%
            <c.sign>Sign text</c>
        """.trimIndent()

        val body = segment.webVttCueBody()

        assertEquals(0L, body.localMapMs)
        assertEquals(2, body.topLevelBlocks.size)
        assertTrue(body.topLevelBlocks.any { it.startsWith("STYLE") })
        assertTrue(body.topLevelBlocks.any { it.startsWith("REGION") })
        assertTrue("region:top line:10%" in body.text)
        assertFalse("STYLE" in body.text)
        assertFalse("REGION" in body.text)
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
    fun playerDiscoveryBridgeCapturesJsonXhrResponses() {
        val script = VideoStreamResolver.playerDiscoveryBridgeScript

        assertTrue("responseType === 'json'" in script)
        assertTrue("JSON.stringify(this.response)" in script)
        assertFalse("responseType && responseType !== 'text') return" in script)
    }
}
