package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.data.AnimeComment

@Composable
internal fun DetailsCommentsContent(
    comments: List<AnimeComment>,
    totalComments: Long,
    commentsPaging: PagingUiState,
    isAuthorized: Boolean,
    expanded: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onAddAnimeComment: (String) -> Unit,
    onLoadMoreAnimeComments: () -> Unit,
    entryFocusRequester: FocusRequester?,
    focusGridState: VisualFocusGridState?,
    focusIndexOffset: Int,
    focusIndices: DetailsCommentFocusIndices,
    focusBlockKey: Any?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusEntryGroup(entryFocusRequester)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DetailsCommentsHeader(
            commentsCount = comments.size,
            totalComments = totalComments,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            entryFocusRequester = entryFocusRequester,
            focusGridState = focusGridState,
            focusIndexOffset = focusIndexOffset,
            focusBlockKey = focusBlockKey,
        )
        if (!expanded) return@Column
        if (isAuthorized) {
            DetailsCommentComposer(
                draft = draft,
                onDraftChange = onDraftChange,
                onSubmit = onAddAnimeComment,
                focusGridState = focusGridState,
                inputFocusIndex = focusIndices.input,
                sendFocusIndex = focusIndices.send,
                focusBlockKey = focusBlockKey,
            )
        }
        comments.forEachIndexed { index, comment ->
            DetailsCommentCard(
                comment = comment,
                focusGridState = focusGridState,
                focusIndex = focusIndices.commentsStart + index,
                focusBlockKey = focusBlockKey,
                blockEntryIndex = focusIndices.commentsStart,
            )
        }
        DetailsCommentsPagingFooter(
            commentsPaging = commentsPaging,
            onRetry = onLoadMoreAnimeComments,
        )
    }
}

@Composable
private fun DetailsCommentsHeader(
    commentsCount: Int,
    totalComments: Long,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    entryFocusRequester: FocusRequester?,
    focusGridState: VisualFocusGridState?,
    focusIndexOffset: Int,
    focusBlockKey: Any?,
) {
    val progressText = when {
        commentsCount == 0 -> null
        totalComments > 0L -> {
            "$commentsCount ${uiText(UiStringKey.Of)} ${localizedViews(totalComments)} ${uiText(UiStringKey.Loaded)}"
        }
        else -> "$commentsCount ${uiText(UiStringKey.Loaded)}"
    }
    val focusModifier = when {
        focusGridState != null -> Modifier.visualFocusGridItem(
            state = focusGridState,
            index = focusIndexOffset,
            horizontal = true,
            vertical = true,
            blockKey = focusBlockKey,
            blockEntryIndex = focusIndexOffset,
        )
        entryFocusRequester != null -> Modifier.focusRequester(entryFocusRequester)
        else -> Modifier
    }
    AccordionHeader(
        title = uiText(UiStringKey.Comments),
        summary = progressText.orEmpty(),
        expanded = expanded,
        active = false,
        onClick = { onExpandedChange(!expanded) },
        centerTitle = true,
        modifier = focusModifier,
    )
}
