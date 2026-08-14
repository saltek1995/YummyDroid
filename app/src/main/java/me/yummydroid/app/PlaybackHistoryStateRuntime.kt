package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PlaybackProgressStorage
import me.yummydroid.app.data.distinctLatestByEpisode

internal class ProfilePlaybackHistoryCache {
    private var profileId: Long? = null
    private var history: List<PlaybackProgress> = emptyList()

    fun historyForAnime(profileId: Long?, animeId: Long): List<PlaybackProgress> {
        if (profileId == null || this.profileId != profileId) return emptyList()
        return history
            .filter { progress -> progress.animeId == animeId }
            .distinctLatestByEpisode()
    }

    fun replace(profileId: Long, history: List<PlaybackProgress>) {
        this.profileId = profileId
        this.history = history.distinctLatestByEpisode()
    }

    fun replaceAnime(profileId: Long, animeId: Long, history: List<PlaybackProgress>) {
        if (this.profileId != profileId) {
            this.profileId = profileId
            this.history = emptyList()
        }
        this.history = (
            this.history.filterNot { progress -> progress.animeId == animeId } +
                history.filter { progress -> progress.animeId == animeId }
            ).distinctLatestByEpisode()
    }

    fun removeAnime(animeId: Long) {
        history = history.filterNot { progress -> progress.animeId == animeId }
    }

    fun clear() {
        profileId = null
        history = emptyList()
    }
}

