package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
internal fun rememberVisualFocusGridState(
    size: Int,
    key: Any? = Unit,
    allowLoosePerpendicularMatch: Boolean = false,
): VisualFocusGridState {
    return remember(size, key, allowLoosePerpendicularMatch) {
        VisualFocusGridState(
            size = size.coerceAtLeast(0),
            allowLoosePerpendicularMatch = allowLoosePerpendicularMatch,
        )
    }
}
