package me.yummydroid.app.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal data class DownloadFocusBinding(
    val requester: FocusRequester,
    val activeKey: String?,
    val onFocused: (String) -> Unit,
)

@Composable
internal fun rememberDownloadFocusBinding(
    model: DownloadScreenModel,
    listState: LazyListState,
    focusCurrentRequestNonce: Long,
): DownloadFocusBinding {
    val focusRequester = remember { FocusRequester() }
    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val activeKey = model.activeFocusKey(focusedKey)
    var handledRequestNonce by remember { mutableLongStateOf(0L) }

    LaunchedEffect(focusCurrentRequestNonce, model.focusKeys) {
        if (
            focusCurrentRequestNonce <= 0L ||
            focusCurrentRequestNonce == handledRequestNonce ||
            activeKey == null
        ) {
            return@LaunchedEffect
        }
        val firstVisibleFocusKey = listState.layoutInfo.visibleItemsInfo
            .asSequence()
            .mapNotNull { item -> model.focusKeysByListIndex[item.index] }
            .firstOrNull()
        val targetFocusKey = firstVisibleFocusKey ?: activeKey
        focusedKey = targetFocusKey
        val targetListIndex = model.listIndexesByFocusKey[targetFocusKey]
        val targetIsVisible = targetListIndex == null ||
            listState.layoutInfo.visibleItemsInfo.any { item -> item.index == targetListIndex }
        if (targetListIndex != null && !targetIsVisible) {
            listState.scrollToItem(targetListIndex, 0)
        }
        listState.focusItemWhenVisible(targetListIndex, focusRequester)
        handledRequestNonce = focusCurrentRequestNonce
    }

    return DownloadFocusBinding(
        requester = focusRequester,
        activeKey = activeKey,
        onFocused = { key -> focusedKey = key },
    )
}

private suspend fun LazyListState.focusItemWhenVisible(
    listIndex: Int?,
    focusRequester: FocusRequester,
) {
    if (listIndex != null) {
        withTimeoutOrNull(1_000L) {
            snapshotFlow {
                layoutInfo.visibleItemsInfo.any { item -> item.index == listIndex }
            }
                .filter { isVisible -> isVisible }
                .first()
        }
    }
    repeat(6) {
        withFrameNanos { }
        if (focusRequester.requestFocusSafely()) return
    }
}
