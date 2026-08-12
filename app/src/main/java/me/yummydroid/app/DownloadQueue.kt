package me.yummydroid.app

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.DownloadProgressInfo
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.YummyAnimeRepository
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.downloadEpisodeSlotKey
import me.yummydroid.app.data.downloadVoiceSlotKey
import me.yummydroid.app.data.hasDownloadedQuality
import me.yummydroid.app.data.isCompletedDownload
import me.yummydroid.app.data.matchingDisplayVoiceTitle
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.sourceProviderRank

// DownloadQueuePolicy
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
    val qualityTitle: String = PreferredQuality.Auto.title,
    val groupKey: String = "",
    val preferredQualityName: String = PreferredQuality.Auto.name,
    val planId: String = "",
    val batchKey: String = "",
    val batchTotal: Int = 0,
    val batchCompleted: Int = 0,
    val isBatchSummary: Boolean = false,
    val existingTaskId: Long? = null,
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

// DownloadRequestIntentProcessor
internal interface DownloadVideoTaskProcessor {
    suspend fun process(
        taskId: Long,
        detailsTitle: String,
        details: AnimeDetails,
        videos: List<VideoVariant>,
        video: VideoVariant,
        preferredQuality: PreferredQuality,
        parentTaskId: Long? = null,
    )
}

internal interface DownloadRequestTaskController {
    fun canStart(taskId: Long): Boolean
    fun removeFinishedTask(taskId: Long)
    fun handleStartFailure(taskId: Long, throwable: Throwable, fallbackMessageRes: Int)
}

private data class DownloadIntentRequest(
    val animeId: Long,
    val existingTaskId: Long?,
    val requestedVideoId: Long?,
    val preferredGroupKey: String,
    val preferredPlanId: String,
    val preferredQuality: PreferredQuality,
    val batchKey: String,
)

internal class DownloadRequestIntentProcessor(
    private val repository: YummyAnimeRepository,
    private val taskRuntime: DownloadTaskRuntime,
    private val videoProcessor: DownloadVideoTaskProcessor,
    private val taskController: DownloadRequestTaskController,
    private val taskQueue: DownloadTaskQueue,
) {
    suspend fun process(intent: Intent) {
        val request = intent.toDownloadIntentRequest(taskQueue) ?: return
        val prepareTaskId = addPreparingTask(request)
        if (!taskController.canStart(prepareTaskId)) return
        markTaskRunning(prepareTaskId)
        runCatching {
            processStartedRequest(prepareTaskId, request)
        }.onFailure { throwable ->
            taskController.handleStartFailure(prepareTaskId, throwable, R.string.ui_download_start_failed)
        }
    }

    private suspend fun processStartedRequest(taskId: Long, request: DownloadIntentRequest) {
        val (details, videos) = repository.getAnimeWithVideos(request.animeId)
        val targets = request.resolveTargets(videos)
        if (targets.isEmpty()) {
            completeWithoutTargets(taskId, request, details, videos)
            return
        }
        if (request.requestedVideoId == null) {
            processAllTargets(taskId, request, details, videos, targets)
        } else {
            videoProcessor.process(
                taskId = taskId,
                detailsTitle = details.title,
                details = details,
                videos = videos,
                video = targets.first(),
                preferredQuality = request.preferredQuality,
            )
            taskController.removeFinishedTask(taskId)
        }
    }

    private fun addPreparingTask(request: DownloadIntentRequest): Long {
        return taskQueue.addTask(
            DownloadTaskRequest(
                animeId = request.animeId,
                videoId = request.requestedVideoId,
                title = taskRuntime.text(R.string.ui_loading),
                episodeTitle = if (request.requestedVideoId == null) {
                    taskRuntime.text(R.string.ui_all_episodes)
                } else {
                    taskRuntime.text(R.string.ui_preparing)
                },
                qualityTitle = request.preferredQuality.title,
                groupKey = request.preferredGroupKey,
                preferredQualityName = request.preferredQuality.name,
                planId = request.preferredPlanId,
                batchKey = request.batchKey,
                existingTaskId = request.existingTaskId,
            ),
        )
    }

    private fun markTaskRunning(taskId: Long) {
        taskQueue.updateTask(
            taskId,
            DownloadTaskUpdate(
                state = DownloadTaskState.Running,
                message = taskRuntime.text(R.string.ui_preparing),
                waitingForUnmetered = false,
            ),
        )
        taskRuntime.notifyChanged()
    }

    private fun DownloadIntentRequest.resolveTargets(videos: List<VideoVariant>): List<VideoVariant> {
        return if (requestedVideoId != null) {
            videos
                .firstOrNull { it.id == requestedVideoId }
                ?.takeUnless { videos.hasDownloadedRequestedSlot(it, preferredQuality) }
                ?.let(::listOf)
                .orEmpty()
        } else {
            videos.selectDownloadAllTargets(preferredGroupKey)
                .filterNot { videos.hasDownloadedRequestedSlot(it, preferredQuality) }
        }
    }

    private fun completeWithoutTargets(
        taskId: Long,
        request: DownloadIntentRequest,
        details: AnimeDetails,
        videos: List<VideoVariant>,
    ) {
        val hasVideos = videos.isNotEmpty()
        val alreadyDownloadedSingle = request.requestedVideoId != null && hasVideos
        taskQueue.updateTask(
            taskId,
            DownloadTaskUpdate(
                title = details.title,
                episodeTitle = when {
                    alreadyDownloadedSingle -> videos.firstOrNull { it.id == request.requestedVideoId }?.episodeTitle
                        ?: taskRuntime.text(R.string.ui_episode)
                    hasVideos -> taskRuntime.text(R.string.ui_all_episodes)
                    else -> taskRuntime.text(R.string.ui_no_episodes)
                },
                progress = if (hasVideos) 1f else 0f,
                state = if (hasVideos) DownloadTaskState.Completed else DownloadTaskState.Failed,
                message = when {
                    alreadyDownloadedSingle -> taskRuntime.text(R.string.ui_episode_already_downloaded)
                    hasVideos -> taskRuntime.text(R.string.ui_all_available_episodes_are_already_downloaded)
                    else -> taskRuntime.text(R.string.ui_no_episodes_to_download)
                },
                waitingForUnmetered = false,
                bytesPerSecond = 0L,
            ),
        )
        taskRuntime.notifyChanged()
    }

    private suspend fun processAllTargets(
        prepareTaskId: Long,
        request: DownloadIntentRequest,
        details: AnimeDetails,
        videos: List<VideoVariant>,
        targets: List<VideoVariant>,
    ) {
        taskQueue.removeTask(prepareTaskId)
        coroutineScope {
            targets.map { video ->
                launch {
                    val taskId = taskQueue.addTask(
                        DownloadTaskRequest(
                            animeId = details.id,
                            videoId = video.id,
                            title = details.title,
                            episodeTitle = video.episodeTitle,
                            qualityTitle = video.downloadTaskSubtitle(request.preferredQuality.title),
                            groupKey = request.preferredGroupKey,
                            preferredQualityName = request.preferredQuality.name,
                            batchKey = request.batchKey,
                        ),
                    )
                    videoProcessor.process(
                        taskId = taskId,
                        detailsTitle = details.title,
                        details = details,
                        videos = videos,
                        video = video,
                        preferredQuality = request.preferredQuality,
                    )
                    taskController.removeFinishedTask(taskId)
                }
            }.joinAll()
        }
    }
}

