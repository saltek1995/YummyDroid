package me.yummydroid.app.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummySurfaceBorder

@Composable
internal fun SearchDialogPanel(
    query: String,
    isTelevision: Boolean,
    visibleHistory: List<String>,
    historyFocusRequesters: List<FocusRequester>,
    focusState: SearchDialogFocusState,
    actions: SearchDialogActions,
    onQueryChange: (String) -> Unit,
    onHistorySelected: (String) -> Unit,
    onLaunchVoiceSearch: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        SearchDialogBackdrop(actions::dismissSearch)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    horizontal = if (isTelevision) 40.dp else 16.dp,
                    vertical = 0.dp,
                )
                .padding(bottom = 10.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .yummyDialogMotion(),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = YummyRadii.mediumShape,
                border = yummySurfaceBorder(YummySurfaceRole.Row),
                shadowElevation = 10.dp,
            ) {
                SearchDialogPanelContent(
                    query = query,
                    visibleHistory = visibleHistory,
                    historyFocusRequesters = historyFocusRequesters,
                    focusState = focusState,
                    actions = actions,
                    onQueryChange = onQueryChange,
                    onHistorySelected = onHistorySelected,
                    onLaunchVoiceSearch = onLaunchVoiceSearch,
                )
            }
        }
    }
}

@Composable
private fun SearchDialogBackdrop(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    )
}
