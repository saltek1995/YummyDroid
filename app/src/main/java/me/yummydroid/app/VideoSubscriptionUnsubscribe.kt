package me.yummydroid.app

import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.matchesAnimeVoice
import me.yummydroid.app.data.matchingSourceKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant

internal data class SubscriptionUnsubscribeTarget(
    val animeId: Long,
    val voiceKey: String,
    val playerId: Long?,
    val playerKey: String,
    val videoIds: List<Long>,
) {
    val requiresVideoLookup: Boolean
        get() = voiceKey.isNotBlank() || playerId != null || playerKey.isNotBlank()

    fun withResolvedVideoIds(videos: List<VideoVariant>): SubscriptionUnsubscribeTarget =
        copy(videoIds = resolveVideoIds(videos))

    fun resolveVideoIds(videos: List<VideoVariant>): List<Long> {
        val resolvedIds = videos
            .filter { video -> video.animeId == animeId && matchesVideo(video) }
            .distinctBy(VideoVariant::matchingSourceKey)
            .map(VideoVariant::id)
            .filter { id -> id > 0L }
        return (videoIds + resolvedIds).distinct()
    }

    fun matchesSubscription(subscription: VideoSubscription): Boolean {
        if (subscription.videoId in videoIds) return true
        if (voiceKey.isNotBlank()) return subscription.matchesAnimeVoice(animeId, voiceKey)
        return matchesPlayerSubscription(subscription)
    }

    fun hintVideos(videos: List<VideoVariant>): List<VideoVariant> {
        if (voiceKey.isBlank()) return emptyList()
        return videos.filter { video -> video.animeId == animeId && video.matchingVoiceKey == voiceKey }
    }

    private fun matchesPlayerSubscription(subscription: VideoSubscription): Boolean {
        if (subscription.animeId != animeId) return false
        if (playerId != null) return subscription.playerId == playerId
        if (playerKey.isBlank()) return false
        return subscription.player.cleanVideoSourceLabel().equals(playerKey, ignoreCase = true)
    }

    private fun matchesVideo(video: VideoVariant): Boolean = when {
        voiceKey.isNotBlank() -> video.matchingVoiceKey == voiceKey
        playerId != null -> video.playerId == playerId
        playerKey.isNotBlank() -> video.player.cleanVideoSourceLabel().equals(playerKey, ignoreCase = true)
        else -> false
    }
}

internal fun VideoSubscription.unsubscribeTarget(
    currentSubscriptions: List<VideoSubscription>,
): SubscriptionUnsubscribeTarget? {
    val targetAnimeId = animeId.takeIf { id -> id > 0L } ?: return null
    val currentMatch = currentSubscriptions.firstOrNull { subscription ->
        subscription.animeId == targetAnimeId && subscription.videoId == videoId
    }
    val targetVoiceKey = matchingVoiceKey.ifBlank { currentMatch?.matchingVoiceKey.orEmpty() }
    val targetPlayerId = playerId.takeIf { id -> id > 0L }
        ?: currentMatch?.playerId?.takeIf { id -> id > 0L }
    val targetPlayerKey = player.cleanVideoSourceLabel()
    if (!hasUnsubscribeIdentity(targetVoiceKey, targetPlayerId, targetPlayerKey)) return null

    return SubscriptionUnsubscribeTarget(
        animeId = targetAnimeId,
        voiceKey = targetVoiceKey,
        playerId = targetPlayerId,
        playerKey = targetPlayerKey,
        videoIds = resolveDirectVideoIds(currentSubscriptions, targetAnimeId, targetVoiceKey),
    )
}

private fun VideoSubscription.hasUnsubscribeIdentity(
    voiceKey: String,
    resolvedPlayerId: Long?,
    playerKey: String,
): Boolean = voiceKey.isNotBlank() || videoId > 0L || resolvedPlayerId != null || playerKey.isNotBlank()

private fun VideoSubscription.resolveDirectVideoIds(
    subscriptions: List<VideoSubscription>,
    targetAnimeId: Long,
    voiceKey: String,
): List<Long> {
    val resolvedIds = subscriptions
        .filter { subscription ->
            subscription.videoId > 0L &&
                subscription.animeId == targetAnimeId &&
                (subscription.videoId == videoId || subscription.matchesTargetVoice(targetAnimeId, voiceKey))
        }
        .map(VideoSubscription::videoId)
    return resolvedIds.ifEmpty { listOf(videoId).filter { id -> id > 0L } }.distinct()
}

private fun VideoSubscription.matchesTargetVoice(animeId: Long, voiceKey: String): Boolean =
    voiceKey.isNotBlank() && matchesAnimeVoice(animeId, voiceKey)

internal fun List<VideoSubscription>.withoutUnsubscribeTarget(
    target: SubscriptionUnsubscribeTarget,
): List<VideoSubscription> = filterNot(target::matchesSubscription)
