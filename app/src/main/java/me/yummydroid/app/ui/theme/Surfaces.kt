package me.yummydroid.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
