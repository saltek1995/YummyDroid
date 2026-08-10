package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertTrue

class SrtCueValidationTest {
    @Test
    fun commaTimingWithTextIsValid() {
        val subtitles = """
            1
            00:00:01,000 --> 00:00:02,000
            Привет.
        """.trimIndent()

        assertTrue(subtitles.hasSubtitleCues(mimeType = "application/x-subrip"))
    }
}
