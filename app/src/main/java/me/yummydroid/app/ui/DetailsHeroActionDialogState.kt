package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import me.yummydroid.app.InputAction

internal class DetailsHeroActionDialogState {
    var downloadOpen by mutableStateOf(false)
    var resetOpen by mutableStateOf(false)

    fun handleInput(action: InputAction): Boolean {
        if (action != InputAction.Back) return false
        return when {
            downloadOpen -> {
                downloadOpen = false
                true
            }
            resetOpen -> {
                resetOpen = false
                true
            }
            else -> false
        }
    }
}

@Composable
internal fun rememberDetailsHeroActionDialogState(
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
): DetailsHeroActionDialogState {
    val state = remember { DetailsHeroActionDialogState() }
    val inputActionHandler by rememberUpdatedState { action: InputAction -> state.handleInput(action) }
    DisposableEffect(state.downloadOpen, state.resetOpen, onRegisterModalInputActionHandler) {
        if (state.downloadOpen || state.resetOpen) {
            onRegisterModalInputActionHandler { action -> inputActionHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }
    return state
}
