package me.yummydroid.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val YummyDarkColors = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF001318),
    primaryContainer = Color(0xFF00C8E8),
    onPrimaryContainer = Color(0xFF001318),
    secondary = Color(0xFFB8FF2D),
    onSecondary = Color(0xFF111900),
    secondaryContainer = Color(0xFF243F12),
    onSecondaryContainer = Color(0xFFE5FFB3),
    tertiary = Color(0xFFFF40D6),
    onTertiary = Color(0xFF26001D),
    tertiaryContainer = Color(0xFF55204B),
    onTertiaryContainer = Color(0xFFFFD6F6),
    background = Color(0xFF070B16),
    onBackground = Color(0xFFF3F8FF),
    surface = Color(0xFF0B1020),
    onSurface = Color(0xFFEAF2FF),
    surfaceVariant = Color(0xFF111B2F),
    onSurfaceVariant = Color(0xFFC9D7EA),
    outline = Color(0xFF39546F),
    outlineVariant = Color(0xFF1E324A),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF2B050B),
    errorContainer = Color(0xFF5E1420),
    onErrorContainer = Color(0xFFFFD7DC),
)

@Composable
fun YummyDroidTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = YummyDarkColors,
        content = content,
    )
}
