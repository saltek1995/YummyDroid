package me.yummydroid.app

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.progressSyncKey
import me.yummydroid.app.data.toAnimeSummary

internal sealed interface WatchHistoryResolution {
    data class Ready(val anime: List<Anime>) : WatchHistoryResolution

    data class Failed(val cause: Throwable) : WatchHistoryResolution
}

internal data class WatchHistoryRefreshPlan(
    val showCachedSnapshot: Boolean,
)

internal class WatchHistoryCoordinator(
    private val readProgress: () -> List<PlaybackProgress>,
    private val saveProgressIfNewer: (PlaybackProgress) -> Unit,
    private val readCachedAnime: (Collection<Long>) -> Map<Long, Anime>,
    private val saveCachedAnime: (Anime) -> Unit,
    private val fetchHistoryPage: suspend (limit: Int, offset: Int) -> List<PlaybackProgress>,
    private val uploadProgress: suspend (PlaybackProgress) -> Boolean,
    private val fetchAnimeSummary: suspend (animeId: Long) -> Anime,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val monotonicClockMs: () -> Long = System::currentTimeMillis,
    private val refreshIntervalMs: Long = BROWSE_REMOTE_REFRESH_INTERVAL_MS,
) {
    private var cacheInitialized = false
    private var lastRemoteCheckAtMs = 0L

    fun beginRefresh(
        force: Boolean,
        hasReadyHistory: Boolean,
        canUseRemote: Boolean,
        loadActive: Boolean,
    ): WatchHistoryRefreshPlan? {
        val plan = watchHistoryRefreshPlan(
            force = force,
            hasReadyHistory = hasReadyHistory,
            canUseRemote = canUseRemote,
            loadActive = loadActive,
            cacheInitialized = cacheInitialized,
            remoteRefreshDue = canUseRemote && remoteRefreshDue(),
        ) ?: return null
        cacheInitialized = true
        return plan
    }

    fun resetRefreshState() {
        cacheInitialized = false
        lastRemoteCheckAtMs = 0L
    }

    fun markRemoteSynchronized() {
        cacheInitialized = true
        lastRemoteCheckAtMs = monotonicClockMs()
    }

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
            lastRemoteCheckAtMs = monotonicClockMs()
            fetchRemoteHistory()
        } else {
            Result.success(emptyList())
        }
        val remoteFailure = remoteResult.exceptionOrNull()
        if (remoteFailure != null && shouldRetryRemoteFailure(remoteFailure)) return null

        return reconcileRemoteHistory(
            remoteResult = remoteResult,
            canUseRemote = remoteEnabled,
        )
    }

    suspend fun readLatestLocalProgress(): List<PlaybackProgress> = withContext(ioDispatcher) {
        readProgress().latestHistoryByAnime()
    }

    suspend fun readCachedAnimeSummaries(history: List<PlaybackProgress>): List<Anime> {
        val cachedById = withContext(ioDispatcher) {
            readCachedAnime(history.map { it.animeId })
        }
        return history.toAnimeSummaries(cachedById)
    }

    suspend fun fetchRemoteHistory(): Result<List<PlaybackProgress>> = runCatching {
        collectWatchHistoryPages(fetchPage = fetchHistoryPage)
    }

    suspend fun reconcileRemoteHistory(
        remoteResult: Result<List<PlaybackProgress>>,
        canUseRemote: Boolean,
    ): WatchHistoryResolution {
        val remoteHistory = remoteResult.getOrDefault(emptyList())
        storeRemoteHistory(remoteHistory)
        val localHistory = withContext(ioDispatcher) { readProgress() }
        if (canUseRemote) {
            uploadNewerLocalProgress(localHistory, remoteHistory)
        }

        val selectedHistory = selectHistoryProgress(
            localHistory = localHistory.latestHistoryByAnime(),
            remoteHistory = remoteHistory.latestHistoryByAnime(),
            remoteFailed = remoteResult.isFailure,
            canUseRemote = canUseRemote,
        )
        if (selectedHistory == null) {
            return WatchHistoryResolution.Failed(
                remoteResult.exceptionOrNull() ?: IllegalStateException("Watch history is unavailable"),
            )
        }
        return WatchHistoryResolution.Ready(resolveAnimeSummaries(selectedHistory))
    }

    suspend fun storeRemoteHistory(history: List<PlaybackProgress>) {
        withContext(ioDispatcher) {
            history.forEach(saveProgressIfNewer)
        }
    }

    suspend fun uploadNewerLocalProgress(
        localHistory: List<PlaybackProgress>,
        remoteHistory: List<PlaybackProgress>,
    ) {
        newerLocalHistoryEntries(localHistory, remoteHistory).forEach { progress ->
            runCatching { uploadProgress(progress) }
        }
    }

    suspend fun resolveAnimeSummaries(history: List<PlaybackProgress>): List<Anime> {
        val cachedById = withContext(ioDispatcher) {
            readCachedAnime(history.map { it.animeId }).toMutableMap()
        }
        val resolved = history.map { progress ->
            cachedById[progress.animeId] ?: runCatching {
                fetchAnimeSummary(progress.animeId)
            }.onSuccess { anime ->
                if (anime.id > 0L) {
                    withContext(ioDispatcher) { saveCachedAnime(anime) }
                    cachedById[anime.id] = anime
                }
            }.getOrElse {
                progress.toAnimeSummary()
            }
        }
        return resolved.distinctBy { it.id }
    }

    private fun remoteRefreshDue(): Boolean {
        return lastRemoteCheckAtMs == 0L ||
            monotonicClockMs() - lastRemoteCheckAtMs >= refreshIntervalMs
    }
}

