package me.yummydroid.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.YummyAnimeRepository
import me.yummydroid.app.data.canMaybeProvideDownloadQuality
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.downloadCandidatesFor
import me.yummydroid.app.data.downloadEpisodeSlotKey
import me.yummydroid.app.data.downloadVoiceSlotKey
import me.yummydroid.app.data.hasDownloadedQuality
import me.yummydroid.app.data.isCompletedDownload
import me.yummydroid.app.data.matchingDisplayVoiceTitle
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.maxKnownSourceQualityHeight
import me.yummydroid.app.data.sourceProviderRank
import me.yummydroid.app.data.sourceResolveIdentity

// DownloadNetworkPolicy
object DownloadNetworkPolicy {
    fun canDownloadNow(context: Context, settings: AppSettings): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        if (settings.allowMeteredDownloads) return true
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun waitingMessage(context: Context, settings: AppSettings): String {
        val messageResId = if (settings.allowMeteredDownloads) {
            R.string.ui_download_network_waiting
        } else {
            R.string.ui_download_network_waiting_unmetered
        }
        return context.localizedString(messageResId, settings.contentLanguage)
    }
}

// DownloadSourceSelection
internal fun List<VideoVariant>.downloadRetryCandidatesFor(
    requested: VideoVariant,
    preferredQuality: PreferredQuality,
): List<VideoVariant> {
    return downloadCandidatesFor(requested)
        .asSequence()
        .filter { candidate -> candidate.canMaybeProvideDownloadQuality(preferredQuality) }
        .distinctBy { candidate -> candidate.sourceResolveIdentity() }
        .sortedWith(downloadRetryCandidateComparator(requested, preferredQuality))
        .toList()
        .ifEmpty { listOf(requested) }
}

internal fun List<VideoVariant>.downloadRetryCandidateForAttempt(attempt: Int): VideoVariant? {
    if (isEmpty()) return null
    val index = (attempt - 1).coerceAtLeast(0) % size
    return this[index]
}

private fun downloadRetryCandidateComparator(
    requested: VideoVariant,
    preferredQuality: PreferredQuality,
): Comparator<VideoVariant> {
    return compareByDescending<VideoVariant> { candidate -> candidate.id == requested.id }
        .thenBy { candidate -> candidate.downloadQualityAvailabilityRank(preferredQuality) }
        .thenBy { candidate -> sourceProviderRank(candidate.player) }
        .thenByDescending { candidate -> candidate.maxKnownSourceQualityHeight() }
        .thenBy { candidate -> candidate.index }
        .thenBy { candidate -> candidate.id }
}

private fun VideoVariant.downloadQualityAvailabilityRank(preferredQuality: PreferredQuality): Int {
    val preferredHeight = preferredQuality.height ?: return 0
    if (sourceQualities.any { it.height == preferredHeight }) return 0
    return 1
}

// DownloadSpeedSettings
internal class DownloadSpeedSettings(
    private val settingsStorage: AppSettingsStorage,
    initialLimitBytesPerSecond: Long,
    initialReadMs: Long,
) {
    @Volatile
    private var limitBytesPerSecond = initialLimitBytesPerSecond
    private val lock = Any()
    private var lastReadMs = initialReadMs

    fun currentLimitBytesPerSecond(): Long {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (now - lastReadMs >= SPEED_LIMIT_SETTINGS_REFRESH_MS) {
                limitBytesPerSecond = settingsStorage.read().downloadSpeedLimitBytesPerSecond
                lastReadMs = now
            }
            return limitBytesPerSecond
        }
    }
}

private const val SPEED_LIMIT_SETTINGS_REFRESH_MS = 1_000L

// DownloadTargetSelection
internal fun downloadBatchKey(
    animeId: Long,
    videoId: Long?,
    groupKey: String,
    quality: PreferredQuality,
): String {
    return listOf(
        animeId.toString(),
        videoId?.toString() ?: "all",
        groupKey,
        quality.name,
        System.currentTimeMillis().toString(),
    ).joinToString(":")
}

