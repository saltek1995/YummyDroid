package me.yummydroid.app.data

import java.util.Locale

// SiteNotification
data class SiteNotification(
    val id: Long,
    val title: String,
    val text: String,
    val clickUrl: String,
    val type: String,
    val subType: String,
    val objectId: Long,
    val dateSeconds: Long,
    val viewed: Boolean,
)

// VideoSubscription
data class VideoSubscription(
    val animeId: Long,
    val title: String,
    val posterUrl: String,
    val player: String,
    val dubbing: String,
    val playerId: Long = 0L,
    val videoId: Long = 0L,
)

// VideoSubscriptionMatching
val VideoSubscription.matchingVoiceKey: String
    get() = dubbing.cleanVideoSourceLabel()
        .takeUnless { it.isKnownPlayerLabel() }
        .orEmpty()
        .normalizedVoiceKey()

val VideoSubscription.profileDisplayKey: String
    get() {
        matchingVoiceKey.takeIf { it.isNotBlank() }?.let { return "$animeId|voice:$it" }
        playerId.takeIf { it > 0L }?.let { return "$animeId|player-id:$it" }
        player.cleanVideoSourceLabel()
            .lowercase(Locale.ROOT)
            .replace(whitespaceRegex, " ")
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { return "$animeId|player:$it" }
        val voiceKey = matchingVoiceKey.ifBlank {
            dubbing
                .lowercase(Locale.ROOT)
                .replace('\u0451', '\u0435')
                .replace(whitespaceRegex, " ")
                .trim()
        }
        return "$animeId|$voiceKey"
    }

val VideoSubscription.profileVoiceTitle: String
    get() {
        if (matchingVoiceKey.isBlank()) return ""
        return dubbing.cleanVideoSourceLabel()
            .ifBlank { dubbing.trim() }
    }

fun List<VideoSubscription>.preferredProfileSubscription(): VideoSubscription {
    return maxWithOrNull(
        compareBy<VideoSubscription> { it.dubbing.cleanVideoSourceLabel().isNotBlank() }
            .thenBy { it.dubbing.cleanVideoSourceLabel().length },
    ) ?: first()
}

fun VideoSubscription.matchesVideoPlayer(video: VideoVariant): Boolean {
    if (animeId != video.animeId) return false
    if (playerId > 0L && video.playerId == playerId) return true
    val subscriptionPlayer = player.cleanVideoSourceLabel()
    return subscriptionPlayer.isNotBlank() &&
        subscriptionPlayer.equals(video.player.cleanVideoSourceLabel(), ignoreCase = true)
}

fun List<VideoSubscription>.hasSubscriptionForVoice(animeId: Long, voiceKey: String): Boolean {
    val normalizedVoiceKey = voiceKey.normalizedVoiceKey()
    return any { it.matchesAnimeVoice(animeId, normalizedVoiceKey) }
}

fun List<VideoSubscription>.isSubscribedTo(video: VideoVariant): Boolean {
    return hasSubscriptionForVoice(video.animeId, video.matchingVoiceKey)
}

fun VideoSubscription.matchesAnimeVoice(animeId: Long, voiceKey: String): Boolean {
    return this.animeId == animeId && matchingVoiceKey == voiceKey.normalizedVoiceKey()
}

val VideoSubscription.matchingPlayerKey: String
    get() = player.cleanVideoSourceLabel().normalizedVoiceKey()
