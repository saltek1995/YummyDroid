package me.yummydroid.app

import android.content.Context
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
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
import me.yummydroid.app.data.sourceProviderRank
import me.yummydroid.app.data.siteVoiceOrderIndex
import me.yummydroid.app.data.sortedDownloadEpisodeSlots
import me.yummydroid.app.data.subtractEpisodeRanges
import me.yummydroid.app.data.writeJson

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
    val error: String? = null,
)

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
        val totalEpisodes = videos
            .map { it.matchingEpisodeKey }
            .distinct()
            .size
        return DownloadPlanBuildResult(
            plan = DownloadPlan(
                id = UUID.randomUUID().toString(),
                animeId = animeId,
                animeTitle = animeTitle,
                preferredQualityName = PreferredQuality.Auto.name,
                qualityNames = emptyList(),
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
    val qualityOrder = normalizedDownloadQualities(acceptableQualities)
    val orderedVoices = voiceOrder
        .filter { it in selectedVoiceKeys }
        .distinct()
    val videosByEpisode = videos.groupBy { it.matchingEpisodeKey }
    val episodeSlots = videos.sortedDownloadEpisodeSlots()
    val sourceComparator = downloadPlanSourceComparator()

    var alreadyDownloaded = 0
    var missingInSelectedVoices = 0
    var missingSelectedQuality = 0
    var excludedByEpisodeSelection = 0
    val items = mutableListOf<DownloadPlanItem>()

    episodeSlots.forEach { episode ->
        val episodeVideos = videosByEpisode[episode.key].orEmpty()
        if (onlyMissing && episodeVideos.any { video -> qualityOrder.any { video.hasDownloadedQuality(it) } }) {
            alreadyDownloaded += 1
            return@forEach
        }
        if (orderedVoices.isEmpty()) {
            missingInSelectedVoices += 1
            return@forEach
        }
        val allowedOrderedVoices = orderedVoices.filter { voiceKey ->
            episodeSelectionsByVoice[voiceKey]?.allows(episode.order) ?: true
        }
        if (allowedOrderedVoices.isEmpty()) {
            excludedByEpisodeSelection += 1
            return@forEach
        }

        val candidatesByVoice = episodeVideos
            .groupBy { it.downloadPlanVoiceKey }
            .mapValues { (_, voiceVideos) -> voiceVideos.sortedWith(sourceComparator) }
        val hasSelectedVoice = allowedOrderedVoices.any { voiceKey -> candidatesByVoice[voiceKey].orEmpty().isNotEmpty() }
        val selectedCandidate = allowedOrderedVoices
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

        if (selectedCandidate == null) {
            if (hasSelectedVoice && qualityOrder.any { it.height != null }) {
                missingSelectedQuality += 1
            } else {
                missingInSelectedVoices += 1
            }
            return@forEach
        }

        val (candidate, quality) = selectedCandidate
        items += DownloadPlanItem(
            episodeKey = episode.key,
            episodeTitle = candidate.episodeTitle,
            videoId = candidate.id,
            voiceKey = candidate.downloadPlanVoiceKey,
            voiceTitle = candidate.downloadPlanVoiceTitle,
            groupKey = candidate.groupKey,
            qualityName = quality.name,
        )
    }

    return DownloadPlanBuildResult(
        plan = DownloadPlan(
            id = UUID.randomUUID().toString(),
            animeId = animeId,
            animeTitle = animeTitle,
            preferredQualityName = qualityOrder.firstOrNull()?.name ?: PreferredQuality.Auto.name,
            qualityNames = qualityOrder.map { it.name },
            onlyMissing = onlyMissing,
            items = items,
        ),
        totalEpisodes = episodeSlots.size,
        selectedVoiceCount = orderedVoices.size,
        alreadyDownloaded = alreadyDownloaded,
        missingInSelectedVoices = missingInSelectedVoices,
        missingSelectedQuality = missingSelectedQuality,
        excludedByEpisodeSelection = excludedByEpisodeSelection,
    )
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
    normalizedInput
        .split(',', ';')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { token ->
            val bounds = token.split(Regex("""\s*-\s*"""))
            val range = when (bounds.size) {
                1 -> {
                    val value = bounds.single().toPositiveEpisodeNumberOrNull()
                        ?: return DownloadEpisodeSelectionParseResult(
                            selection = DownloadEpisodeSelection(ranges),
                            error = "Неверный номер серии: $token",
                        )
                    value..value
                }
                2 -> {
                    val start = bounds[0].toPositiveEpisodeNumberOrNull()
                    val end = bounds[1].toPositiveEpisodeNumberOrNull()
                    if (start == null || end == null || start > end) {
                        return DownloadEpisodeSelectionParseResult(
                            selection = DownloadEpisodeSelection(ranges),
                            error = "Неверный диапазон серий: $token",
                        )
                    }
                    start..end
                }
                else -> {
                    return DownloadEpisodeSelectionParseResult(
                        selection = DownloadEpisodeSelection(ranges),
                        error = "Неверный диапазон серий: $token",
                    )
                }
            }
            ranges += range
        }

    return DownloadEpisodeSelectionParseResult(DownloadEpisodeSelection(ranges.mergeEpisodeRanges()))
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
        error = "В этой озвучке нет серий: ${missingRanges.formatEpisodeRanges(limit = 6)}",
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

private fun List<VideoVariant>.selectDownloadPlanCandidate(preferredQuality: PreferredQuality): VideoVariant? {
    val qualityMatches = filter { it.canMaybeProvideDownloadQuality(preferredQuality) }
    return qualityMatches
        .sortedWith(downloadPlanSourceComparator())
        .firstOrNull()
}

private fun List<VideoVariant>.selectSortedDownloadPlanCandidate(preferredQuality: PreferredQuality): VideoVariant? {
    return firstOrNull { it.canMaybeProvideDownloadQuality(preferredQuality) }
}

private fun downloadPlanSourceComparator(): Comparator<VideoVariant> {
    return compareByDescending<VideoVariant> { it.isOfflineAvailable }
        .thenBy { sourceProviderRank(it.player) }
        .thenByDescending { it.maxKnownSourceQualityHeight() }
        .thenByDescending { it.offlineFiles.maxOfOrNull { file -> file.qualityHeight() } ?: 0 }
        .thenBy { it.index }
        .thenBy { it.id }
}
