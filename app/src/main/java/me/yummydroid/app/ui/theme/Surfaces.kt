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
        YummySurfaceRole.Panel -> YummyColors.deepPanel.copy(alpha = YummyAlpha.subtleSurface)
        YummySurfaceRole.Row -> YummyColors.liftedPanel.copy(alpha = YummyAlpha.rowSurface)
        YummySurfaceRole.ActiveRow -> MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
    }
}

@Composable
internal fun yummySurfaceContentColor(role: YummySurfaceRole): Color {
    return when (role) {
        YummySurfaceRole.ActiveRow -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
}
