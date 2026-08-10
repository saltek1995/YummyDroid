package me.yummydroid.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
