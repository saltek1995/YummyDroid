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

// DownloadEpisodeSelectionParser
private val DownloadEpisodeRangeSeparator = Regex("""\s*-\s*""")

private sealed interface ParsedDownloadEpisodeRange {
    data class Valid(val range: IntRange) : ParsedDownloadEpisodeRange
    data class Invalid(val error: DownloadEpisodeSelectionError) : ParsedDownloadEpisodeRange
}

fun parseDownloadEpisodeSelection(input: String): DownloadEpisodeSelectionParseResult {
    val normalizedInput = input
        .trim()
        .replace('\u2013', '-')
        .replace('\u2014', '-')
    if (normalizedInput.isBlank()) {
        return DownloadEpisodeSelectionParseResult(DownloadEpisodeSelection())
    }

    val ranges = mutableListOf<IntRange>()
    val tokens = normalizedInput
        .split(',', ';')
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
    tokens.forEach { token ->
        when (val parsed = parseDownloadEpisodeRange(token)) {
            is ParsedDownloadEpisodeRange.Valid -> ranges += parsed.range
            is ParsedDownloadEpisodeRange.Invalid -> {
                return DownloadEpisodeSelectionParseResult(
                    selection = DownloadEpisodeSelection(ranges),
                    error = parsed.error,
                )
            }
        }
    }

    return DownloadEpisodeSelectionParseResult(DownloadEpisodeSelection(ranges.mergeEpisodeRanges()))
}

private fun parseDownloadEpisodeRange(token: String): ParsedDownloadEpisodeRange {
    val bounds = token.split(DownloadEpisodeRangeSeparator)
    if (bounds.size == 1) {
        val value = bounds.single().toPositiveEpisodeNumberOrNull()
            ?: return ParsedDownloadEpisodeRange.Invalid(
                DownloadEpisodeSelectionError.InvalidEpisodeNumber(token),
            )
        return ParsedDownloadEpisodeRange.Valid(value..value)
    }
    if (bounds.size != 2) {
        return ParsedDownloadEpisodeRange.Invalid(DownloadEpisodeSelectionError.InvalidEpisodeRange(token))
    }
    val start = bounds[0].toPositiveEpisodeNumberOrNull()
    val end = bounds[1].toPositiveEpisodeNumberOrNull()
    return if (start != null && end != null && start <= end) {
        ParsedDownloadEpisodeRange.Valid(start..end)
    } else {
        ParsedDownloadEpisodeRange.Invalid(DownloadEpisodeSelectionError.InvalidEpisodeRange(token))
    }
}

fun validateDownloadEpisodeSelection(
    input: String,
    availableRanges: List<IntRange>,
): DownloadEpisodeSelectionParseResult {
    val parsed = parseDownloadEpisodeSelection(input)
    if (parsed.error != null || !parsed.selection.isRestricted || availableRanges.isEmpty()) {
        return parsed
    }
    val missingRanges = parsed.selection.ranges
        .flatMap { selectedRange -> selectedRange.subtractEpisodeRanges(availableRanges) }
        .mergeEpisodeRanges()
    if (missingRanges.isEmpty()) return parsed
    return parsed.copy(
        error = DownloadEpisodeSelectionError.MissingEpisodes(
            ranges = missingRanges.formatEpisodeRanges(limit = 6),
        ),
    )
}

fun DownloadPlanItem.resolveVideo(videos: List<VideoVariant>): VideoVariant? {
    val quality = preferredQuality
    return videos.firstOrNull { it.id == videoId }
        ?.takeIf { it.canMaybeProvideDownloadQuality(quality) }
        ?: videos
            .filter { it.matchingEpisodeKey == episodeKey && it.downloadPlanVoiceKey == voiceKey }
            .selectDownloadPlanCandidate(quality)
}

fun List<VideoVariant>.hasDownloadedEpisodeForPlan(
    episodeKey: String,
    preferredQuality: PreferredQuality,
): Boolean {
    return any { it.matchingEpisodeKey == episodeKey && it.hasDownloadedQuality(preferredQuality) }
}

private fun String.toPositiveEpisodeNumberOrNull(): Int? {
    return trim()
        .toIntOrNull()
        ?.takeIf { it > 0 }
}

private fun List<VideoVariant>.selectDownloadPlanCandidate(
    preferredQuality: PreferredQuality,
    sourceComparator: Comparator<VideoVariant> = downloadPlanSourceComparator(),
): VideoVariant? = asSequence()
    .filter { it.canMaybeProvideDownloadQuality(preferredQuality) }
    .minWithOrNull(sourceComparator)

