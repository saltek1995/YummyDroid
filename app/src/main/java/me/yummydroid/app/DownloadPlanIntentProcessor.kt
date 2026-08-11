package me.yummydroid.app

import android.content.Context
import android.content.Intent
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.YummyAnimeRepository

private data class ResolvedDownloadPlanItem(
    val item: DownloadPlanItem,
    val video: VideoVariant,
)

private data class DownloadPlanProgress(
    val completed: AtomicInteger = AtomicInteger(0),
    val failed: AtomicInteger = AtomicInteger(0),
    val nextIndex: AtomicInteger = AtomicInteger(0),
)

internal class DownloadPlanIntentProcessor(
    private val context: Context,
    private val repository: YummyAnimeRepository,
    private val taskRuntime: DownloadTaskRuntime,
    private val videoProcessor: DownloadVideoProcessor,
    private val taskController: DownloadIntentTaskController,
) {
    suspend fun process(intent: Intent) {
        val planStorage = DownloadPlanStorage(context)
        val plan = planStorage.read(intent.getStringExtra(DOWNLOAD_EXTRA_PLAN_ID).orEmpty()) ?: return
        val summaryTaskId = addSummaryTask(plan, intent)
        if (!taskController.canStart(summaryTaskId)) return
        markPlanPreparing(summaryTaskId)
        runCatching {
            processStartedPlan(summaryTaskId, plan, planStorage)
        }.onFailure { throwable ->
            taskController.handleStartFailure(summaryTaskId, throwable, R.string.ui_download_plan_failed)
        }
    }

    private suspend fun processStartedPlan(
        summaryTaskId: Long,
        plan: DownloadPlan,
        planStorage: DownloadPlanStorage,
    ) {
        val (details, videos) = repository.getAnimeWithVideos(plan.animeId)
        val targets = plan.resolveTargets(videos)
        if (targets.isEmpty()) {
            completeEmptyPlan(summaryTaskId, plan, planStorage, details)
            return
        }
        val progress = DownloadPlanProgress()
        initializePlanProgress(summaryTaskId, details.title, targets.size)
        processPlanTargets(summaryTaskId, plan, details, videos, targets, progress)
        finishPlan(
            summaryTaskId = summaryTaskId,
            planId = plan.id,
            planStorage = planStorage,
            done = progress.completed.get(),
            errors = progress.failed.get(),
            total = targets.size,
        )
    }

    private fun addSummaryTask(plan: DownloadPlan, intent: Intent): Long {
        val existingTaskId = intent.getLongExtra(DOWNLOAD_EXTRA_TASK_ID, 0L).takeIf { it > 0L }
        return DownloadCenter.addTask(
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
    }

    private fun markPlanPreparing(summaryTaskId: Long) {
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
    }

    private fun DownloadPlan.resolveTargets(videos: List<VideoVariant>): List<ResolvedDownloadPlanItem> {
        return items
            .mapNotNull { item ->
                item.resolveVideo(videos)?.let { video -> ResolvedDownloadPlanItem(item, video) }
            }
            .filterNot { target ->
                onlyMissing && videos.hasDownloadedEpisodeForPlan(
                    target.item.episodeKey,
                    target.item.preferredQuality,
                )
            }
            .distinctBy { it.item.episodeKey }
    }

    private fun completeEmptyPlan(
        summaryTaskId: Long,
        plan: DownloadPlan,
        planStorage: DownloadPlanStorage,
        details: AnimeDetails,
    ) {
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
    }

    private fun initializePlanProgress(summaryTaskId: Long, title: String, total: Int) {
        DownloadCenter.moveTaskToTop(summaryTaskId)
        DownloadCenter.updateTask(
            id = summaryTaskId,
            title = title,
            episodeTitle = taskRuntime.text(R.string.ui_download_notification_progress, 0, total),
            progress = 0f,
            state = DownloadTaskState.Running,
            message = taskRuntime.text(R.string.ui_download_plan_loading),
            batchTotal = total,
            batchCompleted = 0,
        )
        taskRuntime.notifyChanged()
    }

    private suspend fun processPlanTargets(
        summaryTaskId: Long,
        plan: DownloadPlan,
        details: AnimeDetails,
        videos: List<VideoVariant>,
        targets: List<ResolvedDownloadPlanItem>,
        progress: DownloadPlanProgress,
    ) {
        coroutineScope {
            repeat(taskController.currentSettings().downloadParallelism.coerceIn(1, 4)) {
                launch {
                    processPlanWorker(summaryTaskId, plan, details, videos, targets, progress)
                }
            }
        }
    }

    private suspend fun processPlanWorker(
        summaryTaskId: Long,
        plan: DownloadPlan,
        details: AnimeDetails,
        videos: List<VideoVariant>,
        targets: List<ResolvedDownloadPlanItem>,
        progress: DownloadPlanProgress,
    ) {
        while (true) {
            if (DownloadCenter.isStopRequested(summaryTaskId)) return
            val index = progress.nextIndex.getAndIncrement()
            if (index >= targets.size) return
            val target = targets[index]
            val taskId = addPlanChildTask(summaryTaskId, plan, details, target, targets.size, progress)
            videoProcessor.process(
                taskId = taskId,
                detailsTitle = details.title,
                details = details,
                videos = videos,
                video = target.video,
                preferredQuality = target.item.preferredQuality,
                parentTaskId = summaryTaskId,
            )
            if (updatePlanChildResult(taskId, summaryTaskId, progress, targets.size)) return
        }
    }

    private fun addPlanChildTask(
        summaryTaskId: Long,
        plan: DownloadPlan,
        details: AnimeDetails,
        target: ResolvedDownloadPlanItem,
        total: Int,
        progress: DownloadPlanProgress,
    ): Long {
        return DownloadCenter.addTask(
            animeId = details.id,
            videoId = target.video.id,
            title = details.title,
            episodeTitle = target.item.episodeTitle.ifBlank { target.video.episodeTitle },
            qualityTitle = target.video.downloadTaskSubtitle(
                target.item.preferredQuality.title,
                target.item.voiceTitle,
            ),
            groupKey = target.video.groupKey,
            preferredQuality = target.item.preferredQuality,
            planId = plan.id,
            batchKey = plan.id,
            batchTotal = total,
            batchCompleted = progress.completed.get(),
        )
    }

    private fun updatePlanChildResult(
        taskId: Long,
        summaryTaskId: Long,
        progress: DownloadPlanProgress,
        total: Int,
    ): Boolean {
        val state = DownloadCenter.state.value.tasks.firstOrNull { it.id == taskId }?.state
        when (state) {
            DownloadTaskState.Completed -> updateCompletedPlanChild(taskId, summaryTaskId, progress, total)
            DownloadTaskState.Cancelled -> {
                DownloadCenter.removeTask(taskId)
                progress.failed.incrementAndGet()
            }
            DownloadTaskState.Failed -> progress.failed.incrementAndGet()
            else -> Unit
        }
        return state == DownloadTaskState.Paused
    }

    private fun updateCompletedPlanChild(
        taskId: Long,
        summaryTaskId: Long,
        progress: DownloadPlanProgress,
        total: Int,
    ) {
        DownloadCenter.removeTask(taskId)
        val done = progress.completed.incrementAndGet()
        DownloadCenter.updateTask(
            id = summaryTaskId,
            episodeTitle = taskRuntime.text(R.string.ui_download_notification_progress, done, total),
            progress = done.toFloat() / total.toFloat(),
            message = taskRuntime.text(R.string.ui_download_notification_progress, done, total),
            batchCompleted = done,
        )
        taskRuntime.notifyChanged()
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
            DownloadCenter.isCancelRequested(summaryTaskId) -> markPlanCancelled(summaryTaskId, done)
            DownloadCenter.isPauseRequested(summaryTaskId) -> markPlanPaused(summaryTaskId, done)
            errors > 0 -> markPlanFailed(summaryTaskId, done, total, errors)
            else -> {
                markPlanCompleted(summaryTaskId, done, total)
                planStorage.delete(planId)
            }
        }
        taskRuntime.notifyChanged()
    }

    private fun markPlanCancelled(taskId: Long, done: Int) {
        DownloadCenter.updateTask(
            id = taskId,
            state = DownloadTaskState.Cancelled,
            bytesPerSecond = 0L,
            message = taskRuntime.text(R.string.ui_cancelled),
            batchCompleted = done,
        )
    }

    private fun markPlanPaused(taskId: Long, done: Int) {
        DownloadCenter.updateTask(
            id = taskId,
            state = DownloadTaskState.Paused,
            bytesPerSecond = 0L,
            message = taskRuntime.text(R.string.ui_paused),
            batchCompleted = done,
        )
    }

    private fun markPlanFailed(taskId: Long, done: Int, total: Int, errors: Int) {
        DownloadCenter.updateTask(
            id = taskId,
            state = DownloadTaskState.Failed,
            bytesPerSecond = 0L,
            message = taskRuntime.text(R.string.ui_download_plan_completed_with_errors, done, total, errors),
            batchCompleted = done,
        )
    }

    private fun markPlanCompleted(taskId: Long, done: Int, total: Int) {
        DownloadCenter.updateTask(
            id = taskId,
            progress = 1f,
            downloadedBytes = 0L,
            totalBytes = 0L,
            bytesPerSecond = 0L,
            state = DownloadTaskState.Completed,
            message = taskRuntime.text(R.string.ui_download_plan_completed, done, total),
            batchCompleted = done,
        )
    }
}
