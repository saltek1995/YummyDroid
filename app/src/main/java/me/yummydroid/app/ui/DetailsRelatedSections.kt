package me.yummydroid.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.VideoVariant

@Composable
internal fun DetailsSubscriptionsHostSection(
    extrasState: LoadState<AnimeDetailsExtras>,
    auth: AuthUiState,
    videos: List<VideoVariant>,
    allowSubscriptions: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
) {
    if (!allowSubscriptions) return
    when (extrasState) {
        LoadState.Loading -> Unit
        is LoadState.Error -> Unit
        is LoadState.Ready -> DetailsSubscriptionsSection(
            auth = auth,
            videos = videos,
            subscriptions = extrasState.data.subscriptions,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            onToggleVideoSubscription = onToggleVideoSubscription,
            focusGridState = focusGridState,
            focusIndexOffset = focusIndexOffset,
            focusBlockKey = focusBlockKey,
        )
    }
}

@Composable
internal fun DetailsRecommendationsSection(
    extrasState: LoadState<AnimeDetailsExtras>,
    onOpenAnime: (Long, Any?) -> Unit,
    entryFocusRequester: FocusRequester? = null,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
) {
    if (extrasState !is LoadState.Ready) return
    DetailsAnimeRowSection(
        title = uiText(UiStringKey.Similar),
        animes = extrasState.data.recommendations,
        onOpenAnime = onOpenAnime,
        entryFocusRequester = entryFocusRequester,
        focusGridState = focusGridState,
        focusIndexOffset = focusIndexOffset,
        focusBlockKey = focusBlockKey,
    )
}

@Composable
internal fun DetailsCommentsHostSection(
    extrasState: LoadState<AnimeDetailsExtras>,
    totalComments: Long,
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
    when (extrasState) {
        LoadState.Loading -> Unit
        is LoadState.Error -> Unit
        is LoadState.Ready -> DetailsCommentsSection(
            comments = extrasState.data.comments,
            totalComments = totalComments,
            commentsPaging = extrasState.data.commentsPaging,
            isAuthorized = isAuthorized,
            scrollState = scrollState,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            onAddAnimeComment = onAddAnimeComment,
            onLoadMoreAnimeComments = onLoadMoreAnimeComments,
            entryFocusRequester = entryFocusRequester,
            focusGridState = focusGridState,
            focusIndexOffset = focusIndexOffset,
            focusBlockKey = focusBlockKey,
        )
    }
}

@Composable
internal fun DetailsDescriptionSection(description: String) {
    val normalizedDescription = description.trim()
    if (normalizedDescription.isBlank()) return
    Text(
        text = normalizedDescription,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
    )
}
