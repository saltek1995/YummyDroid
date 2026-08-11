package me.yummydroid.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.flow.collectLatest
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.data.AnimeComment

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
        comments = comments,
        totalComments = totalComments,
        commentsPaging = commentsPaging,
        isAuthorized = isAuthorized,
        expanded = expanded,
        draft = draft,
        onDraftChange = { draft = it },
        onExpandedChange = onExpandedChange,
        onAddAnimeComment = onAddAnimeComment,
        onLoadMoreAnimeComments = onLoadMoreAnimeComments,
        entryFocusRequester = entryFocusRequester,
        focusGridState = focusGridState,
        focusIndexOffset = focusIndexOffset,
        focusIndices = focusIndices,
        focusBlockKey = focusBlockKey,
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
