package me.yummydroid.app

import android.content.Context
import android.content.Intent
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.YummyAnimeRepository
import me.yummydroid.app.data.canMaybeProvideDownloadQuality
import me.yummydroid.app.data.compactEpisodeNumberRanges
import me.yummydroid.app.data.compactEpisodeRanges
import me.yummydroid.app.data.downloadCoverageQualityTitles
import me.yummydroid.app.data.downloadPlanVoiceKey
import me.yummydroid.app.data.downloadPlanVoiceTitle
import me.yummydroid.app.data.formatEpisodeRanges
import me.yummydroid.app.data.hasDownloadedQuality
import me.yummydroid.app.data.isWholeNumber
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.maxKnownSourceQualityHeight
import me.yummydroid.app.data.mergeEpisodeRanges
import me.yummydroid.app.data.normalizedDownloadQualities
import me.yummydroid.app.data.qualityHeight
import me.yummydroid.app.data.readJsonOrNull
import me.yummydroid.app.data.siteVoiceOrderIndex
import me.yummydroid.app.data.sortedDownloadEpisodeSlots
import me.yummydroid.app.data.sourceProviderRank
import me.yummydroid.app.data.subtractEpisodeRanges
import me.yummydroid.app.data.writeJson

// DownloadPlanIntentProcessor
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
            if (throwable is CancellationException) throw throwable
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

// DownloadPlanItem
@Serializable
data class DownloadPlanItem(
    val episodeKey: String,
    val episodeTitle: String,
    val videoId: Long,
    val voiceKey: String,
    val voiceTitle: String,
    val groupKey: String,
    val qualityName: String = PreferredQuality.Auto.name,
)

val DownloadPlanItem.preferredQuality: PreferredQuality
    get() = PreferredQuality.fromName(qualityName) ?: PreferredQuality.Auto

// DownloadPlanModel
@Serializable
data class DownloadPlan(
    val id: String,
    val animeId: Long,
    val animeTitle: String,
    val preferredQualityName: String = PreferredQuality.Auto.name,
    val qualityNames: List<String> = emptyList(),
    val onlyMissing: Boolean,
    val items: List<DownloadPlanItem>,
    val createdAtMs: Long = System.currentTimeMillis(),
) {
    val preferredQuality: PreferredQuality
        get() = acceptableQualities.firstOrNull() ?: PreferredQuality.Auto

    val acceptableQualities: List<PreferredQuality>
        get() = normalizedDownloadQualities(
            qualityNames.mapNotNull(PreferredQuality::fromName)
                .ifEmpty { listOf(PreferredQuality.fromName(preferredQualityName) ?: PreferredQuality.Auto) },
        )

    val qualityTitle: String
        get() = acceptableQualities.joinToString(", ") { it.title }
}

// DownloadPlanModels
data class DownloadVoiceCoverage(
    val voiceKey: String,
    val title: String,
    val episodeCount: Int,
    val downloadedCount: Int,
    val ranges: List<String>,
    val availableEpisodeRanges: List<IntRange>,
    val qualities: List<String>,
)

data class DownloadEpisodeSelection(
    val ranges: List<IntRange> = emptyList(),
) {
    val isRestricted: Boolean
        get() = ranges.isNotEmpty()

    fun allows(order: Double?): Boolean {
        if (!isRestricted) return true
        val episodeNumber = order
            ?.takeIf(::isWholeNumber)
            ?.toInt()
            ?: return false
        return ranges.any { range -> episodeNumber in range }
    }
}

data class DownloadEpisodeSelectionParseResult(
    val selection: DownloadEpisodeSelection,
    val error: DownloadEpisodeSelectionError? = null,
)

sealed interface DownloadEpisodeSelectionError {
    data class InvalidEpisodeNumber(val token: String) : DownloadEpisodeSelectionError
    data class InvalidEpisodeRange(val token: String) : DownloadEpisodeSelectionError
    data class MissingEpisodes(val ranges: String) : DownloadEpisodeSelectionError
}

data class DownloadPlanBuildResult(
    val plan: DownloadPlan,
    val totalEpisodes: Int,
    val selectedVoiceCount: Int,
    val alreadyDownloaded: Int,
    val missingInSelectedVoices: Int,
    val missingSelectedQuality: Int,
    val excludedByEpisodeSelection: Int = 0,
) {
    val scheduledCount: Int
        get() = plan.items.size
}

// DownloadPlanStorage
class DownloadPlanStorage(context: Context) {
    private val directory = File(context.filesDir, "download_plans")

    fun save(plan: DownloadPlan): String {
        directory.mkdirs()
        planFile(plan.id).writeJson(plan)
        return plan.id
    }

    fun read(id: String): DownloadPlan? {
        val safeId = id.takeIf { it.isNotBlank() } ?: return null
        return planFile(safeId).readJsonOrNull()
    }

    fun delete(id: String) {
        if (id.isBlank()) return
        runCatching { planFile(id).delete() }
    }

    private fun planFile(id: String): File {
        val safeName = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .ifBlank { "plan" }
        return File(directory, "$safeName.json")
    }
}

// DownloadVoiceCoverage
fun buildDownloadVoiceCoverages(
    videos: List<VideoVariant>,
    acceptableQualities: Collection<PreferredQuality>,
    selectedVoiceKey: String? = null,
    resolvedQualitiesByVoice: Map<String, List<PreferredQuality>> = emptyMap(),
): List<DownloadVoiceCoverage> {
    val qualityOrder = normalizedDownloadQualities(acceptableQualities)
    val selectedKey = selectedVoiceKey?.takeIf { it.isNotBlank() }
    val siteVoiceOrder = videos.siteVoiceOrderIndex()
    return videos
        .groupBy { it.downloadPlanVoiceKey }
        .mapNotNull { (voiceKey, voiceVideos) ->
            val episodes = voiceVideos
                .sortedDownloadEpisodeSlots()
            val first = voiceVideos.minWithOrNull(downloadPlanSourceComparator()) ?: return@mapNotNull null
            val downloaded = voiceVideos
                .asSequence()
                .filter { video -> qualityOrder.any { video.hasDownloadedQuality(it) } }
                .map { it.matchingEpisodeKey }
                .distinct()
                .count()
            DownloadVoiceCoverage(
                voiceKey = voiceKey,
                title = first.downloadPlanVoiceTitle,
                episodeCount = episodes.size,
                downloadedCount = downloaded,
                ranges = episodes.compactEpisodeRanges(),
                availableEpisodeRanges = episodes.compactEpisodeNumberRanges(),
                qualities = voiceVideos.downloadCoverageQualityTitles(
                    resolvedQualities = resolvedQualitiesByVoice[voiceKey].orEmpty(),
                ),
            )
        }
        .sortedWith(
            compareBy<DownloadVoiceCoverage> { if (selectedKey != null && it.voiceKey == selectedKey) 0 else 1 }
                .thenBy { siteVoiceOrder[it.voiceKey] ?: Int.MAX_VALUE }
                .thenByDescending { it.episodeCount }
                .thenBy { it.title.lowercase(Locale.ROOT) },
        )
}
