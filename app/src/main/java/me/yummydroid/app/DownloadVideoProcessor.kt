package me.yummydroid.app

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.YummyAnimeRepository

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
            val settings = settingsStorage.read()
            if (!DownloadNetworkPolicy.canDownloadNow(context, settings)) {
                taskRuntime.pauseForNetwork(taskId, settings)
                return
            }
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
                val latestSettings = settingsStorage.read()
                if (!DownloadNetworkPolicy.canDownloadNow(context, latestSettings)) {
                    taskRuntime.pauseForNetwork(taskId, latestSettings)
                    return
                }

                attempt += 1
                val attemptVideo = retryCandidates.downloadRetryCandidateForAttempt(attempt) ?: video
                taskRuntime.markAttemptRunning(taskId, attemptVideo, preferredQuality, attempt)
                val result = runCatching {
                    repository.downloadVideo(
                        details = details,
                        videos = videos,
                        video = attemptVideo,
                        preferredQuality = preferredQuality,
                        onProgress = { progress ->
                            if (taskRuntime.isTaskOrParentStopRequested(taskId, parentTaskId)) {
                                throw IllegalStateException(taskRuntime.text(R.string.ui_download_stopped))
                            }
                            taskRuntime.updateTaskProgress(
                                taskId,
                                attemptVideo,
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
                result.onSuccess { downloaded ->
                    taskRuntime.markTaskCompleted(taskId, downloaded, preferredQuality, attempt)
                    return
                }
                if (handleAttemptFailure(result.exceptionOrNull(), taskId, parentTaskId, attempt)) return
            }
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
