package me.yummydroid.app

import java.util.UUID
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.downloadPlanVoiceKey
import me.yummydroid.app.data.downloadPlanVoiceTitle
import me.yummydroid.app.data.hasDownloadedQuality
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.normalizedDownloadQualities
import me.yummydroid.app.data.sortedDownloadEpisodeSlots

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
        .mapValues { (_, voiceVideos) -> voiceVideos.sortedWith(context.sourceComparator) }
    val selectedCandidate = selectDownloadPlanCandidate(
        allowedVoices = allowedVoices,
        candidatesByVoice = candidatesByVoice,
        qualityOrder = context.qualityOrder,
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
): Pair<VideoVariant, PreferredQuality>? {
    return allowedVoices
        .asSequence()
        .flatMap { voiceKey ->
            val voiceVideos = candidatesByVoice[voiceKey].orEmpty()
            qualityOrder.asSequence().mapNotNull { quality ->
                voiceVideos
                    .selectSortedDownloadPlanCandidate(quality)
                    ?.let { candidate -> candidate to quality }
            }
        }
        .firstOrNull()
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

