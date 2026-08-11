package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DetailsCommentComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    focusGridState: VisualFocusGridState?,
    inputFocusIndex: Int,
    sendFocusIndex: Int,
    focusBlockKey: Any?,
) {
    OutlinedTextField(
        value = draft,
        onValueChange = onDraftChange,
        label = { Text(uiText(UiStringKey.Comment)) },
        minLines = 2,
        maxLines = 5,
        modifier = Modifier
            .fillMaxWidth()
            .visualFocusGridItemIfPresent(
                state = focusGridState,
                index = inputFocusIndex,
                blockKey = focusBlockKey,
                blockEntryIndex = inputFocusIndex,
            )
            .padding(1.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        DialogActionButton(
            text = uiText(UiStringKey.Send),
            primary = true,
            onClick = { submitCommentDraft(draft, onSubmit, onDraftChange) },
            modifier = Modifier.visualFocusGridItemIfPresent(
                state = focusGridState,
                index = sendFocusIndex,
                blockKey = focusBlockKey,
                blockEntryIndex = inputFocusIndex,
            ),
        )
    }
}

private fun submitCommentDraft(
    draft: String,
    onSubmit: (String) -> Unit,
    onDraftChange: (String) -> Unit,
) {
    val text = draft.trim()
    if (text.isBlank()) return
    onSubmit(text)
    onDraftChange("")
}
