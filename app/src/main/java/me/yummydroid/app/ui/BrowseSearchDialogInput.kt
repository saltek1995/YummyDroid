package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.yummydroid.app.ui.components.focusRing

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
