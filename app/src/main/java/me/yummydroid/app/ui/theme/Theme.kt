package me.yummydroid.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val YummyDarkColors = darkColorScheme(
    primary = Color(0xFFFFB454),
    onPrimary = Color(0xFF1B1305),
    primaryContainer = Color(0xFFFFB454),
    onPrimaryContainer = Color(0xFF1B1305),
    secondary = Color(0xFF6AE3C1),
    onSecondary = Color(0xFF031B15),
    secondaryContainer = Color(0xFF163D35),
    onSecondaryContainer = Color(0xFFBFF4E6),
    tertiary = Color(0xFF8BB7FF),
    tertiaryContainer = Color(0xFF233B61),
    onTertiaryContainer = Color(0xFFD6E4FF),
    background = Color(0xFF0D121B),
    onBackground = Color(0xFFF3F6FA),
    surface = Color(0xFF161D2A),
    onSurface = Color(0xFFE8EDF4),
    surfaceVariant = Color(0xFF283243),
    onSurfaceVariant = Color(0xFFD0D6E0),
    outline = Color(0xFF8390A3),
    outlineVariant = Color(0xFF4A5568),
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
