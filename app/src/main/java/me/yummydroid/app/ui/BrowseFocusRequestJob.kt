package me.yummydroid.app.ui

import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class FocusRequestJobRef {
    var job: Job? = null
    private var pendingIndex: Int? = null

    fun clearPending() {
        pendingIndex = null
    }

    fun requestFocusWhenReady(
        index: Int,
        focusScope: CoroutineScope,
        requestItemFocus: (Int) -> Boolean,
    ) {
        pendingIndex = index
        if (job?.isActive == true) return
        job = focusScope.launch {
            while (true) {
                val target = pendingIndex ?: break
                var targetChanged = false
                var focused = false
                for (attempt in 0 until 8) {
                    withFrameNanos { }
                    if (pendingIndex != target) {
                        targetChanged = true
                        break
                    }
                    if (requestItemFocus(target)) {
                        focused = true
                        break
                    }
                }
                if (pendingIndex == target && (focused || !targetChanged)) {
                    pendingIndex = null
                }
                if (pendingIndex == null) break
            }
            job = null
        }
    }
}
