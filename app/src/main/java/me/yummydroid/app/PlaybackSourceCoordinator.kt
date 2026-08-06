package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedPlayback
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.hasSameVoiceAs
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingVoiceKey

internal data class PlaybackResolution(
    val playback: ResolvedPlayback,
    val manualFallbackNotice: SourceFallbackNotice? = null,
)

internal data class SourceFallbackNotice(
    val selectedVideo: VideoVariant,
    val reason: String,
)

internal data class PlaybackSourceFallbackPlan(
    val excludedSourceKeys: Set<String>,
    val notice: SourceFallbackNotice?,
)

private data class PlaybackCacheKey(
    val animeId: Long,
    val voiceKey: String,
)

private data class PlaybackResolutionContext(
    val cacheKey: PlaybackCacheKey,
    val manualSourceKey: String?,
    val cachedSourceKey: String?,
    val manualCandidates: List<VideoVariant>,
    val metadataCandidates: List<VideoVariant>,
    val orderedCandidates: List<VideoVariant>,
)

internal class PlaybackSourceCoordinator(
    private val resolveLocalStream: suspend (VideoVariant) -> ResolvedVideoStream,
    private val resolveBestPlayback: suspend (
        List<VideoVariant>,
        PreferredQuality,
        List<VideoVariant>,
        Boolean,
    ) -> ResolvedPlayback,
    private val couldNotSelectSourceMessage: () -> String,
    private val noFallbackAfterManualMessage: () -> String,
    private val failureMessage: (Throwable) -> String = Throwable::userMessage,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val failedSourceCooldownMs: Long = PLAYBACK_FAILED_SOURCE_RETRY_COOLDOWN_MS,
) {
    private var failedSourceKeys: Set<String> = emptySet()
    private val failedSourceRetryAfterMs = mutableMapOf<String, Long>()
    private val sourceCache = mutableMapOf<PlaybackCacheKey, String>()
    private val manualSourceOverrides = mutableMapOf<PlaybackCacheKey, String>()

    fun resetRuntime(clearSourceCache: Boolean) {
        failedSourceKeys = emptySet()
        failedSourceRetryAfterMs.clear()
        if (clearSourceCache) sourceCache.clear()
    }

    fun rememberManualSource(video: VideoVariant) {
        val sourceKey = video.sourceSelectionKey.takeIf { it.isNotBlank() } ?: return
        manualSourceOverrides[video.playbackCacheKey()] = sourceKey
    }

    fun candidates(
        requested: VideoVariant,
        allVideos: List<VideoVariant>,
        excludedSourceKeys: Set<String>,
    ): List<VideoVariant> {
        val pool = allVideos.ifEmpty { listOf(requested) }
        val sameEpisode = pool.filter { it.isSameEpisodeAs(requested) }
            .ifEmpty { listOf(requested) }
        val sameVoice = sameEpisode.filter { it.hasSameVoiceAs(requested) }
        return sameVoice
            .ifEmpty { listOf(requested) }
            .filterNot { it.playbackSourceKey in excludedSourceKeys }
            .sortedForPlaybackSource(
                requested = requested,
                manualSourceKey = manualSourceKey(requested),
            )
    }

    fun fallbackPlan(
        currentVideo: VideoVariant,
        failedVideo: VideoVariant,
        failure: PlaybackFailure,
        reason: String,
    ): PlaybackSourceFallbackPlan? {
        val manualSourceKey = manualSourceKey(currentVideo)
        if (!shouldUseAutomaticPlaybackFallback(currentVideo, failedVideo, manualSourceKey, failure)) {
            return null
        }
        val notice = if (currentVideo.isManualPlaybackSource(manualSourceKey)) {
            SourceFallbackNotice(selectedVideo = failedVideo, reason = reason)
        } else {
            null
        }
        markFailed(failedVideo)
        return PlaybackSourceFallbackPlan(
            excludedSourceKeys = blockedSourceKeys(),
            notice = notice,
        )
    }

    fun confirm(currentVideo: VideoVariant, confirmedVideo: VideoVariant): Boolean {
        if (!currentVideo.hasSamePlaybackSourceAs(confirmedVideo)) return false
        if (manualSourceKey(confirmedVideo) == null) {
            sourceCache[confirmedVideo.playbackCacheKey()] = confirmedVideo.sourceSelectionKey
        }
        val sourceKey = confirmedVideo.playbackSourceKey
        failedSourceKeys = failedSourceKeys - sourceKey
        failedSourceRetryAfterMs.remove(sourceKey)
        return true
    }

    suspend fun resolve(
        requested: VideoVariant,
        candidates: List<VideoVariant>,
        preferredQuality: PreferredQuality,
        metadataCandidates: List<VideoVariant> = candidates,
        useCachedSource: Boolean = true,
        fastStart: Boolean = false,
    ): PlaybackResolution {
        if (requested.localPlaybackUrl.isNotBlank()) {
            return PlaybackResolution(
                playback = ResolvedPlayback(
                    video = requested,
                    stream = resolveLocalStream(requested),
                ),
            )
        }

        val context = resolutionContext(
            requested = requested,
            candidates = candidates,
            metadataCandidates = metadataCandidates,
            useCachedSource = useCachedSource,
        )
        return if (fastStart) {
            resolveFastStart(requested, preferredQuality, context)
        } else {
            resolveWithMetadata(requested, preferredQuality, context)
        }
    }

    private fun resolutionContext(
        requested: VideoVariant,
        candidates: List<VideoVariant>,
        metadataCandidates: List<VideoVariant>,
        useCachedSource: Boolean,
    ): PlaybackResolutionContext {
        val sameVoiceCandidates = candidates.filter { it.hasSameVoiceAs(requested) }
        if (sameVoiceCandidates.isEmpty()) {
            throw IllegalStateException("No playback sources for selected voice")
        }
        val sameVoiceMetadataCandidates = metadataCandidates
            .filter { it.hasSameVoiceAs(requested) }
            .ifEmpty { sameVoiceCandidates }
        val cacheKey = requested.playbackCacheKey()
        val manualSourceKey = manualSourceKey(requested)
            ?.takeIf { sourceKey -> sameVoiceCandidates.any { it.matchesSourceSelectionKey(sourceKey) } }
        val cachedSourceKey = sourceCache[cacheKey]
            ?.takeIf { useCachedSource && manualSourceKey == null }
        val validCachedSourceKey = cachedSourceKey
            ?.takeIf { sourceKey -> sameVoiceCandidates.any { it.matchesSourceSelectionKey(sourceKey) } }
        val manualCandidates = manualSourceKey
            ?.let { sourceKey -> sameVoiceCandidates.filter { it.matchesSourceSelectionKey(sourceKey) } }
            .orEmpty()
        return PlaybackResolutionContext(
            cacheKey = cacheKey,
            manualSourceKey = manualSourceKey,
            cachedSourceKey = cachedSourceKey,
            manualCandidates = manualCandidates,
            metadataCandidates = sameVoiceMetadataCandidates,
            orderedCandidates = sameVoiceCandidates.sortedForPlaybackSource(
                requested = requested,
                manualSourceKey = manualSourceKey,
                cachedSourceKey = validCachedSourceKey,
            ),
        )
    }

    private suspend fun resolveFastStart(
        requested: VideoVariant,
        preferredQuality: PreferredQuality,
        context: PlaybackResolutionContext,
    ): PlaybackResolution {
        val failures = mutableListOf<Throwable>()
        var manualFailure: Throwable? = null
        val selectedManualVideo = context.manualCandidates.firstOrNull() ?: requested

        context.orderedCandidates.fastStartResolutionGroups(context.manualSourceKey).forEach { group ->
            val isManualGroup = context.manualSourceKey != null &&
                group.any { it.matchesSourceSelectionKey(context.manualSourceKey) }
            val playback = resolveFastStartGroup(
                candidates = group,
                preferredQuality = preferredQuality,
                failures = failures,
                onFailure = { throwable -> if (isManualGroup) manualFailure = throwable },
            ) ?: return@forEach

            invalidateChangedCachedSource(context, playback.video)
            return PlaybackResolution(
                playback = playback,
                manualFallbackNotice = manualFallbackNotice(
                    manualFailure = manualFailure,
                    manualSourceKey = context.manualSourceKey,
                    selectedManualVideo = selectedManualVideo,
                    resolvedVideo = playback.video,
                ),
            )
        }
        sourceCache.remove(context.cacheKey)
        throw failures.firstOrNull() ?: IllegalStateException(couldNotSelectSourceMessage())
    }

    private suspend fun resolveFastStartGroup(
        candidates: List<VideoVariant>,
        preferredQuality: PreferredQuality,
        failures: MutableList<Throwable>,
        onFailure: (Throwable) -> Unit,
    ): ResolvedPlayback? {
        for (candidate in candidates) {
            val result = resolveCatching {
                resolveBestPlayback(
                    listOf(candidate),
                    preferredQuality,
                    emptyList(),
                    false,
                )
            }
            result.getOrNull()?.let { return it }
            result.exceptionOrNull()?.let { throwable ->
                failures += throwable
                onFailure(throwable)
            }
        }
        return null
    }

    private suspend fun resolveWithMetadata(
        requested: VideoVariant,
        preferredQuality: PreferredQuality,
        context: PlaybackResolutionContext,
    ): PlaybackResolution {
        val manualResult = context.manualCandidates
            .takeIf { it.isNotEmpty() }
            ?.let { candidates ->
                resolveCandidates(candidates, preferredQuality, context.metadataCandidates)
            }
        manualResult?.getOrNull()?.let { playback ->
            return PlaybackResolution(playback = playback)
        }

        val automaticCandidates = context.orderedCandidates.filterNot { candidate ->
            context.manualSourceKey != null && candidate.matchesSourceSelectionKey(context.manualSourceKey)
        }
        if (automaticCandidates.isEmpty()) {
            throw manualResult?.exceptionOrNull()
                ?: IllegalStateException(noFallbackAfterManualMessage())
        }

        val automaticResult = resolveCandidates(
            candidates = automaticCandidates,
            preferredQuality = preferredQuality,
            metadataCandidates = context.metadataCandidates,
        )
        automaticResult.getOrNull()?.let { playback ->
            invalidateChangedCachedSource(context, playback.video)
            return PlaybackResolution(
                playback = playback,
                manualFallbackNotice = manualFallbackNotice(
                    manualFailure = manualResult?.exceptionOrNull(),
                    manualSourceKey = context.manualSourceKey,
                    selectedManualVideo = context.manualCandidates.firstOrNull() ?: requested,
                    resolvedVideo = playback.video,
                ),
            )
        }

        sourceCache.remove(context.cacheKey)
        throw automaticResult.exceptionOrNull() ?: IllegalStateException(couldNotSelectSourceMessage())
    }

    private suspend fun resolveCandidates(
        candidates: List<VideoVariant>,
        preferredQuality: PreferredQuality,
        metadataCandidates: List<VideoVariant>,
    ): Result<ResolvedPlayback> {
        return resolveCatching {
            resolveBestPlayback(
                candidates,
                preferredQuality,
                metadataCandidates,
                true,
            )
        }
    }

    private fun manualFallbackNotice(
        manualFailure: Throwable?,
        manualSourceKey: String?,
        selectedManualVideo: VideoVariant,
        resolvedVideo: VideoVariant,
    ): SourceFallbackNotice? {
        if (manualFailure == null || manualSourceKey == null) return null
        if (resolvedVideo.matchesSourceSelectionKey(manualSourceKey)) return null
        return SourceFallbackNotice(
            selectedVideo = selectedManualVideo,
            reason = failureMessage(manualFailure),
        )
    }

    private fun manualSourceKey(video: VideoVariant): String? {
        return manualSourceOverrides[video.playbackCacheKey()]
            ?.takeIf { it.isNotBlank() }
    }

    private fun markFailed(video: VideoVariant) {
        val sourceKey = video.playbackSourceKey
        failedSourceKeys = failedSourceKeys + sourceKey
        failedSourceRetryAfterMs[sourceKey] = clockMs() + failedSourceCooldownMs
        removeCachedSource(video)
    }

    private fun blockedSourceKeys(): Set<String> {
        val nowMs = clockMs()
        val expiredSourceKeys = failedSourceKeys.filter { sourceKey ->
            val retryAfterMs = failedSourceRetryAfterMs[sourceKey]
            retryAfterMs == null || nowMs >= retryAfterMs
        }
        failedSourceKeys = failedSourceKeys - expiredSourceKeys.toSet()
        expiredSourceKeys.forEach(failedSourceRetryAfterMs::remove)
        return failedSourceKeys
    }

    private fun invalidateChangedCachedSource(
        context: PlaybackResolutionContext,
        resolvedVideo: VideoVariant,
    ) {
        val cachedSourceKey = context.cachedSourceKey ?: return
        if (!resolvedVideo.matchesSourceSelectionKey(cachedSourceKey)) {
            sourceCache.remove(context.cacheKey)
        }
    }

    private fun removeCachedSource(video: VideoVariant) {
        val cacheKey = video.playbackCacheKey()
        if (video.matchesSourceSelectionKey(sourceCache[cacheKey])) {
            sourceCache.remove(cacheKey)
        }
    }

    private suspend fun <T> resolveCatching(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            Result.failure(throwable)
        }
    }
}

