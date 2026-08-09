package me.yummydroid.app

private const val DOWNLOAD_TASK_HISTORY_LIMIT = 120

internal data class DownloadTaskIdentity(
    val animeId: Long,
    val videoId: Long?,
    val groupKey: String,
    val planId: String,
    val preferredQualityName: String,
)

internal data class DownloadTaskRequest(
    val animeId: Long,
    val videoId: Long?,
    val title: String,
    val episodeTitle: String,
    val qualityTitle: String,
    val groupKey: String,
    val preferredQualityName: String,
    val planId: String,
    val batchKey: String,
    val batchTotal: Int,
    val batchCompleted: Int,
    val isBatchSummary: Boolean,
    val existingTaskId: Long?,
) {
    val identity: DownloadTaskIdentity
        get() = DownloadTaskIdentity(
            animeId = animeId,
            videoId = videoId,
            groupKey = groupKey,
            planId = planId,
            preferredQualityName = preferredQualityName,
        )

    fun createTask(id: Long): DownloadTaskUi {
        return DownloadTaskUi(
            id = id,
            animeId = animeId,
            videoId = videoId,
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
        )
    }

    fun metadataUpdate(): DownloadTaskUpdate {
        return DownloadTaskUpdate(
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
        )
    }
}

internal data class DownloadTaskUpdate(
    val title: String? = null,
    val episodeTitle: String? = null,
    val qualityTitle: String? = null,
    val groupKey: String? = null,
    val preferredQualityName: String? = null,
    val planId: String? = null,
    val batchKey: String? = null,
    val batchTotal: Int? = null,
    val batchCompleted: Int? = null,
    val isBatchSummary: Boolean? = null,
    val progress: Float? = null,
    val downloadedBytes: Long? = null,
    val totalBytes: Long? = null,
    val bytesPerSecond: Long? = null,
    val state: DownloadTaskState? = null,
    val message: String? = null,
    val waitingForUnmetered: Boolean? = null,
    val attemptCount: Int? = null,
)

internal fun DownloadTaskUi.applyUpdate(
    update: DownloadTaskUpdate,
    updatedAtMs: Long = System.currentTimeMillis(),
): DownloadTaskUi {
    return copy(
        title = update.title ?: title,
        episodeTitle = update.episodeTitle ?: episodeTitle,
        qualityTitle = update.qualityTitle ?: qualityTitle,
        groupKey = update.groupKey ?: groupKey,
        preferredQualityName = update.preferredQualityName ?: preferredQualityName,
        planId = update.planId ?: planId,
        batchKey = update.batchKey ?: batchKey,
        batchTotal = update.batchTotal ?: batchTotal,
        batchCompleted = update.batchCompleted ?: batchCompleted,
        isBatchSummary = update.isBatchSummary ?: isBatchSummary,
        progress = update.progress?.coerceIn(0f, 1f) ?: progress,
        downloadedBytes = update.downloadedBytes ?: downloadedBytes,
        totalBytes = update.totalBytes ?: totalBytes,
        bytesPerSecond = update.bytesPerSecond ?: bytesPerSecond,
        state = update.state ?: state,
        message = update.message ?: message,
        waitingForUnmetered = update.waitingForUnmetered ?: waitingForUnmetered,
        attemptCount = update.attemptCount ?: attemptCount,
        updatedAtMs = updatedAtMs,
    )
}

internal fun List<DownloadTaskUi>.findReusableTask(identity: DownloadTaskIdentity): DownloadTaskUi? {
    return firstOrNull { task ->
        task.canBeReused &&
            task.animeId == identity.animeId &&
            task.videoId == identity.videoId &&
            task.groupKey == identity.groupKey &&
            task.planId == identity.planId &&
            task.preferredQualityName == identity.preferredQualityName
    }
}

internal fun List<DownloadTaskUi>.stopTargetIds(id: Long): Set<Long> {
    val task = firstOrNull { it.id == id } ?: return setOf(id)
    if (!task.isBatchSummary || task.batchKey.isBlank()) return setOf(id)
    val batchIds = asSequence()
        .filter { it.batchKey == task.batchKey }
        .filterNot { it.state == DownloadTaskState.Completed || it.state == DownloadTaskState.Cancelled }
        .mapTo(mutableSetOf()) { it.id }
    return batchIds.takeIf { it.size > 1 } ?: setOf(id)
}

internal fun List<DownloadTaskUi>.updateTaskStates(
    ids: Set<Long>,
    state: DownloadTaskState,
    message: String,
    updatedAtMs: Long = System.currentTimeMillis(),
): List<DownloadTaskUi> {
    if (ids.isEmpty()) return this
    return map { task ->
        if (task.id in ids) {
            task.copy(
                state = state,
                bytesPerSecond = 0L,
                message = message,
                waitingForUnmetered = false,
                updatedAtMs = updatedAtMs,
            )
        } else {
            task
        }
    }
}

internal fun List<DownloadTaskUi>.restoreInterruptedTasks(
    waitingForNetworkMessage: String,
    waitingToResumeMessage: String,
): List<DownloadTaskUi> {
    return map { task ->
        if (task.isActive) {
            task.copy(
                state = DownloadTaskState.Paused,
                bytesPerSecond = 0L,
                message = if (task.waitingForUnmetered) waitingForNetworkMessage else waitingToResumeMessage,
            )
        } else {
            task
        }
    }.cappedDownloadTasks()
}

internal fun List<DownloadTaskUi>.cappedDownloadTasks(): List<DownloadTaskUi> {
    if (size <= DOWNLOAD_TASK_HISTORY_LIMIT) return this
    val protectedTasks = filter { it.isProtectedFromHistoryEviction }
    val protectedIds = protectedTasks.mapTo(mutableSetOf()) { it.id }
    val historyLimit = (DOWNLOAD_TASK_HISTORY_LIMIT - protectedTasks.size).coerceAtLeast(0)
    val history = filterNot { it.id in protectedIds }.take(historyLimit)
    return (protectedTasks + history).distinctBy { it.id }
}

private val DownloadTaskUi.canBeReused: Boolean
    get() = isActive || state == DownloadTaskState.Paused || state == DownloadTaskState.Failed

private val DownloadTaskUi.isProtectedFromHistoryEviction: Boolean
    get() = canBeReused || (isBatchSummary && state != DownloadTaskState.Cancelled)
