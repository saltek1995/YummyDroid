package me.yummydroid.app.data

import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

internal suspend fun YummyAnimeRepository.repositoryResolveVideoStream(
    video: VideoVariant,
    preferredQuality: PreferredQuality,
    waitForRuntimeSubtitles: Boolean,
): ResolvedVideoStream {
    val localFile = video.primaryOfflineFile()
    if (localFile != null) {
        return ResolvedVideoStream(
            url = localFile.playbackUrl,
            mimeType = localFile.mimeType,
            headers = emptyMap(),
            maxVideoHeight = null,
        )
    }
    return videoStreamResolver.resolve(
        video = video,
        preferredQuality = preferredQuality,
        waitForRuntimeSubtitles = waitForRuntimeSubtitles,
    ).also { stream ->
        withContext(Dispatchers.IO) {
            runCatching { sourceQualityCache?.save(video, stream) }
        }
    }
}

internal suspend fun YummyAnimeRepository.repositoryResolveAvailableDownloadQualities(
    requested: VideoVariant,
    videos: List<VideoVariant>,
    allEpisodes: Boolean,
): List<PreferredQuality> = withContext(Dispatchers.IO) {
    val candidates = applyCachedSourceQualities(
        videos.downloadQualityCandidatesFor(requested, allEpisodes)
            .map { it.withoutOfflinePlayback() },
    ).distinctBy { it.sourceResolveIdentity() }
    if (candidates.isEmpty()) return@withContext emptyList()

    val heights = repositoryResolveSourceQualityResults(candidates)
        .availableDownloadHeights(allEpisodes)

    PreferredQuality.entries
        .asSequence()
        .filter { it.height != null && it.height in heights }
        .sortedByDescending { it.height ?: 0 }
        .toList()
}

internal suspend fun YummyAnimeRepository.repositoryResolveSampledDownloadQualities(
    voiceKeys: Set<String>,
    videos: List<VideoVariant>,
): Map<String, List<PreferredQuality>> = withContext(Dispatchers.IO) {
    val requestedVoiceKeys = voiceKeys.filter { it.isNotBlank() }.toSet()
    if (requestedVoiceKeys.isEmpty()) return@withContext emptyMap()
    val candidates = applyCachedSourceQualities(
        videos
            .asSequence()
            .filter { it.downloadSampleVoiceKey in requestedVoiceKeys }
            .groupBy {
                "${it.downloadSampleVoiceKey}|${it.player.cleanVideoSourceLabel().lowercase(Locale.ROOT)}"
            }
            .values
            .mapNotNull { group -> group.selectDownloadQualitySampleCandidate() }
            .map { it.withoutOfflinePlayback() },
    ).distinctBy { it.sourceResolveIdentity() }
    if (candidates.isEmpty()) return@withContext emptyMap()

    repositoryResolveSourceQualityResults(candidates)
        .groupBy { result -> result.candidate.downloadSampleVoiceKey }
        .mapValues { (_, results) ->
            results
                .flatMap { it.qualities }
                .normalizedSourceQualities()
                .mapNotNull { quality -> quality.height }
                .distinct()
                .sortedDescending()
                .mapNotNull { height -> PreferredQuality.fromHeight(height) }
        }
        .filterValues { qualities -> qualities.isNotEmpty() }
}

private suspend fun YummyAnimeRepository.repositoryResolveSourceQualityResults(
    candidates: List<VideoVariant>,
): List<SourceQualityResolveResult> {
    val knownQualities = candidates.map { candidate ->
        SourceQualityResolveResult(candidate, candidate.sourceQualities)
    }
    val missingCandidates = candidates.filter { it.sourceQualities.isEmpty() }
    val resolvedQualities = supervisorScope {
        missingCandidates.map { candidate ->
            async {
                runCatching {
                    withTimeout(candidate.sourceResolveTimeoutMs()) {
                        SourceQualityResolveResult(
                            candidate,
                            repositoryResolveVideoStream(
                                video = candidate,
                                preferredQuality = PreferredQuality.Auto,
                                waitForRuntimeSubtitles = true,
                            ).availableQualities,
                        )
                    }
                }.getOrElse {
                    sourceQualityCache?.remove(candidate)
                    SourceQualityResolveResult(candidate, emptyList())
                }
            }
        }.awaitAll()
    }
    return knownQualities + resolvedQualities
}

