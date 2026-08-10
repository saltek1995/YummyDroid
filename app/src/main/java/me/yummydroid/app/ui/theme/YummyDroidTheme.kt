package me.yummydroid.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

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
