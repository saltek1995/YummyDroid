package me.yummydroid.app

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.PlaybackProgress

// WatchHistoryRuntime
internal sealed interface WatchHistoryResolution {
    data class Ready(val anime: List<Anime>) : WatchHistoryResolution

    data class Failed(val cause: Throwable) : WatchHistoryResolution
}

internal data class WatchHistoryRefreshPlan(
    val showCachedSnapshot: Boolean,
)

internal class WatchHistoryCoordinator(
    readProgress: () -> List<PlaybackProgress>,
    saveProgressIfNewer: (PlaybackProgress) -> Unit,
    replaceProgressHistory: (List<PlaybackProgress>) -> Unit = { history ->
        history.forEach(saveProgressIfNewer)
    },
    replaceAnimeProgressHistory: (Long, List<PlaybackProgress>) -> Unit = { _, history ->
        history.forEach(saveProgressIfNewer)
    },
    readCachedAnime: (Collection<Long>) -> Map<Long, Anime>,
    saveCachedAnime: (Anime) -> Unit,
    fetchHistoryPage: suspend (limit: Int, offset: Int) -> List<PlaybackProgress>,
    uploadProgress: suspend (PlaybackProgress) -> Boolean,
    fetchAnimeSummary: suspend (animeId: Long) -> Anime,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    monotonicClockMs: () -> Long = System::currentTimeMillis,
    refreshIntervalMs: Long = BROWSE_REMOTE_REFRESH_INTERVAL_MS,
) {
    private val refreshState = WatchHistoryRefreshState(monotonicClockMs, refreshIntervalMs)
    private val progressSync = WatchHistoryProgressSync(
        readProgress = readProgress,
        saveProgressIfNewer = saveProgressIfNewer,
        replaceProgressHistory = replaceProgressHistory,
        replaceAnimeProgressHistory = replaceAnimeProgressHistory,
        fetchHistoryPage = fetchHistoryPage,
        uploadProgress = uploadProgress,
        ioDispatcher = ioDispatcher,
    )
    private val animeResolver = WatchHistoryAnimeResolver(
        readCachedAnime = readCachedAnime,
        saveCachedAnime = saveCachedAnime,
        fetchAnimeSummary = fetchAnimeSummary,
        ioDispatcher = ioDispatcher,
    )

    fun beginRefresh(
        force: Boolean,
        hasReadyHistory: Boolean,
        canUseRemote: Boolean,
        loadActive: Boolean,
    ): WatchHistoryRefreshPlan? {
        return refreshState.beginRefresh(force, hasReadyHistory, canUseRemote, loadActive)
    }

    fun resetRefreshState() = refreshState.reset()

    fun markRemoteSynchronized() = refreshState.markRemoteSynchronized()

    suspend fun load(
        plan: WatchHistoryRefreshPlan,
        canUseRemote: () -> Boolean,
        onCachedSnapshot: (List<Anime>) -> Unit,
        shouldRetryRemoteFailure: (Throwable) -> Boolean,
    ): WatchHistoryResolution? {
        val localHistorySnapshot = readLatestLocalProgress()
        if (plan.showCachedSnapshot && localHistorySnapshot.isNotEmpty()) {
            onCachedSnapshot(readCachedAnimeSummaries(localHistorySnapshot))
        }

        val remoteEnabled = canUseRemote()
        val remoteResult = if (remoteEnabled) {
            refreshState.markRemoteCheckStarted()
            fetchRemoteHistory()
        } else {
            Result.success(emptyList())
        }
        val remoteFailure = remoteResult.exceptionOrNull()
        if (remoteFailure != null && shouldRetryRemoteFailure(remoteFailure)) return null

        return reconcileRemoteHistory(remoteResult, remoteEnabled)
    }

    suspend fun readLatestLocalProgress(): List<PlaybackProgress> {
        return progressSync.readLatestLocalProgress()
    }

    suspend fun readCachedAnimeSummaries(history: List<PlaybackProgress>): List<Anime> {
        return animeResolver.readCachedAnimeSummaries(history)
    }

    suspend fun fetchRemoteHistory(): Result<List<PlaybackProgress>> {
        return progressSync.fetchRemoteHistory()
    }

    suspend fun reconcileRemoteHistory(
        remoteResult: Result<List<PlaybackProgress>>,
        canUseRemote: Boolean,
    ): WatchHistoryResolution {
        val remoteHistory = remoteResult.getOrDefault(emptyList())
        if (canUseRemote && remoteResult.isSuccess) {
            storeRemoteHistory(remoteHistory)
        }
        val localHistory = progressSync.readAllLocalProgress()
        val selectedHistory = selectHistoryProgress(
            localHistory = localHistory.latestHistoryByAnime(),
            remoteHistory = remoteHistory.latestHistoryByAnime(),
            remoteFailed = remoteResult.isFailure,
            canUseRemote = canUseRemote,
        ) ?: return WatchHistoryResolution.Failed(
            remoteResult.exceptionOrNull() ?: IllegalStateException("Watch history is unavailable"),
        )
        return WatchHistoryResolution.Ready(resolveAnimeSummaries(selectedHistory))
    }

    suspend fun storeRemoteHistory(history: List<PlaybackProgress>) {
        progressSync.storeRemoteHistory(history)
    }

    suspend fun storeRemoteAnimeHistory(animeId: Long, history: List<PlaybackProgress>) {
        progressSync.storeRemoteAnimeHistory(animeId, history)
    }

    suspend fun uploadSupplementalLocalProgress(
        localHistory: List<PlaybackProgress>,
        remoteHistory: List<PlaybackProgress>,
    ): Boolean {
        return progressSync.uploadSupplementalLocalProgress(localHistory, remoteHistory)
    }

    suspend fun resolveAnimeSummaries(history: List<PlaybackProgress>): List<Anime> {
        return animeResolver.resolveAnimeSummaries(history)
    }
}
