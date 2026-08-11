package me.yummydroid.app

import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant

internal fun YummyDroidUiState.withPublishedVideoSubscriptions(
    subscriptions: List<VideoSubscription>,
    canonicalize: (
        subscriptions: List<VideoSubscription>,
        videos: List<VideoVariant>,
        title: String,
        posterUrl: String,
    ) -> List<VideoSubscription>,
): YummyDroidUiState {
    val detailsAnimeId = (route as? AppRoute.Details)?.animeId
        ?: details.readyDataOrNull()?.id
    val currentExtras = detailsExtras.readyDataOrNull()
    val currentDetails = details.readyDataOrNull()
    val detailsVideos = videos.readyDataOrNull()
        .orEmpty()
        .filter { it.animeId == detailsAnimeId }
    val detailsSubscriptions = canonicalize(
        subscriptions,
        detailsVideos,
        currentDetails?.title.orEmpty(),
        currentDetails?.posterUrl.orEmpty(),
    )
    return copy(
        globalSubscriptions = LoadState.Ready(subscriptions),
        detailsExtras = if (detailsAnimeId != null && currentExtras != null) {
            LoadState.Ready(currentExtras.copy(subscriptions = detailsSubscriptions))
        } else {
            detailsExtras
        },
    )
}
