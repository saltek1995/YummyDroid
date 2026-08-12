package me.yummydroid.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.data.AnimeComment
import me.yummydroid.app.formatCommentTimestamp
import me.yummydroid.app.ui.components.focusRing

// DetailsCommentCard
@Composable
internal fun DetailsCommentCard(
    comment: AnimeComment,
    focusGridState: VisualFocusGridState?,
    focusIndex: Int,
    focusBlockKey: Any?,
    blockEntryIndex: Int,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .visualFocusGridItemIfPresent(
                state = focusGridState,
                index = focusIndex,
                blockKey = focusBlockKey,
                blockEntryIndex = blockEntryIndex,
            )
            .focusRing(shape)
            .focusable(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
    ) {
        DetailsCommentCardContent(comment)
    }
}

@Composable
private fun DetailsCommentCardContent(comment: AnimeComment) {
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val commentDate = remember(comment.createdAtSeconds) {
            formatCommentTimestamp(comment.createdAtSeconds)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = comment.userName.ifBlank { uiText(UiStringKey.User) },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (commentDate.isNotBlank()) {
                Text(
                    text = commentDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
        Text(
            text = comment.text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun DetailsCommentsPagingFooter(
    commentsPaging: PagingUiState,
    onRetry: () -> Unit,
) {
    when {
        commentsPaging.isLoadingMore -> DetailsCommentsLoadingFooter()
        commentsPaging.error != null -> DetailsCommentsErrorFooter(commentsPaging.error, onRetry)
    }
}

@Composable
private fun DetailsCommentsLoadingFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun DetailsCommentsErrorFooter(error: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        DialogActionButton(
            text = uiText(UiStringKey.Retry),
            primary = true,
            onClick = onRetry,
            modifier = Modifier,
        )
    }
}

// DetailsCommentComposer
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

// DetailsCommentsContent
internal data class DetailsCommentsContentState(
    val comments: List<AnimeComment>,
    val totalComments: Long,
    val commentsPaging: PagingUiState,
    val isAuthorized: Boolean,
    val expanded: Boolean,
    val draft: String,
    val entryFocusRequester: FocusRequester?,
    val focusGridState: VisualFocusGridState?,
    val focusIndexOffset: Int,
    val focusIndices: DetailsCommentFocusIndices,
    val focusBlockKey: Any?,
)

internal data class DetailsCommentsContentActions(
    val onDraftChange: (String) -> Unit,
    val onExpandedChange: (Boolean) -> Unit,
    val onAddAnimeComment: (String) -> Unit,
    val onLoadMoreAnimeComments: () -> Unit,
)

@Composable
internal fun DetailsCommentsContent(
    state: DetailsCommentsContentState,
    actions: DetailsCommentsContentActions,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusEntryGroup(state.entryFocusRequester)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DetailsCommentsHeader(
            commentsCount = state.comments.size,
            totalComments = state.totalComments,
            expanded = state.expanded,
            onExpandedChange = actions.onExpandedChange,
            entryFocusRequester = state.entryFocusRequester,
            focusGridState = state.focusGridState,
            focusIndexOffset = state.focusIndexOffset,
            focusBlockKey = state.focusBlockKey,
        )
        if (!state.expanded) return@Column
        if (state.isAuthorized) {
            DetailsCommentComposer(
                draft = state.draft,
                onDraftChange = actions.onDraftChange,
                onSubmit = actions.onAddAnimeComment,
                focusGridState = state.focusGridState,
                inputFocusIndex = state.focusIndices.input,
                sendFocusIndex = state.focusIndices.send,
                focusBlockKey = state.focusBlockKey,
            )
        }
        state.comments.forEachIndexed { index, comment ->
            DetailsCommentCard(
                comment = comment,
                focusGridState = state.focusGridState,
                focusIndex = state.focusIndices.commentsStart + index,
                focusBlockKey = state.focusBlockKey,
                blockEntryIndex = state.focusIndices.commentsStart,
            )
        }
        DetailsCommentsPagingFooter(
            commentsPaging = state.commentsPaging,
            onRetry = actions.onLoadMoreAnimeComments,
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

// DetailsCommentsSection
internal data class DetailsCommentFocusIndices(
    val input: Int,
    val send: Int,
    val commentsStart: Int,
)

@Composable
internal fun DetailsCommentsSection(
    comments: List<AnimeComment>,
    totalComments: Long,
    commentsPaging: PagingUiState,
    isAuthorized: Boolean,
    scrollState: ScrollState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddAnimeComment: (String) -> Unit,
    onLoadMoreAnimeComments: () -> Unit,
    entryFocusRequester: FocusRequester? = null,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
) {
    if (comments.isEmpty() && !isAuthorized) return
    var draft by remember { mutableStateOf("") }
    val focusIndices = DetailsCommentFocusIndices(
        input = focusIndexOffset + 1,
        send = focusIndexOffset + 2,
        commentsStart = focusIndexOffset + if (isAuthorized) 3 else 1,
    )
    DetailsCommentsFocusEffect(
        expanded = expanded,
        isAuthorized = isAuthorized,
        focusGridState = focusGridState,
        commentInputFocusIndex = focusIndices.input,
    )
    DetailsCommentsPagingEffect(
        expanded = expanded,
        commentsCount = comments.size,
        commentsPaging = commentsPaging,
        scrollState = scrollState,
        onLoadMore = onLoadMoreAnimeComments,
    )
    DetailsCommentsContent(
        state = DetailsCommentsContentState(
            comments = comments,
            totalComments = totalComments,
            commentsPaging = commentsPaging,
            isAuthorized = isAuthorized,
            expanded = expanded,
            draft = draft,
            entryFocusRequester = entryFocusRequester,
            focusGridState = focusGridState,
            focusIndexOffset = focusIndexOffset,
            focusIndices = focusIndices,
            focusBlockKey = focusBlockKey,
        ),
        actions = DetailsCommentsContentActions(
            onDraftChange = { draft = it },
            onExpandedChange = onExpandedChange,
            onAddAnimeComment = onAddAnimeComment,
            onLoadMoreAnimeComments = onLoadMoreAnimeComments,
        ),
    )
}

@Composable
private fun DetailsCommentsFocusEffect(
    expanded: Boolean,
    isAuthorized: Boolean,
    focusGridState: VisualFocusGridState?,
    commentInputFocusIndex: Int,
) {
    var wasExpanded by remember { mutableStateOf(expanded) }
    LaunchedEffect(expanded, isAuthorized, focusGridState) {
        val opened = !wasExpanded && expanded
        wasExpanded = expanded
        val state = focusGridState ?: return@LaunchedEffect
        if (!opened || !isAuthorized) return@LaunchedEffect
        withFrameNanos { }
        state.requester(commentInputFocusIndex)?.requestFocusSafely()
    }
}

@Composable
private fun DetailsCommentsPagingEffect(
    expanded: Boolean,
    commentsCount: Int,
    commentsPaging: PagingUiState,
    scrollState: ScrollState,
    onLoadMore: () -> Unit,
) {
    LaunchedEffect(
        expanded,
        commentsCount,
        commentsPaging.canLoadMore,
        commentsPaging.isLoadingMore,
    ) {
        if (!expanded) return@LaunchedEffect
        snapshotFlow { scrollState.value to scrollState.maxValue }
            .collectLatest { (current, max) ->
                val nearBottom = max - current < 720
                if (nearBottom && commentsPaging.canLoadMore && !commentsPaging.isLoadingMore) {
                    onLoadMore()
                }
            }
    }
}
