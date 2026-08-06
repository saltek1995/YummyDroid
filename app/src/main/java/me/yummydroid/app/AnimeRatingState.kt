package me.yummydroid.app

import me.yummydroid.app.data.AnimeDetails

internal fun YummyDroidUiState.withOptimisticAnimeRating(
    animeId: Long,
    rating: Int?,
): YummyDroidUiState {
    if (!acceptsAnimeRatingUpdate(animeId)) return this
    val extras = detailsExtras.readyDataOrNull()
    return copy(
        details = details.withAnimeUserRating(animeId, rating),
        detailsExtras = if (extras != null) {
            LoadState.Ready(extras.copy(rating = extras.rating.copy(userRating = rating)))
        } else {
            detailsExtras
        },
    )
}

internal fun YummyDroidUiState.withConfirmedAnimeRating(
    animeId: Long,
    update: AnimeRatingUpdate,
): YummyDroidUiState {
    if (!acceptsAnimeRatingUpdate(animeId)) return this
    val extras = detailsExtras.readyDataOrNull()
    return copy(
        details = details.withAnimeUserRating(animeId, update.userRating),
        detailsExtras = LoadState.Ready(
            extras?.copy(rating = update.summary) ?: AnimeDetailsExtras(rating = update.summary),
        ),
    )
}

internal fun YummyDroidUiState.withRestoredAnimeRating(
    animeId: Long,
    previousDetails: LoadState<AnimeDetails>,
    previousExtras: LoadState<AnimeDetailsExtras>,
    error: String?,
): YummyDroidUiState {
    val stateWithError = if (error == null) this else copy(auth = auth.copy(error = error))
    if (!acceptsAnimeRatingUpdate(animeId)) return stateWithError
    return stateWithError.copy(
        details = previousDetails,
        detailsExtras = previousExtras,
    )
}

private fun YummyDroidUiState.acceptsAnimeRatingUpdate(animeId: Long): Boolean {
    val detailsRoute = route as? AppRoute.Details
    if (detailsRoute != null) return detailsRoute.animeId == animeId
    return details.readyDataOrNull()?.id == animeId
}

private fun LoadState<AnimeDetails>.withAnimeUserRating(
    animeId: Long,
    rating: Int?,
): LoadState<AnimeDetails> {
    val current = readyDataOrNull()?.takeIf { it.id == animeId } ?: return this
    return LoadState.Ready(current.copy(userRating = rating))
}
