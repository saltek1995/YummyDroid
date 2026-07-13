package me.yummydroid.app.data

import java.util.Locale

internal val VideoVariant.matchingVoiceTitle: String
    get() = dubbing.cleanVideoSourceLabel()
        .ifBlank { player.cleanVideoSourceLabel() }
        .ifBlank { "Озвучка" }

internal val VideoVariant.matchingDubbingTitle: String
    get() = dubbing.cleanVideoSourceLabel()

internal val VideoVariant.matchingDubbingKey: String
    get() = matchingDubbingTitle.normalizedVoiceKey()

internal val VideoVariant.matchingVoiceKey: String
    get() = matchingVoiceTitle.normalizedVoiceKey()

internal val VideoVariant.matchingSourceKey: String
    get() = listOf(player.cleanVideoSourceLabel(), matchingVoiceKey)
        .joinToString("|")
        .normalizedVoiceKey()

internal val VideoVariant.matchingEpisodeKey: String
    get() = episode.trim().takeIf { it.isNotBlank() }
        ?: index.takeIf { it > 0 }?.let { "index:$it" }
        ?: "video:$id"

internal val VideoSubscription.matchingVoiceKey: String
    get() = dubbing.cleanVideoSourceLabel()
        .normalizedVoiceKey()

internal val VideoSubscription.matchingSourceKey: String
    get() = listOf(player.cleanVideoSourceLabel(), matchingVoiceKey)
        .joinToString("|")
        .normalizedVoiceKey()

internal fun VideoSubscription.matchesVideoPlayer(video: VideoVariant): Boolean {
    if (animeId != video.animeId) return false
    if (playerId > 0L && video.playerId == playerId) return true
    val subscriptionPlayer = player.cleanVideoSourceLabel()
    return subscriptionPlayer.isNotBlank() &&
        subscriptionPlayer.equals(video.player.cleanVideoSourceLabel(), ignoreCase = true)
}

internal fun List<VideoSubscription>.hasSubscriptionForVoice(animeId: Long, voiceKey: String): Boolean {
    val normalizedVoiceKey = voiceKey.normalizedVoiceKey()
    return any { it.matchesAnimeVoice(animeId, normalizedVoiceKey) }
}

internal fun List<VideoSubscription>.isSubscribedTo(video: VideoVariant): Boolean {
    return hasSubscriptionForVoice(video.animeId, video.matchingVoiceKey)
}

internal fun List<VideoSubscription>.withVoiceSubscriptionState(
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

internal fun List<VideoSubscription>.withAddedSubscriptionTargets(
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

internal fun VideoSubscription.matchesAnimeVoice(animeId: Long, voiceKey: String): Boolean {
    return this.animeId == animeId && matchingVoiceKey == voiceKey.normalizedVoiceKey()
}

internal val VideoVariant.matchingPlayerKey: String
    get() = player.cleanVideoSourceLabel().normalizedVoiceKey()

internal val VideoSubscription.matchingPlayerKey: String
    get() = player.cleanVideoSourceLabel().normalizedVoiceKey()

internal fun VideoVariant.isSameEpisodeAs(other: VideoVariant): Boolean {
    return matchingEpisodeKey == other.matchingEpisodeKey
}

internal fun VideoVariant.hasSameVoiceAs(other: VideoVariant): Boolean {
    return matchingVoiceKey == other.matchingVoiceKey
}

internal fun VideoVariant.episodeOrderValue(): Double? {
    return episode
        .trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?: index.takeIf { it > 0 }?.toDouble()
}

internal fun String.cleanVideoSourceLabel(): String {
    var value = trim()
    knownVideoSourcePrefixes.forEach { prefix ->
        value = value.replace(
            regex = Regex("""^\s*${Regex.escape(prefix)}\s*""", RegexOption.IGNORE_CASE),
            replacement = "",
        ).trim()
    }
    return value
}

internal fun String.normalizedVoiceKey(): String {
    return lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace("озвучка", "")
        .replace("субтитры", "")
        .replace("плеер", "")
        .replace(Regex("""[\s./|•:_-]+"""), "")
        .trim()
}

internal fun VideoVariant.downloadedEpisodeCountForVoice(variants: List<VideoVariant>): Int {
    val voiceKey = matchingVoiceKey
    return variants
        .asSequence()
        .filter { it.matchingVoiceKey == voiceKey && it.isOfflineAvailable }
        .map { it.episodeDownloadSlotKey() }
        .distinct()
        .count()
}

private fun VideoVariant.episodeDownloadSlotKey(): String = matchingEpisodeKey

private val VideoSubscription.subscriptionIdentityKey: String
    get() = "$animeId|$matchingSourceKey|$videoId"

private val knownVideoSourcePrefixes = listOf(
    "Озвучка",
    "Субтитры",
    "Плеер",
)
