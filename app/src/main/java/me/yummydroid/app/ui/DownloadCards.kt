package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.DownloadTaskState
import me.yummydroid.app.DownloadTaskUi
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.yummySurfaceBorder
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor
import me.yummydroid.app.ui.theme.YummySurfaceRole

@Composable
internal fun DownloadTaskCard(
    task: DownloadTaskUi,
    onOpenAnime: () -> Unit,
    onCancelDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = YummyRadii.smallShape
    val actions = task.downloadTaskActions()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .dpadClickable(shape, onOpenAnime),
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        shape = shape,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = task.state.localizedTitle(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (task.state == DownloadTaskState.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = listOf(task.episodeTitle, task.qualityTitle).joinToString(" \u2022 "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (task.isActive || task.state == DownloadTaskState.Completed) {
                LinearProgressIndicator(
                    progress = { task.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (task.message.isNotBlank()) {
                Text(
                    text = task.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val transferText = task.transferStatusText()
            if (transferText.isNotBlank()) {
                Text(
                    text = transferText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (actions.hasAny) {
                DownloadTaskActionButtons(
                    actions = actions,
                    onCancelDownload = onCancelDownload,
                    onPauseDownload = onPauseDownload,
                    onResumeDownload = onResumeDownload,
                )
            }
        }
    }
}

@Composable
private fun DownloadTaskActionButtons(
    actions: DownloadTaskActions,
    onCancelDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (actions.showPause) {
            IconButton(
                onClick = onPauseDownload,
                modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
            ) {
                Icon(Icons.Default.Pause, contentDescription = uiText(UiStringKey.Pause))
            }
        }
        if (actions.showResume) {
            IconButton(
                onClick = onResumeDownload,
                modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = uiText(UiStringKey.ResumeDownload))
            }
        }
        if (actions.showCancel) {
            IconButton(
                onClick = onCancelDownload,
                modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
            ) {
                Icon(Icons.Default.Close, contentDescription = uiText(UiStringKey.CancelDownload))
            }
        }
    }
}

@Composable
internal fun DownloadTaskUi.transferStatusText(): String {
    val status = downloadTransferStatus() ?: return ""
    val percent = "${status.percent}%"
    val size = when {
        status.totalBytes != null && status.downloadedBytes != null -> {
            "${localizedByteSize(status.downloadedBytes)} / ${localizedByteSize(status.totalBytes)}"
        }
        status.downloadedBytes != null -> localizedByteSize(status.downloadedBytes)
        else -> ""
    }
    val speed = status.bytesPerSecond
        ?.let { bytesPerSecond -> "${localizedByteSize(bytesPerSecond)}/${uiText(UiStringKey.S)}" }
        .orEmpty()
    return listOf(percent, size, speed)
        .filter { it.isNotBlank() }
        .joinToString(" \u2022 ")
}

@Composable
internal fun OfflineAnimeRow(
    entry: OfflineAnimeEntry,
    onOpenAnime: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = YummyRadii.smallShape
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .dpadClickable(shape) { onOpenAnime(entry.anime.id) },
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        border = yummySurfaceBorder(YummySurfaceRole.Row),
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PosterImage(
                url = entry.anime.posterUrl,
                contentDescription = entry.anime.title,
                modifier = Modifier
                    .width(58.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = entry.anime.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${entry.downloadedVideos.size} " +
                        "${localizedEpisodesWord(entry.downloadedVideos.size)} \u2022 " +
                        localizedByteSize(entry.totalBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun DownloadTaskState.localizedTitle(): String = when (this) {
    DownloadTaskState.Queued -> uiText(UiStringKey.Queued)
    DownloadTaskState.Running -> uiText(UiStringKey.Loading)
    DownloadTaskState.Paused -> uiText(UiStringKey.Paused)
    DownloadTaskState.Added -> uiText(UiStringKey.Added)
    DownloadTaskState.Completed -> uiText(UiStringKey.DownloadedBc4f6a)
    DownloadTaskState.Failed -> uiText(UiStringKey.Error)
    DownloadTaskState.Cancelled -> uiText(UiStringKey.Cancelled)
}
