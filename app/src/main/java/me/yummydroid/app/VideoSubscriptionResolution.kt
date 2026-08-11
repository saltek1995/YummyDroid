package me.yummydroid.app

import me.yummydroid.app.data.matchesVideoPlayer
import me.yummydroid.app.data.matchingPlayerKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoSubscriptionHint
import me.yummydroid.app.data.VideoVariant

internal fun VideoSubscription.resolveSinglePlayerVoice(
    videos: List<VideoVariant>,
): VideoVariant? {
    val playerVideos = videos.filter(::matchesVideoPlayer)
    val voiceKey = matchingVoiceKey
    val candidates = if (voiceKey.isBlank()) {
        playerVideos
    } else {
        playerVideos.filter { video -> video.matchingVoiceKey == voiceKey }
    }
    return candidates.distinctBy(VideoVariant::matchingVoiceKey).singleOrNull()
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
                hint.matchesSubscriptionPlayer(this)
        }
        .distinctBy(VideoSubscriptionHint::voiceKey)
}

private fun VideoSubscriptionHint.matchesSubscriptionPlayer(
    subscription: VideoSubscription,
): Boolean {
    return (subscription.playerId > 0L && playerId == subscription.playerId) ||
        (subscription.matchingPlayerKey.isNotBlank() && playerKey == subscription.matchingPlayerKey)
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
