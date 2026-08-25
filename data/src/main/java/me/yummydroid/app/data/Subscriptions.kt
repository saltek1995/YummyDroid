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

fun List<VideoVariant>.serverVideoSubscriptions(details: AnimeDetails): List<VideoSubscription> {
    return asSequence()
        .filter { video -> video.animeId == details.id && video.subscribed }
        .distinctBy { video ->
            listOf(
                video.animeId,
                video.playerId,
                video.matchingPlayerKey,
                video.matchingVoiceKey,
            ).joinToString("|")
        }
        .map { video ->
            VideoSubscription(
                animeId = video.animeId,
                title = details.title,
                posterUrl = details.posterUrl,
                player = video.player,
                dubbing = video.dubbing,
                playerId = video.playerId,
                videoId = video.id,
            )
        }
        .toList()
}

fun List<VideoSubscription>.withServerVideoSubscriptions(
    details: AnimeDetails,
    videos: List<VideoVariant>,
): List<VideoSubscription> {
    val currentAnimeSubscriptions = videos.serverVideoSubscriptions(details)
    if (currentAnimeSubscriptions.isEmpty()) return this
    return (currentAnimeSubscriptions + this).distinctBy(VideoSubscription::profileDisplayKey)
}

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
    get() {
        val voiceTitle = dubbing.cleanVideoSourceLabel()
            .ifBlank { dubbing.trim() }
        val playerTitle = player.cleanVideoSourceLabel().ifBlank { player.trim() }
        return listOf(voiceTitle, playerTitle)
            .filter(String::isNotBlank)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .joinToString(" \u00b7 ")
    }

fun VideoSubscription.matchesVideoPlayer(video: VideoVariant): Boolean {
    if (animeId != video.animeId) return false
    if (playerId > 0L && video.playerId == playerId) return true
    val subscriptionPlayer = player.cleanVideoSourceLabel()
    return subscriptionPlayer.isNotBlank() &&
        subscriptionPlayer.equals(video.player.cleanVideoSourceLabel(), ignoreCase = true)
}

fun List<VideoSubscription>.isSubscribedTo(video: VideoVariant): Boolean {
    return any { subscription ->
        subscription.matchesVideoPlayer(video) &&
            subscription.matchingVoiceKey == video.matchingVoiceKey
    }
}

val VideoSubscription.matchingPlayerKey: String
    get() = player.cleanVideoSourceLabel().normalizedVoiceKey()
