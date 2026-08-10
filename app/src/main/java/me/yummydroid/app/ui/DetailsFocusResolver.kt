package me.yummydroid.app.ui

import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.canShowVideoSubscriptions

internal fun resolveDetailsFocusLayout(
    details: AnimeDetails,
    videos: LoadState<List<VideoVariant>>,
    readyVideos: List<VideoVariant>,
    auth: AuthUiState,
    detailsExtras: LoadState<AnimeDetailsExtras>,
    forcedOfflineMode: Boolean,
    relatedExpanded: Boolean,
    subscriptionsExpanded: Boolean,
    commentsExpanded: Boolean,
): DetailsFocusLayout {
    val extras = (detailsExtras as? LoadState.Ready)?.data
    val subscriptionCount = if (forcedOfflineMode) {
        0
    } else {
        detailsSubscriptionFocusItemCount(
            isAuthorized = auth.profile != null,
            videoCount = readyVideos.size,
            voiceGroupCount = if (extras == null) 0 else readyVideos.detailsSubscriptionVoiceGroups().size,
            allowSubscriptions = details.canShowVideoSubscriptions(),
            extrasReady = extras != null,
            expanded = subscriptionsExpanded,
        )
    }
    return buildDetailsFocusLayout(
        DetailsFocusCounts(
            screenshots = details.screenshots.take(24).size,
            relatedAnime = detailsExpandedListFocusCount(details.relatedAnime.size, relatedExpanded),
            episodes = if (videos is LoadState.Ready && videos.data.isNotEmpty()) EpisodeFocusCapacity else 0,
            subscriptions = subscriptionCount,
            recommendations = if (forcedOfflineMode) 0 else extras?.recommendations?.size ?: 0,
            comments = if (forcedOfflineMode) {
                0
            } else {
                detailsCommentsFocusItemCount(
                    extrasReady = extras != null,
                    commentCount = extras?.comments?.size ?: 0,
                    isAuthorized = auth.profile != null,
                    expanded = commentsExpanded,
                )
            },
        ),
    )
}

private const val EpisodeFocusCapacity = 24
