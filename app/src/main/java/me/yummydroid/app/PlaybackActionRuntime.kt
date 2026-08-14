package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.HistoryAnimeCacheStorage
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PlaybackProgressStorage
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.YummyAnimeRepository
import me.yummydroid.app.data.distinctLatestByEpisode
import me.yummydroid.app.data.hasSameVoiceAs
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.toAnimeSummary

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
    private val animePlaybackQualityOverrides = mutableMapOf<Long, PreferredQuality>()

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
        playVideoAt(video, startPositionMs, title, preferredQuality)
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
        val state = currentState()
        val route = state.route as? AppRoute.Player ?: return
        val fallbackPlan = playbackSessionCoordinator.fallbackPlan(
            currentVideo = route.video,
            failedVideo = failedVideo,
            failure = failure,
            reason = playbackFailureReason(failure),
            allVideos = state.videos.readyListOrEmpty(),
            preferredQuality = route.preferredQuality,
            currentStream = state.playerStream.readyDataOrNull(),
        ) ?: return
        val safePositionMs = playbackPositionMs.takeIf { it > 0L } ?: route.startPositionMs
        playbackSessionCoordinator.cancelMetadataLoad()

        playVideoFromCandidates(
            video = fallbackPlan.targetVideo ?: route.video,
            title = route.animeTitle,
            excludedSourceKeys = fallbackPlan.excludedSourceKeys,
            startPositionMs = safePositionMs,
            preferredQuality = route.preferredQuality,
            sourceFallbackNotice = fallbackPlan.notice,
            voiceFallbackFromVideo = fallbackPlan.voiceFallbackFromVideo,
        )
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

        val currentDetails = currentState().details.readyDataOrNull()
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
        var inMemoryHistory = listOf(progress)
        updateState { state ->
            if (state.details.readyDataOrNull()?.id == video.animeId) {
                val history = (state.playbackHistory + progress).distinctLatestByEpisode()
                inMemoryHistory = history
                state.copy(
                    playbackProgress = progress,
                    playbackHistory = history,
                    historyAnime = state.historyAnime.updatedWithLocalHistorySnapshot(
                        progress = progress,
                        anime = currentDetails?.toAnimeSummary(),
                    ),
                )
            } else {
                state.copy(
                    historyAnime = state.historyAnime.updatedWithLocalHistorySnapshot(
                        progress = progress,
                        anime = currentDetails?.toAnimeSummary(),
                    ),
                )
            }
        }
        updateCachedPlaybackProgress(progress, inMemoryHistory)
        val remoteProgress = playbackProgressSiteMirrors(progress, video)
        val profileId = currentState().auth.profile?.id
        playbackProgressOperations.launchLatest(video.animeId, scope) { lease ->
            delay(250)
            val storedHistory = withContext(Dispatchers.IO) {
                currentDetails?.toAnimeSummary()?.let(historyAnimeCacheStorage::save)
                playbackProgressStorage.save(progress)
                playbackProgressStorage.readAnimeHistory(video.animeId)
            }
            if (!lease.isCurrent) return@launchLatest
            updateCachedPlaybackProgress(progress, storedHistory)
            updateState { state ->
                if (state.details.readyDataOrNull()?.id == video.animeId) {
                    state.copy(playbackHistory = storedHistory)
                } else {
                    state
                }
            }
            if (profileId != null && !currentState().forcedOfflineMode) {
                val uploadSucceeded = playbackHistoryStateRuntime.uploadPlaybackProgressToSite(
                    progressEntries = remoteProgress,
                    profileId = profileId,
                    lease = lease,
                )
                if (uploadSucceeded && lease.isCurrent && isActiveProfile(profileId)) {
                    profilePlaybackHistoryCache.replaceAnime(profileId, video.animeId, storedHistory)
                }
            }
        }
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
        return animePlaybackQualityOverrides[animeId] ?: currentState().settings.defaultQuality
    }

    private fun rememberPlaybackQualityOverride(animeId: Long, preferredQuality: PreferredQuality) {
        if (animeId <= 0L) return
        if (preferredQuality == PreferredQuality.Auto) {
            animePlaybackQualityOverrides.remove(animeId)
            return
        }
        if (preferredQuality != currentState().settings.defaultQuality ||
            animeId in animePlaybackQualityOverrides
        ) {
            animePlaybackQualityOverrides[animeId] = preferredQuality
        }
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
    ) {
        resetPlaybackSourceRuntimeState(clearPlaybackSourceCache = clearPlaybackSourceState)
        playVideoFromCandidates(
            video = video,
            title = titleOverride,
            excludedSourceKeys = emptySet(),
            startPositionMs = startPositionMs,
            preferredQuality = preferredQuality,
            resumeChoicePositionMs = resumeChoicePositionMs,
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
