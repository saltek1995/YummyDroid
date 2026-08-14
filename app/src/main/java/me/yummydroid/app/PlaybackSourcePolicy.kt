package me.yummydroid.app

import java.util.Locale
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.hasSameVoiceAs
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.sourceResolutionHeight

internal data class PlaybackFallbackDecision(
    val excludedSourceKeys: Set<String>,
    val targetVideo: VideoVariant,
    val voiceFallbackFromVideo: VideoVariant? = null,
)

private data class PlaybackFallbackCandidatePool(
    val excludedSourceKeys: Set<String>,
    val candidates: List<VideoVariant>,
) {
    fun sameVoiceCandidates(currentVideo: VideoVariant): List<VideoVariant> {
        return candidates.filter { candidate -> candidate.hasSameVoiceAs(currentVideo) }
    }

    fun otherVoiceCandidates(currentVideo: VideoVariant): List<VideoVariant> {
        return candidates.filterNot { candidate -> candidate.hasSameVoiceAs(currentVideo) }
    }
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
    if (!hasSamePlaybackContextAs(other)) return false
    compareKnownProviderWith(other)?.let { return it }
    if (hasSamePositiveVideoIdAs(other)) return true
    return playbackSourceKey == other.playbackSourceKey
}

private fun VideoVariant.hasSamePlaybackContextAs(other: VideoVariant): Boolean {
    return animeId == other.animeId && isSameEpisodeAs(other) && hasSameVoiceAs(other)
}

private fun VideoVariant.compareKnownProviderWith(other: VideoVariant): Boolean? {
    val leftProviderKey = sourceProviderKey
    val rightProviderKey = other.sourceProviderKey
    if (leftProviderKey.isBlank() || rightProviderKey.isBlank()) return null
    return leftProviderKey == rightProviderKey
}

private fun VideoVariant.hasSamePositiveVideoIdAs(other: VideoVariant): Boolean {
    return id > 0L && other.id > 0L && id == other.id
}

internal fun automaticPlaybackFallbackDecision(
    currentVideo: VideoVariant,
    failedVideo: VideoVariant,
    failure: PlaybackFailure,
    allVideos: List<VideoVariant>,
    preferredQuality: PreferredQuality,
    currentStream: ResolvedVideoStream?,
    blockedSourceKeys: Set<String>,
): PlaybackFallbackDecision? {
    if (!currentVideo.hasSamePlaybackSourceAs(failedVideo)) return null
    val pool = playbackFallbackCandidatePool(
        currentVideo = currentVideo,
        failedVideo = failedVideo,
        allVideos = allVideos,
        blockedSourceKeys = blockedSourceKeys,
    ) ?: return null
    val sameVoiceCandidates = pool.sameVoiceCandidates(currentVideo)

    sameVoiceCandidates.sameVoiceFallbackTarget(
        currentVideo = currentVideo,
        failure = failure,
        currentStream = currentStream,
        preferredQuality = preferredQuality,
    )?.let { targetVideo ->
        return PlaybackFallbackDecision(
            excludedSourceKeys = pool.excludedSourceKeys,
            targetVideo = targetVideo,
        )
    }

    if (sameVoiceCandidates.isNotEmpty()) return null
    val voiceFallback = pool.otherVoiceCandidates(currentVideo)
        .bestFallbackCandidate(currentVideo)
        ?: return null
    return PlaybackFallbackDecision(
        excludedSourceKeys = pool.excludedSourceKeys,
        targetVideo = voiceFallback,
        voiceFallbackFromVideo = currentVideo,
    )
}

