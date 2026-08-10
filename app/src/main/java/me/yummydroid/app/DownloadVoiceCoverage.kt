package me.yummydroid.app

import java.util.Locale
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.compactEpisodeNumberRanges
import me.yummydroid.app.data.compactEpisodeRanges
import me.yummydroid.app.data.downloadCoverageQualityTitles
import me.yummydroid.app.data.downloadPlanVoiceKey
import me.yummydroid.app.data.downloadPlanVoiceTitle
import me.yummydroid.app.data.hasDownloadedQuality
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.normalizedDownloadQualities
import me.yummydroid.app.data.siteVoiceOrderIndex
import me.yummydroid.app.data.sortedDownloadEpisodeSlots

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

