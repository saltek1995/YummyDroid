package me.yummydroid.app.ui

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.height
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.ui.PlayerView
import java.util.Locale
import me.yummydroid.app.R
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.PlayerDecoderMode
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedEmbeddedSubtitleTrack
import me.yummydroid.app.data.ResolvedSubtitleTrack
import me.yummydroid.app.data.SourceQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.bestSourceQualityPerHeight
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.episodeOrderValue
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingSourceKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.qualityHeight
import me.yummydroid.app.data.selectForPreferredQuality
import me.yummydroid.app.data.sourceEpisodeCounts
import me.yummydroid.app.data.sourceProviderRank
import me.yummydroid.app.hasSamePlaybackSourceAs
import me.yummydroid.app.sourceSelectionKey

// PlayerQualityOptions
internal fun VideoVariant.localQualityOptions(): List<QualityOption> {
    return offlineFiles
        .filter { it.playbackUrl.isNotBlank() }
        .sortedWith(compareByDescending<OfflineVideoFile> { it.qualityHeight() }.thenBy { it.qualityTitle })
        .distinctBy { it.qualityOptionIdentity() }
        .map { file ->
            QualityOption(
                group = null,
                trackIndex = -1,
                label = file.qualityDisplayTitle(),
                height = file.qualityHeight(),
                bitrate = 0,
                key = file.qualityKey(),
                localFile = file,
            )
        }
}

internal fun List<VideoVariant>.sourceQualityOptionsFor(currentVideo: VideoVariant): List<QualityOption> {
    return filter { it.hasSamePlaybackSourceAs(currentVideo) }
        .ifEmpty { listOf(currentVideo) }
        .flatMap { it.sourceQualities }
        .sourceQualityOptions()
}

internal fun List<SourceQuality>.sourceQualityOptions(): List<QualityOption> {
    return bestSourceQualityPerHeight().mapNotNull { quality ->
        val preferredQuality = PreferredQuality.fromHeight(quality.height) ?: return@mapNotNull null
        val label = quality.title.takeIf { it.isNotBlank() } ?: preferredQuality.title
        QualityOption(
            group = null,
            trackIndex = -1,
            label = label,
            height = quality.height ?: 0,
            bitrate = quality.bitrate,
            key = "source:${quality.height}",
            preferredQuality = preferredQuality,
        )
    }
}

internal fun resolvedOnlineQualityOptions(
    streamOptions: List<QualityOption>,
    trackOptions: List<QualityOption>,
    sourceOptions: List<QualityOption>,
): List<QualityOption> {
    return when {
        streamOptions.isNotEmpty() -> streamOptions
        trackOptions.isNotEmpty() -> trackOptions
        else -> sourceOptions
    }
}

internal fun VideoVariant.withOfflineFile(file: OfflineVideoFile): VideoVariant {
    val mergedLocalFiles = (localFiles + file)
        .filter { it.playbackUrl.isNotBlank() }
        .distinctBy { it.playbackUrl }
        .sortedWith(compareByDescending<OfflineVideoFile> { it.qualityHeight() }.thenBy { it.qualityTitle })
    return copy(
        localPlaybackUrl = file.playbackUrl,
        localMimeType = file.mimeType,
        localBytes = file.bytes,
        localFiles = mergedLocalFiles,
    )
}

internal fun VideoVariant.withoutLocalPlayback(): VideoVariant {
    return copy(
        localPlaybackUrl = "",
        localMimeType = null,
        localBytes = 0L,
        localFiles = emptyList(),
    )
}

internal fun VideoVariant.selectedLocalQualityKey(streamUrl: String): String? {
    val selectedUrl = streamUrl.takeIf { it.startsWith("file:", ignoreCase = true) }
        ?: localPlaybackUrl.takeIf { it.isNotBlank() }
    return offlineFiles.firstOrNull { it.playbackUrl == selectedUrl }?.qualityKey()
}