private fun VideoVariant.playbackCacheKey(): PlaybackCacheKey {
    return PlaybackCacheKey(animeId = animeId, voiceKey = matchingVoiceKey)
}

private fun List<VideoVariant>.fastStartResolutionGroups(
    manualSourceKey: String?,
): List<List<VideoVariant>> {
    val uniqueCandidates = distinctBy { it.playbackSourceKey }
    val manualCandidates = manualSourceKey
        ?.let { sourceKey -> uniqueCandidates.filter { it.matchesSourceSelectionKey(sourceKey) } }
        .orEmpty()
    val automaticCandidates = if (manualCandidates.isEmpty()) {
        uniqueCandidates
    } else {
        uniqueCandidates.filterNot { candidate -> candidate.matchesSourceSelectionKey(manualSourceKey) }
    }
    val automaticGroups = automaticCandidates.groupByEstimatedQuality()
    return if (manualCandidates.isEmpty()) automaticGroups else listOf(manualCandidates) + automaticGroups
}

private fun List<VideoVariant>.groupByEstimatedQuality(): List<List<VideoVariant>> {
    return chunkedBy { candidate -> candidate.estimatedSourceMaxVideoHeight() }
}

private fun <T, K> List<T>.chunkedBy(keyOf: (T) -> K): List<List<T>> {
    if (isEmpty()) return emptyList()
    val groups = mutableListOf<MutableList<T>>()
    var activeKey: K? = null
    forEach { item ->
        val key = keyOf(item)
        if (groups.isEmpty() || activeKey != key) {
            groups += mutableListOf(item)
            activeKey = key
        } else {
            groups.last() += item
        }
    }
    return groups
}
