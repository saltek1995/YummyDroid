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
