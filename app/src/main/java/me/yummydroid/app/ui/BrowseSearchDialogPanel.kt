package me.yummydroid.app.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummySurfaceBorder
import me.yummydroid.app.ui.theme.YummySurfaceRole

@Composable
internal fun SearchDialogPanel(
    query: String,
    isTelevision: Boolean,
    visibleHistory: List<String>,
    historyFocusRequesters: List<FocusRequester>,
    focusRequester: FocusRequester,
    micFocusRequester: FocusRequester,
    firstHistoryFocusRequester: FocusRequester,
    micFocused: Boolean,
    inputFocused: Boolean,
    onDismissSearch: () -> Unit,
    onLaunchVoiceSearch: () -> Unit,
    onFocusInput: () -> Unit,
    onFocusHistoryOrExit: () -> Boolean,
    onQueryChange: (String) -> Unit,
    onSubmitAndHideKeyboard: () -> Unit,
    onMicFocusChanged: (Boolean) -> Unit,
    onInputFocusChanged: (Boolean) -> Unit,
    onHistorySelected: (String) -> Unit,
    onHistoryFocusChanged: (Int, Boolean) -> Unit,
    onExitDown: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { onDismissSearch() }
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
                                    onFocusInput()
                                    true
                                }
                                micFocused && event.key == Key.DirectionDown -> onFocusHistoryOrExit()
                                inputFocused && event.key == Key.DirectionLeft -> {
                                    micFocusRequester.requestFocusSafely()
                                    true
                                }
                                inputFocused && event.key == Key.DirectionDown -> onFocusHistoryOrExit()
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
                            onClick = onLaunchVoiceSearch,
                            modifier = Modifier
                                .size(56.dp)
                                .focusRequester(micFocusRequester)
                                .focusProperties {
                                    right = focusRequester
                                    down = firstHistoryFocusRequester
                                }
                                .onFocusChanged { focusState ->
                                    onMicFocusChanged(focusState.hasFocus)
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (event.key) {
                                        Key.DirectionRight -> {
                                            onFocusInput()
                                            true
                                        }
                                        Key.DirectionDown -> onFocusHistoryOrExit()
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
                                    onSubmitAndHideKeyboard()
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
                                    onInputFocusChanged(focusState.hasFocus)
                                }
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (event.key) {
                                        Key.DirectionLeft -> {
                                            micFocusRequester.requestFocusSafely()
                                            true
                                        }
                                        Key.DirectionDown -> onFocusHistoryOrExit()
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
                            onSelect = onHistorySelected,
                            onFocusedIndexChange = onHistoryFocusChanged,
                            onFocusInput = onFocusInput,
                            onExitDown = onExitDown,
                        )
                    }
                }
            }
        }
    }
}
