package me.yummydroid.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import me.yummydroid.app.InputAction
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummySurfaceBorder
import me.yummydroid.app.ui.theme.yummySurfaceColor

// BrowseSearchDialogActions
internal class SearchDialogActions(
    private val query: String,
    private val isTelevision: Boolean,
    private val inputFocusRequester: FocusRequester,
    private val historyFocusRequesters: List<FocusRequester>,
    private val showKeyboard: () -> Unit,
    private val hideKeyboardAction: () -> Unit,
    private val onSubmitQuery: (String) -> Unit,
    private val onDismiss: () -> Unit,
    private val onExitDown: () -> Unit,
) {
    fun submitCurrentQuery() {
        submittedSearchQuery(query)?.let(onSubmitQuery)
    }

    fun dismissSearch() {
        submitCurrentQuery()
        hideKeyboard()
        onDismiss()
    }

    fun exitDownFromSearch() {
        submitCurrentQuery()
        hideKeyboard()
        onExitDown()
    }

    fun focusInput() {
        inputFocusRequester.requestFocusSafely()
        if (!isTelevision) showKeyboard()
    }

    fun focusHistoryOrExit(): Boolean {
        val firstHistoryFocus = historyFocusRequesters.firstOrNull()
        if (firstHistoryFocus == null) {
            exitDownFromSearch()
        } else {
            hideKeyboard()
            firstHistoryFocus.requestFocusSafely()
        }
        return true
    }

    fun submitAndHideKeyboard() {
        submitCurrentQuery()
        hideKeyboard()
    }

    fun hideKeyboard() {
        hideKeyboardAction()
    }
}

// BrowseSearchDialogFocusState
internal class SearchDialogFocusState {
    val inputFocusRequester = FocusRequester()
    val micFocusRequester = FocusRequester()

    var inputFocused by mutableStateOf(false)
        private set
    var micFocused by mutableStateOf(false)
        private set
    var focusedHistoryIndex by mutableIntStateOf(-1)
        private set

    fun updateInputFocus(focused: Boolean) {
        inputFocused = focused
        if (focused) focusedHistoryIndex = -1
    }

    fun updateMicFocus(focused: Boolean) {
        micFocused = focused
        if (focused) focusedHistoryIndex = -1
    }

    fun setHistoryFocused(index: Int, focused: Boolean) {
        if (focused) {
            focusedHistoryIndex = index
        } else if (focusedHistoryIndex == index) {
            focusedHistoryIndex = -1
        }
    }

    fun retainHistoryIndexWithin(size: Int) {
        if (focusedHistoryIndex >= size) focusedHistoryIndex = -1
    }
}

@Composable
internal fun rememberSearchDialogFocusState(): SearchDialogFocusState {
    return remember { SearchDialogFocusState() }
}

// BrowseSearchDialogInput
@Composable
internal fun SearchDialogInputRow(
    query: String,
    focusState: SearchDialogFocusState,
    firstHistoryFocusRequester: FocusRequester,
    actions: SearchDialogActions,
    onQueryChange: (String) -> Unit,
    onLaunchVoiceSearch: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchDialogMicButton(
            focusState = focusState,
            firstHistoryFocusRequester = firstHistoryFocusRequester,
            actions = actions,
            onClick = onLaunchVoiceSearch,
        )
        SearchDialogQueryField(
            query = query,
            focusState = focusState,
            firstHistoryFocusRequester = firstHistoryFocusRequester,
            actions = actions,
            onQueryChange = onQueryChange,
        )
    }
}

@Composable
private fun SearchDialogMicButton(
    focusState: SearchDialogFocusState,
    firstHistoryFocusRequester: FocusRequester,
    actions: SearchDialogActions,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .focusRequester(focusState.micFocusRequester)
            .focusProperties {
                right = focusState.inputFocusRequester
                down = firstHistoryFocusRequester
            }
            .onFocusChanged { focusState.updateMicFocus(it.hasFocus) }
            .searchDialogMicNavigation(focusState, actions)
            .focusRing(RoundedCornerShape(8.dp)),
    ) {
        Icon(Icons.Default.Mic, contentDescription = uiText(UiStringKey.VoiceSearch))
    }
}

@Composable
private fun RowScope.SearchDialogQueryField(
    query: String,
    focusState: SearchDialogFocusState,
    firstHistoryFocusRequester: FocusRequester,
    actions: SearchDialogActions,
    onQueryChange: (String) -> Unit,
) {
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
        keyboardActions = KeyboardActions(onSearch = { actions.submitAndHideKeyboard() }),
        modifier = Modifier
            .weight(1f)
            .padding(2.dp)
            .focusRequester(focusState.inputFocusRequester)
            .focusProperties {
                left = focusState.micFocusRequester
                down = firstHistoryFocusRequester
            }
            .onFocusChanged { focusState.updateInputFocus(it.hasFocus) }
            .searchDialogInputNavigation(focusState, actions),
    )
}

