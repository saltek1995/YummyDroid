package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

@Composable
internal fun SearchDialogLifecycleEffects(
    keyboardDismissRequest: Long,
    visibleHistory: List<String>,
    focusState: SearchDialogFocusState,
    onFocusInput: () -> Unit,
    onHideKeyboard: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(80)
        onFocusInput()
    }
    LaunchedEffect(keyboardDismissRequest) {
        if (keyboardDismissRequest > 0L) onHideKeyboard()
    }
    LaunchedEffect(visibleHistory) {
        focusState.retainHistoryIndexWithin(visibleHistory.size)
    }
}
