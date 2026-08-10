package me.yummydroid.app

import me.yummydroid.app.data.Anime

data class PagingUiState(
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val error: String? = null,
)

internal fun PagingUiState.canRequestAnimePage(reset: Boolean): Boolean {
    return reset || (!isLoadingMore && canLoadMore)
}

internal fun animePageLoadOffset(items: LoadState<List<Anime>>, reset: Boolean): Int {
    return if (reset) 0 else items.readyListOrEmpty().size
}

internal fun animePageLoadingState(reset: Boolean, canLoadMoreOnReset: Boolean = true): PagingUiState {
    return if (reset) {
        PagingUiState(canLoadMore = canLoadMoreOnReset)
    } else {
        PagingUiState(isLoadingMore = true, error = null)
    }
}

internal fun animePageFailureState(
    currentPaging: PagingUiState,
    reset: Boolean,
    error: String,
): PagingUiState {
    return if (reset) {
        PagingUiState(canLoadMore = true)
    } else {
        currentPaging.copy(
            isLoadingMore = false,
            canLoadMore = true,
            error = error,
        )
    }
}

internal data class AnimePageMerge(
    val items: List<Anime>,
    val paging: PagingUiState,
)

internal fun mergeAnimePage(
    existing: List<Anime>,
    incoming: List<Anime>,
    reset: Boolean,
    pageSize: Int,
): AnimePageMerge {
    val base = if (reset) emptyList() else existing
    val merged = (base + incoming).distinctBy { it.id }
    return AnimePageMerge(
        items = merged,
        paging = PagingUiState(
            isLoadingMore = false,
            canLoadMore = incoming.size >= pageSize && merged.size > base.size,
        ),
    )
}

