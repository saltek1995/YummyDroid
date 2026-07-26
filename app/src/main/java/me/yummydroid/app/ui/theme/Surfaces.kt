package me.yummydroid.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
        YummySurfaceRole.ActiveRow -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.86f)
    }
}

@Composable
internal fun yummySurfaceContentColor(role: YummySurfaceRole): Color {
    return when (role) {
        YummySurfaceRole.ActiveRow -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
}
