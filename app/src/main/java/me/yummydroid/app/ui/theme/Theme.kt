package me.yummydroid.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import me.yummydroid.app.data.AppTheme

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

private val GraphiteColors = YummyDarkColors.copy(
    primary = Color(0xFFE1E6EF),
    onPrimary = Color(0xFF111318),
    secondary = Color(0xFF9AA7B8),
    surface = Color(0xFF181A20),
    surfaceVariant = Color(0xFF262A33),
)

private val OceanColors = YummyDarkColors.copy(
    primary = Color(0xFF6AD8FF),
    onPrimary = Color(0xFF001E2A),
    secondary = Color(0xFF7AF0C4),
    background = Color(0xFF07141B),
    surface = Color(0xFF0D2029),
    surfaceVariant = Color(0xFF153241),
)

private val SakuraColors = YummyDarkColors.copy(
    primary = Color(0xFFFF9EC7),
    onPrimary = Color(0xFF2D0718),
    secondary = Color(0xFFFFC266),
    background = Color(0xFF180F16),
    surface = Color(0xFF241822),
    surfaceVariant = Color(0xFF342435),
)

private val MintColors = YummyDarkColors.copy(
    primary = Color(0xFF78F0B2),
    onPrimary = Color(0xFF041B10),
    secondary = Color(0xFFFFD36A),
    background = Color(0xFF071611),
    surface = Color(0xFF102119),
    surfaceVariant = Color(0xFF1C3429),
)

@Composable
fun YummyDroidTheme(
    appTheme: AppTheme = AppTheme.Yummy,
    content: @Composable () -> Unit,
) {
    val colors = when (appTheme) {
        AppTheme.Yummy -> YummyDarkColors
        AppTheme.Graphite -> GraphiteColors
        AppTheme.Ocean -> OceanColors
        AppTheme.Sakura -> SakuraColors
        AppTheme.Mint -> MintColors
    }
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