internal fun OfflineVideoFile.qualityDisplayTitle(): String {
    return qualityTitle
        .replace('_', ' ')
        .takeIf { it.isNotBlank() }
        ?: "Local"
}

internal fun OfflineVideoFile.qualityKey(): String {
    return "local:${playbackUrl}:${qualityTitle}"
}

internal fun OfflineVideoFile.qualityOptionIdentity(): String {
    return qualityHeight()
        .takeIf { it > 0 }
        ?.let { "height:$it" }
        ?: qualityDisplayTitle().qualityIdentityFromLabel()
}

internal fun QualityOption.qualityOptionIdentity(): String {
    return height
        .takeIf { it > 0 }
        ?.let { "height:$it" }
        ?: label.qualityIdentityFromLabel()
}

internal fun String.qualityIdentityFromLabel(): String {
    val cleaned = replace("downloaded", "", ignoreCase = true)
    val height = Regex("""(?i)(2160|1440|1080|720|576|540|480|360|240|144)p""")
        .find(cleaned)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    if (height != null) return "height:$height"
    return cleaned
        .lowercase(Locale.ROOT)
        .replace(Regex("""[\s\u2022|:_\-]+"""), "")
        .trim()
}

internal fun QualityOption.withDownloadedLabel(downloadedLabel: String): QualityOption {
    if (localFile == null || label.contains(downloadedLabel, ignoreCase = true)) return this
    return copy(label = "$label \u2022 $downloadedLabel")
}

internal fun mergeVideoQualityOptions(
    onlineOptions: List<QualityOption>,
    localOptions: List<QualityOption>,
    offlineMode: Boolean,
    downloadedLabel: String = defaultPlayerControlTexts.downloaded,
): List<QualityOption> {
    val uniqueLocalOptions = localOptions.distinctBy { it.qualityOptionIdentity() }
    if (offlineMode) {
        return uniqueLocalOptions
            .map { it.withDownloadedLabel(downloadedLabel) }
            .sortedByQuality()
    }

    val localByIdentity = uniqueLocalOptions.associateBy { it.qualityOptionIdentity() }
    val onlineWithLocalFiles = onlineOptions.map { online ->
        val local = localByIdentity[online.qualityOptionIdentity()] ?: return@map online
        online.copy(
            label = if (online.label.contains(downloadedLabel, ignoreCase = true)) {
                online.label
            } else {
                "${online.label} \u2022 $downloadedLabel"
            },
            localFile = local.localFile,
        )
    }
    val onlineIdentities = onlineOptions.mapTo(mutableSetOf()) { it.qualityOptionIdentity() }
    val localOnlyOptions = uniqueLocalOptions
        .filterNot { it.qualityOptionIdentity() in onlineIdentities }
        .map { it.withDownloadedLabel(downloadedLabel) }

    return (onlineWithLocalFiles + localOnlyOptions)
        .distinctBy { it.qualityOptionIdentity() }
        .sortedByQuality()
}

internal fun List<QualityOption>.sortedByQuality(): List<QualityOption> {
    return sortedWith(
        compareByDescending<QualityOption> { it.height.coerceAtLeast(0) }
            .thenBy { it.label },
    )
}

internal fun QualityOption.matchesSelectedQualityKey(selectedQualityKey: String?): Boolean {
    val selected = selectedQualityKey?.takeIf { it.isNotBlank() } ?: return false
    return key == selected ||
        localFile?.qualityKey() == selected ||
        qualityOptionIdentity() == selected ||
        qualityOptionIdentity() == selected.qualityIdentityFromLabel()
}

// PlayerSourceOptions
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