// PlaybackHistoryStateRuntime
internal class PlaybackHistoryStateRuntime(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<YummyDroidUiState>,
    private val playbackProgressStorage: PlaybackProgressStorage,
    private val watchHistoryCoordinator: WatchHistoryCoordinator,
    private val playbackProgressOperations: KeyedLatestStateOperationCoordinator<Long>,
    private val playbackHistoryOperations: LatestStateOperationCoordinator,
    private val profilePlaybackHistoryCache: ProfilePlaybackHistoryCache,
    private val saveProgressToSite: suspend (PlaybackProgress) -> Boolean,
    private val updateCachedPlaybackProgress: (PlaybackProgress, List<PlaybackProgress>) -> Unit,
    private val clearCachedPlaybackProgress: (Long) -> Unit,
    private val requestCaptchaRetry: (Throwable, suspend () -> Unit) -> Boolean,
    private val isActiveProfile: (Long) -> Boolean,
) {
    private val localHistoryMergeHandledProfileIds = mutableSetOf<Long>()

    fun clearProfileState() {
        localHistoryMergeHandledProfileIds.clear()
        profilePlaybackHistoryCache.clear()
    }

    fun refreshPlaybackProgressFromSite(animeId: Long) {
        if (animeId <= 0L) return
        val profileId = uiState.value.auth.profile?.id
        playbackProgressOperations.launchLatest(animeId, scope) { lease ->
            val snapshot = syncPlaybackProgressForAnime(animeId, profileId, lease)
            if (!lease.isCurrent) return@launchLatest
            if (snapshot.progress != null) {
                updateCachedPlaybackProgress(snapshot.progress, snapshot.history)
            } else if (snapshot.remoteAuthoritative) {
                clearCachedPlaybackProgress(animeId)
            }
            uiState.update { state ->
                val isCurrentDetails = (state.route as? AppRoute.Details)?.animeId == animeId ||
                    state.details.readyDataOrNull()?.id == animeId
                if (!isCurrentDetails) return@update state
                val progressGroupKey = snapshot.progress?.groupKey
                    ?.takeIf { groupKey -> state.videos.readyListOrEmpty().any { it.groupKey == groupKey } }
                state.copy(
                    selectedVideoGroup = progressGroupKey ?: state.selectedVideoGroup,
                    playbackProgress = if (snapshot.remoteAuthoritative) {
                        snapshot.progress
                    } else {
                        snapshot.progress ?: state.playbackProgress
                    },
                    playbackHistory = if (snapshot.remoteAuthoritative) {
                        snapshot.history
                    } else {
                        snapshot.history.ifEmpty { state.playbackHistory }
                    },
                    playbackHistoryLoading = false,
                )
            }
        }
    }

    fun syncPlaybackHistoryFromSite(
        mergeLocalHistory: Boolean = false,
        mergeCandidates: List<PlaybackProgress>? = null,
        allowLocalHistoryMergePrompt: Boolean = false,
    ) {
        if (uiState.value.forcedOfflineMode) return
        val profileId = uiState.value.auth.profile?.id ?: return
        publishPlaybackHistoryLoading(profileId)
        val request = PlaybackHistorySyncRequest(
            mergeLocalHistory = mergeLocalHistory,
            mergeCandidates = mergeCandidates,
            allowLocalHistoryMergePrompt = allowLocalHistoryMergePrompt,
        )
        playbackHistoryOperations.launchLatest(scope) { lease ->
            syncPlaybackHistoryForProfile(profileId, request, lease)
        }
    }

    fun confirmLocalWatchHistoryMerge() {
        val prompt = uiState.value.localWatchHistoryMergePrompt ?: return
        if (!isActiveProfile(prompt.profileId)) {
            uiState.update { it.copy(localWatchHistoryMergePrompt = null) }
            return
        }
        uiState.update { state ->
            if (state.localWatchHistoryMergePrompt?.profileId == prompt.profileId) {
                state.copy(localWatchHistoryMergePrompt = null)
            } else {
                state
            }
        }
        syncPlaybackHistoryFromSite(mergeLocalHistory = true, mergeCandidates = prompt.entries)
    }

    fun dismissLocalWatchHistoryMerge() {
        val prompt = uiState.value.localWatchHistoryMergePrompt ?: return
        localHistoryMergeHandledProfileIds += prompt.profileId
        uiState.update { state ->
            if (state.localWatchHistoryMergePrompt?.profileId == prompt.profileId) {
                state.copy(localWatchHistoryMergePrompt = null)
            } else {
                state
            }
        }
    }

    fun syncPlaybackProgressToSite(progress: PlaybackProgress) {
        syncPlaybackProgressToSite(listOf(progress))
    }

    fun syncPlaybackProgressToSite(progressEntries: List<PlaybackProgress>) {
        if (uiState.value.forcedOfflineMode) return
        val profileId = uiState.value.auth.profile?.id ?: return
        val animeId = progressEntries.firstOrNull()?.animeId ?: return
        if (animeId <= 0L || progressEntries.none { it.videoId > 0L }) return
        playbackProgressOperations.launchLatest(animeId, scope) { lease ->
            uploadPlaybackProgressToSite(progressEntries, profileId, lease)
        }
    }

    private suspend fun syncPlaybackProgressForAnime(
        animeId: Long,
        profileId: Long?,
        lease: StateOperationLease,
    ): PlaybackProgressSyncSnapshot {
        val localHistory = withContext(Dispatchers.IO) { playbackProgressStorage.readAnimeHistory(animeId) }
        val local = localHistory.maxByOrNull { it.updatedAtMs }
        val localSnapshot = PlaybackProgressSyncSnapshot(
            progress = local,
            history = localHistory,
            remoteAuthoritative = false,
        )
        if (uiState.value.forcedOfflineMode) return localSnapshot
        if (profileId == null || !isActiveProfile(profileId)) return localSnapshot

        val remoteHistoryResult = watchHistoryCoordinator.fetchRemoteHistory()
        if (!lease.isCurrent || !isActiveProfile(profileId)) return localSnapshot
        remoteHistoryResult.exceptionOrNull()?.let { throwable ->
            if (requestCaptchaRetry(throwable) { refreshPlaybackProgressFromSite(animeId) }) {
                return localSnapshot
            }
            return localSnapshot
        }
        val remoteEntries = remoteHistoryResult
            .getOrThrow()
            .filter { it.animeId == animeId }
        watchHistoryCoordinator.storeRemoteAnimeHistory(animeId, remoteEntries)
        val remoteHistory = remoteEntries.distinctLatestByEpisode()
        profilePlaybackHistoryCache.replaceAnime(profileId, animeId, remoteHistory)
        return PlaybackProgressSyncSnapshot(
            progress = remoteHistory.maxByOrNull { it.updatedAtMs },
            history = remoteHistory,
            remoteAuthoritative = true,
        )
    }

    private suspend fun syncPlaybackHistoryForProfile(
        profileId: Long,
        request: PlaybackHistorySyncRequest,
        lease: StateOperationLease,
    ) {
        val localEntries = request.mergeCandidates ?: withContext(Dispatchers.IO) { playbackProgressStorage.readAll() }
        val remoteHistoryResult = watchHistoryCoordinator.fetchRemoteHistory()
        if (!lease.acceptsProfile(profileId)) return
        if (handleRemoteHistoryFailure(remoteHistoryResult, profileId, request, lease)) return

        val remoteEntries = remoteHistoryResult.getOrThrow()
        val supplementalEntries = supplementalLocalHistoryEntries(localEntries, remoteEntries)
        requestLocalWatchHistoryMergeIfAllowed(request, profileId, supplementalEntries)
        val uploadSucceeded = uploadSupplementalHistoryIfNeeded(
            mergeLocalHistory = request.mergeLocalHistory,
            supplementalEntries = supplementalEntries,
            profileId = profileId,
            lease = lease,
        )
        if (!lease.acceptsProfile(profileId)) return
        if (request.mergeLocalHistory && uploadSucceeded) {
            localHistoryMergeHandledProfileIds += profileId
        }

        publishAuthoritativePlaybackHistory(
            profileId = profileId,
            lease = lease,
            entries = authoritativePlaybackHistory(
                remoteEntries = remoteEntries,
                supplementalEntries = supplementalEntries,
                mergeLocalHistory = request.mergeLocalHistory,
                uploadSucceeded = uploadSucceeded,
            ),
        )
    }

    private suspend fun handleRemoteHistoryFailure(
        result: Result<List<PlaybackProgress>>,
        profileId: Long,
        request: PlaybackHistorySyncRequest,
        lease: StateOperationLease,
    ): Boolean {
        val throwable = result.exceptionOrNull() ?: return false
        if (requestCaptchaRetry(throwable) {
            syncPlaybackHistoryFromSite(
                mergeLocalHistory = request.mergeLocalHistory,
                mergeCandidates = request.mergeCandidates,
                allowLocalHistoryMergePrompt = request.allowLocalHistoryMergePrompt,
            )
        }) {
            return true
        }
        refreshPlaybackHistoryFromLocalFallback(profileId, lease)
        return true
    }

    private suspend fun uploadSupplementalHistoryIfNeeded(
        mergeLocalHistory: Boolean,
        supplementalEntries: List<PlaybackProgress>,
        profileId: Long,
        lease: StateOperationLease,
    ): Boolean {
        if (!mergeLocalHistory || supplementalEntries.isEmpty()) return true
        return uploadPlaybackProgressToSite(
            progressEntries = supplementalEntries,
            profileId = profileId,
            lease = lease,
        )
    }

    private fun requestLocalWatchHistoryMergeIfAllowed(
        request: PlaybackHistorySyncRequest,
        profileId: Long,
        supplementalEntries: List<PlaybackProgress>,
    ) {
        if (watchHistorySyncAllowsLocalMergePrompt(request.allowLocalHistoryMergePrompt, request.mergeLocalHistory)) {
            requestLocalWatchHistoryMerge(profileId, supplementalEntries)
        }
    }

    private fun authoritativePlaybackHistory(
        remoteEntries: List<PlaybackProgress>,
        supplementalEntries: List<PlaybackProgress>,
        mergeLocalHistory: Boolean,
        uploadSucceeded: Boolean,
    ): List<PlaybackProgress> {
        return if (mergeLocalHistory && uploadSucceeded) {
            (remoteEntries + supplementalEntries).distinctLatestByEpisode()
        } else {
            remoteEntries.distinctLatestByEpisode()
        }
    }

    private suspend fun publishAuthoritativePlaybackHistory(
        profileId: Long,
        lease: StateOperationLease,
        entries: List<PlaybackProgress>,
    ) {
        watchHistoryCoordinator.storeRemoteHistory(entries)
        profilePlaybackHistoryCache.replace(profileId, entries)
        updateCurrentAnimePlaybackHistory(profileId, lease, entries)
        watchHistoryCoordinator.markRemoteSynchronized()
        val history = entries.latestHistoryByAnime()
        val animes = watchHistoryCoordinator.resolveAnimeSummaries(history)
        updateHistoryAnime(profileId, lease, animes)
    }

    private fun updateCurrentAnimePlaybackHistory(
        profileId: Long,
        lease: StateOperationLease,
        entries: List<PlaybackProgress>,
    ) {
        val currentAnimeId = uiState.value.details.readyDataOrNull()?.id ?: return
        val history = entries
            .filter { progress -> progress.animeId == currentAnimeId }
            .distinctLatestByEpisode()
        val progress = history.maxByOrNull { progress -> progress.updatedAtMs }
        updateCurrentPlaybackHistory(profileId, lease, progress, history)
    }

    private suspend fun refreshPlaybackHistoryFromLocalFallback(
        profileId: Long,
        lease: StateOperationLease,
    ) {
        val currentAnimeId = uiState.value.details.readyDataOrNull()?.id
        if (currentAnimeId != null) {
            val progress = withContext(Dispatchers.IO) { playbackProgressStorage.read(currentAnimeId) }
            val history = withContext(Dispatchers.IO) { playbackProgressStorage.readAnimeHistory(currentAnimeId) }
            updateCurrentPlaybackHistory(profileId, lease, progress, history)
        }
        val history = watchHistoryCoordinator.readLatestLocalProgress()
        val animes = watchHistoryCoordinator.resolveAnimeSummaries(history)
        updateHistoryAnime(profileId, lease, animes)
    }

    private fun updateCurrentPlaybackHistory(
        profileId: Long,
        lease: StateOperationLease,
        progress: PlaybackProgress?,
        history: List<PlaybackProgress>,
    ) {
        uiState.update { state ->
            if (!lease.isCurrent || !isActiveProfile(profileId)) return@update state
            state.copy(
                playbackProgress = progress,
                playbackHistory = history,
                playbackHistoryLoading = false,
            )
        }
    }

    private fun updateHistoryAnime(
        profileId: Long,
        lease: StateOperationLease,
        animes: List<Anime>,
    ) {
        if (!lease.isCurrent || !isActiveProfile(profileId)) return
        uiState.update {
            it.copy(
                historyAnime = LoadState.Ready(animes),
                playbackHistoryLoading = false,
            )
        }
    }

    private fun publishPlaybackHistoryLoading(profileId: Long) {
        uiState.update { state ->
            if (isActiveProfile(profileId)) {
                state.copy(playbackHistoryLoading = true)
            } else {
                state
            }
        }
    }

    private fun requestLocalWatchHistoryMerge(
        profileId: Long,
        entries: List<PlaybackProgress>,
    ) {
        if (profileId in localHistoryMergeHandledProfileIds) return
        uiState.update { state ->
            val prompt = state.localWatchHistoryMergePrompt
            when {
                state.auth.profile?.id != profileId || state.forcedOfflineMode -> state
                entries.isEmpty() && prompt?.profileId == profileId -> {
                    state.copy(localWatchHistoryMergePrompt = null)
                }
                entries.isEmpty() -> state
                prompt?.profileId == profileId && prompt.entries == entries -> state
                else -> {
                    state.copy(
                        localWatchHistoryMergePrompt = LocalWatchHistoryMergePrompt(
                            profileId = profileId,
                            entryCount = entries.size,
                            entries = entries,
                        ),
                    )
                }
            }
        }
    }

    suspend fun uploadPlaybackProgressToSite(
        progressEntries: List<PlaybackProgress>,
        profileId: Long,
        lease: StateOperationLease,
    ): Boolean {
        val validEntries = progressEntries
            .filter { it.videoId > 0L }
            .distinctBy { it.videoId }
        if (validEntries.isEmpty()) return true
        for (progress in validEntries) {
            if (!lease.acceptsProfile(profileId)) return false
            if (!uploadPlaybackProgressEntry(progress, progressEntries, profileId, lease)) return false
        }
        return true
    }

    private suspend fun uploadPlaybackProgressEntry(
        progress: PlaybackProgress,
        progressEntries: List<PlaybackProgress>,
        profileId: Long,
        lease: StateOperationLease,
    ): Boolean {
        val result = runCatching { saveProgressToSite(progress) }
        val failure = result.exceptionOrNull()
        if (failure == null && result.getOrDefault(false)) return true
        if (failure is CancellationException) throw failure
        if (lease.acceptsProfile(profileId)) {
            handlePlaybackProgressUploadFailure(failure, progressEntries)
        }
        return false
    }

    private fun handlePlaybackProgressUploadFailure(
        failure: Throwable?,
        progressEntries: List<PlaybackProgress>,
    ) {
        if (failure != null) {
            requestCaptchaRetry(failure) { syncPlaybackProgressToSite(progressEntries) }
        } else {
            AppLog.w("YummyDroidHistory", "Failed to save anime watch progress on site")
        }
    }

    private fun StateOperationLease.acceptsProfile(profileId: Long): Boolean {
        return isCurrent && isActiveProfile(profileId)
    }

    private data class PlaybackHistorySyncRequest(
        val mergeLocalHistory: Boolean,
        val mergeCandidates: List<PlaybackProgress>?,
        val allowLocalHistoryMergePrompt: Boolean,
    )

    private data class PlaybackProgressSyncSnapshot(
        val progress: PlaybackProgress?,
        val history: List<PlaybackProgress>,
        val remoteAuthoritative: Boolean,
    )
}
