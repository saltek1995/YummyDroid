package me.yummydroid.app

import android.content.Context
import android.content.Intent
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.YummyAnimeRepository

internal class DownloadIntentProcessor(
    private val context: Context,
    private val repository: YummyAnimeRepository,
    private val settingsStorage: AppSettingsStorage,
    private val taskRuntime: DownloadTaskRuntime,
    private val videoProcessor: DownloadVideoProcessor,
) {
    suspend fun process(intent: Intent) {
        if (intent.action == DOWNLOAD_ACTION_PLAN) {
            processPlan(intent)
            return
        }

        val animeId = intent.getLongExtra(DOWNLOAD_EXTRA_ANIME_ID, 0L)
        if (animeId <= 0L) return
        val existingTaskId = intent.getLongExtra(DOWNLOAD_EXTRA_TASK_ID, 0L).takeIf { it > 0L }
        val requestedVideoId = intent.getLongExtra(DOWNLOAD_EXTRA_VIDEO_ID, 0L).takeIf { it > 0L }
        val preferredGroupKey = intent.getStringExtra(DOWNLOAD_EXTRA_GROUP_KEY).orEmpty()
        val preferredPlanId = intent.getStringExtra(DOWNLOAD_EXTRA_PLAN_ID).orEmpty()
        val preferredQuality = intent.getStringExtra(DOWNLOAD_EXTRA_QUALITY_NAME)
            ?.let(PreferredQuality::fromName)
            ?: PreferredQuality.Auto
        val batchKey = existingTaskId
            ?.let { id -> DownloadCenter.state.value.tasks.firstOrNull { it.id == id }?.batchKey }
            ?.takeIf { it.isNotBlank() }
            ?: downloadBatchKey(animeId, requestedVideoId, preferredGroupKey, preferredQuality)
        val prepareTaskId = DownloadCenter.addTask(
            animeId = animeId,
            videoId = requestedVideoId,
            title = taskRuntime.text(R.string.ui_loading),
            episodeTitle = if (requestedVideoId == null) {
                taskRuntime.text(R.string.ui_all_episodes)
            } else {
                taskRuntime.text(R.string.ui_preparing)
            },
            qualityTitle = preferredQuality.title,
            groupKey = preferredGroupKey,
            preferredQuality = preferredQuality,
            planId = preferredPlanId,
            batchKey = batchKey,
            existingTaskId = existingTaskId,
        )

        val settings = settingsStorage.read()
        if (!DownloadNetworkPolicy.canDownloadNow(context, settings)) {
            taskRuntime.pauseForNetwork(prepareTaskId, settings)
            return
        }
        DownloadCenter.updateTask(
            id = prepareTaskId,
            state = DownloadTaskState.Running,
            message = taskRuntime.text(R.string.ui_preparing),
            waitingForUnmetered = false,
        )
        taskRuntime.notifyChanged()

        runCatching {
            val (details, videos) = repository.getAnimeWithVideos(animeId)
            val targets = if (requestedVideoId != null) {
                videos
                    .firstOrNull { it.id == requestedVideoId }
                    ?.takeUnless { videos.hasDownloadedRequestedSlot(it, preferredQuality) }
                    ?.let(::listOf)
                    .orEmpty()
            } else {
                videos.selectDownloadAllTargets(preferredGroupKey)
                    .filterNot { videos.hasDownloadedRequestedSlot(it, preferredQuality) }
            }

            if (targets.isEmpty()) {
                val hasVideos = videos.isNotEmpty()
                val alreadyDownloadedSingle = requestedVideoId != null && hasVideos
                DownloadCenter.updateTask(
                    id = prepareTaskId,
                    title = details.title,
                    episodeTitle = when {
                        alreadyDownloadedSingle -> videos.firstOrNull { it.id == requestedVideoId }?.episodeTitle
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
                return@runCatching
            }

            if (requestedVideoId == null) {
                processAllTargets(
                    prepareTaskId = prepareTaskId,
                    detailsTitle = details.title,
                    details = details,
                    videos = videos,
                    targets = targets,
                    preferredGroupKey = preferredGroupKey,
                    preferredQuality = preferredQuality,
                    batchKey = batchKey,
                )
            } else {
                videoProcessor.process(
                    taskId = prepareTaskId,
                    detailsTitle = details.title,
                    details = details,
                    videos = videos,
                    video = targets.first(),
                    preferredQuality = preferredQuality,
                )
                removeFinishedTask(prepareTaskId)
            }
        }.onFailure { throwable ->
            handleStartFailure(prepareTaskId, throwable, R.string.ui_download_start_failed)
        }
    }

    private suspend fun processAllTargets(
        prepareTaskId: Long,
        detailsTitle: String,
        details: me.yummydroid.app.data.AnimeDetails,
        videos: List<me.yummydroid.app.data.VideoVariant>,
        targets: List<me.yummydroid.app.data.VideoVariant>,
        preferredGroupKey: String,
        preferredQuality: PreferredQuality,
        batchKey: String,
    ) {
        DownloadCenter.removeTask(prepareTaskId)
        coroutineScope {
            targets.map { video ->
                launch {
                    val taskId = DownloadCenter.addTask(
                        animeId = details.id,
                        videoId = video.id,
                        title = detailsTitle,
                        episodeTitle = video.episodeTitle,
                        qualityTitle = video.downloadTaskSubtitle(preferredQuality.title),
                        groupKey = preferredGroupKey,
                        preferredQuality = preferredQuality,
                        batchKey = batchKey,
                    )
                    videoProcessor.process(
                        taskId = taskId,
                        detailsTitle = detailsTitle,
                        details = details,
                        videos = videos,
                        video = video,
                        preferredQuality = preferredQuality,
                    )
                    removeFinishedTask(taskId)
                }
            }.joinAll()
        }
    }

    private fun removeFinishedTask(taskId: Long) {
        val task = DownloadCenter.state.value.tasks.firstOrNull { it.id == taskId }
        if (task?.state == DownloadTaskState.Completed || task?.state == DownloadTaskState.Cancelled) {
            DownloadCenter.removeTask(taskId)
            taskRuntime.notifyChanged()
        }
    }

    private suspend fun processPlan(intent: Intent) {
        val planId = intent.getStringExtra(DOWNLOAD_EXTRA_PLAN_ID).orEmpty()
        val planStorage = DownloadPlanStorage(context)
        val plan = planStorage.read(planId) ?: return
        val existingTaskId = intent.getLongExtra(DOWNLOAD_EXTRA_TASK_ID, 0L).takeIf { it > 0L }
        val summaryTaskId = DownloadCenter.addTask(
            animeId = plan.animeId,
            videoId = null,
            title = plan.animeTitle.ifBlank { taskRuntime.text(R.string.ui_loading) },
            episodeTitle = taskRuntime.text(R.string.ui_download_plan),
            qualityTitle = plan.qualityTitle,
            preferredQuality = plan.preferredQuality,
            planId = plan.id,
            batchKey = plan.id,
            batchTotal = plan.items.size,
            batchCompleted = 0,
            isBatchSummary = true,
            existingTaskId = existingTaskId,
        )

        val initialSettings = settingsStorage.read()
        if (!DownloadNetworkPolicy.canDownloadNow(context, initialSettings)) {
            taskRuntime.pauseForNetwork(summaryTaskId, initialSettings)
            return
        }
        DownloadCenter.updateTask(
            id = summaryTaskId,
            state = DownloadTaskState.Running,
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = -1L,
            bytesPerSecond = 0L,
            message = taskRuntime.text(R.string.ui_preparing_download_plan),
            waitingForUnmetered = false,
            batchCompleted = 0,
        )
        taskRuntime.notifyChanged()

        runCatching {
            val (details, videos) = repository.getAnimeWithVideos(plan.animeId)
            val targets = plan.items
                .mapNotNull { item -> item.resolveVideo(videos)?.let { video -> item to video } }
                .filterNot { (item, _) ->
                    plan.onlyMissing && videos.hasDownloadedEpisodeForPlan(item.episodeKey, item.preferredQuality)
                }
                .distinctBy { (item, _) -> item.episodeKey }

            if (targets.isEmpty()) {
                DownloadCenter.updateTask(
                    id = summaryTaskId,
                    title = details.title,
                    episodeTitle = taskRuntime.text(R.string.ui_download_plan),
                    progress = 1f,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    bytesPerSecond = 0L,
                    state = DownloadTaskState.Completed,
                    message = taskRuntime.text(R.string.ui_all_selected_episodes_are_already_downloaded),
                    waitingForUnmetered = false,
                    batchTotal = 0,
                    batchCompleted = 0,
                )
                planStorage.delete(plan.id)
                taskRuntime.notifyChanged()
                return@runCatching
            }

            val total = targets.size
            DownloadCenter.moveTaskToTop(summaryTaskId)
            val completed = AtomicInteger(0)
            val failed = AtomicInteger(0)
            val nextIndex = AtomicInteger(0)
            DownloadCenter.updateTask(
                id = summaryTaskId,
                title = details.title,
                episodeTitle = taskRuntime.text(R.string.ui_download_notification_progress, 0, total),
                progress = 0f,
                state = DownloadTaskState.Running,
                message = taskRuntime.text(R.string.ui_download_plan_loading),
                batchTotal = total,
                batchCompleted = 0,
            )
            taskRuntime.notifyChanged()

            coroutineScope {
                repeat(settingsStorage.read().downloadParallelism.coerceIn(1, 4)) {
                    launch {
                        while (true) {
                            if (DownloadCenter.isStopRequested(summaryTaskId)) return@launch
                            val index = nextIndex.getAndIncrement()
                            if (index >= targets.size) return@launch
                            val (item, video) = targets[index]
                            val taskId = DownloadCenter.addTask(
                                animeId = details.id,
                                videoId = video.id,
                                title = details.title,
                                episodeTitle = item.episodeTitle.ifBlank { video.episodeTitle },
                                qualityTitle = video.downloadTaskSubtitle(
                                    item.preferredQuality.title,
                                    item.voiceTitle,
                                ),
                                groupKey = video.groupKey,
                                preferredQuality = item.preferredQuality,
                                planId = plan.id,
                                batchKey = plan.id,
                                batchTotal = total,
                                batchCompleted = completed.get(),
                            )
                            videoProcessor.process(
                                taskId = taskId,
                                detailsTitle = details.title,
                                details = details,
                                videos = videos,
                                video = video,
                                preferredQuality = item.preferredQuality,
                                parentTaskId = summaryTaskId,
                            )
                            if (updatePlanChildResult(taskId, summaryTaskId, completed, failed, total)) {
                                return@launch
                            }
                        }
                    }
                }
            }
            finishPlan(summaryTaskId, plan.id, planStorage, completed.get(), failed.get(), total)
        }.onFailure { throwable ->
            handleStartFailure(summaryTaskId, throwable, R.string.ui_download_plan_failed)
        }
    }

    private fun updatePlanChildResult(
        taskId: Long,
        summaryTaskId: Long,
        completed: AtomicInteger,
        failed: AtomicInteger,
        total: Int,
    ): Boolean {
        val state = DownloadCenter.state.value.tasks.firstOrNull { it.id == taskId }?.state
        when (state) {
            DownloadTaskState.Completed -> {
                DownloadCenter.removeTask(taskId)
                val done = completed.incrementAndGet()
                DownloadCenter.updateTask(
                    id = summaryTaskId,
                    episodeTitle = taskRuntime.text(R.string.ui_download_notification_progress, done, total),
                    progress = done.toFloat() / total.toFloat(),
                    message = taskRuntime.text(R.string.ui_download_notification_progress, done, total),
                    batchCompleted = done,
                )
                taskRuntime.notifyChanged()
            }
            DownloadTaskState.Cancelled -> {
                DownloadCenter.removeTask(taskId)
                failed.incrementAndGet()
            }
            DownloadTaskState.Failed -> failed.incrementAndGet()
            else -> Unit
        }
        return state == DownloadTaskState.Paused
    }

    private fun finishPlan(
        summaryTaskId: Long,
        planId: String,
        planStorage: DownloadPlanStorage,
        done: Int,
        errors: Int,
        total: Int,
    ) {
        when {
            DownloadCenter.isCancelRequested(summaryTaskId) -> DownloadCenter.updateTask(
                id = summaryTaskId,
                state = DownloadTaskState.Cancelled,
                bytesPerSecond = 0L,
                message = taskRuntime.text(R.string.ui_cancelled),
                batchCompleted = done,
            )
            DownloadCenter.isPauseRequested(summaryTaskId) -> DownloadCenter.updateTask(
                id = summaryTaskId,
                state = DownloadTaskState.Paused,
                bytesPerSecond = 0L,
                message = taskRuntime.text(R.string.ui_paused),
                batchCompleted = done,
            )
            errors > 0 -> DownloadCenter.updateTask(
                id = summaryTaskId,
                state = DownloadTaskState.Failed,
                bytesPerSecond = 0L,
                message = taskRuntime.text(R.string.ui_download_plan_completed_with_errors, done, total, errors),
                batchCompleted = done,
            )
            else -> {
                DownloadCenter.updateTask(
                    id = summaryTaskId,
                    progress = 1f,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    bytesPerSecond = 0L,
                    state = DownloadTaskState.Completed,
                    message = taskRuntime.text(R.string.ui_download_plan_completed, done, total),
                    batchCompleted = done,
                )
                planStorage.delete(planId)
            }
        }
        taskRuntime.notifyChanged()
    }

    private fun handleStartFailure(taskId: Long, throwable: Throwable, fallbackMessageRes: Int) {
        val latestSettings = settingsStorage.read()
        if (!DownloadNetworkPolicy.canDownloadNow(context, latestSettings)) {
            taskRuntime.pauseForNetwork(taskId, latestSettings)
            return
        }
        DownloadCenter.updateTask(
            id = taskId,
            state = DownloadTaskState.Failed,
            bytesPerSecond = 0L,
            message = throwable.message?.takeIf { it.isNotBlank() }
                ?: taskRuntime.text(fallbackMessageRes),
            waitingForUnmetered = false,
        )
        taskRuntime.notifyChanged()
    }
}