// BrowseSearchDialogInteraction
@Composable
internal fun SearchDialogInteractionContent(
    query: String,
    isTelevision: Boolean,
    remoteInputAction: InputAction?,
    remoteInputActionRequest: Long,
    visibleHistory: List<String>,
    historyFocusRequesters: List<FocusRequester>,
    focusState: SearchDialogFocusState,
    actions: SearchDialogActions,
    onQueryChange: (String) -> Unit,
    onHistorySelected: (String) -> Unit,
    onLaunchVoiceSearch: () -> Unit,
) {
    SearchDialogRemoteInputEffect(
        request = remoteInputActionRequest,
        action = remoteInputAction,
        focusedHistoryIndex = focusState.focusedHistoryIndex,
        visibleHistory = visibleHistory,
        inputFocused = focusState.inputFocused,
        micFocused = focusState.micFocused,
        historyFocusRequesters = historyFocusRequesters,
        onFocusInput = actions::focusInput,
        onFocusHistoryOrExit = actions::focusHistoryOrExit,
        onExitDown = actions::exitDownFromSearch,
        onHideKeyboard = actions::hideKeyboard,
        onFocusMic = { focusState.micFocusRequester.requestFocusSafely() },
        onHistorySelected = onHistorySelected,
        onLaunchVoiceSearch = onLaunchVoiceSearch,
        onSubmitCurrentQuery = actions::submitCurrentQuery,
    )
    SearchDialogPanel(
        query = query,
        isTelevision = isTelevision,
        visibleHistory = visibleHistory,
        historyFocusRequesters = historyFocusRequesters,
        focusState = focusState,
        actions = actions,
        onQueryChange = onQueryChange,
        onHistorySelected = { historyQuery ->
            onHistorySelected(historyQuery)
            actions.focusInput()
        },
        onLaunchVoiceSearch = onLaunchVoiceSearch,
    )
}

// BrowseSearchDialogLifecycle
@Composable
internal fun SearchDialogLifecycleEffects(
    keyboardDismissRequest: Long,
    visibleHistory: List<String>,
    focusState: SearchDialogFocusState,
    onFocusInput: () -> Unit,
    onHideKeyboard: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(80)
        onFocusInput()
    }
    LaunchedEffect(keyboardDismissRequest) {
        if (keyboardDismissRequest > 0L) onHideKeyboard()
    }
    LaunchedEffect(visibleHistory) {
        focusState.retainHistoryIndexWithin(visibleHistory.size)
    }
}

// BrowseSearchDialogPanel
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

// BrowseSearchDialogPanelContent
@Composable
internal fun SearchDialogPanelContent(
    query: String,
    visibleHistory: List<String>,
    historyFocusRequesters: List<FocusRequester>,
    focusState: SearchDialogFocusState,
    actions: SearchDialogActions,
    onQueryChange: (String) -> Unit,
    onHistorySelected: (String) -> Unit,
    onLaunchVoiceSearch: () -> Unit,
) {
    val firstHistoryFocusRequester = historyFocusRequesters.firstOrNull() ?: FocusRequester.Default
    Column(
        modifier = Modifier
            .padding(YummySpacing.sm)
            .searchDialogPanelNavigation(focusState, actions),
        verticalArrangement = Arrangement.spacedBy(YummySpacing.xs),
    ) {
        SearchDialogInputRow(
            query = query,
            focusState = focusState,
            firstHistoryFocusRequester = firstHistoryFocusRequester,
            actions = actions,
            onQueryChange = onQueryChange,
            onLaunchVoiceSearch = onLaunchVoiceSearch,
        )
        if (visibleHistory.isNotEmpty()) {
            SearchHistoryDropdown(
                history = visibleHistory,
                focusRequesters = historyFocusRequesters,
                inputFocusRequester = focusState.inputFocusRequester,
                onSelect = onHistorySelected,
                onFocusedIndexChange = focusState::setHistoryFocused,
                onFocusInput = actions::focusInput,
                onExitDown = actions::exitDownFromSearch,
            )
        }
    }
}

