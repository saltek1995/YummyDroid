package me.yummydroid.app

import android.app.Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.HistoryAnimeCacheStorage
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.PlaybackProgressStorage
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.withOfflineDownloads
import me.yummydroid.app.data.YummyAnimeRepository

internal class OfflineContentRuntime(
    private val application: Application,
    private val scope: CoroutineScope,
    private val repository: YummyAnimeRepository,
    private val playbackProgressStorage: PlaybackProgressStorage,
    private val historyAnimeCacheStorage: HistoryAnimeCacheStorage,
    private val cacheMaintenanceOperations: SerialStateOperationCoordinator,
    private val detailsLoadOperations: LatestStateOperationCoordinator,
    private val playbackProgressOperations: KeyedLatestStateOperationCoordinator<Long>,
    private val playbackHistoryOperations: LatestStateOperationCoordinator,
    private val browseContentCoordinator: BrowseContentCoordinator,
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val clearDetailsRouteCache: () -> Unit,
    private val refresh: () -> Unit,
    private val showNotice: (String) -> Unit,
    private val stringResource: (Int) -> String,
) {
    private var downloadQueueJob: Job? = null


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
            DownloadService.enqueuePlan(application, planId, plan.animeId)
        }
    }

    fun deleteOfflineVideo(animeId: Long, videoId: Long, playbackUrl: String? = null) {
        deleteOfflineContent(animeId, videoId, playbackUrl)
    }

    fun deleteOfflineAnime(animeId: Long) {
        deleteOfflineContent(animeId, null, null)
    }

    private fun deleteOfflineContent(animeId: Long, videoId: Long?, playbackUrl: String?) {
        cacheMaintenanceOperations.launch(scope) {
            try {
                val target = if (videoId == null) {
                    DownloadRemoval(animeId)
                } else {
                    val videos = repository.offlineAnime().firstOrNull { it.anime.id == animeId }?.videos.orEmpty()
                    val episodeKey = videos.firstOrNull { it.id == videoId }?.matchingEpisodeKey
                    val videoIds = videos.filter { episodeKey != null && it.matchingEpisodeKey == episodeKey }
                        .mapTo(mutableSetOf(videoId)) { it.id }
                    DownloadRemoval(animeId, videoIds, setOfNotNull(episodeKey))
                }
                DownloadService.withCacheMaintenance(target) {
                    val removedPlans = withContext(Dispatchers.IO) { DownloadPlanStorage(application).removeTargets(target) }
                    DownloadCenter.cancelTargets(target, removedPlans)
                    if (videoId == null) repository.deleteOfflineAnime(animeId)
                    else repository.deleteOfflineVideo(animeId, videoId, playbackUrl)
                }
                refreshAppContentCacheSize()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                showNotice(throwable.userMessage())
            }
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
        playbackProgressOperations.cancelAll()
        playbackHistoryOperations.cancel()
        cacheMaintenanceOperations.launch(scope) {
            try {
                DownloadService.withCacheMaintenance {
                    UpdateDownloadService.withCacheMaintenance(application) {
                        repository.clearAppContentCache(playbackProgressStorage)
                        val sizeBytes = withContext(Dispatchers.IO) {
                            DownloadPlanStorage(application).clear()
                            historyAnimeCacheStorage.clear()
                            application.clearRuntimeCacheDirectories()
                            calculateAppContentCacheSize(application)
                        }
                        clearDetailsRouteCache()
                        browseContentCoordinator.clearCaches()
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
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                showNotice(throwable.userMessage())
            }
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
            launch {
                repository.offlineContentChanges.collect {
                    browseContentCoordinator.loadOfflineEntries()
                }
            }
            DownloadCenter.state.collect { snapshot ->
                val active = snapshot.activeTasks.firstOrNull()
                val latest = snapshot.tasks.firstOrNull()
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
                                message = latest.message.ifBlank { stringResource(latest.state.titleRes) },
                            )
                            else -> state.offlineDownload.copy(isRunning = false)
                        },
                    )
                }

            }
        }
    }
}

// Reconcile every new video list (including restored routes) with the latest local snapshot.
internal fun YummyDroidUiState.withCurrentOfflineVideos(previous: YummyDroidUiState): YummyDroidUiState {
    if (videos === previous.videos && offlineEntries === previous.offlineEntries) return this
    val entries = offlineEntries.readyDataOrNull() ?: return this
    val currentVideos = videos.readyDataOrNull() ?: return this
    val animeId = details.readyDataOrNull()?.id ?: return this
    val localVideos = entries.firstOrNull { it.anime.id == animeId }?.videos.orEmpty()
    return copy(videos = LoadState.Ready(currentVideos.withOfflineDownloads(localVideos))).withOfflineDetailsState()
}

internal fun YummyDroidUiState.withOfflineDetailsState(): YummyDroidUiState {
    if (!forcedOfflineMode) return this
    val playable = videos.readyListOrEmpty().filter(VideoVariant::isOfflineAvailable)
    return copy(
        selectedVideoGroup = selectedVideoGroup?.takeIf { group -> playable.any { it.groupKey == group } }
            ?: selectInitialVideoGroup(playable, offlineMode = true),
        detailsExtras = LoadState.Ready(detailsExtras.readyDataOrNull() ?: AnimeDetailsExtras()),
        animeMark = LoadState.Ready(animeMark.readyDataOrNull()),
        playbackHistoryLoading = false,
    )
}
