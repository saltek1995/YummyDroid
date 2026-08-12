package me.yummydroid.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

// ActionSurfaces
internal fun yummyActionSurfaceColor(
    enabled: Boolean = true,
    selected: Boolean = false,
    focused: Boolean = false,
): Color {
    return when {
        !enabled -> YummyColors.actionSurfaceDisabled
        focused -> YummyColors.focus
        selected -> YummyColors.actionSurfaceSelected
        else -> YummyColors.actionSurface
    }
}

@Composable
internal fun yummyActionContentColor(
    enabled: Boolean = true,
    selected: Boolean = false,
    focused: Boolean = false,
    destructive: Boolean = false,
): Color {
    return when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.34f)
        focused -> YummyColors.onFocus
        destructive -> MaterialTheme.colorScheme.error
        selected -> YummyColors.focus
        else -> MaterialTheme.colorScheme.onSurface
    }
}

internal fun yummyActionBorder(
    enabled: Boolean = true,
    selected: Boolean = false,
    focused: Boolean = false,
): BorderStroke {
    val color = when {
        !enabled -> YummyColors.actionBorder.copy(alpha = 0.10f)
        focused -> Color.Transparent
        selected -> YummyColors.focus.copy(alpha = 0.30f)
        else -> YummyColors.actionBorder.copy(alpha = 0.18f)
    }
    return BorderStroke(1.dp, color)
}

// SurfaceRoles
internal enum class YummySurfaceRole {
    Panel,
    Row,
    ActiveRow,
}

@Composable
internal fun yummySurfaceColor(role: YummySurfaceRole): Color {
    return when (role) {
        YummySurfaceRole.Panel -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = YummyAlpha.subtleSurface)
        YummySurfaceRole.Row -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = YummyAlpha.rowSurface)
        YummySurfaceRole.ActiveRow -> MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
    }
}

@Composable
internal fun yummySurfaceContentColor(role: YummySurfaceRole): Color {
    return when (role) {
        YummySurfaceRole.ActiveRow -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
internal fun yummySurfaceBorder(role: YummySurfaceRole): BorderStroke {
    val color = when (role) {
        YummySurfaceRole.ActiveRow -> Color.Transparent
        YummySurfaceRole.Panel -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
        YummySurfaceRole.Row -> Color.Transparent
    }
    return BorderStroke(1.dp, color)
}

// YummyAlpha
internal object YummyAlpha {
    const val subtleSurface = 0.42f
    const val rowSurface = 0.92f
    const val disabledSurface = 0.58f
    const val badgeSurface = 0.82f
}

// YummyColors
internal object YummyColors {
    val focus = Color(0xFFFFB454)
    val onFocus = Color(0xFF211200)
    val focusOverlay = Color(0xFFFFB454)
    val rating = Color(0xFFFFB454)
    val offline = Color(0xFFB8FF2D)
    val watched = Color(0xFF3DFF9D)
    val actionSurface = Color(0xFF142238)
    val actionSurfaceSelected = Color(0xFF1A304B)
    val actionSurfaceDisabled = Color(0xFF10192A)
    val actionBorder = Color(0xFF42658A)
}

// YummyColorScheme
internal val YummyDarkColors = darkColorScheme(
    primary = Color(0xFFFFB454),
    onPrimary = Color(0xFF211200),
    primaryContainer = Color(0xFF6A4209),
    onPrimaryContainer = Color(0xFFFFE1B1),
    secondary = Color(0xFF00E5FF),
    onSecondary = Color(0xFF001318),
    secondaryContainer = Color(0xFF063E4A),
    onSecondaryContainer = Color(0xFFC7F7FF),
    tertiary = Color(0xFFFF40D6),
    onTertiary = Color(0xFF26001D),
    tertiaryContainer = Color(0xFF55204B),
    onTertiaryContainer = Color(0xFFFFD6F6),
    background = Color(0xFF121926),
    onBackground = Color(0xFFF3F8FF),
    surface = Color(0xFF111A2C),
    onSurface = Color(0xFFEAF2FF),
    surfaceVariant = Color(0xFF17243A),
    onSurfaceVariant = Color(0xFFC9D7EA),
    outline = Color(0xFF48617D),
    outlineVariant = Color(0xFF263B55),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF2B050B),
    errorContainer = Color(0xFF5E1420),
    onErrorContainer = Color(0xFFFFD7DC),
)

// YummyDroidTheme
@Composable
fun YummyDroidTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = yummyDroidColorScheme(),
        content = content,
    )
}

internal fun yummyDroidColorScheme() = YummyDarkColors

// YummyRadii
internal object YummyRadii {
    val small = 8.dp
    val medium = 12.dp
    val pill = 50.dp

    val smallShape
        get() = RoundedCornerShape(small)

    val mediumShape
        get() = RoundedCornerShape(medium)

    val pillShape
        get() = RoundedCornerShape(pill)
}

// YummySizes
internal object YummySizes {
    val tabHeight = 48.dp
    val dialogButtonHeight = 40.dp
    val dialogButtonMinWidth = 84.dp
    val primaryDialogButtonMinWidth = 104.dp
    val animeCardInfoHeight = 92.dp
    val animeTitleHeight = 42.dp
    val animeMetaHeight = 18.dp
    val episodeHeight = 86.dp
    val badgeIcon = 15.dp
}

// YummySpacing
internal object YummySpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}