// BrowseSearchDialogPanelNavigation
internal fun Modifier.searchDialogPanelNavigation(
    focusState: SearchDialogFocusState,
    actions: SearchDialogActions,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when {
        focusState.micFocused && event.key == Key.DirectionRight -> {
            actions.focusInput()
            true
        }
        focusState.micFocused && event.key == Key.DirectionDown -> actions.focusHistoryOrExit()
        focusState.inputFocused && event.key == Key.DirectionLeft -> {
            focusState.micFocusRequester.requestFocusSafely()
            true
        }
        focusState.inputFocused && event.key == Key.DirectionDown -> actions.focusHistoryOrExit()
        else -> false
    }
}

internal fun Modifier.searchDialogMicNavigation(
    focusState: SearchDialogFocusState,
    actions: SearchDialogActions,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionRight -> {
            actions.focusInput()
            true
        }
        Key.DirectionDown -> actions.focusHistoryOrExit()
        else -> false
    }
}

internal fun Modifier.searchDialogInputNavigation(
    focusState: SearchDialogFocusState,
    actions: SearchDialogActions,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionLeft -> {
            focusState.micFocusRequester.requestFocusSafely()
            true
        }
        Key.DirectionDown -> actions.focusHistoryOrExit()
        else -> false
    }
}

// BrowseSearchDialogRuntime
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

