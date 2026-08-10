package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import me.yummydroid.app.localizedString

@Composable
internal fun uiText(key: UiStringKey): String {
    val context = LocalContext.current
    val language = LocalUiLanguage.current
    return remember(context, language, key) {
        context.localizedString(key.resId, language)
    }
}

@Composable
internal fun uiText(key: UiStringKey, vararg formatArgs: Any): String {
    val context = LocalContext.current
    val language = LocalUiLanguage.current
    return remember(context, language, key, formatArgs.contentHashCode()) {
        context.localizedString(key.resId, language, *formatArgs)
    }
}
