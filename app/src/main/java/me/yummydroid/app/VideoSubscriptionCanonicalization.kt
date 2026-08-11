package me.yummydroid.app

import me.yummydroid.app.data.matchesVideoPlayer
import me.yummydroid.app.data.matchingSourceKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoSubscriptionHint
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.withVoiceSubscriptionState

internal fun canonicalizeVideoSubscriptionsForVideos(
    subscriptions: List<VideoSubscription>,
    videos: List<VideoVariant>,
    hints: List<VideoSubscriptionHint>,
    title: String,
    posterUrl: String,
): List<VideoSubscription> {
    val animeId = videos.firstOrNull()?.animeId?.takeIf { id -> id > 0L } ?: return subscriptions
    val availableVoiceKeys = videos
        .map(VideoVariant::matchingVoiceKey)
        .filter(String::isNotBlank)
        .toSet()
    if (availableVoiceKeys.isEmpty()) return subscriptions

    val activeVoiceKeys = resolveActiveVoiceKeys(
        subscriptions = subscriptions,
        videos = videos,
        hints = hints,
        animeId = animeId,
        availableVoiceKeys = availableVoiceKeys,
    )
    if (activeVoiceKeys.isEmpty()) return subscriptions
    return activeVoiceKeys.fold(subscriptions) { result, voiceKey ->
        val targets = videos
            .filter { video -> video.matchingVoiceKey == voiceKey && video.id > 0L }
            .distinctBy(VideoVariant::matchingSourceKey)
        if (targets.isEmpty()) {
            result
        } else {
            result.withVoiceSubscriptionState(
                animeId = animeId,
                voiceKey = voiceKey,
                videos = targets,
                subscribed = true,
                title = title,
                posterUrl = posterUrl,
            )
        }
    }
}

private fun resolveActiveVoiceKeys(
    subscriptions: List<VideoSubscription>,
    videos: List<VideoVariant>,
    hints: List<VideoSubscriptionHint>,
    animeId: Long,
    availableVoiceKeys: Set<String>,
): Set<String> {
    val videoById = videos.filter { video -> video.id > 0L }.associateBy(VideoVariant::id)
    return buildSet {
        subscriptions
            .filter { subscription -> subscription.animeId == animeId }
            .forEach { subscription ->
                subscription.resolvePrimaryVoiceKey(videoById, videos, availableVoiceKeys)
                    ?.let(::add)
                subscription.resolveVoiceHints(hints)
                    .map(VideoSubscriptionHint::voiceKey)
                    .filterTo(this) { voiceKey -> voiceKey in availableVoiceKeys }
            }
    }
}

private fun VideoSubscription.resolvePrimaryVoiceKey(
    videoById: Map<Long, VideoVariant>,
    videos: List<VideoVariant>,
    availableVoiceKeys: Set<String>,
): String? {
    val directVideoVoiceKey = videoById[videoId]?.matchingVoiceKey.orEmpty()
    val singlePlayerVoiceKey = videos
        .filter(::matchesVideoPlayer)
        .distinctBy(VideoVariant::matchingVoiceKey)
        .singleOrNull()
        ?.matchingVoiceKey
        .orEmpty()
    return when {
        directVideoVoiceKey in availableVoiceKeys -> directVideoVoiceKey
        matchingVoiceKey in availableVoiceKeys -> matchingVoiceKey
        singlePlayerVoiceKey in availableVoiceKeys -> singlePlayerVoiceKey
        else -> null
    }
}
