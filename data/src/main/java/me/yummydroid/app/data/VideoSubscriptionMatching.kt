package me.yummydroid.app.data

import java.util.Locale

val VideoSubscription.matchingVoiceKey: String
    get() = dubbing.cleanVideoSourceLabel()
        .takeUnless { it.isKnownPlayerLabel() }
        .orEmpty()
        .normalizedVoiceKey()

val VideoSubscription.matchingSourceKey: String
    get() = listOf(player.cleanVideoSourceLabel(), matchingVoiceKey)
        .joinToString("|")
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

fun List<VideoSubscription>.withVoiceSubscriptionState(
    animeId: Long,
    voiceKey: String,
    videos: List<VideoVariant>,
    subscribed: Boolean,
    title: String,
    posterUrl: String,
): List<VideoSubscription> {
    val videoIds = videos.map { it.id }.filter { it > 0L }.toSet()
    val targetPlayerIds = videos.map { it.playerId }.filter { it > 0L }.toSet()
    val targetPlayerKeys = videos.map { it.matchingPlayerKey }.filter { it.isNotBlank() }.toSet()
    val retained = filterNot { subscription ->
        subscription.animeId == animeId &&
            (
                subscription.videoId in videoIds ||
                    subscription.matchesAnimeVoice(animeId, voiceKey) ||
                    (
                        subscription.matchingVoiceKey.isBlank() &&
                            (
                                subscription.playerId in targetPlayerIds ||
                                    subscription.matchingPlayerKey in targetPlayerKeys
                            )
                    )
            )
    }
    if (!subscribed) return retained
    return retained.withAddedSubscriptionTargets(videos, title, posterUrl)
}

fun List<VideoSubscription>.withAddedSubscriptionTargets(
    videos: List<VideoVariant>,
    title: String,
    posterUrl: String,
): List<VideoSubscription> {
    val added = videos.map { source ->
        VideoSubscription(
            animeId = source.animeId,
            title = title,
            posterUrl = posterUrl,
            player = source.player,
            dubbing = source.dubbing,
            playerId = source.playerId,
            videoId = source.id,
        )
    }
    return (this + added).distinctBy { it.subscriptionIdentityKey }
}

fun VideoSubscription.matchesAnimeVoice(animeId: Long, voiceKey: String): Boolean {
    return this.animeId == animeId && matchingVoiceKey == voiceKey.normalizedVoiceKey()
}

val VideoSubscription.matchingPlayerKey: String
    get() = player.cleanVideoSourceLabel().normalizedVoiceKey()

private val VideoSubscription.subscriptionIdentityKey: String
    get() = "$animeId|$matchingSourceKey|$videoId"