private fun playbackFallbackCandidatePool(
    currentVideo: VideoVariant,
    failedVideo: VideoVariant,
    allVideos: List<VideoVariant>,
    blockedSourceKeys: Set<String>,
): PlaybackFallbackCandidatePool? {
    val sameEpisodeCandidates = allVideos
        .filter { candidate -> candidate.animeId == currentVideo.animeId && candidate.isSameEpisodeAs(currentVideo) }
        .ifEmpty { listOf(currentVideo) }
    val failedSourceKeys = sameEpisodeCandidates
        .filter { candidate -> candidate.hasSamePlaybackSourceAs(failedVideo) }
        .mapTo(mutableSetOf()) { candidate -> candidate.playbackSourceKey }
    val excludedSourceKeys = blockedSourceKeys + failedVideo.playbackSourceKey + failedSourceKeys
    val candidates = sameEpisodeCandidates
        .filterNot { candidate -> candidate.playbackSourceKey in excludedSourceKeys }
        .filterNot { candidate -> candidate.hasSamePlaybackSourceAs(failedVideo) }
    return candidates
        .takeIf { it.isNotEmpty() }
        ?.let { PlaybackFallbackCandidatePool(excludedSourceKeys, it) }
}

private fun List<VideoVariant>.sameVoiceFallbackTarget(
    currentVideo: VideoVariant,
    failure: PlaybackFailure,
    currentStream: ResolvedVideoStream?,
    preferredQuality: PreferredQuality,
): VideoVariant? {
    return qualityUpgradeCandidate(
        currentVideo = currentVideo,
        currentStream = currentStream,
        preferredQuality = preferredQuality,
    ) ?: if (failure.canSwitchToSameQualitySource()) {
        bestFallbackCandidate(currentVideo)
    } else {
        null
    }
}

private fun PlaybackFailure.canSwitchToSameQualitySource(): Boolean {
    return kind == PlaybackFailureKind.BufferingTimeout ||
        kind == PlaybackFailureKind.SourceUnavailable
}

private fun List<VideoVariant>.qualityUpgradeCandidate(
    currentVideo: VideoVariant,
    currentStream: ResolvedVideoStream?,
    preferredQuality: PreferredQuality,
): VideoVariant? {
    val currentHeight = currentVideo.currentPlaybackQualityHeight(currentStream, preferredQuality)
    if (currentHeight <= 0) return null
    return filter { candidate ->
        candidate.fallbackQualityHeight(preferredQuality) > currentHeight
    }.bestFallbackCandidate(currentVideo)
}

private fun List<VideoVariant>.bestFallbackCandidate(currentVideo: VideoVariant): VideoVariant? {
    return sortedForPlaybackSource(
        requested = currentVideo,
        manualSourceKey = null,
    ).firstOrNull()
}

private fun VideoVariant.currentPlaybackQualityHeight(
    stream: ResolvedVideoStream?,
    preferredQuality: PreferredQuality,
): Int {
    val streamHeight = stream?.selectedVideoHeight
        ?.validPlaybackQualityHeight()
        ?: stream?.sourceResolutionHeight()?.takeIf { it > 0 }
    return (streamHeight ?: fallbackQualityHeight(PreferredQuality.Auto))
        .cappedBy(preferredQuality)
}

private fun VideoVariant.fallbackQualityHeight(preferredQuality: PreferredQuality): Int {
    val knownSourceHeight = sourceQualities
        .mapNotNull { it.height?.validPlaybackQualityHeight() }
        .maxOrNull()
    return (knownSourceHeight ?: estimatedSourceMaxVideoHeight())
        .cappedBy(preferredQuality)
}

private fun Int.cappedBy(preferredQuality: PreferredQuality): Int {
    val safeHeight = validPlaybackQualityHeight() ?: return 0
    val preferredHeight = preferredQuality.height?.validPlaybackQualityHeight() ?: return safeHeight
    return minOf(safeHeight, preferredHeight)
}

private fun Int.validPlaybackQualityHeight(): Int? = takeIf { it in MIN_PLAYBACK_QUALITY_HEIGHT..MAX_PLAYBACK_QUALITY_HEIGHT }

private const val MIN_PLAYBACK_QUALITY_HEIGHT = 100
private const val MAX_PLAYBACK_QUALITY_HEIGHT = 4320

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
            .thenByDescending { it.fallbackQualityHeight(PreferredQuality.Auto) }
            .thenBy {
                if (manualSourceKey == null && it.matchesSourceSelectionKey(cachedSourceKey)) 0 else 1
            }
            .thenBy { if (it.hasSamePlaybackSourceAs(requested)) 0 else 1 }
            .thenBy { it.index }
            .thenBy { it.id },
    )
}
