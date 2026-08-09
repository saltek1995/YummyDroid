package me.yummydroid.app

import android.content.Context
import me.yummydroid.app.data.PreferredQuality

object DownloadCenter {
    private val controller = DownloadCenterController()

    val state = controller.state

    fun initialize(context: Context) {
        controller.initialize(context)
    }

    fun addTask(
        animeId: Long,
        videoId: Long?,
        title: String,
        episodeTitle: String,
        qualityTitle: String = PreferredQuality.Auto.title,
        groupKey: String = "",
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
        planId: String = "",
        batchKey: String = "",
        batchTotal: Int = 0,
        batchCompleted: Int = 0,
        isBatchSummary: Boolean = false,
        existingTaskId: Long? = null,
    ): Long {
        return controller.addTask(
            DownloadTaskRequest(
                animeId = animeId,
                videoId = videoId,
                title = title,
                episodeTitle = episodeTitle,
                qualityTitle = qualityTitle,
                groupKey = groupKey,
                preferredQualityName = preferredQuality.name,
                planId = planId,
                batchKey = batchKey,
                batchTotal = batchTotal,
                batchCompleted = batchCompleted,
                isBatchSummary = isBatchSummary,
                existingTaskId = existingTaskId,
            ),
        )
    }

    fun updateTask(
        id: Long,
        title: String? = null,
        episodeTitle: String? = null,
        qualityTitle: String? = null,
        groupKey: String? = null,
        preferredQualityName: String? = null,
        planId: String? = null,
        batchKey: String? = null,
        batchTotal: Int? = null,
        batchCompleted: Int? = null,
        isBatchSummary: Boolean? = null,
        progress: Float? = null,
        downloadedBytes: Long? = null,
        totalBytes: Long? = null,
        bytesPerSecond: Long? = null,
        state: DownloadTaskState? = null,
        message: String? = null,
        waitingForUnmetered: Boolean? = null,
        attemptCount: Int? = null,
    ) {
        controller.updateTask(
            id,
            DownloadTaskUpdate(
                title = title,
                episodeTitle = episodeTitle,
                qualityTitle = qualityTitle,
                groupKey = groupKey,
                preferredQualityName = preferredQualityName,
                planId = planId,
                batchKey = batchKey,
                batchTotal = batchTotal,
                batchCompleted = batchCompleted,
                isBatchSummary = isBatchSummary,
                progress = progress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                bytesPerSecond = bytesPerSecond,
                state = state,
                message = message,
                waitingForUnmetered = waitingForUnmetered,
                attemptCount = attemptCount,
            ),
        )
    }

    fun requestPause(id: Long) {
        controller.requestPause(id)
    }

    fun requestCancel(id: Long) {
        controller.requestCancel(id)
    }

    fun isCancelRequested(id: Long): Boolean = controller.isCancelRequested(id)

    fun isPauseRequested(id: Long): Boolean = controller.isPauseRequested(id)

    fun isStopRequested(id: Long): Boolean = controller.isStopRequested(id)

    fun clearStopRequest(id: Long) {
        controller.clearStopRequest(id)
    }

    fun resumeTask(context: Context, id: Long) {
        controller.resumeTask(context, id)
    }

    fun resumeWaitingForAllowedNetwork(context: Context) {
        controller.resumeWaitingForAllowedNetwork(context)
    }

    fun clearFinished() {
        controller.clearFinished()
    }

    fun removeTask(id: Long) {
        controller.removeTask(id)
    }

    fun moveTaskToTop(id: Long) {
        controller.moveTaskToTop(id)
    }

    fun clearHistory() {
        controller.clearFinished()
    }

    fun clearAll() {
        controller.clearAll()
    }
}
