package me.yummydroid.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.LoadState
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.PosterCardSize

@Composable
internal fun AnimeGridSection(
    contentState: LoadState<List<Anime>>,
    pagingState: PagingUiState,
    gridState: LazyGridState,
    cardSize: PosterCardSize,
    contentTopPadding: Dp = 0.dp,
    contentBottomPadding: Dp = 0.dp,
    focusFirstRequest: FocusFirstRequest,
    focusCurrentRequestNonce: Long,
    contentFocusEnabled: Boolean = true,
    currentFocusedIndex: () -> Int,
    onFocusedIndexChange: (Int) -> Unit,
    backToTopSection: BrowseSection,
    onRegisterBackToTopHandler: ((HomeBackToTopHandler?) -> Unit)? = null,
    emptyMessage: String,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onExitHorizontalDirection: (VisualGridDirection) -> Boolean = { true },
    onExitUp: () -> Boolean = { false },
    exitUpFocusRequester: FocusRequester? = null,
    onExitDown: () -> Boolean = { false },
    onOpenAnime: (Long) -> Unit,
) {
    AnimeGridRoot(
        AnimeGridParams(
            contentState = contentState,
            pagingState = pagingState,
            gridState = gridState,
            cardSize = cardSize,
            contentTopPadding = contentTopPadding,
            contentBottomPadding = contentBottomPadding,
            focusFirstRequest = focusFirstRequest,
            focusCurrentRequestNonce = focusCurrentRequestNonce,
            contentFocusEnabled = contentFocusEnabled,
            currentFocusedIndex = currentFocusedIndex,
            onFocusedIndexChange = onFocusedIndexChange,
            backToTopSection = backToTopSection,
            onRegisterBackToTopHandler = onRegisterBackToTopHandler,
            emptyMessage = emptyMessage,
            onRetry = onRetry,
            onLoadMore = onLoadMore,
            onExitHorizontalDirection = onExitHorizontalDirection,
            onExitUp = onExitUp,
            exitUpFocusRequester = exitUpFocusRequester,
            onExitDown = onExitDown,
            onOpenAnime = onOpenAnime,
        ),
    )
}

@Composable
private fun AnimeGridRoot(params: AnimeGridParams) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        AnimeListStateContent(
            state = params.contentState,
            onRetry = params.onRetry,
            emptyMessage = params.emptyMessage,
        ) { animes ->
            val layout = rememberAnimeGridLayout(params, animes.size, maxWidth, maxHeight)
            AnimeGridCoordinator(params, animes, layout)
        }
    }
}
