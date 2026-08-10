package me.yummydroid.app.ui

import java.util.Locale
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingSourceKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.sourceEpisodeCounts
import me.yummydroid.app.data.sourceProviderRank
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.hasSamePlaybackSourceAs
import me.yummydroid.app.sourceSelectionKey

internal data class SourceOption(
    val key: String,
    val label: String,
    val video: VideoVariant,
)

internal fun List<VideoVariant>.sourceOptionsFor(
    currentVideo: VideoVariant,
    selectedVoiceKey: String?,
    sourceSubtitleSourceKeys: Set<String> = emptySet(),
    sourceSubtitleSelectionKeys: Set<String> = emptySet(),
    sourceSubtitleLabel: String = "Has subtitles",
): List<SourceOption> {
    val requestedVoiceKey = selectedVoiceKey?.takeIf { it.isNotBlank() } ?: currentVideo.matchingVoiceKey
    val voiceKey = requestedVoiceKey
        .takeIf { key ->
            any { candidate ->
                candidate.animeId == currentVideo.animeId &&
                    candidate.matchingVoiceKey == key
            }
        }
        ?: currentVideo.matchingVoiceKey
    val voiceVideos = filter { candidate ->
        candidate.animeId == currentVideo.animeId &&
            candidate.matchingVoiceKey == voiceKey
    }
    val episodeCountsBySource = voiceVideos.sourceEpisodeCounts()
    return filter { candidate ->
        candidate.animeId == currentVideo.animeId &&
            candidate.isSameEpisodeAs(currentVideo) &&
            candidate.matchingVoiceKey == voiceKey
    }
        .ifEmpty { listOf(currentVideo) }
        .sortedWith(
            compareBy<VideoVariant> { sourceProviderRank(it.player) }
                .thenBy { it.playbackSourceLabel(false).lowercase(Locale.ROOT) }
                .thenBy { it.index }
                .thenBy { it.id },
        )
        .distinctBy { it.sourceSelectionKey }
        .map { video ->
            val sourceLabel = video.playbackSourceLabel(false)
            val sourceEpisodeCount = episodeCountsBySource[video.matchingSourceKey].takeIf { it != null && it > 0 }
            val suffixParts = buildList {
                sourceEpisodeCount?.let { add(it.toString()) }
                if (
                    video.matchingSourceKey in sourceSubtitleSourceKeys ||
                    video.sourceSelectionKey in sourceSubtitleSelectionKeys
                ) {
                    add(sourceSubtitleLabel)
                }
            }
            val suffix = suffixParts.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = " (", postfix = ")").orEmpty()
            SourceOption(
                key = video.sourceSelectionKey,
                label = "$sourceLabel$suffix",
                video = video,
            )
        }
}

internal fun List<SourceOption>.withCurrentSubtitleMarker(
    currentVideo: VideoVariant,
    hasSubtitles: Boolean,
    sourceSubtitleLabel: String,
): List<SourceOption> {
    val label = sourceSubtitleLabel.trim()
    if (!hasSubtitles || label.isBlank()) return this
    return map { option ->
        if (!option.video.hasSamePlaybackSourceAs(currentVideo) || option.label.hasSourceOptionSuffixPart(label)) {
            option
        } else {
            option.copy(label = option.label.withSourceOptionSuffixPart(label))
        }
    }
}

private fun String.hasSourceOptionSuffixPart(part: String): Boolean {
    val normalizedPart = part.trim().lowercase(Locale.ROOT)
    if (normalizedPart.isBlank()) return false
    return substringAfterLast('(', missingDelimiterValue = "")
        .substringBeforeLast(')')
        .split(',')
        .map { it.trim().lowercase(Locale.ROOT) }
        .any { it == normalizedPart }
}

private fun String.withSourceOptionSuffixPart(part: String): String {
    val suffix = part.trim()
    if (suffix.isBlank()) return this
    val closingIndex = lastIndexOf(')')
    val openingIndex = lastIndexOf('(')
    return if (endsWith(")") && openingIndex >= 0 && openingIndex < closingIndex) {
        replaceRange(closingIndex, closingIndex, ", $suffix")
    } else {
        "$this ($suffix)"
    }
}

internal fun SubtitleOption.subtitleOptionIdentity(): String {
    val stableKey = key.substringBeforeLast(':', missingDelimiterValue = key)
    return listOf(
        language.orEmpty().lowercase(Locale.ROOT),
        label.lowercase(Locale.ROOT),
        stableKey.lowercase(Locale.ROOT),
    ).joinToString(":").replace(Regex("""\s+"""), "")
}

internal fun SubtitleOption.matchesSelectedSubtitleKey(selectedSubtitleKey: String?): Boolean {
    val selected = selectedSubtitleKey?.takeIf { it.isNotBlank() } ?: return false
    return key == selected || subtitleOptionIdentity() == selected
}

