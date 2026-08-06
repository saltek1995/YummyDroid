package me.yummydroid.app

import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.matchesAnimeVoice
import me.yummydroid.app.data.matchesVideoPlayer
import me.yummydroid.app.data.matchingPlayerKey
import me.yummydroid.app.data.matchingSourceKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoSubscriptionHint
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.withVoiceSubscriptionState

internal data class SubscriptionUnsubscribeTarget(
    val animeId: Long,
    val voiceKey: String,
    val playerId: Long?,
    val playerKey: String,
    val videoIds: List<Long>,
) {
    val requiresVideoLookup: Boolean
        get() = voiceKey.isNotBlank() || playerId != null || playerKey.isNotBlank()

    fun withResolvedVideoIds(videos: List<VideoVariant>): SubscriptionUnsubscribeTarget {
        return copy(videoIds = resolveVideoIds(videos))
    }

    fun resolveVideoIds(videos: List<VideoVariant>): List<Long> {
        return (
            videoIds + videos
                .filter { video -> video.animeId == animeId && matchesVideo(video) }
                .distinctBy { it.matchingSourceKey }
                .map { it.id }
                .filter { it > 0L }
            ).distinct()
    }

    fun matchesSubscription(subscription: VideoSubscription): Boolean {
        return subscription.videoId in videoIds ||
            (voiceKey.isNotBlank() && subscription.matchesAnimeVoice(animeId, voiceKey)) ||
            (
                voiceKey.isBlank() &&
                    subscription.animeId == animeId &&
                    (
                        (playerId != null && subscription.playerId == playerId) ||
                            (
                                playerId == null &&
                                    playerKey.isNotBlank() &&
                                    subscription.player.cleanVideoSourceLabel().equals(playerKey, ignoreCase = true)
                            )
                    )
            )
    }

    fun hintVideos(videos: List<VideoVariant>): List<VideoVariant> {
        if (voiceKey.isBlank()) return emptyList()
        return videos.filter { it.animeId == animeId && it.matchingVoiceKey == voiceKey }
    }

    private fun matchesVideo(video: VideoVariant): Boolean {
        return when {
            voiceKey.isNotBlank() -> video.matchingVoiceKey == voiceKey
            playerId != null -> video.playerId == playerId
            playerKey.isNotBlank() -> video.player.cleanVideoSourceLabel().equals(playerKey, ignoreCase = true)
            else -> false
        }
    }
}

internal fun VideoSubscription.unsubscribeTarget(
    currentSubscriptions: List<VideoSubscription>,
): SubscriptionUnsubscribeTarget? {
    val animeId = animeId.takeIf { it > 0L } ?: return null
    val targetVoiceKey = matchingVoiceKey.ifBlank {
        currentSubscriptions.firstOrNull { it.animeId == animeId && it.videoId == videoId }
            ?.matchingVoiceKey
            .orEmpty()
    }
    val targetPlayerId = playerId.takeIf { it > 0L }
        ?: currentSubscriptions.firstOrNull { it.animeId == animeId && it.videoId == videoId }
            ?.playerId
            ?.takeIf { it > 0L }
    val targetPlayerKey = player.cleanVideoSourceLabel()
    if (targetVoiceKey.isBlank() && videoId <= 0L && targetPlayerId == null && targetPlayerKey.isBlank()) {
        return null
    }
    val directVideoIds = currentSubscriptions
        .filter { currentSubscription ->
            currentSubscription.videoId > 0L &&
                currentSubscription.animeId == animeId &&
                (
                    currentSubscription.videoId == videoId ||
                        (targetVoiceKey.isNotBlank() && currentSubscription.matchesAnimeVoice(animeId, targetVoiceKey))
                )
        }
        .map { it.videoId }
        .ifEmpty { listOf(videoId).filter { it > 0L } }
        .distinct()
    return SubscriptionUnsubscribeTarget(
        animeId = animeId,
        voiceKey = targetVoiceKey,
        playerId = targetPlayerId,
        playerKey = targetPlayerKey,
        videoIds = directVideoIds,
    )
}

internal fun List<VideoSubscription>.withoutUnsubscribeTarget(
    target: SubscriptionUnsubscribeTarget,
): List<VideoSubscription> {
    return filterNot { target.matchesSubscription(it) }
}