private fun Intent.toDownloadIntentRequest(taskQueue: DownloadTaskQueue): DownloadIntentRequest? {
    val animeId = getLongExtra(DOWNLOAD_EXTRA_ANIME_ID, 0L)
    if (animeId <= 0L) return null
    val existingTaskId = getLongExtra(DOWNLOAD_EXTRA_TASK_ID, 0L).takeIf { it > 0L }
    val requestedVideoId = getLongExtra(DOWNLOAD_EXTRA_VIDEO_ID, 0L).takeIf { it > 0L }
    val preferredGroupKey = getStringExtra(DOWNLOAD_EXTRA_GROUP_KEY).orEmpty()
    val preferredQuality = getStringExtra(DOWNLOAD_EXTRA_QUALITY_NAME)
        ?.let(PreferredQuality::fromName)
        ?: PreferredQuality.Auto
    val batchKey = existingTaskId
        ?.let(taskQueue::task)
        ?.batchKey
        ?.takeIf(String::isNotBlank)
        ?: downloadBatchKey(animeId, requestedVideoId, preferredGroupKey, preferredQuality)
    return DownloadIntentRequest(
        animeId = animeId,
        existingTaskId = existingTaskId,
        requestedVideoId = requestedVideoId,
        preferredGroupKey = preferredGroupKey,
        preferredPlanId = getStringExtra(DOWNLOAD_EXTRA_PLAN_ID).orEmpty(),
        preferredQuality = preferredQuality,
        batchKey = batchKey,
    )
}

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
    val preferredVoiceKey = firstOrNull { it.groupKey == preferredGroupKey }?.matchingVoiceKey
    return groupBy(VideoVariant::downloadEpisodeSlotKey)
        .toSortedMap(compareBy<String> { it.toDoubleOrNull() ?: Double.MAX_VALUE }.thenBy { it })
        .values
        .mapNotNull { episodeVideos ->
            if (preferredVoiceKey != null) {
                episodeVideos
                    .filter { it.matchingVoiceKey == preferredVoiceKey }
                    .sortedWith(downloadTargetComparator(preferredGroupKey))
                    .firstOrNull()
            } else {
                episodeVideos.sortedWith(downloadTargetComparator("")).firstOrNull()
            }
        }
}

