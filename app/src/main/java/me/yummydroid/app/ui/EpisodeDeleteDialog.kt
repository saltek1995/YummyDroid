package me.yummydroid.app.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import java.util.Locale
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.matchingVoiceTitle
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.qualityHeight
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.formatByteSize

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
        .ifBlank { "Озвучка" }
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

internal fun OfflineDeleteFile.displayTitle(totalBytes: Long = file.bytes): String {
    return listOf(
        displayVoiceTitle(),
        file.qualityDisplayTitle(),
        totalBytes.takeIf { it > 0L }?.let(::formatByteSize),
    ).filterNot { it.isNullOrBlank() }.joinToString(" • ")
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

@Composable
internal fun EpisodeDeleteDialog(
    video: VideoVariant,
    downloadedVariants: List<VideoVariant>,
    onDelete: (List<OfflineDeleteTarget>) -> Unit,
    onDismiss: () -> Unit,
) {
    if (downloadedVariants.isEmpty()) return
    val voiceGroups = downloadedVariants
        .groupBy { it.matchingVoiceKey }
        .values
        .map { variants -> variants.sortedForPlayer() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${uiText("Удалить")} ${video.localizedEpisodeTitle()}") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    SelectableFilterRow(
                        title = uiText("Все скачанные варианты"),
                        selected = false,
                        onClick = { onDelete(downloadedVariants.offlineDeleteTargets()) },
                    )
                }
                items(voiceGroups, key = { variants -> "delete-offline:${variants.first().matchingVoiceKey}" }) { variants ->
                    val files = variants.offlineDeleteFiles()
                    val fileRows = files
                        .groupBy { it.displayKey() }
                        .values
                        .map { group -> group.sortedBy { it.file.playbackUrl } }
                    val representative = files.firstOrNull()
                    val qualities = files.map { it.file.qualityDisplayTitle() }.distinct().joinToString(", ")
                    val bytes = files.sumOf { it.file.bytes.coerceAtLeast(0L) }
                    val info = listOf(
                        representative?.displayVoiceTitle(),
                        qualities.ifBlank { null },
                        bytes.takeIf { it > 0L }?.let(::formatByteSize),
                    ).filterNot { it.isNullOrBlank() }.joinToString(" • ")
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SelectableFilterRow(
                            title = info,
                            selected = false,
                            onClick = { onDelete(variants.offlineDeleteTargets()) },
                        )
                        if (fileRows.size > 1) {
                            fileRows.forEach { row ->
                                val fileInfo = row.first().displayTitle(
                                    totalBytes = row.sumOf { it.file.bytes.coerceAtLeast(0L) },
                                )
                                SelectableFilterRow(
                                    title = "  $fileInfo",
                                    selected = false,
                                    onClick = { onDelete(row.map { it.target }) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText("Закрыть"),
                    onClick = onDismiss,
                )
            }
        },
    )
}