// PlayerSubtitleConfiguration
internal fun ResolvedSubtitleTrack.toMedia3SubtitleConfiguration(): MediaItem.SubtitleConfiguration? {
    val cleanUri = uri.takeIf { it.isNotBlank() } ?: return null
    if (!isMaterializedSubtitleTrack()) return null
    val resolvedMimeType = subtitleMimeTypeForMedia3(cleanUri, mimeType)
        ?.takeIf { it.isSideLoadedSubtitleMimeType() }
        ?: return null
    return MediaItem.SubtitleConfiguration.Builder(cleanUri.toUri()).apply {
        setMimeType(resolvedMimeType)
        language?.takeIf { it.isNotBlank() }?.let(::setLanguage)
        setId(media3SubtitleId())
        subtitleLabelForMedia3(label, cleanUri).takeIf { it.isNotBlank() }?.let { resolvedLabel ->
            setLabel(resolvedLabel)
        }
        setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
    }.build()
}

internal fun ResolvedSubtitleTrack.toMedia3SubtitleReference(): ResolvedSubtitleTrackReference? {
    val cleanUri = uri.takeIf { it.isNotBlank() } ?: return null
    if (!isMaterializedSubtitleTrack()) return null
    return ResolvedSubtitleTrackReference(
        media3Id = media3SubtitleId(),
        label = subtitleLabelForMedia3(label, cleanUri),
    )
}

internal fun ResolvedSubtitleTrack.toSubtitleDisplayReference(sourceIndex: Int): ResolvedSubtitleTrackReference? {
    val cleanUri = uri.takeIf { it.isNotBlank() } ?: return null
    val resolvedLabel = subtitleLabelForMedia3(label, cleanUri)
        .takeIf { it.isNotBlank() }
        ?: return null
    return ResolvedSubtitleTrackReference(
        media3Id = if (isMaterializedSubtitleTrack()) media3SubtitleId() else "",
        label = resolvedLabel,
        language = language,
        sourceIndex = sourceIndex,
    )
}

internal fun ResolvedEmbeddedSubtitleTrack.toSubtitleDisplayReference(sourceIndex: Int): ResolvedSubtitleTrackReference? {
    val resolvedLabel = label.subtitleUserVisibleLabel()
        ?: language?.subtitleLanguageDisplayName()
        ?: id.subtitleUserVisibleLabel()
        ?: return null
    return ResolvedSubtitleTrackReference(
        media3Id = id,
        label = resolvedLabel,
        language = language,
        sourceIndex = sourceIndex,
    )
}

internal fun ResolvedSubtitleTrack.isMaterializedSubtitleTrack(): Boolean {
    val cleanUri = uri.takeIf { it.isNotBlank() } ?: return false
    return cleanUri.startsWith("file:", ignoreCase = true) ||
        cleanUri.startsWith("content:", ignoreCase = true)
}

private fun ResolvedSubtitleTrack.media3SubtitleId(): String {
    val cleanUri = uri.trim()
    return listOf(
        "external-subtitle",
        cleanUri,
        language.orEmpty(),
        subtitleLabelForMedia3(label, cleanUri),
    ).joinToString(":")
}

internal fun subtitleLabelForMedia3(label: String, uri: String): String {
    label.subtitleUserVisibleLabel()?.let { return it }
    return uri.subtitleIdentifierLabel()
}