internal suspend fun YummyAnimeRepository.repositoryResolveBestPlaybackSource(
    candidates: List<VideoVariant>,
    preferredQuality: PreferredQuality,
    metadataCandidates: List<VideoVariant>,
    waitForRuntimeSubtitles: Boolean,
): ResolvedPlayback {
    val uniqueCandidates = candidates.distinctBy { it.sourceResolveIdentity() }.ifEmpty {
        throw IOException("No sources are available for the episode")
    }

    val selectableKeys = uniqueCandidates.mapTo(mutableSetOf()) { it.sourceResolveIdentity() }
    val uniqueMetadataCandidates = (uniqueCandidates + metadataCandidates)
        .distinctBy { it.sourceResolveIdentity() }

    val attempts = repositoryResolveCandidateAttempts(
        candidates = uniqueMetadataCandidates,
        preferredQuality = preferredQuality,
        waitForRuntimeSubtitles = waitForRuntimeSubtitles,
    )
    val best = attempts.bestPlayback(selectableKeys)
    if (best != null) return best.withMetadataFromAttempts(attempts)

    throw attempts.resolveFailure("Could not start any episode source")
}

internal suspend fun YummyAnimeRepository.repositoryResolvePlaybackMetadata(
    playback: ResolvedPlayback,
    metadataCandidates: List<VideoVariant>,
    preferredQuality: PreferredQuality,
): ResolvedPlayback {
    val candidates = (listOf(playback.video) + metadataCandidates)
        .filter { candidate ->
            candidate.isSameEpisodeAs(playback.video) && candidate.hasSameVoiceAs(playback.video)
        }
        .distinctBy { it.sourceResolveIdentity() }
        .ifEmpty { return playback }
    val attempts = repositoryResolveCandidateAttempts(
        candidates = candidates,
        preferredQuality = preferredQuality,
        waitForRuntimeSubtitles = true,
    )
    return playback.withMetadataFromAttempts(
        attempts + SourceResolveAttempt(
            index = -1,
            candidate = playback.video,
            playback = playback,
        ),
    )
}

internal suspend fun YummyAnimeRepository.repositoryResolveDownloadPlaybacks(
    requested: VideoVariant,
    videos: List<VideoVariant>,
    preferredQuality: PreferredQuality,
): List<ResolvedPlayback> {
    val uniqueCandidates = applyCachedSourceQualities(
        videos.downloadCandidatesFor(requested)
            .map { it.withoutOfflinePlayback() },
    ).distinctBy { it.sourceResolveIdentity() }
        .ifEmpty {
            throw IOException("No online sources are available for downloading this episode")
        }

    val attempts = repositoryResolveCandidateAttempts(uniqueCandidates, preferredQuality)
    val playbacks = attempts.downloadPlaybacks(preferredQuality)
    if (playbacks.isNotEmpty()) return playbacks

    val requestedHeight = preferredQuality.height
    if (requestedHeight != null && attempts.any { it.playback != null }) {
        throw IOException(
            "No working source with ${preferredQuality.title} quality is available for download",
        )
    }
    throw attempts.resolveFailure("Could not find a working source for download")
}

private suspend fun YummyAnimeRepository.repositoryResolveCandidateAttempts(
    candidates: List<VideoVariant>,
    preferredQuality: PreferredQuality,
    waitForRuntimeSubtitles: Boolean = true,
): List<SourceResolveAttempt> {
    return supervisorScope {
        candidates.mapIndexed { index, candidate ->
            async {
                runCatching {
                    withTimeout(candidate.sourceResolveTimeoutMs()) {
                        videoStreamResolver.resolve(
                            video = candidate,
                            preferredQuality = preferredQuality,
                            waitForRuntimeSubtitles = waitForRuntimeSubtitles,
                        )
                    }
                }.fold(
                    onSuccess = { stream ->
                        withContext(Dispatchers.IO) {
                            runCatching { sourceQualityCache?.save(candidate, stream) }
                        }
                        val playback = ResolvedPlayback(
                            video = candidate,
                            stream = stream.withSourceSubtitleVideo(candidate),
                        )
                        SourceResolveAttempt(
                            index = index,
                            candidate = candidate,
                            playback = playback,
                        )
                    },
                    onFailure = { throwable ->
                        SourceResolveAttempt(
                            index = index,
                            candidate = candidate,
                            failure = throwable,
                        )
                    },
                )
            }
        }.awaitAll()
    }
}
