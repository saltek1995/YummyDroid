package me.yummydroid.app.data

import java.util.Locale

internal val VideoVariant.matchingVoiceTitle: String
    get() = dubbing.cleanVideoSourceLabel()
        .ifBlank { player.cleanVideoSourceLabel() }
        .ifBlank { "Озвучка" }

internal val VideoVariant.matchingVoiceKey: String
    get() = matchingVoiceTitle.normalizedVoiceKey()

internal val VideoVariant.matchingSourceKey: String
    get() = listOf(player.cleanVideoSourceLabel(), matchingVoiceKey)
        .joinToString("|")
        .normalizedVoiceKey()

internal val VideoSubscription.matchingVoiceKey: String
    get() = dubbing.cleanVideoSourceLabel()
        .ifBlank { player.cleanVideoSourceLabel() }
        .normalizedVoiceKey()

internal val VideoSubscription.matchingSourceKey: String
    get() = listOf(player.cleanVideoSourceLabel(), matchingVoiceKey)
        .joinToString("|")
        .normalizedVoiceKey()

internal fun List<VideoSubscription>.hasSubscriptionForVoice(animeId: Long, voiceKey: String): Boolean {
    val normalizedVoiceKey = voiceKey.normalizedVoiceKey()
    return any { it.matchesAnimeVoice(animeId, normalizedVoiceKey) }
}

internal fun List<VideoSubscription>.isSubscribedTo(video: VideoVariant): Boolean {
    return hasSubscriptionForVoice(video.animeId, video.matchingVoiceKey)
}

internal fun VideoSubscription.matchesAnimeVoice(animeId: Long, voiceKey: String): Boolean {
    return this.animeId == animeId && matchingVoiceKey == voiceKey.normalizedVoiceKey()
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

private fun VideoVariant.episodeDownloadSlotKey(): String {
    return episode.trim().takeIf { it.isNotBlank() }
        ?: index.takeIf { it > 0 }?.let { "index:$it" }
        ?: "video:$id"
}

private val knownVideoSourcePrefixes = listOf(
    "Озвучка",
    "Субтитры",
    "Плеер",
)
