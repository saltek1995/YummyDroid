package me.yummydroid.app.ui

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = android.app.Application::class)
class ReleaseNotesTextTest {
    @Test
    fun emptyHtmlAndCommentsHaveNoVisibleDescription() {
        listOf("", "  ", "<!-- hidden -->", "<p><!-- hidden\ncomment --></p>", "<p>&nbsp;</p>")
            .forEach { assertEquals("", it.releaseNotesDisplayText()) }
    }

    @Test
    fun plainTextAndMarkdownKeepLineBreaksAndLiteralCharacters() {
        val notes = "Исправления:\n- Загрузка при скорости < 3 Мбит/с\n- **Плеер** & звук\n<https://example.com>"
        assertEquals(notes, notes.releaseNotesDisplayText())
    }

    @Test
    fun visibleDescriptionRemainsAfterRemovingComments() {
        assertEquals("Исправлен плеер.", "<!-- hidden -->Исправлен плеер.".releaseNotesDisplayText())
    }

    @Test
    fun htmlDescriptionShowsTextAndDecodesEntities() {
        val text = "<p>Исправлен <b>плеер</b> &amp; звук.</p><p>Вторая строка<br>Третья строка</p>"
            .releaseNotesDisplayText()
        assertTrue(text.startsWith("Исправлен плеер & звук."))
        assertTrue(text.contains("\n"))
        assertTrue(text.endsWith("Вторая строка\nТретья строка"))
        assertFalse(text.contains("<"))
    }
}
