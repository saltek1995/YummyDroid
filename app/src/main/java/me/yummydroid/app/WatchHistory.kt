package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.distinctLatestByEpisode
import me.yummydroid.app.data.progressSyncKey
import me.yummydroid.app.data.toAnimeSummary

// WatchHistoryAnimeResolver
internal class WatchHistoryAnimeResolver(
    private val readCachedAnime: (Collection<Long>) -> Map<Long, Anime>,
    private val saveCachedAnime: (Anime) -> Unit,
    private val fetchAnimeSummary: suspend (animeId: Long) -> Anime,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun readCachedAnimeSummaries(history: List<PlaybackProgress>): List<Anime> {
        val cachedById = withContext(ioDispatcher) {
            readCachedAnime(history.map { it.animeId })
        }
        return history.toAnimeSummaries(cachedById)
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
}

// WatchHistoryPolicy
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

internal fun supplementalLocalHistoryEntries(
    localHistory: List<PlaybackProgress>,
    remoteHistory: List<PlaybackProgress>,
): List<PlaybackProgress> {
    val remoteByEpisode = remoteHistory.bestProgressBy(PlaybackProgress::progressSyncKey)
    val remoteByAnime = remoteHistory.bestProgressBy { it.animeId.toString() }
    return localHistory.filter { local ->
        local.videoId > 0L &&
            local.canSupplementEpisode(remoteByEpisode[local.progressSyncKey()]) &&
            local.canAdvanceAnime(remoteByAnime[local.animeId.toString()])
    }
}

internal fun watchHistorySyncAllowsLocalMergePrompt(
    allowLocalHistoryMergePrompt: Boolean,
    mergeLocalHistory: Boolean,
): Boolean = allowLocalHistoryMergePrompt && !mergeLocalHistory

private fun List<PlaybackProgress>.bestProgressBy(
    key: (PlaybackProgress) -> String,
): Map<String, PlaybackProgress> {
    return groupBy(key).mapValues { (_, entries) ->
        entries.maxWithOrNull(progressAdvanceComparator)
            ?: entries.maxByOrNull { it.updatedAtMs }
            ?: error("Progress group is empty")
    }
}

private val progressAdvanceComparator = compareBy<PlaybackProgress>(
    { progress -> if (progress.episodeNumberOrNull() != null) 1 else 0 },
    { progress -> progress.episodeNumberOrNull() ?: Double.NEGATIVE_INFINITY },
    { progress -> progress.positionMs },
    { progress -> progress.updatedAtMs },
)

private fun PlaybackProgress.canSupplementEpisode(remote: PlaybackProgress?): Boolean {
    if (remote == null) return true
    return positionMs > remote.positionMs ||
        (positionMs == remote.positionMs && updatedAtMs > remote.updatedAtMs)
}

private fun PlaybackProgress.canAdvanceAnime(remote: PlaybackProgress?): Boolean {
    if (remote == null) return true
    val localEpisode = episodeNumberOrNull()
    val remoteEpisode = remote.episodeNumberOrNull()
    return when {
        localEpisode != null && remoteEpisode != null && localEpisode != remoteEpisode -> {
            localEpisode > remoteEpisode
        }
        progressSyncKey() == remote.progressSyncKey() || localEpisode != null && localEpisode == remoteEpisode -> {
            positionMs > remote.positionMs ||
                (positionMs == remote.positionMs && updatedAtMs > remote.updatedAtMs)
        }
        else -> false
    }
}

private fun PlaybackProgress.episodeNumberOrNull(): Double? = episode.trim().toDoubleOrNull()

internal fun selectHistoryProgress(
    localHistory: List<PlaybackProgress>,
    remoteHistory: List<PlaybackProgress>,
    remoteFailed: Boolean,
    canUseRemote: Boolean,
): List<PlaybackProgress>? {
    return when {
        canUseRemote && !remoteFailed -> remoteHistory
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

internal fun List<PlaybackProgress>.toAnimeSummaries(cachedById: Map<Long, Anime>): List<Anime> {
    return map { progress -> cachedById[progress.animeId] ?: progress.toAnimeSummary() }
        .distinctBy { it.id }
}

private const val WATCH_HISTORY_PAGE_SIZE = 100
private const val MAX_HISTORY_PAGES_WITHOUT_NEW_ENTRIES = 2

// WatchHistoryProgressSync
internal class WatchHistoryProgressSync(
    private val readProgress: () -> List<PlaybackProgress>,
    private val saveProgressIfNewer: (PlaybackProgress) -> Unit,
    private val replaceProgressHistory: (List<PlaybackProgress>) -> Unit = { history ->
        history.forEach(saveProgressIfNewer)
    },
    private val replaceAnimeProgressHistory: (Long, List<PlaybackProgress>) -> Unit = { _, history ->
        history.forEach(saveProgressIfNewer)
    },
    private val fetchHistoryPage: suspend (limit: Int, offset: Int) -> List<PlaybackProgress>,
    private val uploadProgress: suspend (PlaybackProgress) -> Boolean,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun readLatestLocalProgress(): List<PlaybackProgress> = withContext(ioDispatcher) {
        readProgress().latestHistoryByAnime()
    }

    suspend fun readAllLocalProgress(): List<PlaybackProgress> = withContext(ioDispatcher) {
        readProgress()
    }

    suspend fun fetchRemoteHistory(): Result<List<PlaybackProgress>> = runCatching {
        collectWatchHistoryPages(fetchPage = fetchHistoryPage)
    }.onFailure { throwable ->
        if (throwable is CancellationException) throw throwable
    }

    suspend fun storeRemoteHistory(history: List<PlaybackProgress>) {
        withContext(ioDispatcher) {
            replaceProgressHistory(history)
        }
    }

    suspend fun storeRemoteAnimeHistory(animeId: Long, history: List<PlaybackProgress>) {
        withContext(ioDispatcher) {
            replaceAnimeProgressHistory(animeId, history)
        }
    }

    suspend fun uploadSupplementalLocalProgress(
        localHistory: List<PlaybackProgress>,
        remoteHistory: List<PlaybackProgress>,
    ): Boolean {
        var success = true
        supplementalLocalHistoryEntries(localHistory, remoteHistory).forEach { progress ->
            val uploaded = runCatching { uploadProgress(progress) }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                }
                .getOrDefault(false)
            if (!uploaded) success = false
        }
        return success
    }
}

// WatchHistoryRefreshState
internal class WatchHistoryRefreshState(
    private val monotonicClockMs: () -> Long,
    private val refreshIntervalMs: Long,
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

    fun reset() {
        cacheInitialized = false
        lastRemoteCheckAtMs = 0L
    }

    fun markRemoteCheckStarted() {
        lastRemoteCheckAtMs = monotonicClockMs()
    }

    fun markRemoteSynchronized() {
        cacheInitialized = true
        markRemoteCheckStarted()
    }

    private fun remoteRefreshDue(): Boolean {
        return lastRemoteCheckAtMs == 0L ||
            monotonicClockMs() - lastRemoteCheckAtMs >= refreshIntervalMs
    }
}

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
