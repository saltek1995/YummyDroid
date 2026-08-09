package me.yummydroid.app

import android.content.Context
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.DownloadProgressInfo
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant

internal class DownloadTaskRuntime(
    private val context: Context,
    private val settingsStorage: AppSettingsStorage,
    private val updateNotification: () -> Unit,
) {
    fun text(resId: Int, vararg formatArgs: Any): String {
        val language = settingsStorage.read().contentLanguage
        return if (formatArgs.isEmpty()) {
            context.localizedString(resId, language)
        } else {
            context.localizedString(resId, language, *formatArgs)
        }
    }

    fun notifyChanged() = updateNotification()

    fun markTaskRunning(
        taskId: Long,
        detailsTitle: String,
        video: VideoVariant,
        preferredQuality: PreferredQuality,
    ) {
        DownloadCenter.updateTask(
            id = taskId,
            title = detailsTitle,
            episodeTitle = video.episodeTitle,
            qualityTitle = video.downloadTaskSubtitle(preferredQuality.title),
            state = DownloadTaskState.Running,
            message = text(R.string.ui_loading),
            waitingForUnmetered = false,
        )
        notifyChanged()
    }

    fun markAttemptRunning(
        taskId: Long,
        video: VideoVariant,
        preferredQuality: PreferredQuality,
        attempt: Int,
    ) {
        DownloadCenter.updateTask(
            id = taskId,
            state = DownloadTaskState.Running,
            bytesPerSecond = 0L,
            episodeTitle = video.episodeTitle,
            qualityTitle = video.downloadTaskSubtitle(preferredQuality.title),
            message = if (attempt == 1) {
                text(R.string.ui_loading)
            } else {
                text(R.string.ui_download_retry_message, attempt, DOWNLOAD_TASK_MAX_ATTEMPTS, "")
                    .trimEnd(':', ' ')
            },
            waitingForUnmetered = false,
            attemptCount = attempt,
        )
        notifyChanged()
    }

    fun updateTaskProgress(
        taskId: Long,
        video: VideoVariant,
        preferredQuality: PreferredQuality,
        progress: DownloadProgressInfo,
        attempt: Int,
    ) {
        val taskSubtitle = video.downloadTaskSubtitle(
            quality = progress.qualityTitle.ifBlank { preferredQuality.title },
            voice = progress.voiceTitle,
        )
        DownloadCenter.updateTask(
            id = taskId,
            progress = progress.fraction.coerceIn(0f, 1f),
            downloadedBytes = progress.downloadedBytes,
            totalBytes = progress.totalBytes,
            bytesPerSecond = progress.bytesPerSecond,
            qualityTitle = taskSubtitle,
            message = text(R.string.ui_loading),
            waitingForUnmetered = false,
            attemptCount = attempt,
        )
        notifyChanged()
    }

    fun markTaskCompleted(
        taskId: Long,
        downloaded: VideoVariant,
        preferredQuality: PreferredQuality,
        attempt: Int,
    ) {
        val completedFile = downloaded.completedDownloadFile(preferredQuality)
        val completedBytes = completedFile?.bytes?.coerceAtLeast(0L) ?: 0L
        DownloadCenter.clearStopRequest(taskId)
        DownloadCenter.updateTask(
            id = taskId,
            progress = 1f,
            downloadedBytes = completedBytes,
            totalBytes = completedBytes,
            bytesPerSecond = 0L,
            episodeTitle = downloaded.episodeTitle,
            qualityTitle = downloaded.downloadTaskSubtitle(
                quality = completedFile?.qualityTitle?.takeIf { it.isNotBlank() } ?: preferredQuality.title,
                voice = completedFile?.voiceTitle.orEmpty(),
            ),
            state = DownloadTaskState.Completed,
            message = text(R.string.ui_downloaded_bc4f6a),
            waitingForUnmetered = false,
            attemptCount = attempt,
        )
        notifyChanged()
    }

    fun markTaskFailed(taskId: Long, errorMessage: String, attempt: Int) {
        DownloadCenter.clearStopRequest(taskId)
        DownloadCenter.updateTask(
            id = taskId,
            bytesPerSecond = 0L,
            state = DownloadTaskState.Failed,
            message = errorMessage,
            waitingForUnmetered = false,
            attemptCount = attempt,
        )
        notifyChanged()
    }

    fun markTaskRetrying(taskId: Long, errorMessage: String, attempt: Int) {
        DownloadCenter.updateTask(
            id = taskId,
            bytesPerSecond = 0L,
            message = text(
                R.string.ui_download_retry_message,
                attempt + 1,
                DOWNLOAD_TASK_MAX_ATTEMPTS,
                errorMessage,
            ),
            waitingForUnmetered = false,
            attemptCount = attempt,
        )
        notifyChanged()
    }

    fun isTaskOrParentStopRequested(taskId: Long, parentTaskId: Long?): Boolean {
        return DownloadCenter.isStopRequested(taskId) ||
            parentTaskId?.let(DownloadCenter::isStopRequested) == true
    }

    fun isTaskOrParentCancelRequested(taskId: Long, parentTaskId: Long?): Boolean {
        return DownloadCenter.isCancelRequested(taskId) ||
            parentTaskId?.let(DownloadCenter::isCancelRequested) == true
    }

    fun handleTaskInterruption(
        taskId: Long,
        parentTaskId: Long?,
        clearStopRequestOnCancel: Boolean,
        clearStopRequestOnPause: Boolean,
        waitingForUnmetered: Boolean? = null,
    ): Boolean {
        val handling = taskInterruptionHandling(
            taskId = taskId,
            parentTaskId = parentTaskId,
            clearStopRequestOnCancel = clearStopRequestOnCancel,
            clearStopRequestOnPause = clearStopRequestOnPause,
            waitingForUnmetered = waitingForUnmetered,
        ) ?: return false
        updateInterruptedTask(taskId, handling.interruption, handling.waitingForUnmetered)
        if (handling.clearStopRequest) {
            DownloadCenter.clearStopRequest(taskId)
        }
        notifyChanged()
        return true
    }

    fun taskInterruption(taskId: Long, parentTaskId: Long?): DownloadTaskInterruption? {
        return resolveDownloadTaskInterruption(
            taskCancelRequested = DownloadCenter.isCancelRequested(taskId),
            parentCancelRequested = parentTaskId?.let(DownloadCenter::isCancelRequested) == true,
            taskPauseRequested = DownloadCenter.isPauseRequested(taskId),
            parentPauseRequested = parentTaskId?.let(DownloadCenter::isPauseRequested) == true,
        )
    }

    fun pauseForNetwork(taskId: Long, settings: AppSettings) {
        DownloadCenter.updateTask(
            id = taskId,
            state = DownloadTaskState.Paused,
            bytesPerSecond = 0L,
            message = DownloadNetworkPolicy.waitingMessage(context, settings),
            waitingForUnmetered = true,
        )
        notifyChanged()
    }

    fun updateInterruptedTask(
        taskId: Long,
        interruption: DownloadTaskInterruption,
        waitingForUnmetered: Boolean? = null,
    ) {
        val state = when (interruption) {
            DownloadTaskInterruption.Cancelled -> DownloadTaskState.Cancelled
            DownloadTaskInterruption.Paused -> DownloadTaskState.Paused
        }
        val message = when (interruption) {
            DownloadTaskInterruption.Cancelled -> text(R.string.ui_cancelled)
            DownloadTaskInterruption.Paused -> text(R.string.ui_paused)
        }
        DownloadCenter.updateTask(
            id = taskId,
            state = state,
            bytesPerSecond = 0L,
            message = message,
            waitingForUnmetered = waitingForUnmetered,
        )
    }

    private fun taskInterruptionHandling(
        taskId: Long,
        parentTaskId: Long?,
        clearStopRequestOnCancel: Boolean,
        clearStopRequestOnPause: Boolean,
        waitingForUnmetered: Boolean?,
    ): DownloadTaskInterruptionHandling? {
        return resolveDownloadTaskInterruptionHandling(
            taskCancelRequested = DownloadCenter.isCancelRequested(taskId),
            parentCancelRequested = parentTaskId?.let(DownloadCenter::isCancelRequested) == true,
            taskPauseRequested = DownloadCenter.isPauseRequested(taskId),
            parentPauseRequested = parentTaskId?.let(DownloadCenter::isPauseRequested) == true,
            clearStopRequestOnCancel = clearStopRequestOnCancel,
            clearStopRequestOnPause = clearStopRequestOnPause,
            waitingForUnmetered = waitingForUnmetered,
        )
    }
}

internal const val DOWNLOAD_TASK_MAX_ATTEMPTS = 5