internal fun downloadPlanSourceComparator(): Comparator<VideoVariant> {
    return compareByDescending<VideoVariant> { it.isOfflineAvailable }
        .thenBy { sourceProviderRank(it.player) }
        .thenByDescending { it.maxKnownSourceQualityHeight() }
        .thenByDescending { it.offlineFiles.maxOfOrNull { file -> file.qualityHeight() } ?: 0 }
        .thenBy { it.index }
        .thenBy { it.id }
}

// DownloadPlanBuilder
fun buildDownloadPlan(
    animeId: Long,
    animeTitle: String,
    videos: List<VideoVariant>,
    acceptableQualities: Collection<PreferredQuality>,
    selectedVoiceKeys: Set<String>,
    voiceOrder: List<String>,
    onlyMissing: Boolean,
    episodeSelectionsByVoice: Map<String, DownloadEpisodeSelection> = emptyMap(),
): DownloadPlanBuildResult {
    if (acceptableQualities.isEmpty()) {
        return buildEmptyQualityDownloadPlanResult(
            animeId = animeId,
            animeTitle = animeTitle,
            videos = videos,
            onlyMissing = onlyMissing,
        )
    }
    val qualityOrder = normalizedDownloadQualities(acceptableQualities)
    val orderedVoices = voiceOrder
        .filter { it in selectedVoiceKeys }
        .distinct()
    val videosByEpisode = videos.groupBy { it.matchingEpisodeKey }
    val episodeSlots = videos.sortedDownloadEpisodeSlots()
    val context = DownloadPlanBuildContext(
        qualityOrder = qualityOrder,
        orderedVoices = orderedVoices,
        onlyMissing = onlyMissing,
        episodeSelectionsByVoice = episodeSelectionsByVoice,
    )
    val accumulator = DownloadPlanAccumulator()

    episodeSlots.forEach { episode ->
        accumulator.record(
            planDownloadEpisode(
                episodeKey = episode.key,
                episodeOrder = episode.order,
                episodeVideos = videosByEpisode[episode.key].orEmpty(),
                context = context,
            ),
        )
    }

    return accumulator.toBuildResult(
        plan = createDownloadPlan(
            animeId = animeId,
            animeTitle = animeTitle,
            qualityOrder = qualityOrder,
            onlyMissing = onlyMissing,
            items = accumulator.items,
        ),
        totalEpisodes = episodeSlots.size,
        selectedVoiceCount = orderedVoices.size,
    )
}

private fun buildEmptyQualityDownloadPlanResult(
    animeId: Long,
    animeTitle: String,
    videos: List<VideoVariant>,
    onlyMissing: Boolean,
): DownloadPlanBuildResult {
    val totalEpisodes = videos
        .map { it.matchingEpisodeKey }
        .distinct()
        .size
    return DownloadPlanBuildResult(
        plan = createDownloadPlan(
            animeId = animeId,
            animeTitle = animeTitle,
            qualityOrder = emptyList(),
            onlyMissing = onlyMissing,
            items = emptyList(),
        ),
        totalEpisodes = totalEpisodes,
        selectedVoiceCount = 0,
        alreadyDownloaded = 0,
        missingInSelectedVoices = 0,
        missingSelectedQuality = totalEpisodes,
    )
}

private data class DownloadPlanBuildContext(
    val qualityOrder: List<PreferredQuality>,
    val orderedVoices: List<String>,
    val onlyMissing: Boolean,
    val episodeSelectionsByVoice: Map<String, DownloadEpisodeSelection>,
    val sourceComparator: Comparator<VideoVariant> = downloadPlanSourceComparator(),
)

private sealed interface DownloadEpisodePlanDecision {
    data class Schedule(val item: DownloadPlanItem) : DownloadEpisodePlanDecision
    data object AlreadyDownloaded : DownloadEpisodePlanDecision
    data object MissingVoice : DownloadEpisodePlanDecision
    data object MissingQuality : DownloadEpisodePlanDecision
    data object ExcludedBySelection : DownloadEpisodePlanDecision
}

private class DownloadPlanAccumulator {
    val items = mutableListOf<DownloadPlanItem>()
    private var alreadyDownloaded = 0
    private var missingInSelectedVoices = 0
    private var missingSelectedQuality = 0
    private var excludedByEpisodeSelection = 0

    fun record(decision: DownloadEpisodePlanDecision) {
        when (decision) {
            is DownloadEpisodePlanDecision.Schedule -> items += decision.item
            DownloadEpisodePlanDecision.AlreadyDownloaded -> alreadyDownloaded += 1
            DownloadEpisodePlanDecision.MissingVoice -> missingInSelectedVoices += 1
            DownloadEpisodePlanDecision.MissingQuality -> missingSelectedQuality += 1
            DownloadEpisodePlanDecision.ExcludedBySelection -> excludedByEpisodeSelection += 1
        }
    }