internal fun VideoSubscription.resolveSinglePlayerVoice(videos: List<VideoVariant>): VideoVariant? {
    val candidates = videos.filter { video ->
        matchesVideoPlayer(video)
    }.let { playerVideos ->
        val voiceKey = matchingVoiceKey
        if (voiceKey.isBlank()) {
            playerVideos
        } else {
            playerVideos.filter { it.matchingVoiceKey == voiceKey }
        }
    }
    return candidates
        .distinctBy { it.matchingVoiceKey }
        .singleOrNull()
}

internal fun VideoSubscription.resolveVoiceHints(
    hints: List<VideoSubscriptionHint>,
): List<VideoSubscriptionHint> {
    if (animeId <= 0L) return emptyList()
    val explicitVoiceKey = matchingVoiceKey
    return hints
        .filter { hint ->
            hint.animeId == animeId &&
                (explicitVoiceKey.isBlank() || hint.voiceKey == explicitVoiceKey) &&
                (
                    (playerId > 0L && hint.playerId == playerId) ||
                        (matchingPlayerKey.isNotBlank() && hint.playerKey == matchingPlayerKey)
                )
        }
        .distinctBy { it.voiceKey }
}

internal fun VideoSubscription.withResolvedVoice(video: VideoVariant): VideoSubscription {
    return copy(
        player = video.player.ifBlank { player },
        dubbing = video.dubbing.ifBlank { dubbing },
        playerId = video.playerId.takeIf { it > 0L } ?: playerId,
        videoId = video.id.takeIf { it > 0L } ?: videoId,
    )
}

internal fun VideoSubscription.withResolvedHint(hint: VideoSubscriptionHint): VideoSubscription {
    return copy(
        title = title.ifBlank { hint.title },
        posterUrl = posterUrl.ifBlank { hint.posterUrl },
        dubbing = hint.voiceTitle.ifBlank { dubbing },
        playerId = playerId.takeIf { it > 0L } ?: hint.playerId,
    )
}

internal fun canonicalizeVideoSubscriptionsForVideos(
    subscriptions: List<VideoSubscription>,
    videos: List<VideoVariant>,
    hints: List<VideoSubscriptionHint>,
    title: String,
    posterUrl: String,
): List<VideoSubscription> {
    val animeId = videos.firstOrNull()?.animeId?.takeIf { it > 0L } ?: return subscriptions
    val videoById = videos
        .filter { it.id > 0L }
        .associateBy { it.id }
    val availableVoiceKeys = videos
        .map { it.matchingVoiceKey }
        .filter { it.isNotBlank() }
        .toSet()
    if (availableVoiceKeys.isEmpty()) return subscriptions

    val activeVoiceKeys = linkedSetOf<String>()
    subscriptions
        .filter { it.animeId == animeId }
        .forEach { subscription ->
            val directVideoVoiceKey = videoById[subscription.videoId]
                ?.matchingVoiceKey
                .orEmpty()
            val singlePlayerVoiceKey = videos
                .filter { subscription.matchesVideoPlayer(it) }
                .distinctBy { video -> video.matchingVoiceKey }
                .singleOrNull()
                ?.matchingVoiceKey
                .orEmpty()
            val hintedVoiceKeys = subscription.resolveVoiceHints(hints)
                .map { it.voiceKey }
                .filter { it in availableVoiceKeys }
            when {
                directVideoVoiceKey in availableVoiceKeys -> activeVoiceKeys += directVideoVoiceKey
                subscription.matchingVoiceKey in availableVoiceKeys -> activeVoiceKeys += subscription.matchingVoiceKey
                singlePlayerVoiceKey in availableVoiceKeys -> activeVoiceKeys += singlePlayerVoiceKey
            }
            activeVoiceKeys += hintedVoiceKeys
        }

    if (activeVoiceKeys.isEmpty()) return subscriptions
    var result = subscriptions
    activeVoiceKeys.forEach { voiceKey ->
        val targets = videos
            .filter { video -> video.matchingVoiceKey == voiceKey && video.id > 0L }
            .distinctBy { it.matchingSourceKey }
        if (targets.isNotEmpty()) {
            result = result.withVoiceSubscriptionState(
                animeId = animeId,
                voiceKey = voiceKey,
                videos = targets,
                subscribed = true,
                title = title,
                posterUrl = posterUrl,
            )
        }
    }
    return result
}
