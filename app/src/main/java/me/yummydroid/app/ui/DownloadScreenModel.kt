package me.yummydroid.app.ui

import kotlin.math.roundToInt
import me.yummydroid.app.DownloadTaskState
import me.yummydroid.app.DownloadTaskUi

internal data class DownloadScreenModel(
    val planTasks: List<DownloadTaskUi>,
    val queueTasks: List<DownloadTaskUi>,
    val visibleTasks: List<DownloadTaskUi>,
    val offlineAnimeIds: List<Long>,
    val focusKeys: List<String>,
    val listIndexesByFocusKey: Map<String, Int>,
    val focusKeysByListIndex: Map<Int, String>,
    val canClearHistory: Boolean,
) {
    val isEmpty: Boolean
        get() = visibleTasks.isEmpty() && offlineAnimeIds.isEmpty()

    fun activeFocusKey(previousFocusKey: String?): String? {
        return previousFocusKey
            ?.takeIf { it in focusKeys }
            ?: focusKeys.firstOrNull()
    }
}

internal fun buildDownloadScreenModel(
    tasks: List<DownloadTaskUi>,
    offlineAnimeIds: List<Long>,
): DownloadScreenModel {
    val planTasks = tasks.filter { it.isBatchSummary }
    val queueTasks = tasks
        .filterNot { it.isBatchSummary }
        .filter(DownloadTaskUi::isVisibleQueueTask)
    val visibleTasks = planTasks + queueTasks
    val focusKeys = visibleTasks.map { task -> downloadTaskFocusKey(task.id) } +
        offlineAnimeIds.map(::downloadOfflineFocusKey)
    val listIndexesByFocusKey = buildDownloadFocusIndexes(
        planTasks = planTasks,
        queueTasks = queueTasks,
        offlineAnimeIds = offlineAnimeIds,
    )
    return DownloadScreenModel(
        planTasks = planTasks,
        queueTasks = queueTasks,
        visibleTasks = visibleTasks,
        offlineAnimeIds = offlineAnimeIds,
        focusKeys = focusKeys,
        listIndexesByFocusKey = listIndexesByFocusKey,
        focusKeysByListIndex = listIndexesByFocusKey.entries.associate { (key, index) -> index to key },
        canClearHistory = tasks.any { task ->
            !task.isActive && task.state != DownloadTaskState.Paused
        },
    )
}

internal fun downloadTaskFocusKey(taskId: Long): String = "task:$taskId"

internal fun downloadOfflineFocusKey(animeId: Long): String = "offline:$animeId"

private fun DownloadTaskUi.isVisibleQueueTask(): Boolean {
    return isActive || state == DownloadTaskState.Paused || state == DownloadTaskState.Failed
}

private fun buildDownloadFocusIndexes(
    planTasks: List<DownloadTaskUi>,
    queueTasks: List<DownloadTaskUi>,
    offlineAnimeIds: List<Long>,
): Map<String, Int> {
    val indexes = linkedMapOf<String, Int>()
    var listIndex = 0
    if (planTasks.isNotEmpty()) {
        listIndex += 1
        planTasks.forEach { task ->
            indexes[downloadTaskFocusKey(task.id)] = listIndex
            listIndex += 1
        }
    }
    if (queueTasks.isNotEmpty()) {
        listIndex += 1
        queueTasks.forEach { task ->
            indexes[downloadTaskFocusKey(task.id)] = listIndex
            listIndex += 1
        }
    }
    if (offlineAnimeIds.isNotEmpty()) {
        listIndex += 1
        offlineAnimeIds.forEach { animeId ->
            indexes[downloadOfflineFocusKey(animeId)] = listIndex
            listIndex += 1
        }
    }
    return indexes
}

internal data class DownloadTaskActions(
    val showPause: Boolean,
    val showResume: Boolean,
    val showCancel: Boolean,
) {
    val hasAny: Boolean
        get() = showPause || showResume || showCancel
}

internal fun DownloadTaskUi.downloadTaskActions(): DownloadTaskActions {
    return DownloadTaskActions(
        showPause = state == DownloadTaskState.Running || state == DownloadTaskState.Queued,
        showResume = canResume,
        showCancel = isActive || state == DownloadTaskState.Paused || state == DownloadTaskState.Failed,
    )
}

internal data class DownloadTransferStatus(
    val percent: Int,
    val downloadedBytes: Long?,
    val totalBytes: Long?,
    val bytesPerSecond: Long?,
)

internal fun DownloadTaskUi.downloadTransferStatus(): DownloadTransferStatus? {
    val isVisible = isActive ||
        state == DownloadTaskState.Completed ||
        state == DownloadTaskState.Paused ||
        state == DownloadTaskState.Failed
    if (!isVisible) return null

    val positiveDownloadedBytes = downloadedBytes.takeIf { it > 0L }
    return DownloadTransferStatus(
        percent = (progress.coerceIn(0f, 1f) * 100f).roundToInt(),
        downloadedBytes = positiveDownloadedBytes,
        totalBytes = totalBytes.takeIf { it > 0L && positiveDownloadedBytes != null },
        bytesPerSecond = bytesPerSecond.takeIf { isActive && it > 0L },
    )
}
