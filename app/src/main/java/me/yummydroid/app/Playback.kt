package me.yummydroid.app

import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.HistoryAnimeCacheStorage
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PlaybackProgressStorage
import me.yummydroid.app.data.PlaybackSelection
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedPlayback
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.YummyAnimeRepository
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.distinctLatestByEpisode
import me.yummydroid.app.data.forOfflineQuality
import me.yummydroid.app.data.episodeOrderValue
import me.yummydroid.app.data.hasSameVoiceAs
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.matchingVoiceTitle
import me.yummydroid.app.data.sourceResolutionHeight
import me.yummydroid.app.data.toAnimeSummary

internal fun VideoVariant.toPlaybackSelection(updatedAtMs: Long): PlaybackSelection {
    return PlaybackSelection(
        animeId = animeId,
        groupKey = groupKey,
        voiceKey = matchingVoiceKey,
        sourceKey = sourceSelectionKey,
        updatedAtMs = updatedAtMs,
    )
}

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

internal enum class PlaybackFailureOutcome {
    Ignored,
    Recovering,
    Failed,
}

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

    fun handlePlaybackFailure(
        failedVideo: VideoVariant,
        playbackPositionMs: Long,
        failure: PlaybackFailure,
        reason: String,
    ): PlaybackFailureOutcome {
        val state = currentState()
        val route = state.route as? AppRoute.Player ?: return PlaybackFailureOutcome.Ignored
        if (!route.video.hasSamePlaybackSourceAs(failedVideo)) return PlaybackFailureOutcome.Ignored
        if (state.playerStream !is LoadState.Ready) return PlaybackFailureOutcome.Ignored

        val plan = sourceCoordinator.fallbackPlan(
            currentVideo = route.video,
            failedVideo = failedVideo,
            failure = failure,
            reason = reason,
            allVideos = state.videos.readyListOrEmpty(),
            preferredQuality = route.preferredQuality,
            currentStream = state.playerStream.data,
        ) ?: run {
            cancelMetadataLoad()
            reportCurrentPlaybackFailure(reason)
            return PlaybackFailureOutcome.Failed
        }
        play(
            PlaybackSessionRequest(
                video = plan.targetVideo ?: route.video,
                title = route.animeTitle,
                excludedSourceKeys = plan.excludedSourceKeys,
                startPositionMs = playbackPositionMs.takeIf { it > 0L } ?: route.startPositionMs,
                preferredQuality = route.preferredQuality,
                sourceFallbackNotice = plan.notice,
                voiceFallbackFromVideo = plan.voiceFallbackFromVideo,
            ),
        )
        return PlaybackFailureOutcome.Recovering
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

    private fun reportCurrentPlaybackFailure(message: String) {
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
        playerStream = LoadState.Error(message),
        playbackMetadataLoading = false,
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
    private val readPlaybackSelection: (Long) -> PlaybackSelection? = { null },
    private val savePlaybackSelection: (PlaybackSelection) -> Unit = {},
    private val failureMessage: (Throwable) -> String = Throwable::userMessage,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val failedSourceCooldownMs: Long = PLAYBACK_FAILED_SOURCE_RETRY_COOLDOWN_MS,
) {
    private var failedSourceKeys: Set<String> = emptySet()
    private val failedSourceRetryAfterMs = mutableMapOf<String, Long>()
    private val sourceCache = mutableMapOf<PlaybackCacheKey, String>()
    private val manualSourceOverrides = mutableMapOf<PlaybackCacheKey, String>()
    private val playbackSelectionCache = mutableMapOf<Long, PlaybackSelection?>()

    fun resetRuntime(clearSourceCache: Boolean) {
        failedSourceKeys = emptySet()
        failedSourceRetryAfterMs.clear()
        if (clearSourceCache) sourceCache.clear()
    }

    fun rememberManualSource(video: VideoVariant) {
        val sourceKey = video.sourceSelectionKey.takeIf { it.isNotBlank() } ?: return
        manualSourceOverrides[video.playbackCacheKey()] = sourceKey
        val selection = video.toPlaybackSelection(updatedAtMs = clockMs())
        playbackSelectionCache[video.animeId] = selection
        savePlaybackSelection(selection)
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
            failure.kind.shouldShowSourceFallbackNotice()
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
        if (requested.isOfflineAvailable) {
            val localVideo = requested.forOfflineQuality(preferredQuality)
            return PlaybackResolution(
                playback = ResolvedPlayback(
                    video = localVideo,
                    stream = resolveLocalStream(localVideo),
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
            ?: playbackSelection(video.animeId)
                ?.takeIf { selection ->
                    selection.voiceKey.isBlank() || selection.voiceKey == video.matchingVoiceKey
                }
                ?.sourceKey
                ?.takeIf { it.isNotBlank() }
    }

    private fun playbackSelection(animeId: Long): PlaybackSelection? {
        if (playbackSelectionCache.containsKey(animeId)) return playbackSelectionCache[animeId]
        return readPlaybackSelection(animeId).also { playbackSelectionCache[animeId] = it }
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
    get() = joinSourceKey(
        player.cleanVideoSourceLabel().lowercase(Locale.ROOT),
        url.sourceProviderFingerprint(),
    )

internal val VideoVariant.playbackSourceKey: String
    get() = joinSourceKey(
        animeId.toString(),
        matchingEpisodeKey,
        matchingVoiceKey,
        sourceSelectionKey,
        id.takeIf { it > 0L }?.let { "id:$it" }
            ?: url.sourcePlaybackFingerprint().takeIf { it.isNotBlank() }
            ?: index.takeIf { it > 0 }?.let { "index:$it" }
            ?: "unknown",
    )

internal val VideoVariant.sourceSelectionKey: String
    get() = sourceProviderKey.takeIf { it.isNotBlank() }
        ?: playerId.takeIf { it > 0L }?.let { "player-id:$it" }
        ?: player.cleanVideoSourceLabel()
            .lowercase(Locale.ROOT)
            .replace(SourceSelectionWhitespacePattern, " ")
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
    val host = SourceFingerprintHostPattern.find(value)?.groupValues?.getOrNull(1).orEmpty()
    val path = SourceFingerprintPathPattern.find(value)?.groupValues?.getOrNull(1)
        ?.substringBefore('/')
        .orEmpty()
    return joinSourceKey(host, path, delimiter = "/")
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
        else -> SourceQualityHeightPattern.find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
    }
}

private fun joinSourceKey(
    vararg parts: String?,
    delimiter: String = "|",
): String {
    return parts.asSequence()
        .filterNotNull()
        .filter(String::isNotBlank)
        .joinToString(delimiter)
}

private val SourceFingerprintHostPattern = Regex("""^https?://([^/?#]+)""")
private val SourceFingerprintPathPattern = Regex("""^https?://[^/]+/([^?#]+)""")
private val SourceQualityHeightPattern = Regex("""(?i)(2160|1440|1080|720|576|540|480|360|240|144)p""")
private val SourceSelectionWhitespacePattern = Regex("""\s+""")

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

internal class PlayerNoticeRuntime(
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val sourceFallbackMessage: (String, String, String) -> String,
    private val voiceFallbackMessage: (String, String, String) -> String,
    private val playbackPlayerErrorMessage: () -> String,
    private val playbackBufferingTimeoutMessage: () -> String,
    private val genericSourceLabel: () -> String,
) {
    private var playerNoticeId = 0L

    fun showTransientNotice(message: String) {
        updateState {
            it.copy(
                playerNotice = PlayerNotice(
                    id = ++playerNoticeId,
                    message = message,
                ),
            )
        }
    }

    fun showPlaybackSourceFallbackNotice(notice: SourceFallbackNotice, fallbackVideo: VideoVariant) {
        if (fallbackVideo.hasSamePlaybackSourceAs(notice.selectedVideo)) return
        val selectedLabel = notice.selectedVideo.playbackNoticeSourceLabel()
        val fallbackLabel = fallbackVideo.playbackNoticeSourceLabel()
        showTransientNotice(sourceFallbackMessage(selectedLabel, notice.reason, fallbackLabel))
    }

    fun showPlaybackVoiceFallbackNotice(previousVideo: VideoVariant, fallbackVideo: VideoVariant) {
        if (fallbackVideo.hasSameVoiceAs(previousVideo)) return
        showTransientNotice(
            voiceFallbackMessage(
                previousVideo.matchingVoiceTitle,
                fallbackVideo.episodeTitle,
                fallbackVideo.matchingVoiceTitle,
            ),
        )
    }

    fun playbackFailureReason(failure: PlaybackFailure): String {
        return failure.message
            ?.takeIf { it.isNotBlank() }
            ?: when (failure.kind) {
                PlaybackFailureKind.PlayerError -> playbackPlayerErrorMessage()
                PlaybackFailureKind.BufferingTimeout -> playbackBufferingTimeoutMessage()
                PlaybackFailureKind.SourceUnavailable -> playbackPlayerErrorMessage()
            }
    }

    private fun VideoVariant.playbackNoticeSourceLabel(): String {
        return player.cleanVideoSourceLabel()
            .ifBlank { player }
            .ifBlank { genericSourceLabel() }
    }
}

// PlaybackActionRuntime
internal class PlaybackActionRuntime(
    private val scope: CoroutineScope,
    private val repository: YummyAnimeRepository,
    private val playbackSessionCoordinator: PlaybackSessionCoordinator,
    private val animeMarkCoordinator: AnimeMarkCoordinator,
    private val playbackHistoryStateRuntime: PlaybackHistoryStateRuntime,
    private val playbackProgressStorage: PlaybackProgressStorage,
    private val historyAnimeCacheStorage: HistoryAnimeCacheStorage,
    private val playbackProgressOperations: KeyedLatestStateOperationCoordinator<Long>,
    private val profilePlaybackHistoryCache: ProfilePlaybackHistoryCache,
    private val browseContentCoordinator: BrowseContentCoordinator,
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val updateCachedPlaybackProgress: (PlaybackProgress, List<PlaybackProgress>) -> Unit,
    private val clearCachedPlaybackProgress: (Long) -> Unit,
    private val isActiveProfile: (Long) -> Boolean,
    private val requestCaptchaRetry: (Throwable, suspend () -> Unit) -> Boolean,
    private val playbackFailureReason: (PlaybackFailure) -> String,
    private val openAnime: (animeId: Long, pushCurrent: Boolean) -> Unit,
    private val showNotice: (String) -> Unit,
) {
    private val playbackQualityPreferences = PlaybackQualityPreferences()

    fun playVideo(video: VideoVariant) {
        val title = currentState().details.readyDataOrNull()?.title.orEmpty()
        playVideoAt(
            video = video,
            startPositionMs = 0L,
            titleOverride = title,
            preferredQuality = playbackQualityForAnime(video.animeId),
        )
    }

    fun playVideo(video: VideoVariant, animeTitle: String) {
        val title = animeTitle.ifBlank { currentState().details.readyDataOrNull()?.title.orEmpty() }
        playVideoAt(
            video = video,
            startPositionMs = 0L,
            titleOverride = title,
            preferredQuality = playbackQualityForAnime(video.animeId),
        )
    }

    fun playVideoAt(video: VideoVariant, startPositionMs: Long) {
        val title = currentState().details.readyDataOrNull()?.title
            ?: (currentState().route as? AppRoute.Player)?.animeTitle
            ?: ""
        playVideoAt(
            video = video,
            startPositionMs = startPositionMs,
            titleOverride = title,
            preferredQuality = playbackQualityForAnime(video.animeId),
        )
    }

    fun playVideoAt(
        video: VideoVariant,
        startPositionMs: Long,
        titleOverride: String,
        preferredQuality: PreferredQuality,
    ) {
        playVideoAt(
            video = video,
            startPositionMs = startPositionMs,
            titleOverride = titleOverride,
            preferredQuality = preferredQuality,
            resumeChoicePositionMs = null,
        )
    }

    fun playVideoAtQuality(video: VideoVariant, startPositionMs: Long, preferredQuality: PreferredQuality) {
        val title = currentState().details.readyDataOrNull()?.title
            ?: (currentState().route as? AppRoute.Player)?.animeTitle
            ?: ""
        rememberPlaybackQualityOverride(video.animeId, preferredQuality)
        playbackSessionCoordinator.rememberManualSource(video)
        playVideoAt(
            video = video,
            startPositionMs = startPositionMs,
            titleOverride = title,
            preferredQuality = preferredQuality,
            lockPlaybackSource = true,
        )
    }

    fun selectPlaybackSource(video: VideoVariant, startPositionMs: Long) {
        val route = currentState().route as? AppRoute.Player
        val title = currentState().details.readyDataOrNull()?.title
            ?: route?.animeTitle
            ?: ""
        val preferredQuality = route
            ?.takeIf { it.video.animeId == video.animeId && it.video.hasSameVoiceAs(video) }
            ?.preferredQuality
            ?: playbackQualityForAnime(video.animeId)
        playbackSessionCoordinator.rememberManualSource(video)
        playVideoAt(
            video = video,
            startPositionMs = startPositionMs,
            titleOverride = title,
            preferredQuality = preferredQuality,
            clearPlaybackSourceState = true,
            lockPlaybackSource = true,
        )
    }

    fun playVideoWithResumeChoice(video: VideoVariant, resumePositionMs: Long) {
        val title = currentState().details.readyDataOrNull()?.title.orEmpty()
        playVideoAt(
            video = video,
            startPositionMs = 0L,
            titleOverride = title,
            preferredQuality = playbackQualityForAnime(video.animeId),
            resumeChoicePositionMs = resumePositionMs.takeIf { it > 0L },
        )
    }

    fun choosePlayerResumePosition(startPositionMs: Long) {
        updateState { state ->
            val route = state.route as? AppRoute.Player ?: return@updateState state
            if (route.resumeChoicePositionMs == null) return@updateState state
            state.copy(
                route = route.copy(
                    startPositionMs = startPositionMs.coerceAtLeast(0L),
                    resumeChoicePositionMs = null,
                ),
            )
        }
    }

    fun retryVideo() {
        val route = currentState().route as? AppRoute.Player ?: return
        playVideoAt(route.video, route.startPositionMs)
    }

    fun fallbackPlaybackSource(failedVideo: VideoVariant, playbackPositionMs: Long, failure: PlaybackFailure) {
        val reason = playbackFailureReason(failure)
        val outcome = playbackSessionCoordinator.handlePlaybackFailure(
            failedVideo = failedVideo,
            playbackPositionMs = playbackPositionMs,
            failure = failure,
            reason = reason,
        )
        if (outcome == PlaybackFailureOutcome.Failed) showNotice(reason)
    }

    fun confirmPlaybackSource(video: VideoVariant) {
        val route = currentState().route as? AppRoute.Player ?: return
        if (!playbackSessionCoordinator.confirm(route.video, video)) return
        animeMarkCoordinator.maybeMarkWatching(video)
    }

    fun handlePlaybackEnded(video: VideoVariant) {
        val state = currentState()
        state.details.readyDataOrNull()
            ?.takeIf { it.id == video.animeId }
            ?: return
        val videos = state.videos.readyListOrEmpty()
        animeMarkCoordinator.maybeMarkWatchedOnCompletion(video, state)

        if (!video.hasFollowingEpisodeIn(videos)) {
            openAnime(video.animeId, false)
        }
    }

    fun savePlaybackProgress(video: VideoVariant, positionMs: Long, durationMs: Long) {
        if (video.animeId <= 0L || positionMs < 0L) return

        val stateBeforeUpdate = currentState()
        val currentDetails = stateBeforeUpdate.details.readyDataOrNull()
            ?.takeIf { it.id == video.animeId }
        val progress = PlaybackProgress(
            animeId = video.animeId,
            videoId = video.id,
            animeTitle = currentDetails?.title.orEmpty(),
            posterUrl = currentDetails?.posterUrl.orEmpty(),
            groupKey = video.groupKey,
            episode = video.episode.ifBlank { video.matchingEpisodeKey },
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            updatedAtMs = System.currentTimeMillis(),
        )
        val anime = currentDetails?.toAnimeSummary()
        updateState { state -> state.withLocalPlaybackProgress(progress, anime) }
        updateCachedPlaybackProgress(progress, stateBeforeUpdate.playbackHistoryWith(progress))
        val remoteProgress = playbackProgressSiteMirrors(progress, video)
        val profileId = currentState().auth.profile?.id
        playbackProgressOperations.launchLatest(video.animeId, scope) { lease ->
            delay(250)
            val storedHistory = withContext(Dispatchers.IO) {
                anime?.let(historyAnimeCacheStorage::save)
                playbackProgressStorage.save(progress)
                playbackProgressStorage.readAnimeHistory(video.animeId)
            }
            if (!lease.isCurrent) return@launchLatest
            updateCachedPlaybackProgress(progress, storedHistory)
            updateState { state ->
                state.withStoredPlaybackHistory(video.animeId, storedHistory)
            }
            uploadStoredPlaybackProgress(progress, remoteProgress, storedHistory, profileId, lease)
        }
    }

    private suspend fun uploadStoredPlaybackProgress(
        progress: PlaybackProgress,
        remoteProgress: List<PlaybackProgress>,
        storedHistory: List<PlaybackProgress>,
        profileId: Long?,
        lease: StateOperationLease,
    ) {
        if (profileId == null || currentState().forcedOfflineMode) return
        val uploaded = playbackHistoryStateRuntime.uploadPlaybackProgressToSite(remoteProgress, profileId, lease)
        if (!uploaded || !lease.isCurrent || !isActiveProfile(profileId)) return
        profilePlaybackHistoryCache.replaceAnime(profileId, progress.animeId, storedHistory)
    }

    fun resetAnimeWatchProgress(animeId: Long) {
        if (animeId <= 0L) return
        val state = currentState()
        playbackProgressOperations.launchLatest(animeId, scope) { lease ->
            val storedVideoIds = withContext(Dispatchers.IO) {
                playbackProgressStorage.readAnimeHistory(animeId).map { it.videoId }
            }
            if (!lease.isCurrent) return@launchLatest
            val videoIds = (
                state.videos.readyListOrEmpty()
                    .filter { it.animeId == animeId }
                    .map { it.id } +
                    state.playbackHistory
                        .filter { it.animeId == animeId }
                        .map { it.videoId } +
                    storedVideoIds
                )
                .filter { it > 0L }
                .distinct()

            clearAnimeWatchProgressLocally(animeId, lease)
            val profileId = state.auth.profile?.id
            if (!lease.isCurrent || state.forcedOfflineMode || profileId == null || videoIds.isEmpty()) {
                return@launchLatest
            }
            deleteAnimeWatchProgressFromSite(animeId, videoIds, profileId, lease)
        }
    }

    private fun playbackQualityForAnime(animeId: Long): PreferredQuality {
        return playbackQualityPreferences.forAnime(animeId, currentState().settings.defaultQuality)
    }

    private fun rememberPlaybackQualityOverride(animeId: Long, preferredQuality: PreferredQuality) {
        playbackQualityPreferences.select(animeId, preferredQuality)
    }

    private fun resetPlaybackSourceRuntimeState(clearPlaybackSourceCache: Boolean) {
        playbackSessionCoordinator.resetRuntime(clearSourceCache = clearPlaybackSourceCache)
    }

    private fun playVideoAt(
        video: VideoVariant,
        startPositionMs: Long,
        titleOverride: String,
        preferredQuality: PreferredQuality = currentState().settings.defaultQuality,
        resumeChoicePositionMs: Long? = null,
        clearPlaybackSourceState: Boolean = false,
        lockPlaybackSource: Boolean = false,
        playWhenReady: Boolean = true,
    ) {
        resetPlaybackSourceRuntimeState(clearPlaybackSourceCache = clearPlaybackSourceState)
        playVideoFromCandidates(
            video = video,
            title = titleOverride,
            excludedSourceKeys = emptySet(),
            startPositionMs = startPositionMs,
            preferredQuality = preferredQuality,
            resumeChoicePositionMs = resumeChoicePositionMs,
            lockPlaybackSource = lockPlaybackSource,
            playWhenReady = playWhenReady,
        )
    }

    private fun playVideoFromCandidates(
        video: VideoVariant,
        title: String,
        excludedSourceKeys: Set<String>,
        startPositionMs: Long,
        preferredQuality: PreferredQuality,
        resumeChoicePositionMs: Long? = null,
        sourceFallbackNotice: SourceFallbackNotice? = null,
        voiceFallbackFromVideo: VideoVariant? = null,
        lockPlaybackSource: Boolean = false,
        playWhenReady: Boolean = true,
    ) {
        playbackSessionCoordinator.play(
            PlaybackSessionRequest(
                video = video,
                title = title,
                excludedSourceKeys = excludedSourceKeys,
                startPositionMs = startPositionMs,
                preferredQuality = preferredQuality,
                resumeChoicePositionMs = resumeChoicePositionMs,
                sourceFallbackNotice = sourceFallbackNotice,
                voiceFallbackFromVideo = voiceFallbackFromVideo,
                lockPlaybackSource = lockPlaybackSource,
                playWhenReady = playWhenReady,
            ),
        )
    }

    private fun playbackProgressSiteMirrors(
        progress: PlaybackProgress,
        video: VideoVariant,
    ): List<PlaybackProgress> {
        val sameEpisodeVoiceVideos = currentState().videos.readyListOrEmpty()
            .asSequence()
            .filter { candidate ->
                candidate.animeId == video.animeId &&
                    candidate.id > 0L &&
                    candidate.isSameEpisodeAs(video) &&
                    candidate.hasSameVoiceAs(video)
            }
            .distinctBy { it.id }
            .toList()
            .ifEmpty { listOf(video).filter { it.id > 0L } }

        return sameEpisodeVoiceVideos.map { candidate ->
            progress.copy(
                videoId = candidate.id,
                groupKey = candidate.groupKey,
                episode = candidate.episode.ifBlank { progress.episode },
            )
        }
    }

    private suspend fun deleteAnimeWatchProgressFromSite(
        animeId: Long,
        videoIds: List<Long>,
        profileId: Long,
        lease: StateOperationLease,
    ) {
        runCatching { repository.deleteWatchProgress(videoIds) }
            .onSuccess {
                if (lease.isCurrent && isActiveProfile(profileId) &&
                    currentState().homeSection == BrowseSection.History
                ) {
                    browseContentCoordinator.loadHistory(force = true)
                }
            }
            .onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                if (!lease.isCurrent || !isActiveProfile(profileId)) return@onFailure
                if (!requestCaptchaRetry(throwable) { retryAnimeWatchProgressDeletion(animeId, videoIds) }) {
                    AppLog.w("YummyDroidHistory", "Failed to reset anime watch progress", throwable)
                    showNotice(throwable.userMessage())
                }
            }
    }

    private fun retryAnimeWatchProgressDeletion(animeId: Long, videoIds: List<Long>) {
        val profileId = currentState().auth.profile?.id ?: return
        playbackProgressOperations.launchLatest(animeId, scope) { lease ->
            deleteAnimeWatchProgressFromSite(animeId, videoIds, profileId, lease)
        }
    }

    private suspend fun clearAnimeWatchProgressLocally(animeId: Long, lease: StateOperationLease) {
        withContext(Dispatchers.IO) {
            playbackProgressStorage.clearAnime(animeId)
        }
        if (!lease.isCurrent) return
        clearCachedPlaybackProgress(animeId)
        profilePlaybackHistoryCache.removeAnime(animeId)
        updateState { state ->
            val isCurrentDetails = (state.route as? AppRoute.Details)?.animeId == animeId ||
                state.details.readyDataOrNull()?.id == animeId
            state.copy(
                playbackProgress = if (isCurrentDetails) null else state.playbackProgress,
                playbackHistory = if (isCurrentDetails) emptyList() else state.playbackHistory,
                historyAnime = state.historyAnime.withoutAnime(animeId),
            )
        }
    }
}

internal class PlaybackQualityPreferences {
    private val selections = mutableMapOf<Long, PreferredQuality>()

    fun forAnime(animeId: Long, defaultQuality: PreferredQuality): PreferredQuality =
        selections[animeId] ?: defaultQuality

    fun select(animeId: Long, quality: PreferredQuality) {
        if (animeId > 0L) selections[animeId] = quality
    }
}

internal fun shouldPublishPlaybackProgressToUi(route: AppRoute): Boolean = route !is AppRoute.Player

internal fun YummyDroidUiState.playbackHistoryWith(progress: PlaybackProgress): List<PlaybackProgress> {
    val history = playbackHistory.takeIf { details.readyDataOrNull()?.id == progress.animeId }.orEmpty()
    return (history + progress).distinctLatestByEpisode()
}

internal fun YummyDroidUiState.withLocalPlaybackProgress(
    progress: PlaybackProgress,
    anime: Anime?,
): YummyDroidUiState {
    if (!shouldPublishPlaybackProgressToUi(route)) return this
    val isCurrentAnime = details.readyDataOrNull()?.id == progress.animeId
    return copy(
        playbackProgress = if (isCurrentAnime) progress else playbackProgress,
        playbackHistory = if (isCurrentAnime) playbackHistoryWith(progress) else playbackHistory,
        historyAnime = historyAnime.updatedWithLocalHistorySnapshot(progress, anime),
    )
}

internal fun YummyDroidUiState.withStoredPlaybackHistory(
    animeId: Long,
    history: List<PlaybackProgress>,
): YummyDroidUiState {
    if (!shouldPublishPlaybackProgressToUi(route)) return this
    if (details.readyDataOrNull()?.id != animeId) return this
    return copy(playbackHistory = history)
}

private fun LoadState<List<Anime>>.updatedWithLocalHistorySnapshot(
    progress: PlaybackProgress,
    anime: Anime?,
): LoadState<List<Anime>> {
    val summary = anime ?: progress.toAnimeSummary()
    return when (this) {
        is LoadState.Ready -> LoadState.Ready(
            (listOf(summary) + data.filterNot { it.id == progress.animeId })
                .distinctBy { it.id },
        )

        else -> this
    }
}

private fun LoadState<List<Anime>>.withoutAnime(animeId: Long): LoadState<List<Anime>> {
    return when (this) {
        is LoadState.Ready -> LoadState.Ready(data.filterNot { it.id == animeId })
        else -> this
    }
}
