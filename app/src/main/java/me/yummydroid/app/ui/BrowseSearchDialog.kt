package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.delay
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
    val uiLanguage = LocalUiLanguage.current
    val voicePrompt = uiText(UiStringKey.WhatShouldIFind)
    val voiceUnavailable = uiText(UiStringKey.VoiceSearchIsNotAvailableOnThisDevice)
    val focusRequester = remember { FocusRequester() }
    val micFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var inputFocused by remember { mutableStateOf(false) }
    var micFocused by remember { mutableStateOf(false) }
    var focusedHistoryIndex by remember { mutableIntStateOf(-1) }
    val isTelevision = remember(configuration.uiMode) {
        val uiMode = configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK
        uiMode == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
    val visibleHistory = remember(query, searchHistory) {
        visibleSearchHistory(searchHistory)
    }
    val historyFocusRequesters = remember(visibleHistory) {
        visibleHistory.map { FocusRequester() }
    }
    val firstHistoryFocusRequester = historyFocusRequesters.firstOrNull() ?: FocusRequester.Default
    fun submitCurrentQuery() {
        submittedSearchQuery(query)?.let(onSubmitQuery)
    }
    fun dismissSearch() {
        submitCurrentQuery()
        keyboardController?.hide()
        onDismiss()
    }
    fun exitDownFromSearch() {
        submitCurrentQuery()
        keyboardController?.hide()
        onExitDown()
    }
    fun focusInput() {
        focusRequester.requestFocusSafely()
        if (!isTelevision) {
            keyboardController?.show()
        }
    }
    fun focusHistoryOrExit(): Boolean {
        val firstHistoryFocus = historyFocusRequesters.firstOrNull()
        if (firstHistoryFocus != null) {
            keyboardController?.hide()
            firstHistoryFocus.requestFocusSafely()
        } else {
            exitDownFromSearch()
        }
        return true
    }
    val launchVoiceSearch = rememberSearchVoiceAction(
        language = uiLanguage,
        prompt = voicePrompt,
        unavailableMessage = voiceUnavailable,
        onBeforeLaunch = { keyboardController?.hide() },
        onRecognized = { recognizedText ->
            onQueryChange(recognizedText)
            onSubmitQuery(recognizedText)
        },
    )

    LaunchedEffect(Unit) {
        delay(80)
        focusInput()
    }

    LaunchedEffect(keyboardDismissRequest) {
        if (keyboardDismissRequest > 0L) {
            keyboardController?.hide()
        }
    }

    LaunchedEffect(visibleHistory) {
        if (focusedHistoryIndex >= visibleHistory.size) {
            focusedHistoryIndex = -1
        }
    }

    SearchDialogRemoteInputEffect(
        request = remoteInputActionRequest,
        action = remoteInputAction,
        focusedHistoryIndex = focusedHistoryIndex,
        visibleHistory = visibleHistory,
        inputFocused = inputFocused,
        micFocused = micFocused,
        historyFocusRequesters = historyFocusRequesters,
        onFocusInput = ::focusInput,
        onFocusHistoryOrExit = ::focusHistoryOrExit,
        onExitDown = ::exitDownFromSearch,
        onHideKeyboard = { keyboardController?.hide() },
        onFocusMic = { micFocusRequester.requestFocusSafely() },
        onHistorySelected = onHistorySelected,
        onLaunchVoiceSearch = launchVoiceSearch,
        onSubmitCurrentQuery = ::submitCurrentQuery,
    )

    SearchDialogPanel(
        query = query,
        isTelevision = isTelevision,
        visibleHistory = visibleHistory,
        historyFocusRequesters = historyFocusRequesters,
        focusRequester = focusRequester,
        micFocusRequester = micFocusRequester,
        firstHistoryFocusRequester = firstHistoryFocusRequester,
        micFocused = micFocused,
        inputFocused = inputFocused,
        onDismissSearch = ::dismissSearch,
        onLaunchVoiceSearch = launchVoiceSearch,
        onFocusInput = ::focusInput,
        onFocusHistoryOrExit = ::focusHistoryOrExit,
        onQueryChange = onQueryChange,
        onSubmitAndHideKeyboard = {
            submitCurrentQuery()
            keyboardController?.hide()
        },
        onMicFocusChanged = { focused ->
            micFocused = focused
            if (focused) {
                focusedHistoryIndex = -1
            }
        },
        onInputFocusChanged = { focused ->
            inputFocused = focused
            if (focused) {
                focusedHistoryIndex = -1
            }
        },
        onHistorySelected = { historyQuery ->
            onHistorySelected(historyQuery)
            focusInput()
        },
        onHistoryFocusChanged = { index, focused ->
            if (focused) {
                focusedHistoryIndex = index
            } else if (focusedHistoryIndex == index) {
                focusedHistoryIndex = -1
            }
        },
        onExitDown = ::exitDownFromSearch,
    )

}
