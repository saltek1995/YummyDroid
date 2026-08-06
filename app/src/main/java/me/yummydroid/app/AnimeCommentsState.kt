package me.yummydroid.app

import me.yummydroid.app.data.AnimeComment

internal fun AnimeDetailsExtras.withAnimeCommentsLoading(): AnimeDetailsExtras {
    return copy(
        commentsPaging = commentsPaging.copy(
            isLoadingMore = true,
            error = null,
        ),
    )
}

internal fun AnimeDetailsExtras.withLoadedAnimeComments(
    incoming: List<AnimeComment>,
    pageSize: Int,
): AnimeDetailsExtras {
    val previousComments = comments
    val mergedComments = (previousComments + incoming).distinctBy(AnimeComment::id)
    return copy(
        comments = mergedComments,
        commentsPaging = PagingUiState(
            isLoadingMore = false,
            canLoadMore = incoming.size >= pageSize && mergedComments.size > previousComments.size,
        ),
    )
}

internal fun AnimeDetailsExtras.withAnimeCommentsFailure(error: String): AnimeDetailsExtras {
    return copy(
        commentsPaging = commentsPaging.copy(
            isLoadingMore = false,
            error = error,
        ),
    )
}

internal fun AnimeDetailsExtras.withAddedAnimeComment(comment: AnimeComment): AnimeDetailsExtras {
    return copy(comments = (listOf(comment) + comments).distinctBy(AnimeComment::id))
}
