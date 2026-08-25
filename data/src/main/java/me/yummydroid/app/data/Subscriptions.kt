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
        val playerKey = playerId.takeIf { it > 0L }?.let { "id:$it" }
            ?: player.cleanVideoSourceLabel()
            .lowercase(Locale.ROOT)
            .replace(whitespaceRegex, " ")
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { "name:$it" }
            .orEmpty()
        val voiceKey = matchingVoiceKey.ifBlank {
            dubbing
                .lowercase(Locale.ROOT)
                .replace('\u0451', '\u0435')
                .replace(whitespaceRegex, " ")
                .trim()
        }
        return "$animeId|$playerKey|$voiceKey"
    }

val VideoSubscription.profileVoiceTitle: String
    get() = dubbing.cleanVideoSourceLabel().ifBlank { dubbing.trim() }

val VideoSubscription.profilePlayerTitle: String
    get() = player.cleanVideoSourceLabel().ifBlank { player.trim() }

fun VideoSubscription.matchesVideoPlayer(video: VideoVariant): Boolean {
    if (animeId != video.animeId) return false
    if (playerId > 0L && video.playerId == playerId) return true
    val subscriptionPlayer = player.cleanVideoSourceLabel()
    return subscriptionPlayer.isNotBlank() &&
        subscriptionPlayer.equals(video.player.cleanVideoSourceLabel(), ignoreCase = true)
}

val VideoSubscription.matchingPlayerKey: String
    get() = player.cleanVideoSourceLabel().normalizedVoiceKey()
