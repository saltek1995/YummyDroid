package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WebVttCueLayoutTest {
    @Test
    fun overlappingWebVttCuesWithoutPlacementAreStackedApart() {
        val subtitles = """
            WEBVTT

            00:02:46.900 --> 00:02:49.150
            <b>С НАДРЫВОМ</b>

            00:02:46.900 --> 00:02:49.150
            <b>УЛЫБКА</b>

            00:02:46.900 --> 00:02:51.530
            Умейте слушать. Недопустимо говорить только о себе.

            00:02:47.150 --> 00:02:51.530
            <b>УМЕЙТЕ СЛУШАТЬ <i>!</i></b>
        """.trimIndent()

        val playable = assertNotNull(subtitles.toPlayableSubtitleBody(uri = "sub_rus-2.vtt"))

        assertTrue("00:02:46.900 --> 00:02:49.150 line:8% position:50% align:center" in playable.text)
        assertTrue("00:02:46.900 --> 00:02:49.150 line:22% position:50% align:center" in playable.text)
        assertTrue("00:02:46.900 --> 00:02:51.530 line:-1 position:50% align:center" in playable.text)
        assertTrue("00:02:47.150 --> 00:02:51.530 line:36% position:50% align:center" in playable.text)
        assertTrue(playable.text.hasSubtitleCues(mimeType = playable.mimeType))
    }

    @Test
    fun explicitWebVttPlacementIsNotRewritten() {
        val subtitles = """
            WEBVTT

            00:00:01.000 --> 00:00:03.000 line:70% position:20% align:start
            Existing placement.

            00:00:01.500 --> 00:00:03.000
            Plain overlapping subtitle.
        """.trimIndent()

        val playable = assertNotNull(subtitles.toPlayableSubtitleBody(uri = "subtitle.vtt"))

        assertTrue("00:00:01.000 --> 00:00:03.000 line:70% position:20% align:start" in playable.text)
        assertTrue("00:00:01.500 --> 00:00:03.000 line:-1 position:50% align:center" in playable.text)
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
}
