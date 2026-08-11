package me.yummydroid.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import me.yummydroid.app.data.Anime

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun AnimeGridContent(
    params: AnimeGridParams,
    animes: List<Anime>,
    layout: AnimeGridLayout,
    actions: AnimeGridActions,
    focusUpdateBlocked: Boolean,
    onRetainedFocusedIndexChange: (Int) -> Unit,
) {
    BrowseGridScrollLocalProvider(touchOverscrollEnabled = layout.touchOverscrollEnabled) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(layout.columnsCount),
            state = params.gridState,
            contentPadding = PaddingValues(
                start = layout.horizontalPadding,
                top = layout.topContentPadding,
                end = layout.horizontalPadding,
                bottom = layout.bottomContentPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(BrowseGridHorizontalGap),
            verticalArrangement = Arrangement.spacedBy(BrowseGridVerticalGap),
            modifier = Modifier
                .fillMaxSize()
                .browseTouchBounceOverscroll(
                    enabled = layout.touchOverscrollEnabled,
                    gridState = params.gridState,
                )
                .onPreviewKeyEvent { event ->
                    when {
                        event.type != KeyEventType.KeyDown -> false
                        !params.contentFocusEnabled -> false
                        else -> params.currentFocusedIndex().let { index ->
                            index in animes.indices && actions.handleGridDirection(index, event.key)
                        }
                    }
                }
                .focusGroup(),
        ) {
            animeGridCards(
                params = params,
                animes = animes,
                layout = layout,
                actions = actions,
                focusUpdateBlocked = focusUpdateBlocked,
                onRetainedFocusedIndexChange = onRetainedFocusedIndexChange,
            )
            animeGridFooter(params)
        }
    }
}

private fun LazyGridScope.animeGridCards(
    params: AnimeGridParams,
    animes: List<Anime>,
    layout: AnimeGridLayout,
    actions: AnimeGridActions,
    focusUpdateBlocked: Boolean,
    onRetainedFocusedIndexChange: (Int) -> Unit,
) {
    itemsIndexed(
        items = animes,
        key = { _, anime -> anime.id },
        contentType = { _, _ -> "anime-card" },
    ) { index, anime ->
        AnimeCard(
            anime = anime,
            onClick = {
                actions.updateFocusedIndex(index)
                onRetainedFocusedIndexChange(index)
                params.onOpenAnime(anime.id)
            },
            modifier = Modifier
                .focusProperties { canFocus = params.contentFocusEnabled }
                .focusRequester(layout.itemFocusRequesters[index])
                .then(
                    if (params.exitUpFocusRequester != null && index < layout.columnsCount) {
                        Modifier.focusProperties { up = params.exitUpFocusRequester }
                    } else {
                        Modifier
                    },
                )
                .onPreviewKeyEvent { event ->
                    event.type == KeyEventType.KeyDown && actions.handleGridDirection(index, event.key)
                }
                .onFocusChanged { focusState ->
                    if (focusState.hasFocus && !focusUpdateBlocked) actions.updateFocusedIndex(index)
                },
        )
    }
}

private fun LazyGridScope.animeGridFooter(params: AnimeGridParams) {
    val pagingState = params.pagingState
    if (!pagingState.isLoadingMore && !pagingState.canLoadMore && pagingState.error == null) return
    item(span = { GridItemSpan(maxLineSpan) }) {
        PagingGridFooter(
            paging = pagingState,
            onLoadMore = params.onLoadMore,
        )
    }
}
