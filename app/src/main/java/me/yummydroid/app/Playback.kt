package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedPlayback
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.episodeOrderValue
import me.yummydroid.app.data.hasSameVoiceAs
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey

// PlaybackSessionCoordinator
internal data class PlaybackSessionRequest(
    val video: VideoVariant,
    val title: String,
    val excludedSourceKeys: Set<String>,
    val startPositionMs: Long,
    val preferredQuality: PreferredQuality,
    val resumeChoicePositionMs: Long? = null,
    val sourceFallbackNotice: SourceFallbackNotice? = null,
    val voiceFallbackFromVideo: VideoVariant? = null,
    val lockPlaybackSource: Boolean = false,
    val playWhenReady: Boolean = true,
)

private data class PlaybackRouteTarget(
    val video: VideoVariant,
    val title: String,
    val preferredQuality: PreferredQuality,
)

private data class PlaybackMetadataTarget(
    val video: VideoVariant,
    val title: String,
    val preferredQuality: PreferredQuality,
    val streamUrl: String,
)

private data class PlaybackStateUpdate(
    val state: YummyDroidUiState,
    val accepted: Boolean,
)

internal class PlaybackSessionCoordinator(
    private val scope: CoroutineScope,
    private val sourceCoordinator: PlaybackSourceCoordinator,
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val resolvePlaybackMetadata: suspend (
        ResolvedPlayback,
        List<VideoVariant>,
        PreferredQuality,
    ) -> ResolvedPlayback,
    private val cachedSiteBaseUrl: () -> String,
    private val offlineUnavailableMessage: () -> String,
    private val sourceResolveTimeoutMessage: () -> String,
    private val onFallbackNotice: (SourceFallbackNotice, VideoVariant) -> Unit,
    private val onVoiceFallbackNotice: (VideoVariant, VideoVariant) -> Unit,
    private val onMetadataFailure: (Throwable) -> Unit,
    private val sourceResolveTimeoutMs: Long = PLAYBACK_SOURCE_RESOLVE_TIMEOUT_MS,
) {
    private val loadOperations = LatestStateOperationCoordinator()
    private val metadataOperations = LatestStateOperationCoordinator()

    fun play(request: PlaybackSessionRequest) {
        cancelMetadataLoad()
        val normalizedRequest = request.normalized()
        val forcedOfflineMode = currentState().forcedOfflineMode
        updateState { state -> state.withStartedPlayback(normalizedRequest) }
        loadOperations.launchLatest(scope) { lease ->
            load(normalizedRequest, forcedOfflineMode, lease)
        }
    }

    fun resetRuntime(clearSourceCache: Boolean) {
        loadOperations.cancel()
        cancelMetadataLoad()
        sourceCoordinator.resetRuntime(clearSourceCache = clearSourceCache)
    }

    fun rememberManualSource(video: VideoVariant) {
        sourceCoordinator.rememberManualSource(video)
    }

    fun fallbackPlan(
        currentVideo: VideoVariant,
        failedVideo: VideoVariant,
        failure: PlaybackFailure,
        reason: String,
        allVideos: List<VideoVariant>,
        preferredQuality: PreferredQuality,
        currentStream: ResolvedVideoStream?,
    ): PlaybackSourceFallbackPlan? {
        return sourceCoordinator.fallbackPlan(
            currentVideo = currentVideo,
            failedVideo = failedVideo,
            failure = failure,
            reason = reason,
            allVideos = allVideos,
            preferredQuality = preferredQuality,
            currentStream = currentStream,
        )
    }

    fun confirm(currentVideo: VideoVariant, confirmedVideo: VideoVariant): Boolean {
        return sourceCoordinator.confirm(currentVideo, confirmedVideo)
    }

    fun cancelMetadataLoad() {
        metadataOperations.cancel()
        updateState { state ->
            if (state.playbackMetadataLoading) {
                state.copy(playbackMetadataLoading = false)
            } else {
                state
            }
        }
    }

    fun reportCurrentPlaybackFailure(message: String) {
        val route = currentState().route as? AppRoute.Player ?: return
        updateState { state ->
            state.withPlaybackFailure(
                target = PlaybackRouteTarget(
                    video = route.video,
                    title = route.animeTitle,
                    preferredQuality = route.preferredQuality,
                ),
                message = message,
            )
        }
    }

    private suspend fun load(
        request: PlaybackSessionRequest,
        forcedOfflineMode: Boolean,
        lease: StateOperationLease,
    ) {
        val allVideos = candidatePool(request.video)
        if (!lease.isCurrent) return
        val metadataCandidates = sourceCoordinator.candidates(
            requested = request.video,
            allVideos = allVideos,
            excludedSourceKeys = emptySet(),
        ).let { candidates ->
            if (forcedOfflineMode) candidates.filter(VideoVariant::isOfflineAvailable) else candidates
        }
        val candidates = metadataCandidates
            .filterNot { it.playbackSourceKey in request.excludedSourceKeys }
            .lockedToSourceWhenRequested(request)
        if (forcedOfflineMode && candidates.isEmpty()) {
            if (lease.isCurrent) {
                updateState { state -> state.withOfflinePlaybackUnavailable(offlineUnavailableMessage()) }
            }
            return
        }

        val routeVideo = request.routeVideo(candidates, forcedOfflineMode)
        if (routeVideo != request.video) {
            updateState { state -> state.withPlaybackRouteVideo(request, routeVideo) }
        }
        resolve(request, routeVideo, candidates, metadataCandidates, lease)
    }

    private suspend fun resolve(
        request: PlaybackSessionRequest,
        routeVideo: VideoVariant,
        candidates: List<VideoVariant>,
        metadataCandidates: List<VideoVariant>,
        lease: StateOperationLease,
    ) {
        runCatching {
            resolvePlaybackSource(routeVideo, candidates, request.preferredQuality, metadataCandidates)
        }.onSuccess { resolution ->
            if (lease.isCurrent) acceptResolution(request, routeVideo, resolution, metadataCandidates)
        }.onFailure { throwable ->
            if (throwable is CancellationException && throwable !is TimeoutCancellationException) throw throwable
            if (!lease.isCurrent) return@onFailure
            val target = request.routeTarget(routeVideo)
            val message = if (throwable is TimeoutCancellationException) {
                sourceResolveTimeoutMessage()
            } else {
                throwable.userMessage()
            }
            updateState { state -> state.withPlaybackFailure(target, message) }
        }
    }

    private suspend fun resolvePlaybackSource(
        routeVideo: VideoVariant,
        candidates: List<VideoVariant>,
        preferredQuality: PreferredQuality,
        metadataCandidates: List<VideoVariant>,
    ): PlaybackResolution {
        val resolveBlock: suspend () -> PlaybackResolution = {
            sourceCoordinator.resolve(
                requested = routeVideo,
                candidates = candidates,
                preferredQuality = preferredQuality,
                metadataCandidates = metadataCandidates,
                fastStart = true,
            )
        }
        return if (sourceResolveTimeoutMs > 0L) {
            withTimeout(sourceResolveTimeoutMs) { resolveBlock() }
        } else {
            resolveBlock()
        }
    }

    private fun acceptResolution(
        request: PlaybackSessionRequest,
        routeVideo: VideoVariant,
        resolution: PlaybackResolution,
        metadataCandidates: List<VideoVariant>,
    ) {
        val playback = resolution.playback
        val target = request.routeTarget(routeVideo)
        var accepted = false
        updateState { state ->
            state.withResolvedPlayback(target, playback, cachedSiteBaseUrl).also { result ->
                accepted = result.accepted
            }.state
        }
        if (!accepted) return

        val fallbackNotice = resolution.manualFallbackNotice ?: request.sourceFallbackNotice
        fallbackNotice?.let { onFallbackNotice(it, playback.video) }
        request.voiceFallbackFromVideo
            ?.takeIf { previousVideo -> !playback.video.hasSameVoiceAs(previousVideo) }
            ?.let { previousVideo -> onVoiceFallbackNotice(previousVideo, playback.video) }
        startMetadataLoad(
            playback = playback,
            title = request.title,
            preferredQuality = request.preferredQuality,
            metadataCandidates = metadataCandidates,
        )
    }

    private fun candidatePool(video: VideoVariant): List<VideoVariant> {
        val stateVideos = currentState().videos.readyListOrEmpty()
        val stateAnimeVideos = stateVideos.filter { it.animeId == video.animeId }
        val hasUsableStatePool = stateAnimeVideos.size > 1 &&
            stateAnimeVideos.any { it.isSameEpisodeAs(video) && it.hasSameVoiceAs(video) }
        if (hasUsableStatePool) return stateAnimeVideos
        return stateAnimeVideos.ifEmpty { stateVideos.ifEmpty { listOf(video) } }
    }

    private fun startMetadataLoad(
        playback: ResolvedPlayback,
        title: String,
        preferredQuality: PreferredQuality,
        metadataCandidates: List<VideoVariant>,
    ) {
        val target = PlaybackMetadataTarget(
            video = playback.video,
            title = title,
            preferredQuality = preferredQuality,
            streamUrl = playback.stream.url,
        )
        setMetadataLoading(target, loading = true)
        metadataOperations.launchLatest(scope) { lease ->
            try {
                val enrichedPlayback = resolvePlaybackMetadata(
                    playback,
                    metadataCandidates,
                    preferredQuality,
                )
                if (!lease.isCurrent) return@launchLatest
                updateState { state ->
                    state.withPlaybackMetadata(target, enrichedPlayback, cachedSiteBaseUrl)
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                if (lease.isCurrent) onMetadataFailure(throwable)
            } finally {
                if (lease.isCurrent) {
                    setMetadataLoading(target, loading = false)
                }
            }
        }
    }

    private fun setMetadataLoading(target: PlaybackMetadataTarget, loading: Boolean) {
        updateState { state -> state.withPlaybackMetadataLoading(target, loading) }
    }
}

private fun PlaybackSessionRequest.normalized(): PlaybackSessionRequest {
    return copy(
        startPositionMs = startPositionMs.coerceAtLeast(0L),
        resumeChoicePositionMs = resumeChoicePositionMs?.takeIf { it > 0L },
    )
}

private fun PlaybackSessionRequest.routeTarget(video: VideoVariant): PlaybackRouteTarget {
    return PlaybackRouteTarget(
        video = video,
        title = title,
        preferredQuality = preferredQuality,
    )
}

private fun PlaybackSessionRequest.routeVideo(
    candidates: List<VideoVariant>,
    forcedOfflineMode: Boolean,
): VideoVariant {
    return if (forcedOfflineMode && !video.isOfflineAvailable) candidates.first() else video
}

private fun List<VideoVariant>.lockedToSourceWhenRequested(request: PlaybackSessionRequest): List<VideoVariant> {
    if (!request.lockPlaybackSource) return this
    return filter { it.hasSamePlaybackSourceAs(request.video) }.ifEmpty { listOf(request.video) }
}

private fun YummyDroidUiState.withStartedPlayback(request: PlaybackSessionRequest): YummyDroidUiState {
    return copy(
        route = AppRoute.Player(
            video = request.video,
            animeTitle = request.title,
            startPositionMs = request.startPositionMs,
            preferredQuality = request.preferredQuality,
            resumeChoicePositionMs = request.resumeChoicePositionMs,
            playWhenReady = request.playWhenReady,
        ),
        navigationBackStack = navigationStackAfterOptionalPush(route !is AppRoute.Player),
        playerStream = LoadState.Loading,
        playbackMetadataLoading = false,
    )
}

private fun YummyDroidUiState.withOfflinePlaybackUnavailable(message: String): YummyDroidUiState {
    return copy(
        offlineDownload = OfflineDownloadUiState(
            isRunning = false,
            message = message,
        ),
    )
}

private fun YummyDroidUiState.withPlaybackRouteVideo(
    request: PlaybackSessionRequest,
    routeVideo: VideoVariant,
): YummyDroidUiState {
    val playerRoute = route as? AppRoute.Player ?: return this
    return if (playerRoute.video == request.video && playerRoute.animeTitle == request.title) {
        copy(route = playerRoute.copy(video = routeVideo))
    } else {
        this
    }
}

private fun YummyDroidUiState.withPlaybackVideos(
    animeId: Long,
    loadedVideos: List<VideoVariant>,
): YummyDroidUiState {
    val playerRoute = route as? AppRoute.Player ?: return this
    return if (playerRoute.video.animeId == animeId) {
        copy(videos = LoadState.Ready(loadedVideos))
    } else {
        this
    }
}

private fun YummyDroidUiState.withResolvedPlayback(
    target: PlaybackRouteTarget,
    playback: ResolvedPlayback,
    cachedSiteBaseUrl: () -> String,
): PlaybackStateUpdate {
    val playerRoute = route as? AppRoute.Player
    if (playerRoute?.matches(target) != true) return PlaybackStateUpdate(this, accepted = false)
    return PlaybackStateUpdate(
        state = copy(
            route = playerRoute.copy(video = playback.video),
            siteBaseUrl = cachedSiteBaseUrl(),
            selectedVideoGroup = playback.video.groupKey,
            playerStream = LoadState.Ready(playback.stream),
            playbackMetadataLoading = false,
        ),
        accepted = true,
    )
}

private fun YummyDroidUiState.withPlaybackFailure(
    target: PlaybackRouteTarget,
    message: String,
): YummyDroidUiState {
    val playerRoute = route as? AppRoute.Player
    return if (playerRoute?.matches(target) == true) {
        copy(
            playerStream = LoadState.Error(message),
            playbackMetadataLoading = false,
        )
    } else {
        this
    }
}

private fun AppRoute.Player.matches(target: PlaybackRouteTarget): Boolean {
    return video == target.video &&
        animeTitle == target.title &&
        preferredQuality == target.preferredQuality
}

private fun YummyDroidUiState.withPlaybackMetadataLoading(
    target: PlaybackMetadataTarget,
    loading: Boolean,
): YummyDroidUiState {
    if (!matches(target)) return this
    return if (playbackMetadataLoading == loading) this else copy(playbackMetadataLoading = loading)
}

private fun YummyDroidUiState.withPlaybackMetadata(
    target: PlaybackMetadataTarget,
    playback: ResolvedPlayback,
    cachedSiteBaseUrl: () -> String,
): YummyDroidUiState {
    val playerRoute = route as? AppRoute.Player ?: return this
    val activeStream = playerStream.readyDataOrNull() ?: return this
    if (!playerRoute.matches(target, activeStream)) return this
    if (playback.video == playerRoute.video && playback.stream == activeStream) {
        return copy(playbackMetadataLoading = false)
    }
    return copy(
        route = playerRoute.copy(video = playback.video),
        siteBaseUrl = cachedSiteBaseUrl(),
        selectedVideoGroup = playback.video.groupKey,
        playerStream = LoadState.Ready(playback.stream),
        playbackMetadataLoading = false,
    )
}

private fun YummyDroidUiState.matches(target: PlaybackMetadataTarget): Boolean {
    val playerRoute = route as? AppRoute.Player ?: return false
    val activeStream = playerStream.readyDataOrNull() ?: return false
    return playerRoute.matches(target, activeStream)
}

private fun AppRoute.Player.matches(
    target: PlaybackMetadataTarget,
    activeStream: ResolvedVideoStream,
): Boolean {
    return animeTitle == target.title &&
        preferredQuality == target.preferredQuality &&
        video.isSameEpisodeAs(target.video) &&
        video.hasSameVoiceAs(target.video) &&
        video.hasSamePlaybackSourceAs(target.video) &&
        activeStream.url == target.streamUrl
}

// PlaybackSourceCoordinator
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
    val targetVideo: VideoVariant? = null,
    val voiceFallbackFromVideo: VideoVariant? = null,
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
        allVideos: List<VideoVariant>,
        preferredQuality: PreferredQuality,
        currentStream: ResolvedVideoStream?,
    ): PlaybackSourceFallbackPlan? {
        val manualSourceKey = manualSourceKey(currentVideo)
        if (currentVideo.isManualPlaybackSource(manualSourceKey)) return null
        val decision = automaticPlaybackFallbackDecision(
            currentVideo = currentVideo,
            failedVideo = failedVideo,
            failure = failure,
            allVideos = allVideos,
            preferredQuality = preferredQuality,
            currentStream = currentStream,
            blockedSourceKeys = blockedSourceKeys(),
        ) ?: return null
        val shouldShowSourceNotice = decision.voiceFallbackFromVideo == null &&
            (currentVideo.isManualPlaybackSource(manualSourceKey) || failure.kind.shouldShowSourceFallbackNotice())
        val notice = if (shouldShowSourceNotice) {
            SourceFallbackNotice(selectedVideo = failedVideo, reason = reason)
        } else {
            null
        }
        markFailed(failedVideo)
        return PlaybackSourceFallbackPlan(
            excludedSourceKeys = decision.excludedSourceKeys,
            notice = notice,
            targetVideo = decision.targetVideo,
            voiceFallbackFromVideo = decision.voiceFallbackFromVideo,
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
        val manualCandidates = context.manualCandidates
        if (manualCandidates.isNotEmpty()) {
            return resolveFastStartGroup(
                candidates = manualCandidates,
                preferredQuality = preferredQuality,
                failures = mutableListOf(),
                onFailure = {},
            )?.let { playback ->
                PlaybackResolution(playback = playback)
            } ?: throw IllegalStateException(noFallbackAfterManualMessage())
        }

        val failures = mutableListOf<Throwable>()

        context.orderedCandidates.fastStartResolutionGroups(context.manualSourceKey).forEach { group ->
            val playback = resolveFastStartGroup(
                candidates = group,
                preferredQuality = preferredQuality,
                failures = failures,
                onFailure = {},
            ) ?: return@forEach

            invalidateChangedCachedSource(context, playback.video)
            return PlaybackResolution(
                playback = playback,
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
        if (context.manualCandidates.isNotEmpty()) {
            throw manualResult?.exceptionOrNull()
                ?: IllegalStateException(noFallbackAfterManualMessage())
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

private fun PlaybackFailureKind.shouldShowSourceFallbackNotice(): Boolean {
    return this == PlaybackFailureKind.BufferingTimeout ||
        this == PlaybackFailureKind.SourceUnavailable
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

// PlaybackSourceState
internal fun Throwable.userMessage(): String {
    return message?.takeIf { it.isNotBlank() } ?: "Could not load data"
}

private const val PLAYBACK_SOURCE_RESOLVE_TIMEOUT_MS = 30_000L

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