internal fun List<VideoVariant>.selectDownloadAllTargets(preferredGroupKey: String): List<VideoVariant> {
    val preferredVoiceKey = firstOrNull { it.groupKey == preferredGroupKey }
        ?.matchingVoiceKey
    return groupBy { it.downloadEpisodeSlotKey }
        .toSortedMap(compareBy<String> { it.toDoubleOrNull() ?: Double.MAX_VALUE }.thenBy { it })
        .values
        .mapNotNull { episodeVideos ->
            if (preferredVoiceKey != null) {
                episodeVideos
                    .filter { it.matchingVoiceKey == preferredVoiceKey }
                    .sortedWith(downloadTargetComparator(preferredGroupKey))
                    .firstOrNull()
            } else {
                episodeVideos.sortedWith(downloadTargetComparator()).firstOrNull()
            }
        }
}

internal fun List<VideoVariant>.hasDownloadedRequestedSlot(
    video: VideoVariant,
    preferredQuality: PreferredQuality,
): Boolean {
    val key = video.downloadVoiceSlotKey
    return any { candidate ->
        candidate.downloadVoiceSlotKey == key &&
            candidate.hasDownloadedQuality(preferredQuality)
    }
}

internal fun VideoVariant.completedDownloadFile(preferredQuality: PreferredQuality): OfflineVideoFile? {
    return offlineFiles.firstOrNull { it.isCompletedDownload(preferredQuality) }
        ?: offlineFiles.firstOrNull()
}

internal fun VideoVariant.downloadTaskSubtitle(
    quality: String,
    voice: String = "",
): String {
    val voiceTitle = voice.ifBlank {
        matchingDisplayVoiceTitle
    }.ifBlank { "Voice" }
    val sourceTitle = player.cleanVideoSourceLabel()
        .ifBlank { player }
        .ifBlank { "Source" }
    val qualityTitle = quality.ifBlank { "Auto" }
    return listOf(voiceTitle, sourceTitle, qualityTitle)
        .filter { it.isNotBlank() }
        .joinToString(" \u2022 ")
}

private fun downloadTargetComparator(preferredGroupKey: String = ""): Comparator<VideoVariant> {
    return compareByDescending<VideoVariant> { it.isOfflineAvailable }
        .thenBy { if (preferredGroupKey.isNotBlank() && it.groupKey == preferredGroupKey) 0 else 1 }
        .thenBy { sourceProviderRank(it.player) }
        .thenBy { it.index }
}

