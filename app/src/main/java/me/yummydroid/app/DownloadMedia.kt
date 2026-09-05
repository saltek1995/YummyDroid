package me.yummydroid.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import me.yummydroid.app.data.DownloadSourceCoolingDown
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.MAX_DOWNLOAD_PARALLELISM
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.YummyAnimeRepository
import me.yummydroid.app.data.canMaybeProvideDownloadQuality
import me.yummydroid.app.data.downloadCandidatesFor
import me.yummydroid.app.data.maxKnownSourceQualityHeight
import me.yummydroid.app.data.matchingEpisodeKey
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

// One live limit for all writers; lowering it never interrupts an acquired slot.
internal class DownloadExecutionLimits(initialSettings: AppSettings) {
    private data class Slots(val active: Int, val limit: Int)
    private val slots = MutableStateFlow(Slots(0, initialSettings.downloadParallelism.coerceIn(1, MAX_DOWNLOAD_PARALLELISM)))
    @Volatile
    var speedBytesPerSecond: Long = initialSettings.downloadSpeedLimitBytesPerSecond
        private set

    fun update(settings: AppSettings) {
        speedBytesPerSecond = settings.downloadSpeedLimitBytesPerSecond
        slots.update { it.copy(limit = settings.downloadParallelism.coerceIn(1, MAX_DOWNLOAD_PARALLELISM)) }
    }

    suspend inline fun <T> withPermit(action: () -> T): T {
        acquire()
        try {
            currentCoroutineContext().ensureActive()
            return action()
        } finally {
            release()
        }
    }

    suspend fun <T> withSourceCooldown(
        shouldStop: () -> Boolean,
        onWaiting: (Long) -> Unit,
        nowMs: () -> Long = System::currentTimeMillis,
        wait: suspend (Long) -> Unit = { delay(it) },
        action: suspend () -> Result<T>,
    ): Result<T>? {
        while (true) {
            if (shouldStop()) return null
            val result = withPermit {
                if (shouldStop()) return null
                action()
            }
            val cooldown = result.exceptionOrNull() as? DownloadSourceCoolingDown ?: return result
            var displayedMinutes = -1L
            while (true) {
                currentCoroutineContext().ensureActive()
                if (shouldStop()) return null
                val remaining = cooldown.retryAtMs - nowMs()
                if (remaining <= 0L) break
                val minutes = (remaining + 59_999L) / 60_000L
                if (minutes != displayedMinutes) {
                    onWaiting(minutes)
                    displayedMinutes = minutes
                }
                wait(minOf(remaining, 1_000L))
            }
        }
    }

    @PublishedApi
    internal suspend fun acquire() {
        while (true) {
            val current = slots.value
            if (current.active < current.limit) {
                if (slots.compareAndSet(current, current.copy(active = current.active + 1))) return
            } else {
                slots.first { it != current }
            }
        }
    }

    @PublishedApi
    internal fun release() {
        slots.update { it.copy(active = it.active - 1) }
    }
}

// DownloadVideoProcessor
internal class DownloadVideoProcessor(
    private val context: Context,
    private val repository: YummyAnimeRepository,
    private val settingsStorage: AppSettingsStorage,
    private val executionLimits: DownloadExecutionLimits,
    private val taskRuntime: DownloadTaskRuntime,
) : DownloadVideoTaskProcessor {
    override suspend fun process(
        taskId: Long,
        detailsTitle: String,
        details: AnimeDetails,
        videos: List<VideoVariant>,
        video: VideoVariant,
        preferredQuality: PreferredQuality,
        parentTaskId: Long?,
    ) {
        val completed = DownloadService.commands.runVideo(video.id, video.matchingEpisodeKey) {
            processVideo(taskId, detailsTitle, details, videos, video, preferredQuality, parentTaskId)
        }
        if (!completed) {
            taskRuntime.updateInterruptedTask(taskId, DownloadTaskInterruption.Cancelled, waitingForUnmetered = false)
            taskRuntime.notifyChanged()
        }
    }

    private suspend fun processVideo(
        taskId: Long,
        detailsTitle: String,
        details: AnimeDetails,
        videos: List<VideoVariant>,
        video: VideoVariant,
        preferredQuality: PreferredQuality,
        parentTaskId: Long?,
    ) {
        val retryCandidates = videos.downloadRetryCandidatesFor(video, preferredQuality)
        var attempt = 0
        while (attempt < DOWNLOAD_TASK_MAX_ATTEMPTS) {
            val result = executionLimits.withSourceCooldown(
                shouldStop = { handleCheckpointInterruption(taskId, parentTaskId) || pauseIfNetworkUnavailable(taskId) },
                onWaiting = { minutes -> taskRuntime.markTaskWaitingForSource(taskId, minutes) },
            ) {
                val attemptVideo = retryCandidates.downloadRetryCandidateForAttempt(attempt + 1) ?: video
                if (attempt == 0) taskRuntime.markTaskRunning(taskId, detailsTitle, attemptVideo, preferredQuality)
                taskRuntime.markAttemptRunning(taskId, attemptVideo, preferredQuality, attempt + 1)
                downloadAttempt(
                    taskId = taskId,
                    parentTaskId = parentTaskId,
                    details = details,
                    videos = videos,
                    video = attemptVideo,
                    preferredQuality = preferredQuality,
                    attempt = attempt + 1,
                )
            } ?: return
            result.onSuccess { downloaded ->
                taskRuntime.markTaskCompleted(taskId, downloaded, preferredQuality, attempt + 1)
                return
            }
            attempt += 1
            if (handleAttemptFailure(result.exceptionOrNull(), taskId, parentTaskId, attempt)) return
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
        val operationContext = currentCoroutineContext()
        return runCatching {
            repository.downloadVideo(
                details = details,
                videos = videos,
                video = video,
                preferredQuality = preferredQuality,
                onProgress = { progressVideo, progress ->
                    operationContext.ensureActive()
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
                    !operationContext.isActive || taskRuntime.isTaskOrParentStopRequested(taskId, parentTaskId)
                },
                deletePartialOnCancel = {
                    taskRuntime.isTaskOrParentCancelRequested(taskId, parentTaskId)
                },
            )
        }.onFailure { failure ->
            operationContext.ensureActive()
            if (failure is CancellationException) throw failure
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
            taskRuntime.clearStopRequest(taskId)
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
