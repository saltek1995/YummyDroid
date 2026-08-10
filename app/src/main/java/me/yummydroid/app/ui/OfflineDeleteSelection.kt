package me.yummydroid.app.ui

import android.net.Uri
import androidx.core.net.toUri
import java.util.Locale
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.matchingVoiceTitle
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.qualityHeight
import me.yummydroid.app.data.VideoVariant

internal data class OfflineDeleteTarget(
    val animeId: Long,
    val videoId: Long,
    val playbackUrl: String?,
)

internal data class OfflineDeleteFile(
    val variant: VideoVariant,
    val file: OfflineVideoFile,
) {
    val target: OfflineDeleteTarget
        get() = OfflineDeleteTarget(variant.animeId, variant.id, file.playbackUrl)
}

internal fun List<VideoVariant>.offlineDeleteFiles(): List<OfflineDeleteFile> {
    return flatMap { variant ->
        variant.offlineFiles
            .filter { it.playbackUrl.isNotBlank() }
            .distinctBy { it.playbackUrl }
            .map { OfflineDeleteFile(variant, it) }
    }
        .distinctBy { it.file.playbackUrl }
        .sortedWith(
            compareBy<OfflineDeleteFile> { it.displayVoiceTitle().lowercase(Locale.ROOT) }
                .thenByDescending { it.file.qualityHeight() }
                .thenBy { it.file.bytes },
        )
}

internal fun List<VideoVariant>.offlineDeleteTargets(): List<OfflineDeleteTarget> {
    val fileTargets = offlineDeleteFiles().map { it.target }
    if (fileTargets.isNotEmpty()) return fileTargets.distinctBy { it.playbackUrl }
    return filter { it.isOfflineAvailable }
        .map { OfflineDeleteTarget(it.animeId, it.id, null) }
        .distinctBy { Triple(it.animeId, it.videoId, it.playbackUrl) }
}

internal fun OfflineDeleteFile.displayVoiceTitle(): String {
    return file.voiceTitle
        .ifBlank { file.voiceTitleFromDownloadPath() }
        .ifBlank { variant.matchingVoiceTitle }
        .ifBlank { file.player.cleanVideoSourceLabel() }
        .ifBlank { variant.player.cleanVideoSourceLabel() }
        .orEmpty()
}

internal fun OfflineDeleteFile.displayKey(): String {
    return cacheRowKey()
}

internal fun OfflineDeleteFile.cacheRowKey(): String {
    return listOf(
        variant.offlineEpisodeIdentity(),
        displayVoiceTitle().lowercase(Locale.ROOT),
        file.qualityDisplayTitle().lowercase(Locale.ROOT),
    ).joinToString("|")
}

internal fun OfflineDeleteFile.displayTitle(
    voiceTitle: String = displayVoiceTitle(),
    totalBytesLabel: String? = null,
): String {
    return listOf(
        voiceTitle,
        file.qualityDisplayTitle(),
        totalBytesLabel,
    ).filterNot { it.isNullOrBlank() }.joinToString(" вЂў ")
}

internal fun VideoVariant.offlineEpisodeIdentity(): String {
    return episode.trim().takeIf { it.isNotBlank() }
        ?: index.takeIf { it > 0 }?.toString()
        ?: id.toString()
}

internal fun VideoVariant.offlineEpisodeSortKey(): Double {
    return offlineEpisodeIdentity().toDoubleOrNull() ?: index.takeIf { it > 0 }?.toDouble() ?: Double.MAX_VALUE
}

internal fun OfflineVideoFile.voiceTitleFromDownloadPath(): String {
    val path = playbackUrl.toUri().path.orEmpty()
    val parts = path.split('/').filter { it.isNotBlank() }
    val rootIndex = parts.indexOfLast { it.equals("YummyDroid", ignoreCase = true) }
    val voicePart = parts.getOrNull(rootIndex + 2).orEmpty()
    return Uri.decode(voicePart)
        .replace('_', ' ')
        .takeIf { it.isNotBlank() && !it.equals("auto", ignoreCase = true) }
        .orEmpty()
}
