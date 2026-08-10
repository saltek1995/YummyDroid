package me.yummydroid.app.ui

import androidx.compose.ui.focus.FocusRequester

internal fun FocusRequester.requestFocusSafely(): Boolean {
    return runCatching { requestFocus() }.getOrDefault(false)
}