// BrowseSearchHistoryDropdown
@Composable
internal fun SearchHistoryDropdown(
    history: List<String>,
    focusRequesters: List<FocusRequester>,
    inputFocusRequester: FocusRequester,
    onSelect: (String) -> Unit,
    onFocusedIndexChange: (Int, Boolean) -> Unit,
    onFocusInput: () -> Unit,
    onExitDown: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(YummyRadii.smallShape)
            .background(yummySurfaceColor(YummySurfaceRole.Panel))
            .border(yummySurfaceBorder(YummySurfaceRole.Panel), YummyRadii.smallShape),
    ) {
        history.forEachIndexed { index, historyQuery ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.14f)),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .focusRequester(focusRequesters.getOrElse(index) { FocusRequester.Default })
                    .focusProperties {
                        up = if (index == 0) {
                            inputFocusRequester
                        } else {
                            focusRequesters[index - 1]
                        }
                        down = focusRequesters.getOrElse(index + 1) { FocusRequester.Default }
                    }
                    .onFocusChanged { focusState ->
                        onFocusedIndexChange(index, focusState.isFocused || focusState.hasFocus)
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.DirectionUp -> {
                                if (index == 0) {
                                    onFocusInput()
                                } else {
                                    focusRequesters[index - 1].requestFocusSafely()
                                }
                                true
                            }
                            Key.DirectionDown -> {
                                val nextFocus = focusRequesters.getOrNull(index + 1)
                                if (nextFocus == null) {
                                    onExitDown()
                                } else {
                                    nextFocus.requestFocusSafely()
                                }
                                true
                            }
                            else -> false
                        }
                    }
                    .dpadClickable(YummyRadii.smallShape) { onSelect(historyQuery) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = historyQuery,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// BrowseSearchLogic
private const val SearchHistoryVisibleLimit = 6

internal fun visibleSearchHistory(searchHistory: List<String>): List<String> {
    return searchHistory.take(SearchHistoryVisibleLimit)
}

internal fun submittedSearchQuery(query: String): String? {
    return query.trim().takeIf { it.isNotBlank() }
}

// BrowseSearchRemoteInputEffect
@Composable
internal fun SearchDialogRemoteInputEffect(
    request: Long,
    action: InputAction?,
    focusedHistoryIndex: Int,
    visibleHistory: List<String>,
    inputFocused: Boolean,
    micFocused: Boolean,
    historyFocusRequesters: List<FocusRequester>,
    onFocusInput: () -> Unit,
    onFocusHistoryOrExit: () -> Boolean,
    onExitDown: () -> Unit,
    onHideKeyboard: () -> Unit,
    onFocusMic: () -> Unit,
    onHistorySelected: (String) -> Unit,
    onLaunchVoiceSearch: () -> Unit,
    onSubmitCurrentQuery: () -> Unit,
) {
    LaunchedEffect(request) {
        if (request <= 0L) return@LaunchedEffect
        when (
            val command = resolveSearchRemoteInputCommand(
                action = action,
                focusedHistoryIndex = focusedHistoryIndex,
                visibleHistoryCount = visibleHistory.size,
                historyFocusRequesterCount = historyFocusRequesters.size,
                inputFocused = inputFocused,
                micFocused = micFocused,
            )
        ) {
            SearchRemoteInputCommand.None -> Unit
            SearchRemoteInputCommand.FocusInput -> onFocusInput()
            SearchRemoteInputCommand.FocusHistoryOrExit -> onFocusHistoryOrExit()
            SearchRemoteInputCommand.ExitDown -> onExitDown()
            SearchRemoteInputCommand.FocusMic -> onFocusMic()
            SearchRemoteInputCommand.LaunchVoiceSearch -> onLaunchVoiceSearch()
            SearchRemoteInputCommand.SubmitCurrentQuery -> {
                onSubmitCurrentQuery()
                onHideKeyboard()
            }
            is SearchRemoteInputCommand.FocusHistory -> {
                if (command.hideKeyboard) onHideKeyboard()
                historyFocusRequesters.getOrNull(command.index)?.requestFocusSafely()
            }
            is SearchRemoteInputCommand.SelectHistory -> {
                onHistorySelected(visibleHistory[command.index])
                onFocusInput()
            }
        }
    }
}

// BrowseSearchRemoteInputPolicy
internal sealed interface SearchRemoteInputCommand {
    data object None : SearchRemoteInputCommand
    data object FocusInput : SearchRemoteInputCommand
    data object FocusHistoryOrExit : SearchRemoteInputCommand
    data object ExitDown : SearchRemoteInputCommand
    data object FocusMic : SearchRemoteInputCommand
    data object LaunchVoiceSearch : SearchRemoteInputCommand
    data object SubmitCurrentQuery : SearchRemoteInputCommand
    data class FocusHistory(val index: Int, val hideKeyboard: Boolean) : SearchRemoteInputCommand
    data class SelectHistory(val index: Int) : SearchRemoteInputCommand
}

internal fun resolveSearchRemoteInputCommand(
    action: InputAction?,
    focusedHistoryIndex: Int,
    visibleHistoryCount: Int,
    historyFocusRequesterCount: Int,
    inputFocused: Boolean,
    micFocused: Boolean,
): SearchRemoteInputCommand = when (action) {
    InputAction.Up -> when {
        focusedHistoryIndex > 0 -> SearchRemoteInputCommand.FocusHistory(
            index = focusedHistoryIndex - 1,
            hideKeyboard = false,
        )
        focusedHistoryIndex == 0 -> SearchRemoteInputCommand.FocusInput
        !inputFocused && !micFocused -> SearchRemoteInputCommand.FocusInput
        else -> SearchRemoteInputCommand.None
    }
    InputAction.Down -> when {
        focusedHistoryIndex < 0 -> SearchRemoteInputCommand.FocusHistoryOrExit
        focusedHistoryIndex + 1 >= historyFocusRequesterCount -> SearchRemoteInputCommand.ExitDown
        else -> SearchRemoteInputCommand.FocusHistory(
            index = focusedHistoryIndex + 1,
            hideKeyboard = true,
        )
    }
    InputAction.Left -> when {
        inputFocused -> SearchRemoteInputCommand.FocusMic
        !micFocused && focusedHistoryIndex < 0 -> SearchRemoteInputCommand.FocusMic
        else -> SearchRemoteInputCommand.None
    }
    InputAction.Right -> if (micFocused) {
        SearchRemoteInputCommand.FocusInput
    } else {
        SearchRemoteInputCommand.None
    }
    InputAction.Confirm -> when {
        focusedHistoryIndex in 0 until visibleHistoryCount -> {
            SearchRemoteInputCommand.SelectHistory(focusedHistoryIndex)
        }
        micFocused -> SearchRemoteInputCommand.LaunchVoiceSearch
        inputFocused -> SearchRemoteInputCommand.SubmitCurrentQuery
        else -> SearchRemoteInputCommand.FocusInput
    }
    InputAction.Play,
    InputAction.Pause,
    InputAction.PlayPause,
    InputAction.PreviousEpisode,
    InputAction.NextEpisode,
    InputAction.Back,
    null -> SearchRemoteInputCommand.None
}

// BrowseSearchVoiceAction
@Composable
internal fun rememberSearchVoiceAction(
    language: ContentLanguage,
    prompt: String,
    unavailableMessage: String,
    onBeforeLaunch: () -> Unit,
    onRecognized: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognizedText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (recognizedText.isNotBlank()) {
                onRecognized(recognizedText)
            }
        }
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.voiceRecognizerTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        runCatching {
            onBeforeLaunch()
            launcher.launch(intent)
        }.onFailure { throwable ->
            if (throwable is ActivityNotFoundException) {
                Toast.makeText(context, unavailableMessage, Toast.LENGTH_SHORT).show()
            } else {
                throw throwable
            }
        }
        Unit
    }
}
