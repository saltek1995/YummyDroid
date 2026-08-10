package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TtmlCueValidationTest {
    @Test
    fun emptyParagraphIsInvalid() {
        val subtitles = """<tt><body><div><p begin="00:00:01.000" end="00:00:02.000"></p></div></body></tt>"""

        assertFalse(subtitles.hasSubtitleCues(mimeType = "application/ttml+xml"))
    }

    @Test
    fun paragraphWithTextIsValid() {
        val subtitles = """<tt><body><div><p begin="00:00:01.000" end="00:00:02.000">Привет.</p></div></body></tt>"""

        assertTrue(subtitles.hasSubtitleCues(mimeType = "application/ttml+xml"))
    }
}