private fun String.subtitleLanguageDisplayName(): String? {
    if (isBlank() || this == C.LANGUAGE_UNDETERMINED) return null
    return runCatching { Locale.forLanguageTag(this).getDisplayLanguage(Locale.getDefault()) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}

internal fun String.subtitleUserVisibleLabel(): String? {
    val cleaned = trim().takeIf { it.isNotBlank() } ?: return null
    return cleaned.takeUnless { cleaned.isTechnicalSubtitleLabel() }
}

private fun String.isTechnicalSubtitleLabel(): Boolean {
    val normalized = lowercase(Locale.ROOT)
    return normalized.isSubtitleCacheHash() ||
        normalized.isNumericTrackId() ||
        normalized.isOpaqueHexTrackId()
}

private fun String.isSubtitleCacheHash(): Boolean {
    if (!startsWith("subtitle_") || length < 24) return false
    return removePrefix("subtitle_").all(Char::isAsciiHexDigit)
}

private fun String.isNumericTrackId(): Boolean = all(Char::isDigit)

private fun String.isOpaqueHexTrackId(): Boolean {
    if (length !in 4..16 || !all(Char::isAsciiHexDigit)) return false
    return any(Char::isDigit) && any { it in 'a'..'f' }
}

private fun Char.isAsciiHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f'

internal fun subtitleMimeTypeForMedia3(uri: String, mimeType: String?): String? {
    val source = mimeType?.takeIf { it.isNotBlank() } ?: uri
    val lower = source.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
    return when {
        "mpegurl" in lower || lower.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
        "subrip" in lower || lower.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        "text/vtt" in lower || lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
        "text/x-ssa" in lower || lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        "ttml" in lower || lower.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
        else -> null
    }
}

internal fun String.isSideLoadedSubtitleMimeType(): Boolean {
    return this == MimeTypes.TEXT_VTT ||
        this == MimeTypes.APPLICATION_SUBRIP ||
        this == MimeTypes.TEXT_SSA ||
        this == MimeTypes.APPLICATION_TTML
}

// PlayerSubtitleTracks
@OptIn(UnstableApi::class)
internal fun ExoPlayer.selectSubtitle(option: SubtitleOption) {
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .addOverride(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex))
        .build()
}

@OptIn(UnstableApi::class)
internal fun ExoPlayer.disableSubtitles() {
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        .build()
}

internal data class SubtitleOption(
    val group: Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val selectionFlags: Int,
    val key: String,
    val isResolvedTrack: Boolean = false,
)

internal data class ResolvedSubtitleTrackReference(
    val media3Id: String,
    val label: String,
    val language: String? = null,
    val sourceIndex: Int? = null,
)

private data class SubtitleTrackCandidate(
    val group: Tracks.Group,
    val trackIndex: Int,
    val format: androidx.media3.common.Format,
)

@OptIn(UnstableApi::class)
internal fun Tracks.subtitleOptions(
    texts: PlayerControlTexts,
    resolvedSubtitles: List<ResolvedSubtitleTrackReference>? = null,
): List<SubtitleOption> {
    val candidates = groups
        .filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
        .flatMap { group ->
            (0 until group.length)
                .filter { trackIndex -> group.isTrackSupported(trackIndex) }
                .map { trackIndex -> SubtitleTrackCandidate(group, trackIndex, group.getTrackFormat(trackIndex)) }
        }
    val resolvedSubtitleReferences = resolvedSubtitles.orEmpty()
    val fallbackResolvedSubtitle = singleResolvedSubtitleFallback(
        resolvedSubtitles = resolvedSubtitleReferences,
        media3SubtitleTrackCount = candidates.size,
    )
    val options = candidates
        .mapIndexedNotNull { candidateIndex, candidate ->
            val format = candidate.format
            val trackIndex = candidate.trackIndex
            val media3Label = format.subtitleLabel(texts, trackIndex)
            val resolvedSubtitle = format.matchingResolvedSubtitleReference(
                resolvedSubtitles = resolvedSubtitleReferences,
            )
                ?: orderedResolvedSubtitleFallback(
                    resolvedSubtitles = resolvedSubtitleReferences,
                    media3SubtitleTrackCount = candidates.size,
                    media3SubtitleTrackIndex = candidateIndex,
                )
                ?: fallbackResolvedSubtitle
            if (resolvedSubtitle == null && !format.canShowUnresolvedSubtitleTrack()) {
                return@mapIndexedNotNull null
            }
            val label = media3Label.subtitleDisplayLabel(
                texts = texts,
                trackIndex = trackIndex,
                resolvedSubtitleLabel = resolvedSubtitle?.label,
            )
            SubtitleOption(
                group = candidate.group,
                trackIndex = trackIndex,
                label = label,
                language = format.language,
                selectionFlags = format.selectionFlags,
                key = "${format.id.orEmpty()}:${format.language.orEmpty()}:${format.label.orEmpty()}:$trackIndex",
                isResolvedTrack = resolvedSubtitle != null,
            )
        }
        .distinctBy { it.subtitleOptionIdentity() }
    val resolvedOptions = options.filter { option -> option.isResolvedTrack }
    val visibleOptions = if (
        shouldShowOnlyResolvedSubtitleOptions(
            resolvedSubtitles = resolvedSubtitles,
            hasResolvedOptions = resolvedOptions.isNotEmpty(),
        )
    ) {
        resolvedOptions
    } else {
        options
    }
    return visibleOptions
        .sortedWith(compareByDescending<SubtitleOption> { it.isResolvedTrack }.thenBy { it.label })
}

