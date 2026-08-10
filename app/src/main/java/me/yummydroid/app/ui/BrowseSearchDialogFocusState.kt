package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

internal class SearchDialogFocusState {
    val inputFocusRequester = FocusRequester()
    val micFocusRequester = FocusRequester()

    var inputFocused by mutableStateOf(false)
        private set
    var micFocused by mutableStateOf(false)
        private set
    var focusedHistoryIndex by mutableIntStateOf(-1)
        private set

    fun updateInputFocus(focused: Boolean) {
        inputFocused = focused
        if (focused) focusedHistoryIndex = -1
    }

    fun updateMicFocus(focused: Boolean) {
        micFocused = focused
        if (focused) focusedHistoryIndex = -1
    }

    fun setHistoryFocused(index: Int, focused: Boolean) {
        if (focused) {
            focusedHistoryIndex = index
        } else if (focusedHistoryIndex == index) {
            focusedHistoryIndex = -1
        }
    }

    fun retainHistoryIndexWithin(size: Int) {
        if (focusedHistoryIndex >= size) focusedHistoryIndex = -1
    }
}

@Composable
internal fun rememberSearchDialogFocusState(): SearchDialogFocusState {
    return remember { SearchDialogFocusState() }
}
