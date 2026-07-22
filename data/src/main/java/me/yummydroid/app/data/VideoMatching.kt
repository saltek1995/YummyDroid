package me.yummydroid.app.data

import java.util.Locale

val VideoVariant.matchingVoiceTitle: String
    get() = matchingDubbingTitle.ifBlank { "Озвучка" }

val VideoVariant.matchingDisplayVoiceTitle: String
    get() = matchingDubbingTitle
        .ifBlank { player.cleanVideoSourceLabel() }
        .ifBlank { matchingVoiceTitle }

val VideoVariant.matchingDubbingTitle: String
    get() = dubbing.cleanVideoSourceLabel()
        .takeUnless { it.isKnownPlayerLabel() }
        .orEmpty()

val VideoVariant.matchingDubbingKey: String
    get() = matchingDubbingTitle.normalizedVoiceKey()

val VideoVariant.matchingVoiceKey: String
    get() = matchingDubbingTitle.normalizedVoiceKey()

val VideoVariant.matchingSourceKey: String
    get() = listOf(player.cleanVideoSourceLabel(), matchingVoiceKey)
        .joinToString("|")
        .normalizedVoiceKey()

val VideoVariant.matchingEpisodeKey: String
    get() = episode.trim().takeIf { it.isNotBlank() }
        ?: index.takeIf { it > 0 }?.let { "index:$it" }
        ?: "video:$id"

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
            .replace(Regex("""\s+"""), " ")
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { return "$animeId|player:$it" }
        val voiceKey = matchingVoiceKey.ifBlank {
            dubbing
                .lowercase(Locale.ROOT)
                .replace('\u0451', '\u0435')
                .replace(Regex("""\s+"""), " ")
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

val VideoVariant.matchingPlayerKey: String
    get() = player.cleanVideoSourceLabel().normalizedVoiceKey()

val VideoSubscription.matchingPlayerKey: String
    get() = player.cleanVideoSourceLabel().normalizedVoiceKey()

fun VideoVariant.isSameEpisodeAs(other: VideoVariant): Boolean {
    return matchingEpisodeKey == other.matchingEpisodeKey
}

fun VideoVariant.hasSameVoiceAs(other: VideoVariant): Boolean {
    return matchingVoiceKey == other.matchingVoiceKey
}

fun VideoVariant.episodeOrderValue(): Double? {
    return episode
        .trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?: index.takeIf { it > 0 }?.toDouble()
}

val VideoVariant.downloadVoiceSlotKey: String
    get() = listOf(
        animeId.toString(),
        matchingEpisodeKey,
        matchingVoiceKey,
    ).joinToString("|") { it.trim().lowercase(Locale.ROOT) }

val VideoVariant.sourceSlotKey: String
    get() = listOf(
        animeId.toString(),
        matchingEpisodeKey,
        matchingPlayerKey,
        matchingVoiceKey,
    ).joinToString("|") { it.trim().lowercase(Locale.ROOT) }

val VideoVariant.downloadEpisodeSlotKey: String
    get() = matchingEpisodeKey

fun sourceProviderRank(player: String): Int {
    val normalized = player.cleanVideoSourceLabel().lowercase(Locale.ROOT)
    return when {
        "cvh" in normalized || "cdnvideohub" in normalized -> 0
        "alloha" in normalized -> 1
        "kodik" in normalized -> 2
        "aksor" in normalized -> 3
        "sibnet" in normalized -> 4
        else -> 10
    }
}

fun OfflineVideoFile.matchesPreferredQuality(preferredQuality: PreferredQuality): Boolean {
    val preferredHeight = preferredQuality.height ?: return true
    return qualityHeight() == preferredHeight
}

fun String.cleanVideoSourceLabel(): String {
    var value = trim()
    knownVideoSourcePrefixes.forEach { prefix ->
        value = value.replace(
            regex = Regex("""^\s*${Regex.escape(prefix)}\s*""", RegexOption.IGNORE_CASE),
            replacement = "",
        ).trim()
    }
    return value
}

fun String.isKnownPlayerLabel(): Boolean {
    val key = cleanVideoSourceLabel().normalizedVoiceKey()
    return key in knownVideoPlayerLabelKeys
}

fun String.normalizedVoiceKey(): String {
    return lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace("озвучка", "")
        .replace("субтитры", "")
        .replace("плеер", "")
        .replace(Regex("""[\s./|•:_-]+"""), "")
        .trim()
}

fun VideoVariant.downloadedEpisodeCountForVoice(variants: List<VideoVariant>): Int {
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

private val knownVideoPlayerLabelKeys = setOf(
    "alloha",
    "kodik",
    "cvh",
    "sibnet",
    "aksor",
    "hls",
    "mp4",
    "videocdn",
    "cdnvideohub",
    "videoframe",
    "aniboom",
)