internal fun androidx.media3.common.Format.canShowUnresolvedSubtitleTrack(): Boolean {
    return sampleMimeType != MimeTypes.APPLICATION_CEA608 &&
        sampleMimeType != MimeTypes.APPLICATION_CEA708
}

internal fun shouldShowOnlyResolvedSubtitleOptions(
    resolvedSubtitles: List<ResolvedSubtitleTrackReference>?,
    hasResolvedOptions: Boolean,
): Boolean {
    return !resolvedSubtitles.isNullOrEmpty() && hasResolvedOptions
}

internal fun singleResolvedSubtitleFallback(
    resolvedSubtitles: List<ResolvedSubtitleTrackReference>,
    media3SubtitleTrackCount: Int,
): ResolvedSubtitleTrackReference? {
    return resolvedSubtitles.singleOrNull().takeIf { media3SubtitleTrackCount == 1 }
}

internal fun orderedResolvedSubtitleFallback(
    resolvedSubtitles: List<ResolvedSubtitleTrackReference>,
    media3SubtitleTrackCount: Int,
    media3SubtitleTrackIndex: Int,
): ResolvedSubtitleTrackReference? {
    if (media3SubtitleTrackCount <= 0) return null
    val orderedResolvedSubtitles = resolvedSubtitles
        .filter { subtitle -> subtitle.label.subtitleUserVisibleLabel() != null }
        .sortedBy { subtitle -> subtitle.sourceIndex ?: Int.MAX_VALUE }
    if (orderedResolvedSubtitles.size != media3SubtitleTrackCount) return null
    return orderedResolvedSubtitles.getOrNull(media3SubtitleTrackIndex)
}

internal fun List<SubtitleOption>.defaultSubtitleOption(): SubtitleOption? {
    return firstOrNull { option -> (option.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0 }
        ?: firstOrNull()
}

@OptIn(UnstableApi::class)
internal fun Tracks.currentSubtitleKey(): String? {
    return groups
        .asSequence()
        .filter { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
        .flatMap { group ->
            (0 until group.length)
                .asSequence()
                .filter { trackIndex -> group.isTrackSelected(trackIndex) }
                .map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    "${format.id.orEmpty()}:${format.language.orEmpty()}:${format.label.orEmpty()}:$trackIndex"
                }
        }
        .firstOrNull()
}

@OptIn(UnstableApi::class)
internal fun Tracks.playerOptionsIdentity(): Int {
    var result = 1
    groups
        .asSequence()
        .filter { group -> group.type == C.TRACK_TYPE_VIDEO || group.type == C.TRACK_TYPE_TEXT }
        .filter { group -> group.isSupported }
        .forEach { group ->
            result = result * 31 + group.type
            result = result * 31 + System.identityHashCode(group.mediaTrackGroup)
            result = result * 31 + group.length
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) continue
                val format = group.getTrackFormat(trackIndex)
                result = result * 31 + trackIndex
                result = result * 31 + format.id.orEmpty().hashCode()
                result = result * 31 + format.sampleMimeType.orEmpty().hashCode()
                result = result * 31 + format.label.orEmpty().hashCode()
                result = result * 31 + format.language.orEmpty().hashCode()
                result = result * 31 + format.width
                result = result * 31 + format.height
                result = result * 31 + format.bitrate
                result = result * 31 + format.selectionFlags
            }
        }
    return result
}

