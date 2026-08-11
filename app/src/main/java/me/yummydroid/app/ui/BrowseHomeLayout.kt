package me.yummydroid.app.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

@Composable
internal fun BrowseHomeLayout(
    state: BrowseHomeLayoutState,
    actions: BrowseHomeLayoutActions,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { focusState ->
                actions.onLayerFocusChanged(focusState.isFocused || focusState.hasFocus)
            }
            .focusGroup(),
    ) {
        BrowseHomeContentLayout(state, actions)
        if (state.chromePolicy.showBottomChrome) {
            BrowseHomeBottomChrome(state, actions)
        }
    }
}