internal fun watchHistoryRefreshPlan(
    force: Boolean,
    hasReadyHistory: Boolean,
    canUseRemote: Boolean,
    loadActive: Boolean,
    cacheInitialized: Boolean,
    remoteRefreshDue: Boolean,
): WatchHistoryRefreshPlan? {
    if (loadActive && !force) return null
    if (force) return WatchHistoryRefreshPlan(showCachedSnapshot = true)

    val cachedSnapshotMissing = !cacheInitialized || !hasReadyHistory
    if (cachedSnapshotMissing) return WatchHistoryRefreshPlan(showCachedSnapshot = true)
    if (canUseRemote && remoteRefreshDue) return WatchHistoryRefreshPlan(showCachedSnapshot = false)
    return null
}

internal fun List<PlaybackProgress>.latestHistoryByAnime(): List<PlaybackProgress> {
    return groupBy { it.animeId }
        .values
        .mapNotNull { entries -> entries.maxByOrNull { it.updatedAtMs } }
        .sortedByDescending { it.updatedAtMs }
}

internal fun newerLocalHistoryEntries(
    localHistory: List<PlaybackProgress>,
    remoteHistory: List<PlaybackProgress>,
): List<PlaybackProgress> {
    val remoteByEpisode = remoteHistory.associateBy { it.progressSyncKey() }
    return localHistory.filter { local ->
        local.videoId > 0L && local.isNewerThan(remoteByEpisode[local.progressSyncKey()])
    }
}

internal fun selectHistoryProgress(
    localHistory: List<PlaybackProgress>,
    remoteHistory: List<PlaybackProgress>,
    remoteFailed: Boolean,
    canUseRemote: Boolean,
): List<PlaybackProgress>? {
    return when {
        localHistory.isNotEmpty() -> localHistory
        remoteHistory.isNotEmpty() -> remoteHistory
        remoteFailed && canUseRemote -> null
        else -> emptyList()
    }
}

internal suspend fun collectWatchHistoryPages(
    pageSize: Int = WATCH_HISTORY_PAGE_SIZE,
    maxOffset: Int = WATCH_HISTORY_MAX_OFFSET,
    fetchPage: suspend (limit: Int, offset: Int) -> List<PlaybackProgress>,
): List<PlaybackProgress> {
    val history = mutableListOf<PlaybackProgress>()
    val seenKeys = mutableSetOf<String>()
    var offset = 0
    var pagesWithoutNewEntries = 0
    while (offset <= maxOffset) {
        val pageEntries = fetchPage(pageSize, offset)
        if (pageEntries.isEmpty()) return history

        val uniqueEntries = pageEntries.filter { seenKeys.add(it.progressSyncKey()) }
        if (uniqueEntries.isNotEmpty()) {
            history += uniqueEntries
            pagesWithoutNewEntries = 0
        } else {
            pagesWithoutNewEntries += 1
            if (pagesWithoutNewEntries >= MAX_HISTORY_PAGES_WITHOUT_NEW_ENTRIES) return history
        }
        offset += pageSize
    }
    return history
}

private fun List<PlaybackProgress>.toAnimeSummaries(cachedById: Map<Long, Anime>): List<Anime> {
    return map { progress -> cachedById[progress.animeId] ?: progress.toAnimeSummary() }
        .distinctBy { it.id }
}

private const val WATCH_HISTORY_PAGE_SIZE = 100
private const val MAX_HISTORY_PAGES_WITHOUT_NEW_ENTRIES = 2
