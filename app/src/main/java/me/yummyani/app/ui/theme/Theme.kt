package me.yummyani.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val YummyDarkColors = darkColorScheme(
    primary = Color(0xFFFFB454),
    onPrimary = Color(0xFF1B1305),
    secondary = Color(0xFF6AE3C1),
    onSecondary = Color(0xFF031B15),
    tertiary = Color(0xFF8BB7FF),
    background = Color(0xFF0F131C),
    onBackground = Color(0xFFF3F6FA),
    surface = Color(0xFF171D29),
    onSurface = Color(0xFFE8EDF4),
    surfaceVariant = Color(0xFF222A38),
    onSurfaceVariant = Color(0xFFC4CAD4),
    error = Color(0xFFFF6B7A),
)

@Composable
fun YummyAniTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YummyDarkColors,
        content = content,
    )
}
