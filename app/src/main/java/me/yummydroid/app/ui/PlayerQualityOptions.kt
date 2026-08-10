package me.yummydroid.app.ui

import androidx.compose.foundation.layout.height
import java.util.Locale
import me.yummydroid.app.data.bestSourceQualityPerHeight
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.qualityHeight
import me.yummydroid.app.data.sourceQualitiesForSameEpisodeVoice
import me.yummydroid.app.data.SourceQuality
import me.yummydroid.app.data.VideoVariant

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
    return sourceQualitiesForSameEpisodeVoice(currentVideo).sourceQualityOptions()
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

