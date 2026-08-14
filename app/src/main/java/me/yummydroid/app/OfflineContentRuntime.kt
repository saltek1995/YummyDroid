package me.yummydroid.app

import android.app.Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.HistoryAnimeCacheStorage
import me.yummydroid.app.data.PlaybackProgressStorage
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.YummyAnimeRepository

internal class OfflineContentRuntime(
    private val application: Application,
    private val scope: CoroutineScope,
    private val repository: YummyAnimeRepository,
    private val playbackProgressStorage: PlaybackProgressStorage,
    private val historyAnimeCacheStorage: HistoryAnimeCacheStorage,
    private val cacheMaintenanceOperations: SerialStateOperationCoordinator,
    private val detailsLoadOperations: LatestStateOperationCoordinator,
    private val offlineDetailsRefreshOperations: KeyedLatestStateOperationCoordinator<Long>,
    private val playbackProgressOperations: KeyedLatestStateOperationCoordinator<Long>,
    private val playbackHistoryOperations: LatestStateOperationCoordinator,
    private val browseContentCoordinator: BrowseContentCoordinator,
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val cacheDetailsRouteState: (Long) -> Unit,
    private val clearDetailsRouteCache: () -> Unit,
    private val refresh: () -> Unit,
    private val showNotice: (String) -> Unit,
    private val stringResource: (Int) -> String,
) {
    private var downloadQueueJob: Job? = null
    private var completedDownloadTaskIds: Set<Long> = emptySet()

    fun downloadVideoForOffline(video: VideoVariant, preferredQuality: PreferredQuality = PreferredQuality.Auto) {
        if (currentState().forcedOfflineMode) {
            updateState {
                it.copy(
                    offlineDownload = OfflineDownloadUiState(
                        videoId = video.id,
                        isRunning = false,
                        message = stringResource(R.string.ui_download_unavailable_offline),
                    ),
                )
            }
            return
        }
        DownloadService.enqueueVideo(
            context = application,
            animeId = video.animeId,
            videoId = video.id,
            groupKey = video.groupKey,
            quality = preferredQuality,
        )
        updateState {
            it.copy(
                offlineDownload = OfflineDownloadUiState(
                    videoId = video.id,
                    isRunning = true,
                    progress = 0f,
                    message = stringResource(R.string.ui_added),
                ),
            )
        }
    }

    suspend fun resolveAvailableDownloadQualities(
        video: VideoVariant,
        videos: List<VideoVariant>,
        allEpisodes: Boolean,
    ): List<PreferredQuality> {
        if (currentState().forcedOfflineMode) return emptyList()
        return repository.resolveAvailableDownloadQualities(video, videos, allEpisodes)
    }

    suspend fun resolveSampledDownloadQualities(
        selectedVoiceKeys: Set<String>,
        videos: List<VideoVariant>,
    ): Map<String, List<PreferredQuality>> {
        if (currentState().forcedOfflineMode) return emptyMap()
        return repository.resolveSampledDownloadQualities(selectedVoiceKeys, videos)
    }

    fun downloadAllVideosForOffline(plan: DownloadPlan) {
        val state = currentState()
        if (state.forcedOfflineMode) {
            updateState {
                it.copy(
                    offlineDownload = OfflineDownloadUiState(
                        isRunning = false,
                        message = stringResource(R.string.ui_download_unavailable_offline),
                    ),
                )
            }
            return
        }
        if (plan.items.isEmpty()) return
        updateState {
            it.copy(
                offlineDownload = OfflineDownloadUiState(
                    isRunning = true,
                    progress = 0f,
                    message = stringResource(R.string.ui_added),
                ),
            )
        }
        cacheMaintenanceOperations.launch(scope) {
            val planId = withContext(Dispatchers.IO) { DownloadPlanStorage(application).save(plan) }
            DownloadService.enqueuePlan(application, planId)
        }
    }

    fun deleteOfflineVideo(animeId: Long, videoId: Long, playbackUrl: String? = null) {
        cacheMaintenanceOperations.launch(scope) {
            repository.deleteOfflineVideo(animeId, videoId, playbackUrl)
            refreshCurrentDetailsFromOfflineCache(animeId)
            browseContentCoordinator.loadOfflineEntries()
            refreshAppContentCacheSize()
        }
    }

    fun deleteOfflineAnime(animeId: Long) {
        cacheMaintenanceOperations.launch(scope) {
            repository.deleteOfflineAnime(animeId)
            refreshCurrentDetailsFromOfflineCache(animeId)
            browseContentCoordinator.loadOfflineEntries()
            refreshAppContentCacheSize()
        }
    }

    fun refreshAppContentCacheSize() {
        cacheMaintenanceOperations.launch(scope) {
            val sizeBytes = withContext(Dispatchers.IO) {
                calculateAppContentCacheSize(application)
            }
            updateState { it.copy(appContentCacheSizeBytes = sizeBytes) }
        }
    }

    fun clearAppContentCache() {
        detailsLoadOperations.cancel()
        offlineDetailsRefreshOperations.cancelAll()
        playbackProgressOperations.cancelAll()
        playbackHistoryOperations.cancel()
        cacheMaintenanceOperations.launch(scope) {
            repository.clearAppContentCache(playbackProgressStorage)
            val sizeBytes = withContext(Dispatchers.IO) {
                historyAnimeCacheStorage.clear()
                application.clearRuntimeCacheDirectories()
                calculateAppContentCacheSize(application)
            }
            clearDetailsRouteCache()
            browseContentCoordinator.clearCaches()
            DownloadCenter.clearAll()
            updateState {
                it.copy(
                    playbackProgress = null,
                    playbackHistory = emptyList(),
                    historyAnime = if (it.homeSection == BrowseSection.History) {
                        LoadState.Loading
                    } else {
                        LoadState.Ready(emptyList())
                    },
                    offlineEntries = LoadState.Ready(emptyList()),
                    downloadQueue = DownloadQueueSnapshot(),
                    offlineDownload = OfflineDownloadUiState(message = stringResource(R.string.ui_cache_cleared)),
                    appContentCacheSizeBytes = sizeBytes,
                )
            }
            refresh()
        }
    }

    fun clearDownloadHistory() {
        DownloadCenter.clearHistory()
    }

    fun cancelDownload(taskId: Long) {
        DownloadCenter.requestCancel(taskId)
    }

    fun pauseDownload(taskId: Long) {
        DownloadCenter.requestPause(taskId)
    }

    fun resumeDownload(taskId: Long) {
        DownloadCenter.resumeTask(application, taskId)
    }

    fun observeDownloadQueue() {
        downloadQueueJob?.cancel()
        downloadQueueJob = scope.launch {
            DownloadCenter.state.collect { snapshot ->
                val active = snapshot.activeTasks.firstOrNull()
                val latest = snapshot.tasks.firstOrNull()
                val completedIds = snapshot.tasks
                    .filter { it.state == DownloadTaskState.Completed }
                    .map { it.id }
                    .toSet()
                val hasNewCompletion = completedIds.any { it !in completedDownloadTaskIds }
                completedDownloadTaskIds = completedIds

                updateState { state ->
                    state.copy(
                        downloadQueue = snapshot,
                        offlineDownload = when {
                            active != null -> OfflineDownloadUiState(
                                videoId = active.videoId,
                                isRunning = true,
                                progress = active.progress,
                                message = active.message.ifBlank { stringResource(R.string.ui_loading) },
                            )
                            latest != null -> OfflineDownloadUiState(
                                videoId = latest.videoId,
                                isRunning = false,
                                progress = latest.progress,
                                message = latest.message.ifBlank { latest.state.title },
                            )
                            else -> state.offlineDownload.copy(isRunning = false)
                        },
                    )
                }

                if (hasNewCompletion) {
                    browseContentCoordinator.loadOfflineEntries()
                    val currentAnimeId = currentState().details.readyDataOrNull()?.id
                    if (currentAnimeId != null) {
                        refreshCurrentDetailsFromOfflineCache(currentAnimeId)
                    }
                }
            }
        }
    }

    private fun refreshCurrentDetailsFromOfflineCache(animeId: Long) {
        if (currentState().details.readyDataOrNull()?.id != animeId) return
        offlineDetailsRefreshOperations.launchLatest(animeId, scope) { lease ->
            runCatching { repository.getAnimeWithVideos(animeId) }
                .onSuccess { (details, videos) ->
                    val progress = withContext(Dispatchers.IO) { playbackProgressStorage.read(animeId) }
                    val history = withContext(Dispatchers.IO) { playbackProgressStorage.readAnimeHistory(animeId) }
                    if (!lease.isCurrent) return@onSuccess
                    var accepted = false
                    updateState { state ->
                        if ((state.route as? AppRoute.Details)?.animeId != animeId) return@updateState state
                        accepted = true
                        state.copy(
                            details = LoadState.Ready(details),
                            videos = LoadState.Ready(videos),
                            playbackProgress = progress,
                            playbackHistory = history,
                        )
                    }
                    if (accepted) cacheDetailsRouteState(animeId)
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    val progress = withContext(Dispatchers.IO) { playbackProgressStorage.read(animeId) }
                    val history = withContext(Dispatchers.IO) { playbackProgressStorage.readAnimeHistory(animeId) }
                    var accepted = false
                    updateState { state ->
                        if ((state.route as? AppRoute.Details)?.animeId != animeId) return@updateState state
                        accepted = true
                        state.copy(
                            playbackProgress = progress,
                            playbackHistory = history,
                        )
                    }
                    if (accepted) {
                        cacheDetailsRouteState(animeId)
                        showNotice(throwable.userMessage())
                    }
                }
        }
    }
}