    fun toBuildResult(
        plan: DownloadPlan,
        totalEpisodes: Int,
        selectedVoiceCount: Int,
    ): DownloadPlanBuildResult {
        return DownloadPlanBuildResult(
            plan = plan,
            totalEpisodes = totalEpisodes,
            selectedVoiceCount = selectedVoiceCount,
            alreadyDownloaded = alreadyDownloaded,
            missingInSelectedVoices = missingInSelectedVoices,
            missingSelectedQuality = missingSelectedQuality,
            excludedByEpisodeSelection = excludedByEpisodeSelection,
        )
    }
}

private fun planDownloadEpisode(
    episodeKey: String,
    episodeOrder: Double?,
    episodeVideos: List<VideoVariant>,
    context: DownloadPlanBuildContext,
): DownloadEpisodePlanDecision {
    if (context.onlyMissing && episodeVideos.hasDownloadedPlanQuality(context.qualityOrder)) {
        return DownloadEpisodePlanDecision.AlreadyDownloaded
    }
    if (context.orderedVoices.isEmpty()) {
        return DownloadEpisodePlanDecision.MissingVoice
    }

    val allowedVoices = context.orderedVoices.filter { voiceKey ->
        context.episodeSelectionsByVoice[voiceKey]?.allows(episodeOrder) ?: true
    }
    if (allowedVoices.isEmpty()) {
        return DownloadEpisodePlanDecision.ExcludedBySelection
    }

    val candidatesByVoice = episodeVideos
        .groupBy { it.downloadPlanVoiceKey }
    val selectedCandidate = selectDownloadPlanCandidate(
        allowedVoices = allowedVoices,
        candidatesByVoice = candidatesByVoice,
        qualityOrder = context.qualityOrder,
        sourceComparator = context.sourceComparator,
    )
    if (selectedCandidate != null) {
        val (candidate, quality) = selectedCandidate
        return DownloadEpisodePlanDecision.Schedule(candidate.toDownloadPlanItem(episodeKey, quality))
    }

    val hasSelectedVoice = allowedVoices.any { candidatesByVoice[it].orEmpty().isNotEmpty() }
    return if (hasSelectedVoice && context.qualityOrder.any { it.height != null }) {
        DownloadEpisodePlanDecision.MissingQuality
    } else {
        DownloadEpisodePlanDecision.MissingVoice
    }
}

private fun selectDownloadPlanCandidate(
    allowedVoices: List<String>,
    candidatesByVoice: Map<String, List<VideoVariant>>,
    qualityOrder: List<PreferredQuality>,
    sourceComparator: Comparator<VideoVariant>,
): Pair<VideoVariant, PreferredQuality>? {
    allowedVoices.forEach { voiceKey ->
        val voiceVideos = candidatesByVoice[voiceKey].orEmpty()
        qualityOrder.forEach { quality ->
            val candidate = voiceVideos.selectDownloadPlanCandidate(quality, sourceComparator)
            if (candidate != null) {
                return candidate to quality
            }
        }
    }
    return null
}

private fun VideoVariant.toDownloadPlanItem(
    episodeKey: String,
    quality: PreferredQuality,
): DownloadPlanItem {
    return DownloadPlanItem(
        episodeKey = episodeKey,
        episodeTitle = episodeTitle,
        videoId = id,
        voiceKey = downloadPlanVoiceKey,
        voiceTitle = downloadPlanVoiceTitle,
        groupKey = groupKey,
        qualityName = quality.name,
    )
}

private fun List<VideoVariant>.hasDownloadedPlanQuality(
    qualityOrder: List<PreferredQuality>,
): Boolean {
    return any { video -> qualityOrder.any { video.hasDownloadedQuality(it) } }
}

private fun createDownloadPlan(
    animeId: Long,
    animeTitle: String,
    qualityOrder: List<PreferredQuality>,
    onlyMissing: Boolean,
    items: List<DownloadPlanItem>,
): DownloadPlan {
    return DownloadPlan(
        id = UUID.randomUUID().toString(),
        animeId = animeId,
        animeTitle = animeTitle,
        preferredQualityName = qualityOrder.firstOrNull()?.name ?: PreferredQuality.Auto.name,
        qualityNames = qualityOrder.map { it.name },
        onlyMissing = onlyMissing,
        items = items,
    )
}

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
