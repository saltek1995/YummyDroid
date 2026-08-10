package me.yummydroid.app.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import me.yummydroid.app.InputAction

@Composable
internal fun SearchDialog(
    query: String,
    searchHistory: List<String> = emptyList(),
    keyboardDismissRequest: Long = 0L,
    remoteInputAction: InputAction? = null,
    remoteInputActionRequest: Long = 0L,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: (String) -> Unit = {},
    onHistorySelected: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onExitDown: () -> Unit = onDismiss,
) {
    val configuration = LocalConfiguration.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusState = rememberSearchDialogFocusState()
    val isTelevision = remember(configuration.uiMode) {
        val uiMode = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        uiMode == Configuration.UI_MODE_TYPE_TELEVISION
    }
    val visibleHistory = remember(query, searchHistory) {
        visibleSearchHistory(searchHistory)
    }
    val historyFocusRequesters = remember(visibleHistory) {
        visibleHistory.map { FocusRequester() }
    }
    val actions = SearchDialogActions(
        query = query,
        isTelevision = isTelevision,
        inputFocusRequester = focusState.inputFocusRequester,
        historyFocusRequesters = historyFocusRequesters,
        showKeyboard = { keyboardController?.show() },
        hideKeyboardAction = { keyboardController?.hide() },
        onSubmitQuery = onSubmitQuery,
        onDismiss = onDismiss,
        onExitDown = onExitDown,
    )
    val launchVoiceSearch = rememberSearchVoiceAction(
        language = LocalUiLanguage.current,
        prompt = uiText(UiStringKey.WhatShouldIFind),
        unavailableMessage = uiText(UiStringKey.VoiceSearchIsNotAvailableOnThisDevice),
        onBeforeLaunch = actions::hideKeyboard,
        onRecognized = { recognizedText ->
            onQueryChange(recognizedText)
            onSubmitQuery(recognizedText)
        },
    )

    SearchDialogLifecycleEffects(
        keyboardDismissRequest = keyboardDismissRequest,
        visibleHistory = visibleHistory,
        focusState = focusState,
        onFocusInput = actions::focusInput,
        onHideKeyboard = actions::hideKeyboard,
    )
    SearchDialogInteractionContent(
        query = query,
        isTelevision = isTelevision,
        remoteInputAction = remoteInputAction,
        remoteInputActionRequest = remoteInputActionRequest,
        visibleHistory = visibleHistory,
        historyFocusRequesters = historyFocusRequesters,
        focusState = focusState,
        actions = actions,
        onQueryChange = onQueryChange,
        onHistorySelected = onHistorySelected,
        onLaunchVoiceSearch = launchVoiceSearch,
    )
}