@OptIn(UnstableApi::class)
internal fun androidx.media3.common.Format.subtitleLabel(
    texts: PlayerControlTexts,
    trackIndex: Int,
): String {
    val explicitLabel = label?.subtitleUserVisibleLabel()
    val idLabel = id
        ?.takeIf { it.isNotBlank() }
        ?.subtitleIdentifierLabel()
        ?.takeIf { it.isNotBlank() }
    val languageLabel = language
        ?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
        ?.let { languageTag ->
            runCatching { Locale.forLanguageTag(languageTag).getDisplayLanguage(Locale.getDefault()) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }
    return explicitLabel
        ?: idLabel
        ?: languageLabel
        ?: "${texts.subtitles} ${trackIndex + 1}"
}

internal fun String.subtitleDisplayLabel(
    texts: PlayerControlTexts,
    trackIndex: Int,
    resolvedSubtitleLabel: String? = null,
): String {
    val cleaned = subtitleUserVisibleLabel()
    val resolvedLabel = resolvedSubtitleLabel?.subtitleUserVisibleLabel()
    return when {
        cleaned == null -> resolvedLabel ?: "${texts.subtitles} ${trackIndex + 1}"
        cleaned.isGenericSubtitleLabel(texts, trackIndex) -> resolvedLabel ?: cleaned
        else -> cleaned
    }
}

internal fun androidx.media3.common.Format.matchingResolvedSubtitleReference(
    resolvedSubtitles: List<ResolvedSubtitleTrackReference>,
): ResolvedSubtitleTrackReference? {
    if (resolvedSubtitles.isEmpty()) return null
    id?.takeIf { it.isNotBlank() }?.let { currentId ->
        resolvedSubtitles.firstOrNull { it.media3Id == currentId }?.let { return it }
    }
    val normalizedTokens = subtitleIdentityTokens()
        .mapNotNull { token -> token.subtitleUserVisibleLabel() }
        .map { token -> token.normalizedSubtitleIdentityToken() }
        .filter { token -> token.isNotBlank() }
        .toSet()
    if (normalizedTokens.isEmpty()) return null
    return resolvedSubtitles.firstOrNull { subtitle ->
        val normalizedLabel = subtitle.label
            .subtitleUserVisibleLabel()
            ?.normalizedSubtitleIdentityToken()
        normalizedLabel != null && normalizedLabel in normalizedTokens
    }
}

private fun androidx.media3.common.Format.subtitleIdentityTokens(): List<String> {
    return listOfNotNull(
        this.label,
        id?.subtitleIdentifierLabel(),
        this.label?.subtitleIdentifierLabel(),
    )
}

internal fun String.subtitleIdentifierLabel(): String {
    val fileName = substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .takeIf { it.isNotBlank() }
        ?: return ""
    val label = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
    return label
        .subtitleUserVisibleLabel()
        ?.replace('_', ' ')
        ?.replace('-', ' ')
        ?.takeIf {
            (
                contains("file:", ignoreCase = true) ||
                    '/' in this ||
                    '\\' in this
                )
        }
        .orEmpty()
}

private fun String.isGenericSubtitleLabel(texts: PlayerControlTexts, trackIndex: Int): Boolean {
    val normalized = normalizedSubtitleIdentityToken()
    return normalized == "${texts.subtitles}${trackIndex + 1}".normalizedSubtitleIdentityToken() ||
        normalized == "subtitles${trackIndex + 1}" ||
        normalized == "subtitle${trackIndex + 1}" ||
        normalized == "captions${trackIndex + 1}" ||
        normalized == "caption${trackIndex + 1}"
}

private fun String.normalizedSubtitleIdentityToken(): String {
    return trim().lowercase(Locale.ROOT).replace(Regex("""\s+"""), "")
}

// PlayerTrackControls
internal fun PlayerView.bindPlayerQualityControl(binding: PlayerControllerBinding) {
    findViewById<TextView>(R.id.yummy_player_quality)?.apply {
        val qualityTitle = binding.qualityOptions.selectedQualityControlText(binding.selectedQualityKey)
        applyPlayerQualityControl(qualityTitle, "${binding.texts.quality}: $qualityTitle")
        visibility = View.VISIBLE
        setPlayerControlEnabled(binding.qualityOptions.isNotEmpty())
        setOnClickListener {
            if (binding.qualityOptions.isEmpty()) return@setOnClickListener
            showQualityPopup(
                anchor = this,
                player = binding.player,
                options = binding.qualityOptions,
                selectedQualityKey = binding.selectedQualityKey,
                onSelectedQualityKeyChange = binding.onSelectedQualityKeyChange,
                onSelectLocalQuality = binding.onSelectLocalQuality,
                onSelectPreferredQuality = binding.onSelectPreferredQuality,
                onPlaybackSelectionStarted = binding.onPlaybackSelectionStarted,
                onRememberPlayerControlFocus = binding.onRememberPlayerControlFocus,
            )
        }
    }
}

internal fun PlayerView.bindPlayerSubtitleControl(binding: PlayerControllerBinding) {
    findViewById<ImageButton>(R.id.yummy_player_subtitles)?.apply {
        val label = if (binding.subtitlesLoading && binding.subtitleOptions.isEmpty()) {
            "${binding.texts.subtitles}..."
        } else {
            binding.texts.subtitles
        }
        applyPlayerIconControl(
            iconResId = R.drawable.ic_player_subtitles,
            label = label,
            active = binding.selectedSubtitleKey != SUBTITLE_OFF_KEY && binding.subtitleOptions.isNotEmpty(),
        )
        visibility = View.VISIBLE
        setPlayerControlEnabled(binding.subtitleOptions.isNotEmpty())
        setOnClickListener {
            if (binding.subtitleOptions.isEmpty()) return@setOnClickListener
            showSubtitlePopup(
                anchor = this,
                player = binding.player,
                options = binding.subtitleOptions,
                selectedSubtitleKey = binding.selectedSubtitleKey,
                texts = binding.texts,
                onRememberPlayerControlFocus = binding.onRememberPlayerControlFocus,
                onSelectedSubtitleKeyChange = binding.onSelectedSubtitleKeyChange,
            )
        }
    }
}

// PlayerTrackSelection
@OptIn(UnstableApi::class)
internal fun ExoPlayer.selectQuality(option: QualityOption) {
    val group = option.group ?: return
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        .setMaxVideoBitrate(Int.MAX_VALUE)
        .addOverride(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        .build()
}

internal fun QualityOption.hasPlayableQualityConstraint(): Boolean {
    return group != null
}

internal fun List<QualityOption>.preferredOption(preferredQuality: PreferredQuality): QualityOption? {
    return takeIf { preferredQuality.height != null }?.selectForPreferredQuality(
        preferredQuality = preferredQuality,
        height = { it.height },
        bitrate = { it.bitrate },
    )
}

@OptIn(UnstableApi::class)
internal fun PlayerDecoderMode.mediaCodecSelector(): MediaCodecSelector {
    return when (this) {
        PlayerDecoderMode.Auto -> MediaCodecSelector.DEFAULT
        PlayerDecoderMode.Hardware -> MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val defaults = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            )
            defaults.filter { it.hardwareAccelerated }.ifEmpty { defaults }
        }
        PlayerDecoderMode.Software -> MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val defaults = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            )
            defaults.filter { it.softwareOnly }.ifEmpty { defaults }
        }
    }
}

