package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedPlayback
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.hasSameVoiceAs
import me.yummydroid.app.data.isSameEpisodeAs

internal data class PlaybackSessionRequest(
    val video: VideoVariant,
    val title: String,
    val excludedSourceKeys: Set<String>,
    val startPositionMs: Long,
    val preferredQuality: PreferredQuality,
    val resumeChoicePositionMs: Long? = null,
    val sourceFallbackNotice: SourceFallbackNotice? = null,
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
    private val fetchVideos: suspend (Long) -> List<VideoVariant>,
    private val resolvePlaybackMetadata: suspend (
        ResolvedPlayback,
        List<VideoVariant>,
        PreferredQuality,
    ) -> ResolvedPlayback,
    private val cachedSiteBaseUrl: () -> String,
    private val offlineUnavailableMessage: () -> String,
    private val onFallbackNotice: (SourceFallbackNotice, VideoVariant) -> Unit,
    private val onMetadataFailure: (Throwable) -> Unit,
) {
    private var loadJob: Job? = null
    private var metadataJob: Job? = null
    private var metadataLoadId = 0L

    fun play(request: PlaybackSessionRequest) {
        loadJob?.cancel()
        cancelMetadataLoad()
        val normalizedRequest = request.normalized()
        val forcedOfflineMode = currentState().forcedOfflineMode
        updateState { state -> state.withStartedPlayback(normalizedRequest) }
        loadJob = scope.launch {
            load(normalizedRequest, forcedOfflineMode)
        }
    }

    fun resetRuntime(clearSourceCache: Boolean) {
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
    ): PlaybackSourceFallbackPlan? {
        return sourceCoordinator.fallbackPlan(currentVideo, failedVideo, failure, reason)
    }

    fun confirm(currentVideo: VideoVariant, confirmedVideo: VideoVariant): Boolean {
        return sourceCoordinator.confirm(currentVideo, confirmedVideo)
    }

    fun cancelMetadataLoad() {
        metadataLoadId += 1L
        metadataJob?.cancel()
        metadataJob = null
        updateState { state ->
            if (state.playbackMetadataLoading) {
                state.copy(playbackMetadataLoading = false)
            } else {
                state
            }
        }
    }

    private suspend fun load(request: PlaybackSessionRequest, forcedOfflineMode: Boolean) {
        val allVideos = candidatePool(request.video)
        val metadataCandidates = sourceCoordinator.candidates(
            requested = request.video,
            allVideos = allVideos,
            excludedSourceKeys = emptySet(),
        ).let { candidates ->
            if (forcedOfflineMode) candidates.filter(VideoVariant::isOfflineAvailable) else candidates
        }
        val candidates = metadataCandidates.filterNot { it.playbackSourceKey in request.excludedSourceKeys }
        if (forcedOfflineMode && candidates.isEmpty()) {
            updateState { state -> state.withOfflinePlaybackUnavailable(offlineUnavailableMessage()) }
            return
        }

        val routeVideo = request.routeVideo(candidates, forcedOfflineMode)
        if (routeVideo != request.video) {
            updateState { state -> state.withPlaybackRouteVideo(request, routeVideo) }
        }
        resolve(request, routeVideo, candidates, metadataCandidates)
    }

    private suspend fun resolve(
        request: PlaybackSessionRequest,
        routeVideo: VideoVariant,
        candidates: List<VideoVariant>,
        metadataCandidates: List<VideoVariant>,
    ) {
        runCatching {
            sourceCoordinator.resolve(
                requested = routeVideo,
                candidates = candidates,
                preferredQuality = request.preferredQuality,
                metadataCandidates = metadataCandidates,
                fastStart = true,
            )
        }.onSuccess { resolution ->
            acceptResolution(request, routeVideo, resolution, metadataCandidates)
        }.onFailure { throwable ->
            if (throwable is CancellationException) return@onFailure
            val target = request.routeTarget(routeVideo)
            updateState { state -> state.withPlaybackFailure(target, throwable.userMessage()) }
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
        startMetadataLoad(
            playback = playback,
            title = request.title,
            preferredQuality = request.preferredQuality,
            metadataCandidates = metadataCandidates,
        )
    }

    private suspend fun candidatePool(video: VideoVariant): List<VideoVariant> {
        val stateVideos = currentState().videos.readyListOrEmpty()
        val stateAnimeVideos = stateVideos.filter { it.animeId == video.animeId }
        val hasUsableStatePool = stateAnimeVideos.size > 1 &&
            stateAnimeVideos.any { it.isSameEpisodeAs(video) && it.hasSameVoiceAs(video) }
        if (hasUsableStatePool) return stateAnimeVideos
        if (video.animeId <= 0L || currentState().forcedOfflineMode) {
            return stateAnimeVideos.ifEmpty { stateVideos.ifEmpty { listOf(video) } }
        }

        val loadedVideos = runCatching { fetchVideos(video.animeId) }.getOrDefault(emptyList())
        if (loadedVideos.isNotEmpty()) {
            updateState { state -> state.withPlaybackVideos(video.animeId, loadedVideos) }
            return loadedVideos
        }
        return stateAnimeVideos.ifEmpty { stateVideos.ifEmpty { listOf(video) } }
    }

    private fun startMetadataLoad(
        playback: ResolvedPlayback,
        title: String,
        preferredQuality: PreferredQuality,
        metadataCandidates: List<VideoVariant>,
    ) {
        val loadId = ++metadataLoadId
        metadataJob?.cancel()
        val target = PlaybackMetadataTarget(
            video = playback.video,
            title = title,
            preferredQuality = preferredQuality,
            streamUrl = playback.stream.url,
        )
        setMetadataLoading(target, loading = true)
        metadataJob = scope.launch {
            try {
                val enrichedPlayback = resolvePlaybackMetadata(
                    playback,
                    metadataCandidates,
                    preferredQuality,
                )
                updateState { state ->
                    state.withPlaybackMetadata(target, enrichedPlayback, cachedSiteBaseUrl)
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                onMetadataFailure(throwable)
            } finally {
                if (metadataLoadId == loadId) {
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

private fun YummyDroidUiState.withStartedPlayback(request: PlaybackSessionRequest): YummyDroidUiState {
    return copy(
        route = AppRoute.Player(
            video = request.video,
            animeTitle = request.title,
            startPositionMs = request.startPositionMs,
            preferredQuality = request.preferredQuality,
            resumeChoicePositionMs = request.resumeChoicePositionMs,
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
