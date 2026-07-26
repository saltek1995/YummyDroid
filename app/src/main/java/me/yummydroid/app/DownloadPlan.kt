package me.yummydroid.app

import android.content.Context
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.serialization.Serializable
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.episodeOrderValue
import me.yummydroid.app.data.matchesPreferredQuality
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.matchingVoiceTitle
import me.yummydroid.app.data.qualityHeight
import me.yummydroid.app.data.readJsonOrNull
import me.yummydroid.app.data.sourceProviderRank
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
    val players: List<String>,
    val qualities: List<String>,
)

data class DownloadPlanBuildResult(
    val plan: DownloadPlan,
    val totalEpisodes: Int,
    val selectedVoiceCount: Int,
    val alreadyDownloaded: Int,
    val missingInSelectedVoices: Int,
    val missingSelectedQuality: Int,
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
): List<DownloadVoiceCoverage> {
    val qualityOrder = normalizedDownloadQualities(acceptableQualities)
    val selectedKey = selectedVoiceKey?.takeIf { it.isNotBlank() }
    return videos
        .groupBy { it.downloadPlanVoiceKey }
        .mapNotNull { (voiceKey, voiceVideos) ->
            val episodes = voiceVideos
                .distinctBy { it.matchingEpisodeKey }
                .map { it.downloadEpisodeSlot() }
                .sortedWith(downloadEpisodeSlotComparator())
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
                players = voiceVideos
                    .map { it.player.cleanVideoSourceLabel().ifBlank { it.player } }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sortedBy { sourceProviderRank(it) },
                qualities = voiceVideos
                    .flatMap { it.sourceQualities }
                    .mapNotNull { it.height }
                    .distinct()
                    .sortedDescending()
                    .map { "${it}p" },
            )
        }
        .sortedWith(
            compareBy<DownloadVoiceCoverage> { if (selectedKey != null && it.voiceKey == selectedKey) 0 else 1 }
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
    val episodeSlots = videos
        .groupBy { it.matchingEpisodeKey }
        .values
        .mapNotNull { group -> group.firstOrNull()?.downloadEpisodeSlot() }
        .sortedWith(downloadEpisodeSlotComparator())

    var alreadyDownloaded = 0
    var missingInSelectedVoices = 0
    var missingSelectedQuality = 0
    val items = mutableListOf<DownloadPlanItem>()

    episodeSlots.forEach { episode ->
        val episodeVideos = videos.filter { it.matchingEpisodeKey == episode.key }
        if (onlyMissing && episodeVideos.any { video -> qualityOrder.any { video.hasDownloadedQuality(it) } }) {
            alreadyDownloaded += 1
            return@forEach
        }

        val selectedCandidates = orderedVoices
            .flatMap { voiceKey -> episodeVideos.filter { it.downloadPlanVoiceKey == voiceKey } }
        val hasSelectedVoice = selectedCandidates.isNotEmpty()
        val selectedCandidate = orderedVoices
            .asSequence()
            .flatMap { voiceKey ->
                qualityOrder.asSequence().mapNotNull { quality ->
                    episodeVideos
                        .filter { it.downloadPlanVoiceKey == voiceKey }
                        .selectDownloadPlanCandidate(quality)
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

private fun normalizedDownloadQualities(qualities: Collection<PreferredQuality>): List<PreferredQuality> {
    val concrete = qualities
        .filter { it.height != null }
        .distinctBy { it.height }
        .sortedByDescending { it.height ?: 0 }
    if (concrete.isNotEmpty()) return concrete
    return listOf(PreferredQuality.Auto)
}

private val VideoVariant.downloadPlanVoiceKey: String
    get() = matchingVoiceKey.ifBlank { groupKey.lowercase(Locale.ROOT) }

private val VideoVariant.downloadPlanVoiceTitle: String
    get() = matchingVoiceTitle
        .ifBlank { dubbing.cleanVideoSourceLabel() }
        .ifBlank { groupTitle }
        .ifBlank { player.cleanVideoSourceLabel() }
        .ifBlank { "Озвучка" }

private data class DownloadEpisodeSlot(
    val key: String,
    val title: String,
    val order: Double?,
)

private fun VideoVariant.downloadEpisodeSlot(): DownloadEpisodeSlot {
    return DownloadEpisodeSlot(
        key = matchingEpisodeKey,
        title = episode.trim().takeIf { it.isNotBlank() } ?: matchingEpisodeKey,
        order = episodeOrderValue(),
    )
}

private fun downloadEpisodeSlotComparator(): Comparator<DownloadEpisodeSlot> {
    return compareBy<DownloadEpisodeSlot> { it.order ?: Double.MAX_VALUE }
        .thenBy { it.title }
        .thenBy { it.key }
}

private fun List<DownloadEpisodeSlot>.compactEpisodeRanges(): List<String> {
    if (isEmpty()) return emptyList()
    val ranges = mutableListOf<String>()
    var start = first()
    var previous = first()

    drop(1).forEach { current ->
        val contiguous = previous.order?.let { previousOrder ->
            current.order?.let { currentOrder ->
                isWholeNumber(previousOrder) &&
                    isWholeNumber(currentOrder) &&
                    currentOrder.toInt() == previousOrder.toInt() + 1
            }
        } == true
        if (contiguous) {
            previous = current
        } else {
            ranges += start.rangeTitle(previous)
            start = current
            previous = current
        }
    }
    ranges += start.rangeTitle(previous)
    return ranges
}

private fun DownloadEpisodeSlot.rangeTitle(end: DownloadEpisodeSlot): String {
    val startTitle = order?.formatEpisodeNumber() ?: title
    val endTitle = end.order?.formatEpisodeNumber() ?: end.title
    return if (key == end.key) startTitle else "$startTitle-$endTitle"
}

private fun Double.formatEpisodeNumber(): String {
    val asInt = toInt()
    return if (isWholeNumber(this)) asInt.toString() else toString().trimEnd('0').trimEnd('.')
}

private fun isWholeNumber(value: Double): Boolean {
    return value % 1.0 == 0.0
}

private fun List<VideoVariant>.selectDownloadPlanCandidate(preferredQuality: PreferredQuality): VideoVariant? {
    val qualityMatches = filter { it.canMaybeProvideDownloadQuality(preferredQuality) }
    return qualityMatches
        .sortedWith(downloadPlanSourceComparator())
        .firstOrNull()
}

private fun VideoVariant.canMaybeProvideDownloadQuality(preferredQuality: PreferredQuality): Boolean {
    val height = preferredQuality.height ?: return true
    val qualities = sourceQualities
    return qualities.isEmpty() || qualities.any { it.height == height }
}

private fun VideoVariant.hasDownloadedQuality(preferredQuality: PreferredQuality): Boolean {
    return offlineFiles.any { it.isCompletedDownload(preferredQuality) }
}

private fun OfflineVideoFile.isCompletedDownload(preferredQuality: PreferredQuality): Boolean {
    return playbackUrl.isNotBlank() && bytes > 0L && matchesPreferredQuality(preferredQuality)
}

private fun downloadPlanSourceComparator(): Comparator<VideoVariant> {
    return compareByDescending<VideoVariant> { it.isOfflineAvailable }
        .thenBy { sourceProviderRank(it.player) }
        .thenByDescending { it.sourceQualities.maxOfOrNull { quality -> quality.height ?: 0 } ?: 0 }
        .thenByDescending { it.offlineFiles.maxOfOrNull { file -> file.qualityHeight() } ?: 0 }
        .thenBy { it.index }
        .thenBy { it.id }
}
