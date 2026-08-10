package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssCueValidationTest {
    @Test
    fun dialogueWithoutVisibleTextIsInvalid() {
        val subtitles = "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,{\\an8}\\N"

        assertFalse(subtitles.hasSubtitleCues(uri = "subtitle.ass"))
    }

    @Test
    fun dialogueWithVisibleTextIsValid() {
        val subtitles = "Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,{\\an8}Привет, мир"

        assertTrue(subtitles.hasSubtitleCues(uri = "subtitle.ass"))
    }
}
