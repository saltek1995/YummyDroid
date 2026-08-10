package me.yummydroid.app.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

internal class VisualFocusRetentionState(private val size: Int) {
    private val focusedIndexState = mutableIntStateOf(-1)
    private val lastFocusedIndexState = mutableIntStateOf(-1)
    private val lastFocusedKeyState = mutableStateOf<Any?>(null)

    val focusedIndex: Int? get() = focusedIndexState.intValue.takeIf(::contains)
    val lastFocusedIndex: Int? get() = lastFocusedIndexState.intValue.takeIf(::contains)
    val lastFocusedKey: Any? get() = lastFocusedKeyState.value

    fun focus(index: Int, focusKey: Any?) {
        focusedIndexState.intValue = index
        lastFocusedIndexState.intValue = index
        lastFocusedKeyState.value = focusKey
    }

    fun clearFocusedIndex(index: Int) {
        if (focusedIndexState.intValue == index) {
            focusedIndexState.intValue = -1
        }
    }

    fun updateLastFocusedKey(focusKey: Any) {
        lastFocusedKeyState.value = focusKey
    }

    private fun contains(index: Int): Boolean = index in 0 until size
}
