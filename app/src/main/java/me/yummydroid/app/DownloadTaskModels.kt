package me.yummydroid.app

import kotlinx.serialization.Serializable
import me.yummydroid.app.data.PreferredQuality

enum class DownloadTaskState {
    Queued,
    Running,
    Paused,
    Added,
    Completed,
    Failed,
    Cancelled,
}

@Serializable
data class DownloadTaskUi(
    val id: Long,
    val animeId: Long,
    val videoId: Long?,
    val title: String,
    val episodeTitle: String,
    val qualityTitle: String = PreferredQuality.Auto.title,
    val groupKey: String = "",
    val preferredQualityName: String = PreferredQuality.Auto.name,
    val planId: String = "",
    val batchKey: String = "",
    val batchTotal: Int = 0,
    val batchCompleted: Int = 0,
    val isBatchSummary: Boolean = false,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val bytesPerSecond: Long = 0L,
    val state: DownloadTaskState = DownloadTaskState.Queued,
    val message: String = "",
    val waitingForUnmetered: Boolean = false,
    val attemptCount: Int = 0,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    val isActive: Boolean
        get() = state == DownloadTaskState.Queued || state == DownloadTaskState.Running

    val canResume: Boolean
        get() = state == DownloadTaskState.Paused || state == DownloadTaskState.Failed
}

data class DownloadQueueSnapshot(
    val tasks: List<DownloadTaskUi> = emptyList(),
) {
    val activeTasks: List<DownloadTaskUi>
        get() = tasks.filter { it.isActive }
}