@OptIn(UnstableApi::class)
internal fun PlayerBufferPreset.toLoadControl(): DefaultLoadControl {
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(minBufferMs, maxBufferMs, playbackBufferMs, rebufferMs)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
}

@OptIn(UnstableApi::class)
internal fun Player.currentQualityKey(): String? {
    (this as? ExoPlayer)?.videoFormat
        ?.takeIf { format -> format.width > 0 || format.height > 0 }
        ?.let { format ->
            return "${format.height}:${format.bitrate}:${format.qualityLabel()}"
        }

    return currentTracks
        .groups
        .asSequence()
        .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
        .flatMap { group ->
            (0 until group.length)
                .asSequence()
                .filter { trackIndex -> group.isTrackSelected(trackIndex) }
                .map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    "${format.height}:${format.bitrate}:${format.qualityLabel()}"
                }
        }
        .firstOrNull()
}

internal data class QualityOption(
    val group: Tracks.Group?,
    val trackIndex: Int,
    val label: String,
    val height: Int,
    val bitrate: Int,
    val key: String,
    val localFile: OfflineVideoFile? = null,
    val preferredQuality: PreferredQuality? = null,
)

@OptIn(UnstableApi::class)
internal fun Tracks.videoQualityOptions(): List<QualityOption> {
    return groups
        .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }
        .flatMap { group ->
            (0 until group.length)
                .filter { trackIndex -> group.isTrackSupported(trackIndex) }
                .map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    QualityOption(
                        group = group,
                        trackIndex = trackIndex,
                        label = format.qualityLabel(),
                        height = format.height,
                        bitrate = format.bitrate,
                        key = "${format.height}:${format.bitrate}:${format.qualityLabel()}",
                        preferredQuality = PreferredQuality.fromHeight(format.height),
                    )
                }
        }
        .sortedWith(
            compareByDescending<QualityOption> { it.height.takeIf { height -> height > 0 } ?: 0 }
                .thenByDescending { it.bitrate.takeIf { bitrate -> bitrate > 0 } ?: 0 }
                .thenBy { it.label },
        )
        .distinctBy { it.qualityOptionIdentity() }
}