internal fun List<VideoVariant>.hasDownloadedRequestedSlot(
    video: VideoVariant,
    preferredQuality: PreferredQuality,
): Boolean {
    val key = video.downloadVoiceSlotKey
    return any { candidate ->
        candidate.downloadVoiceSlotKey == key && candidate.hasDownloadedQuality(preferredQuality)
    }
}

private fun downloadTargetComparator(preferredGroupKey: String): Comparator<VideoVariant> {
    return compareByDescending<VideoVariant>(VideoVariant::isOfflineAvailable)
        .thenBy { if (preferredGroupKey.isNotBlank() && it.groupKey == preferredGroupKey) 0 else 1 }
        .thenBy { sourceProviderRank(it.player) }
        .thenBy(VideoVariant::index)
}

internal fun VideoVariant.completedDownloadFile(preferredQuality: PreferredQuality): OfflineVideoFile? {
    return offlineFiles.firstOrNull { it.isCompletedDownload(preferredQuality) }
        ?: offlineFiles.firstOrNull()
}

// DownloadTaskControlPolicy
internal enum class DownloadTaskInterruption {
    Cancelled,
    Paused,
}

internal data class DownloadTaskInterruptionHandling(
    val interruption: DownloadTaskInterruption,
    val clearStopRequest: Boolean,
    val waitingForUnmetered: Boolean?,
)

internal fun resolveDownloadTaskInterruption(
    taskCancelRequested: Boolean,
    parentCancelRequested: Boolean,
    taskPauseRequested: Boolean,
    parentPauseRequested: Boolean,
): DownloadTaskInterruption? = when {
    taskCancelRequested || parentCancelRequested -> DownloadTaskInterruption.Cancelled
    taskPauseRequested || parentPauseRequested -> DownloadTaskInterruption.Paused
    else -> null
}

internal fun resolveDownloadTaskInterruptionHandling(
    taskCancelRequested: Boolean,
    parentCancelRequested: Boolean,
    taskPauseRequested: Boolean,
    parentPauseRequested: Boolean,
    clearStopRequestOnCancel: Boolean,
    clearStopRequestOnPause: Boolean,
    waitingForUnmetered: Boolean? = null,
): DownloadTaskInterruptionHandling? {
    val interruption = resolveDownloadTaskInterruption(
        taskCancelRequested = taskCancelRequested,
        parentCancelRequested = parentCancelRequested,
        taskPauseRequested = taskPauseRequested,
        parentPauseRequested = parentPauseRequested,
    ) ?: return null
    return DownloadTaskInterruptionHandling(
        interruption = interruption,
        clearStopRequest = when (interruption) {
            DownloadTaskInterruption.Cancelled -> clearStopRequestOnCancel
            DownloadTaskInterruption.Paused -> clearStopRequestOnPause
        },
        waitingForUnmetered = waitingForUnmetered,
    )
}

// DownloadTaskModels
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

// DownloadTaskRuntime
internal interface DownloadTaskStore {
    fun updateTask(id: Long, update: DownloadTaskUpdate)
    fun isCancelRequested(id: Long): Boolean
    fun isPauseRequested(id: Long): Boolean
    fun isStopRequested(id: Long): Boolean
    fun clearStopRequest(id: Long)
}

internal interface DownloadTaskQueue : DownloadTaskStore {
    fun addTask(request: DownloadTaskRequest): Long
    fun removeTask(id: Long)
    fun task(id: Long): DownloadTaskUi?
}

