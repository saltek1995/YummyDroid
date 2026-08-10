package me.yummydroid.app.ui

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.VideoVariant

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
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text("${uiText(UiStringKey.Delete)} ${video.localizedEpisodeTitle()}") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    SelectableFilterRow(
                        title = uiText(UiStringKey.AllDownloadedVariants),
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
                    val voiceTitle = representative?.displayVoiceTitle().orEmpty().ifBlank { uiText(UiStringKey.Voice) }
                    val info = listOf(
                        voiceTitle,
                        qualities.ifBlank { null },
                        bytes.takeIf { it > 0L }?.let { localizedByteSize(it) },
                    ).filterNot { it.isNullOrBlank() }.joinToString(" \u2022 ")
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SelectableFilterRow(
                            title = info,
                            selected = false,
                            onClick = { onDelete(variants.offlineDeleteTargets()) },
                        )
                        if (fileRows.size > 1) {
                            fileRows.forEach { row ->
                                val rowBytes = row.sumOf { it.file.bytes.coerceAtLeast(0L) }
                                val rowVoiceTitle = row.first().displayVoiceTitle().ifBlank { uiText(UiStringKey.Voice) }
                                val fileInfo = row.first().displayTitle(
                                    voiceTitle = rowVoiceTitle,
                                    totalBytesLabel = rowBytes.takeIf { it > 0L }?.let { localizedByteSize(it) },
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
                    text = uiText(UiStringKey.Close),
                    onClick = onDismiss,
                )
            }
        },
    )
}
