package me.yummydroid.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class YummyColorSchemeTest {
    @Test
    fun semanticColorSchemeKeepsItsArgbContract() {
        with(YummyDarkColors) {
            assertEquals(Color(0xFFFFB454), primary)
            assertEquals(Color(0xFF211200), onPrimary)
            assertEquals(Color(0xFF6A4209), primaryContainer)
            assertEquals(Color(0xFFFFE1B1), onPrimaryContainer)
            assertEquals(Color(0xFF00E5FF), secondary)
            assertEquals(Color(0xFF001318), onSecondary)
            assertEquals(Color(0xFF063E4A), secondaryContainer)
            assertEquals(Color(0xFFC7F7FF), onSecondaryContainer)
            assertEquals(Color(0xFFFF40D6), tertiary)
            assertEquals(Color(0xFF26001D), onTertiary)
            assertEquals(Color(0xFF55204B), tertiaryContainer)
            assertEquals(Color(0xFFFFD6F6), onTertiaryContainer)
            assertEquals(Color(0xFF121926), background)
            assertEquals(Color(0xFFF3F8FF), onBackground)
            assertEquals(Color(0xFF111A2C), surface)
            assertEquals(Color(0xFFEAF2FF), onSurface)
            assertEquals(Color(0xFF17243A), surfaceVariant)
            assertEquals(Color(0xFFC9D7EA), onSurfaceVariant)
            assertEquals(Color(0xFF48617D), outline)
            assertEquals(Color(0xFF263B55), outlineVariant)
            assertEquals(Color(0xFFFF6B7A), error)
            assertEquals(Color(0xFF2B050B), onError)
            assertEquals(Color(0xFF5E1420), errorContainer)
            assertEquals(Color(0xFFFFD7DC), onErrorContainer)
        }
    }
}