@OptIn(UnstableApi::class)
internal fun androidx.media3.common.Format.qualityLabel(): String {
    return when {
        height > 0 -> "${height}p"
        width > 0 -> "${width}px"
        else -> "Video"
    }
}

// PlayerVideoSelection
internal fun List<VideoVariant>.sortedForPlayer(): List<VideoVariant> {
    return sortedWith(
        compareBy<VideoVariant> { it.episodeOrderValue() ?: Double.MAX_VALUE }
            .thenBy { it.index.takeIf { index -> index > 0 } ?: Int.MAX_VALUE }
            .thenBy { if (it.isOfflineAvailable) 0 else 1 }
            .thenBy { it.id },
    )
}

internal fun List<VideoVariant>.sortedForPlayer(
    preferredGroupKey: String?,
    preferredVoiceKey: String? = matchingVoiceKeyForGroup(preferredGroupKey),
): List<VideoVariant> {
    val voiceKey = preferredVoiceKey?.takeIf { it.isNotBlank() }
    val scopedVideos = voiceKey
        ?.let { key -> filter { it.matchingVoiceKey == key } }
        ?.takeIf { it.isNotEmpty() }
        ?: this
    return scopedVideos.groupBy { it.matchingEpisodeKey }
        .values
        .mapNotNull { variants ->
            variants.minWithOrNull(
                compareBy<VideoVariant> { if (it.isOfflineAvailable) 0 else 1 }
                    .thenBy { if (it.groupKey == preferredGroupKey) 0 else 1 }
                    .thenBy { sourceProviderRank(it.player) }
                    .thenBy { it.index }
                    .thenBy { it.id },
            )
        }
        .sortedForPlayer()
}

internal fun List<VideoVariant>.matchingVoiceKeyForGroup(groupKey: String?): String? {
    return groupKey
        ?.takeIf { it.isNotBlank() }
        ?.let { key -> firstOrNull { it.groupKey == key }?.matchingVoiceKey }
        ?.takeIf { it.isNotBlank() }
}

internal fun VideoVariant.playbackSourceLabel(isLocalPlayback: Boolean = localPlaybackUrl.isNotBlank()): String {
    return if (isLocalPlayback) {
        "Local"
    } else {
        player.cleanVideoSourceLabel().ifBlank { player }.ifBlank { "HLS" }
    }
}