// DownloadVideoProcessor
internal class DownloadVideoProcessor(
    private val context: Context,
    private val repository: YummyAnimeRepository,
    private val settingsStorage: AppSettingsStorage,
    private val downloadSlots: Semaphore,
    private val taskRuntime: DownloadTaskRuntime,
) {
    suspend fun process(
        taskId: Long,
        detailsTitle: String,
        details: AnimeDetails,
        videos: List<VideoVariant>,
        video: VideoVariant,
        preferredQuality: PreferredQuality,
        parentTaskId: Long? = null,
    ) {
        downloadSlots.withPermit {
            if (pauseIfNetworkUnavailable(taskId)) return
            if (
                taskRuntime.handleTaskInterruption(
                    taskId = taskId,
                    parentTaskId = parentTaskId,
                    clearStopRequestOnCancel = true,
                    clearStopRequestOnPause = false,
                )
            ) {
                return
            }

            taskRuntime.markTaskRunning(taskId, detailsTitle, video, preferredQuality)
            val retryCandidates = videos.downloadRetryCandidatesFor(video, preferredQuality)
            var attempt = 0
            while (attempt < DOWNLOAD_TASK_MAX_ATTEMPTS) {
                if (handleCheckpointInterruption(taskId, parentTaskId)) return
                if (pauseIfNetworkUnavailable(taskId)) return

                attempt += 1
                val attemptVideo = retryCandidates.downloadRetryCandidateForAttempt(attempt) ?: video
                taskRuntime.markAttemptRunning(taskId, attemptVideo, preferredQuality, attempt)
                val result = downloadAttempt(
                    taskId = taskId,
                    parentTaskId = parentTaskId,
                    details = details,
                    videos = videos,
                    video = attemptVideo,
                    preferredQuality = preferredQuality,
                    attempt = attempt,
                )
                result.onSuccess { downloaded ->
                    taskRuntime.markTaskCompleted(taskId, downloaded, preferredQuality, attempt)
                    return
                }
                if (handleAttemptFailure(result.exceptionOrNull(), taskId, parentTaskId, attempt)) return
            }
        }
    }

    private fun pauseIfNetworkUnavailable(taskId: Long): Boolean {
        val settings = settingsStorage.read()
        if (DownloadNetworkPolicy.canDownloadNow(context, settings)) return false
        taskRuntime.pauseForNetwork(taskId, settings)
        return true
    }

    private suspend fun downloadAttempt(
        taskId: Long,
        parentTaskId: Long?,
        details: AnimeDetails,
        videos: List<VideoVariant>,
        video: VideoVariant,
        preferredQuality: PreferredQuality,
        attempt: Int,
    ): Result<VideoVariant> {
        return runCatching {
            repository.downloadVideo(
                details = details,
                videos = videos,
                video = video,
                preferredQuality = preferredQuality,
                onProgress = { progressVideo, progress ->
                    if (taskRuntime.isTaskOrParentStopRequested(taskId, parentTaskId)) {
                        throw IllegalStateException(taskRuntime.text(R.string.ui_download_stopped))
                    }
                    taskRuntime.updateTaskProgress(
                        taskId,
                        progressVideo,
                        preferredQuality,
                        progress,
                        attempt,
                    )
                },
                isCancelled = {
                    taskRuntime.isTaskOrParentStopRequested(taskId, parentTaskId)
                },
                deletePartialOnCancel = {
                    taskRuntime.isTaskOrParentCancelRequested(taskId, parentTaskId)
                },
            )
        }
    }

    private fun handleCheckpointInterruption(taskId: Long, parentTaskId: Long?): Boolean {
        return taskRuntime.handleTaskInterruption(
            taskId = taskId,
            parentTaskId = parentTaskId,
            clearStopRequestOnCancel = true,
            clearStopRequestOnPause = true,
            waitingForUnmetered = false,
        )
    }

    private suspend fun handleAttemptFailure(
        throwable: Throwable?,
        taskId: Long,
        parentTaskId: Long?,
        attempt: Int,
    ): Boolean {
        val failure = throwable ?: return false
        val interruption = taskRuntime.taskInterruption(taskId, parentTaskId)
        val settingsAfterFailure = settingsStorage.read()
        if (interruption != null || !DownloadNetworkPolicy.canDownloadNow(context, settingsAfterFailure)) {
            DownloadCenter.clearStopRequest(taskId)
            if (interruption == null) {
                taskRuntime.pauseForNetwork(taskId, settingsAfterFailure)
            } else {
                taskRuntime.updateInterruptedTask(taskId, interruption, waitingForUnmetered = false)
                taskRuntime.notifyChanged()
            }
            return true
        }

        val errorMessage = failure.message?.takeIf { it.isNotBlank() }
            ?: taskRuntime.text(R.string.ui_error)
        if (attempt >= DOWNLOAD_TASK_MAX_ATTEMPTS) {
            taskRuntime.markTaskFailed(taskId, errorMessage, attempt)
            return true
        }
        taskRuntime.markTaskRetrying(taskId, errorMessage, attempt)
        delay(DOWNLOAD_TASK_RETRY_DELAY_MS * attempt)
        return false
    }
}

private const val DOWNLOAD_TASK_RETRY_DELAY_MS = 1_500L
