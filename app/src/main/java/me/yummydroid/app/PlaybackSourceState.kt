package me.yummydroid.app

import java.util.Locale
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.episodeOrderValue
import me.yummydroid.app.data.hasSameVoiceAs
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey

internal fun Throwable.userMessage(): String {
    return message?.takeIf { it.isNotBlank() } ?: "Could not load data"
}

internal fun VideoVariant.isFinalEpisodeFor(details: AnimeDetails, allVideos: List<VideoVariant>): Boolean {
    val sameAnimeVideos = allVideos.filter { it.animeId == animeId }
    val lastVideo = sameAnimeVideos
        .maxWithOrNull(
            compareBy<VideoVariant> { it.episodeOrderValue() ?: 0.0 }
                .thenBy { it.index }
                .thenBy { it.id },
    )
    if (lastVideo != null) return lastVideo.isSameEpisodeAs(this)

    return false
}

internal fun VideoVariant.hasFollowingEpisodeIn(allVideos: List<VideoVariant>): Boolean {
    val sameAnimeVideos = allVideos
        .filter { it.animeId == animeId }
        .ifEmpty { listOf(this) }
    val currentOrder = episodeOrderValue()
    if (currentOrder != null) {
        return sameAnimeVideos.any { candidate ->
            val candidateOrder = candidate.episodeOrderValue()
            candidateOrder != null && candidateOrder > currentOrder
        }
    }

    val episodeVideos = sameAnimeVideos
        .groupBy { it.matchingEpisodeKey }
        .values
        .mapNotNull { variants ->
            variants.minWithOrNull(
                compareBy<VideoVariant> { it.index }
                    .thenBy { it.id },
            )
        }
        .sortedWith(
            compareBy<VideoVariant> { it.index }
                .thenBy { it.id },
        )
    val currentIndex = episodeVideos.indexOfFirst { it.isSameEpisodeAs(this) }
        .takeIf { it >= 0 }
        ?: return false
    return currentIndex < episodeVideos.lastIndex
}

internal fun PlaybackProgress.isNewerThan(other: PlaybackProgress?): Boolean {
    return updatedAtMs > (other?.updatedAtMs ?: Long.MIN_VALUE)
}

internal fun ResolvedVideoStream.isLocalPlaybackStream(): Boolean {
    return url.startsWith("file:", ignoreCase = true) || url.startsWith("content:", ignoreCase = true)
}

internal val VideoVariant.sourceProviderKey: String
    get() = listOf(
        player.cleanVideoSourceLabel().lowercase(Locale.ROOT),
        url.sourceProviderFingerprint(),
    ).filter { it.isNotBlank() }.joinToString("|")

internal val VideoVariant.playbackSourceKey: String
    get() = listOf(
        animeId.toString(),
        matchingEpisodeKey,
        matchingVoiceKey,
        sourceSelectionKey,
        id.takeIf { it > 0L }?.let { "id:$it" }
            ?: url.sourcePlaybackFingerprint().takeIf { it.isNotBlank() }
            ?: index.takeIf { it > 0 }?.let { "index:$it" }
            ?: "unknown",
    ).filter { it.isNotBlank() }.joinToString("|")

internal val VideoVariant.sourceSelectionKey: String
    get() = sourceProviderKey.takeIf { it.isNotBlank() }
        ?: playerId.takeIf { it > 0L }?.let { "player-id:$it" }
        ?: player.cleanVideoSourceLabel()
            .lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), " ")
            .trim()
            .takeIf { it.isNotBlank() }
        ?: id.takeIf { it > 0L }?.let { "id:$it" }
        ?: url.sourcePlaybackFingerprint().takeIf { it.isNotBlank() }
        ?: index.takeIf { it > 0 }?.let { "index:$it" }
        ?: ""

internal fun VideoVariant.matchesSourceSelectionKey(key: String?): Boolean {
    val selected = key?.takeIf { it.isNotBlank() } ?: return false
    return sourceSelectionKey == selected || sourceProviderKey == selected || playbackSourceKey == selected
}

internal fun VideoVariant.isManualPlaybackSource(manualSourceKey: String?): Boolean {
    return matchesSourceSelectionKey(manualSourceKey)
}

internal fun VideoVariant.hasSamePlaybackSourceAs(other: VideoVariant): Boolean {
    if (animeId != other.animeId || !isSameEpisodeAs(other) || !hasSameVoiceAs(other)) return false
    val leftProviderKey = sourceProviderKey
    val rightProviderKey = other.sourceProviderKey
    if (leftProviderKey.isNotBlank() && rightProviderKey.isNotBlank()) {
        return leftProviderKey == rightProviderKey
    }
    if (id > 0L && other.id > 0L && id == other.id) return true
    return playbackSourceKey == other.playbackSourceKey
}

internal fun shouldUseAutomaticPlaybackFallback(
    currentVideo: VideoVariant,
    failedVideo: VideoVariant,
    manualSourceKey: String?,
    failure: PlaybackFailure,
): Boolean {
    if (!currentVideo.hasSamePlaybackSourceAs(failedVideo)) return false
    return failure.kind != PlaybackFailureKind.BufferingTimeout ||
        !currentVideo.isManualPlaybackSource(manualSourceKey)
}

internal fun String.sourceProviderFingerprint(): String {
    val value = trim().lowercase(Locale.ROOT)
    val host = Regex("""^https?://([^/?#]+)""").find(value)?.groupValues?.getOrNull(1).orEmpty()
    val path = Regex("""^https?://[^/]+/([^?#]+)""").find(value)?.groupValues?.getOrNull(1)
        ?.substringBefore('/')
        .orEmpty()
    return listOf(host, path).filter { it.isNotBlank() }.joinToString("/")
}

internal fun String.sourcePlaybackFingerprint(): String {
    return trim()
        .substringBefore('#')
        .lowercase(Locale.ROOT)
}

internal fun VideoVariant.estimatedSourceMaxVideoHeight(): Int {
    val lowerPlayer = player.lowercase(Locale.ROOT)
    val lowerUrl = url.lowercase(Locale.ROOT)
    return when {
        "cvh" in lowerPlayer || "iframecvh" in lowerUrl -> 1080
        "alloha" in lowerPlayer || "alloha" in lowerUrl -> 1080
        "aksor" in lowerPlayer || "aksor" in lowerUrl -> 1080
        else -> Regex("""(?i)(2160|1440|1080|720|576|540|480|360|240|144)p""")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
    }
}

internal fun List<VideoVariant>.sortedForPlaybackSource(
    requested: VideoVariant,
    manualSourceKey: String?,
    cachedSourceKey: String? = null,
): List<VideoVariant> {
    return sortedWith(
        compareBy<VideoVariant> { if (it.matchesSourceSelectionKey(manualSourceKey)) 0 else 1 }
            .thenBy { if (it.isOfflineAvailable) 0 else 1 }
            .thenByDescending { it.estimatedSourceMaxVideoHeight() }
            .thenBy {
                if (manualSourceKey == null && it.matchesSourceSelectionKey(cachedSourceKey)) 0 else 1
            }
            .thenBy { if (it.hasSamePlaybackSourceAs(requested)) 0 else 1 }
            .thenBy { it.index }
            .thenBy { it.id },
    )
}

