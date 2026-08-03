package me.yummydroid.app.ui

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
internal fun rememberBrowseRootLazyGridState(): LazyGridState {
    return rememberSaveable(
        saver = listSaver(
            save = { state ->
                listOf(
                    state.firstVisibleItemIndex,
                    state.firstVisibleItemScrollOffset,
                )
            },
            restore = { values ->
                LazyGridState(
                    firstVisibleItemIndex = values.getOrElse(0) { 0 },
                    firstVisibleItemScrollOffset = values.getOrElse(1) { 0 },
                )
            },
        ),
    ) {
        LazyGridState()
    }
}
