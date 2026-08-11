package me.yummydroid.app

import android.content.Intent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.YummyAnimeRepository

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
    private val videoProcessor: DownloadVideoProcessor,
    private val taskController: DownloadIntentTaskController,
) {
    suspend fun process(intent: Intent) {
        val request = intent.toDownloadIntentRequest() ?: return
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
        return DownloadCenter.addTask(
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
            preferredQuality = request.preferredQuality,
            planId = request.preferredPlanId,
            batchKey = request.batchKey,
            existingTaskId = request.existingTaskId,
        )
    }

    private fun markTaskRunning(taskId: Long) {
        DownloadCenter.updateTask(
            id = taskId,
            state = DownloadTaskState.Running,
            message = taskRuntime.text(R.string.ui_preparing),
            waitingForUnmetered = false,
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
        DownloadCenter.updateTask(
            id = taskId,
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
        DownloadCenter.removeTask(prepareTaskId)
        coroutineScope {
            targets.map { video ->
                launch {
                    val taskId = DownloadCenter.addTask(
                        animeId = details.id,
                        videoId = video.id,
                        title = details.title,
                        episodeTitle = video.episodeTitle,
                        qualityTitle = video.downloadTaskSubtitle(request.preferredQuality.title),
                        groupKey = request.preferredGroupKey,
                        preferredQuality = request.preferredQuality,
                        batchKey = request.batchKey,
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

private fun Intent.toDownloadIntentRequest(): DownloadIntentRequest? {
    val animeId = getLongExtra(DOWNLOAD_EXTRA_ANIME_ID, 0L)
    if (animeId <= 0L) return null
    val existingTaskId = getLongExtra(DOWNLOAD_EXTRA_TASK_ID, 0L).takeIf { it > 0L }
    val requestedVideoId = getLongExtra(DOWNLOAD_EXTRA_VIDEO_ID, 0L).takeIf { it > 0L }
    val preferredGroupKey = getStringExtra(DOWNLOAD_EXTRA_GROUP_KEY).orEmpty()
    val preferredQuality = getStringExtra(DOWNLOAD_EXTRA_QUALITY_NAME)
        ?.let(PreferredQuality::fromName)
        ?: PreferredQuality.Auto
    val batchKey = existingTaskId
        ?.let { id -> DownloadCenter.state.value.tasks.firstOrNull { it.id == id }?.batchKey }
        ?.takeIf { it.isNotBlank() }
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
