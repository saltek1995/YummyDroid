package me.yummydroid.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.yummydroid.app.InputAction
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummySurfaceBorder
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.YummySurfaceRole

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
    val context = LocalContext.current
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
    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognizedText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (recognizedText.isNotBlank()) {
                onQueryChange(recognizedText)
                onSubmitQuery(recognizedText)
            }
        }
    }
    val launchVoiceSearch = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, uiLanguage.voiceRecognizerTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, voicePrompt)
        }
        runCatching {
            keyboardController?.hide()
            voiceSearchLauncher.launch(intent)
        }.onFailure { throwable ->
            if (throwable is ActivityNotFoundException) {
                Toast.makeText(
                    context,
                    voiceUnavailable,
                    Toast.LENGTH_SHORT,
                ).show()
            } else {
                throw throwable
            }
        }
        Unit
    }

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

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { dismissSearch() }
                },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = if (isTelevision) 40.dp else 16.dp,
                    top = 0.dp,
                    end = if (isTelevision) 40.dp else 16.dp,
                    bottom = 10.dp,
                ),
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
                Column(
                    modifier = Modifier
                        .padding(YummySpacing.sm)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when {
                                micFocused && event.key == Key.DirectionRight -> {
                                    focusInput()
                                    true
                                }
                                micFocused && event.key == Key.DirectionDown -> focusHistoryOrExit()
                                inputFocused && event.key == Key.DirectionLeft -> {
                                    micFocusRequester.requestFocusSafely()
                                    true
                                }
                                inputFocused && event.key == Key.DirectionDown -> focusHistoryOrExit()
                                else -> false
                            }
                        },
                    verticalArrangement = Arrangement.spacedBy(YummySpacing.xs),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = launchVoiceSearch,
                            modifier = Modifier
                                .size(56.dp)
                                .focusRequester(micFocusRequester)
                                .focusProperties {
                                    right = focusRequester
                                    down = firstHistoryFocusRequester
                                }
                                .onFocusChanged { focusState ->
                                    micFocused = focusState.hasFocus
                                    if (focusState.hasFocus) {
                                        focusedHistoryIndex = -1
                                    }
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (event.key) {
                                        Key.DirectionRight -> {
                                            focusInput()
                                            true
                                        }
                                        Key.DirectionDown -> focusHistoryOrExit()
                                        else -> false
                                    }
                                }
                                .focusRing(RoundedCornerShape(8.dp)),
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = uiText(UiStringKey.VoiceSearch))
                        }
                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            placeholder = { Text(uiText(UiStringKey.FindAnime)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Search,
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    submitCurrentQuery()
                                    keyboardController?.hide()
                                },
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .focusRequester(focusRequester)
                                .focusProperties {
                                    left = micFocusRequester
                                    down = firstHistoryFocusRequester
                                }
                                .onFocusChanged { focusState ->
                                    inputFocused = focusState.hasFocus
                                    if (focusState.hasFocus) {
                                        focusedHistoryIndex = -1
                                    }
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            micFocusRequester.requestFocusSafely()
                                            true
                                        }
                                        Key.DirectionDown -> focusHistoryOrExit()
                                        else -> false
                                    }
                                },
                        )
                    }
                    if (visibleHistory.isNotEmpty()) {
                        SearchHistoryDropdown(
                            history = visibleHistory,
                            focusRequesters = historyFocusRequesters,
                            inputFocusRequester = focusRequester,
                            onSelect = { historyQuery ->
                                onHistorySelected(historyQuery)
                                focusInput()
                            },
                            onFocusedIndexChange = { index, focused ->
                                if (focused) {
                                    focusedHistoryIndex = index
                                } else if (focusedHistoryIndex == index) {
                                    focusedHistoryIndex = -1
                                }
                            },
                            onFocusInput = ::focusInput,
                            onExitDown = ::exitDownFromSearch,
                        )
                    }
                }
            }
        }
    }
}