internal class DownloadTaskRuntime(
    private val context: Context,
    private val settingsStorage: AppSettingsStorage,
    private val updateNotification: () -> Unit,
    private val taskStore: DownloadTaskStore,
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
        taskStore.updateTask(
            taskId,
            DownloadTaskUpdate(
                title = detailsTitle,
                episodeTitle = video.episodeTitle,
                qualityTitle = video.downloadTaskSubtitle(preferredQuality.title),
                state = DownloadTaskState.Running,
                message = text(R.string.ui_loading),
                waitingForUnmetered = false,
            ),
        )
        notifyChanged()
    }

    fun markAttemptRunning(
        taskId: Long,
        video: VideoVariant,
        preferredQuality: PreferredQuality,
        attempt: Int,
    ) {
        taskStore.updateTask(
            taskId,
            DownloadTaskUpdate(
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
            ),
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
        taskStore.updateTask(
            taskId,
            DownloadTaskUpdate(
                progress = progress.fraction.coerceIn(0f, 1f),
                downloadedBytes = progress.downloadedBytes,
                totalBytes = progress.totalBytes,
                bytesPerSecond = progress.bytesPerSecond,
                qualityTitle = taskSubtitle,
                message = text(R.string.ui_loading),
                waitingForUnmetered = false,
                attemptCount = attempt,
            ),
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
        clearStopRequest(taskId)
        taskStore.updateTask(
            taskId,
            DownloadTaskUpdate(
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
            ),
        )
        notifyChanged()
    }

    fun markTaskFailed(taskId: Long, errorMessage: String, attempt: Int) {
        clearStopRequest(taskId)
        taskStore.updateTask(
            taskId,
            DownloadTaskUpdate(
                bytesPerSecond = 0L,
                state = DownloadTaskState.Failed,
                message = errorMessage,
                waitingForUnmetered = false,
                attemptCount = attempt,
            ),
        )
        notifyChanged()
    }

    fun markTaskRetrying(taskId: Long, errorMessage: String, attempt: Int) {
        taskStore.updateTask(
            taskId,
            DownloadTaskUpdate(
                bytesPerSecond = 0L,
                message = text(
                    R.string.ui_download_retry_message,
                    attempt + 1,
                    DOWNLOAD_TASK_MAX_ATTEMPTS,
                    errorMessage,
                ),
                waitingForUnmetered = false,
                attemptCount = attempt,
            ),
        )
        notifyChanged()
    }

    fun isTaskOrParentStopRequested(taskId: Long, parentTaskId: Long?): Boolean {
        return taskStore.isStopRequested(taskId) ||
            parentTaskId?.let(taskStore::isStopRequested) == true
    }

    fun isTaskOrParentCancelRequested(taskId: Long, parentTaskId: Long?): Boolean {
        return taskStore.isCancelRequested(taskId) ||
            parentTaskId?.let(taskStore::isCancelRequested) == true
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
            clearStopRequest(taskId)
        }
        notifyChanged()
        return true
    }

    fun taskInterruption(taskId: Long, parentTaskId: Long?): DownloadTaskInterruption? {
        return resolveDownloadTaskInterruption(
            taskCancelRequested = taskStore.isCancelRequested(taskId),
            parentCancelRequested = parentTaskId?.let(taskStore::isCancelRequested) == true,
            taskPauseRequested = taskStore.isPauseRequested(taskId),
            parentPauseRequested = parentTaskId?.let(taskStore::isPauseRequested) == true,
        )
    }

    fun pauseForNetwork(taskId: Long, settings: AppSettings) {
        taskStore.updateTask(
            taskId,
            DownloadTaskUpdate(
                state = DownloadTaskState.Paused,
                bytesPerSecond = 0L,
                message = networkWaitingMessage(settings),
                waitingForUnmetered = true,
            ),
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
        taskStore.updateTask(
            taskId,
            DownloadTaskUpdate(
                state = state,
                bytesPerSecond = 0L,
                message = message,
                waitingForUnmetered = waitingForUnmetered,
            ),
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
            taskCancelRequested = taskStore.isCancelRequested(taskId),
            parentCancelRequested = parentTaskId?.let(taskStore::isCancelRequested) == true,
            taskPauseRequested = taskStore.isPauseRequested(taskId),
            parentPauseRequested = parentTaskId?.let(taskStore::isPauseRequested) == true,
            clearStopRequestOnCancel = clearStopRequestOnCancel,
            clearStopRequestOnPause = clearStopRequestOnPause,
            waitingForUnmetered = waitingForUnmetered,
        )
    }

    fun clearStopRequest(taskId: Long) {
        taskStore.clearStopRequest(taskId)
    }

    private fun networkWaitingMessage(settings: AppSettings): String {
        val messageResId = if (settings.allowMeteredDownloads) {
            R.string.ui_download_network_waiting
        } else {
            R.string.ui_download_network_waiting_unmetered
        }
        return context.localizedString(messageResId, settings.contentLanguage)
    }
}

internal fun VideoVariant.downloadTaskSubtitle(
    quality: String,
    voice: String = "",
): String {
    val voiceTitle = voice.ifBlank { matchingDisplayVoiceTitle }.ifBlank { "Voice" }
    val sourceTitle = player.cleanVideoSourceLabel().ifBlank { player }.ifBlank { "Source" }
    val qualityTitle = quality.ifBlank { "Auto" }
    return listOf(voiceTitle, sourceTitle, qualityTitle)
        .filter(String::isNotBlank)
        .joinToString(" \u2022 ")
}

internal const val DOWNLOAD_TASK_MAX_ATTEMPTS = 5
