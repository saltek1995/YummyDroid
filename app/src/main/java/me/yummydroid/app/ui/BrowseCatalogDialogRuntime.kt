package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import me.yummydroid.app.InputAction

internal class BrowseCatalogDialogRuntime {
    var searchDialogOpen by mutableStateOf(false)
    var filtersDialogOpen by mutableStateOf(false)
    var searchKeyboardBackConsumed by mutableStateOf(false)
    var searchKeyboardDismissRequest by mutableLongStateOf(0L)
    var searchInputActionRequest by mutableLongStateOf(0L)
    var searchInputAction by mutableStateOf<InputAction?>(null)

    fun closeCatalogDialogs() {
        filtersDialogOpen = false
        searchDialogOpen = false
    }

    fun resetSearchInputState() {
        searchKeyboardBackConsumed = false
        searchInputAction = null
        searchInputActionRequest = 0L
    }

    fun handleInputAction(action: InputAction): Boolean {
        return when {
            searchDialogOpen -> handleSearchInputAction(action)
            filtersDialogOpen && action == InputAction.Back -> {
                filtersDialogOpen = false
                true
            }
            else -> false
        }
    }

    private fun handleSearchInputAction(action: InputAction): Boolean {
        return when (action) {
            InputAction.Back -> {
                if (searchKeyboardBackConsumed) {
                    searchDialogOpen = false
                } else {
                    searchKeyboardBackConsumed = true
                    searchKeyboardDismissRequest += 1L
                }
                true
            }
            InputAction.Up,
            InputAction.Down,
            InputAction.Left,
            InputAction.Right,
            InputAction.Confirm -> {
                searchKeyboardBackConsumed = true
                searchInputAction = action
                searchInputActionRequest += 1L
                true
            }
            InputAction.Play,
            InputAction.Pause,
            InputAction.PlayPause,
            InputAction.PreviousEpisode,
            InputAction.NextEpisode -> false
        }
    }
}

@Composable
internal fun rememberBrowseCatalogDialogRuntime(
    catalogActionsEnabled: Boolean,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
): BrowseCatalogDialogRuntime {
    val runtime = remember { BrowseCatalogDialogRuntime() }
    LaunchedEffect(catalogActionsEnabled) {
        if (!catalogActionsEnabled) runtime.closeCatalogDialogs()
    }
    LaunchedEffect(runtime.searchDialogOpen) {
        if (runtime.searchDialogOpen) runtime.resetSearchInputState()
    }
    val modalInputActionHandler by rememberUpdatedState { action: InputAction ->
        runtime.handleInputAction(action)
    }
    DisposableEffect(
        runtime.searchDialogOpen,
        runtime.filtersDialogOpen,
        onRegisterModalInputActionHandler,
    ) {
        if (runtime.searchDialogOpen || runtime.filtersDialogOpen) {
            onRegisterModalInputActionHandler { action -> modalInputActionHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }
    return runtime
}
