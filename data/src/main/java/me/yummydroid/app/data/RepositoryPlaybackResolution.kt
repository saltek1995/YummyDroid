package me.yummydroid.app.data

import java.io.IOException
import java.util.Locale
internal const val SOURCE_RESOLVE_TIMEOUT_MS = 12_000L
internal const val CVH_SOURCE_RESOLVE_TIMEOUT_MS = 25_000L
internal const val RUNTIME_SOURCE_RESOLVE_TIMEOUT_MS = 45_000L

internal fun VideoVariant.sourceResolveTimeoutMs(): Long {
    val source = listOf(url, player.cleanVideoSourceLabel())
        .joinToString(" ")
        .lowercase(Locale.ROOT)
    return when {
        "alloha" in source || "alloh" in source -> RUNTIME_SOURCE_RESOLVE_TIMEOUT_MS
        "cvh" in source || "cdnvideohub" in source || "iframecvh" in source -> CVH_SOURCE_RESOLVE_TIMEOUT_MS
        else -> SOURCE_RESOLVE_TIMEOUT_MS
    }
}

internal data class SourceResolveAttempt(
    val index: Int,
    val candidate: VideoVariant,
    val playback: ResolvedPlayback? = null,
    val failure: Throwable? = null,
)

private fun List<SourceResolveAttempt>.successfulPlaybacks(): List<Pair<Int, ResolvedPlayback>> {
    return mapNotNull { attempt -> attempt.playback?.let { playback -> attempt.index to playback } }
}

internal fun List<SourceResolveAttempt>.bestPlayback(
    selectableKeys: Set<String>? = null,
): ResolvedPlayback? {
    return successfulPlaybacks()
        .filter { (_, playback) ->
            selectableKeys == null || playback.video.sourceResolveIdentity() in selectableKeys
        }
        .sortedWith(
            compareByDescending<Pair<Int, ResolvedPlayback>> { (_, playback) -> playback.video.isOfflineAvailable }
                .thenByDescending { (_, playback) -> playback.stream.sourceResolutionHeight() }
                .thenByDescending { (_, playback) -> playback.stream.hasSubtitles }
                .thenBy { (index, _) -> index },
        )
        .firstOrNull()
        ?.second
}

internal fun ResolvedPlayback.withMetadataFromAttempts(
    attempts: List<SourceResolveAttempt>,
): ResolvedPlayback {
    val sameEpisodeAttempts = attempts
        .filter { attempt -> attempt.candidate.isSameEpisodeAs(video) }
    val sameVoiceAttempts = sameEpisodeAttempts
        .filter { attempt -> attempt.candidate.hasSameVoiceAs(video) }

    return withMergedPlaybackMetadata(
        metadataPlaybacks = sameVoiceAttempts
            .asSequence()
            .mapNotNull { attempt -> attempt.playback }
            .toList(),
    )
}

internal fun ResolvedPlayback.withMergedPlaybackMetadata(
    metadataPlaybacks: List<ResolvedPlayback>,
): ResolvedPlayback {
    val sameVoicePlaybacks = metadataPlaybacks
        .asSequence()
        .filter { playback -> playback.video.isSameEpisodeAs(video) && playback.video.hasSameVoiceAs(video) }
        .toList()

    val sameSourceStream = sameVoicePlaybacks
        .filter { playback -> playback.video.sourceResolveIdentity() == video.sourceResolveIdentity() }
        .maxWithOrNull(
            compareBy<ResolvedPlayback> { playback -> if (playback.stream.hasSubtitles) 1 else 0 }
                .thenBy { playback -> playback.stream.availableQualities.size },
        )
        ?.stream
        ?: stream

    val mergedQualities = (stream.sourceQualitiesWithMax() + sameVoicePlaybacks.flatMap { playback ->
        playback.stream.sourceQualitiesWithMax()
    }).normalizedSourceQualities()
    val sourceSubtitleSourceKeys = (stream.sourceSubtitleSourceKeys + sameVoicePlaybacks.mapNotNull { playback ->
        playback.video.matchingSourceKey.takeIf { key -> key.isNotBlank() && playback.stream.hasResolvedSubtitles }
    }).toSet()

    if (
        sameSourceStream.subtitles == stream.subtitles &&
        sameSourceStream.embeddedSubtitles == stream.embeddedSubtitles &&
        sameSourceStream.hasEmbeddedSubtitles == stream.hasEmbeddedSubtitles &&
        mergedQualities == stream.availableQualities.normalizedSourceQualities() &&
        sourceSubtitleSourceKeys == stream.sourceSubtitleSourceKeys
    ) {
        return this
    }
    return copy(
        stream = stream.copy(
            subtitles = sameSourceStream.subtitles,
            embeddedSubtitles = sameSourceStream.embeddedSubtitles,
            hasEmbeddedSubtitles = sameSourceStream.hasEmbeddedSubtitles,
            availableQualities = mergedQualities,
            sourceSubtitleSourceKeys = sourceSubtitleSourceKeys,
        ),
    )
}

private fun ResolvedVideoStream.sourceQualitiesWithMax(): List<SourceQuality> {
    return availableQualities + listOfNotNull(maxVideoHeight?.let { SourceQuality(height = it) })
}

internal fun List<SourceResolveAttempt>.downloadPlaybacks(preferredQuality: PreferredQuality): List<ResolvedPlayback> {
    val preferredHeight = preferredQuality.height
    return successfulPlaybacks()
        .filter { (_, playback) ->
            preferredHeight == null || playback.stream.hasExactDownloadQuality(preferredHeight)
        }
        .sortedWith(
            compareByDescending<Pair<Int, ResolvedPlayback>> { (_, playback) ->
                playback.stream.qualityScore(preferredQuality)
            }.thenBy { (index, _) -> index },
        )
        .map { it.second }
}

internal fun List<SourceResolveAttempt>.resolveFailure(message: String): IOException {
    val details = mapNotNull { attempt ->
        attempt.failure?.let { throwable ->
            "${attempt.candidate.groupTitle.ifBlank { attempt.candidate.player }}: ${throwable.message.orEmpty()}"
        }
    }
        .take(4)
        .joinToString("; ")
        .takeIf { it.isNotBlank() }

    return IOException(
        buildString {
            append(message)
            if (details != null) append(": ").append(details)
        },
    )
}
