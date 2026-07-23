package me.yummydroid.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.DownloadTaskState
import me.yummydroid.app.formatByteSize
import me.yummydroid.app.readyListOrEmpty
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.yummySurfaceBorder
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.YummyDroidUiState

@Composable
internal fun DownloadsSection(
    state: YummyDroidUiState,
    focusCurrentRequestNonce: Long,
    onClearHistory: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onResumeDownload: (Long) -> Unit,
    onOpenAnime: (Long) -> Unit,
) {
    val offlineEntries = state.offlineEntries.readyListOrEmpty()
    val tasks = state.downloadQueue.tasks

    if (tasks.isEmpty() && offlineEntries.isEmpty()) {
        EmptyPane(
            message = uiText("Скачанных серий пока нет"),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val downloadFocusRequester = remember { FocusRequester() }
    val downloadsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var focusedDownloadKey by rememberSaveable { mutableStateOf<String?>(null) }
    val downloadFocusKeys = remember(tasks, offlineEntries) {
        tasks.map { task -> "task:${task.id}" } +
            offlineEntries.map { entry -> "offline:${entry.anime.id}" }
    }
    val downloadFocusListIndexes = remember(tasks, offlineEntries) {
        val indexes = mutableMapOf<String, Int>()
        var listIndex = 0
        if (tasks.isNotEmpty()) {
            listIndex += 1
            tasks.forEach { task ->
                indexes["task:${task.id}"] = listIndex
                listIndex += 1
            }
        }
        if (offlineEntries.isNotEmpty()) {
            listIndex += 1
            offlineEntries.forEach { entry ->
                indexes["offline:${entry.anime.id}"] = listIndex
                listIndex += 1
            }
        }
        indexes
    }
    val activeDownloadFocusKey = focusedDownloadKey
        ?.takeIf { key -> key in downloadFocusKeys }
        ?: downloadFocusKeys.firstOrNull()
    var handledCurrentFocusRequestNonce by remember { mutableStateOf(0L) }

    suspend fun focusDownloadWhenVisible(listIndex: Int?) {
        if (listIndex != null) {
            withTimeoutOrNull(1_000L) {
                snapshotFlow {
                    downloadsListState.layoutInfo.visibleItemsInfo.any { item -> item.index == listIndex }
                }
                    .filter { isVisible -> isVisible }
                    .first()
            }
        }
        repeat(6) {
            withFrameNanos { }
            if (runCatching { downloadFocusRequester.requestFocus() }.getOrDefault(false)) return
        }
    }

    LaunchedEffect(focusCurrentRequestNonce, downloadFocusKeys) {
        if (
            focusCurrentRequestNonce <= 0L ||
            focusCurrentRequestNonce == handledCurrentFocusRequestNonce ||
            activeDownloadFocusKey == null
        ) {
            return@LaunchedEffect
        }
        val firstVisibleDownloadFocusKey = downloadsListState.layoutInfo.visibleItemsInfo
            .asSequence()
            .mapNotNull { item ->
                downloadFocusListIndexes.entries
                    .firstOrNull { (_, listIndex) -> listIndex == item.index }
                    ?.key
            }
            .firstOrNull()
        val targetFocusKey = firstVisibleDownloadFocusKey ?: activeDownloadFocusKey
        focusedDownloadKey = targetFocusKey
        val targetListIndex = downloadFocusListIndexes[targetFocusKey]
        val targetIsVisible = targetListIndex == null ||
            downloadsListState.layoutInfo.visibleItemsInfo.any { item -> item.index == targetListIndex }
        if (targetListIndex != null && !targetIsVisible) {
            downloadsListState.scrollToItem(targetListIndex, 0)
        }
        focusDownloadWhenVisible(targetListIndex)
        handledCurrentFocusRequestNonce = focusCurrentRequestNonce
    }

    LazyColumn(
        state = downloadsListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (tasks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = uiText("Очередь загрузок"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f),
                    )
                    if (tasks.any { !it.isActive && it.state != DownloadTaskState.Paused }) {
                        DialogActionButton(
                            text = uiText("Очистить"),
                            onClick = onClearHistory,
                        )
                    }
                }
            }
            items(tasks, key = { it.id }) { task ->
                val focusKey = "task:${task.id}"
                DownloadTaskCard(
                    task = task,
                    onOpenAnime = { onOpenAnime(task.animeId) },
                    onCancelDownload = { onCancelDownload(task.id) },
                    onPauseDownload = { onPauseDownload(task.id) },
                    onResumeDownload = { onResumeDownload(task.id) },
                    modifier = Modifier
                        .then(
                            if (focusKey == activeDownloadFocusKey) {
                                Modifier.focusRequester(downloadFocusRequester)
                            } else {
                                Modifier
                            },
                        )
                        .onFocusChanged { focusState ->
                            if (focusState.hasFocus) {
                                focusedDownloadKey = focusKey
                            }
                        },
                )
            }
        }

        if (offlineEntries.isNotEmpty()) {
            item {
                Text(
                    text = uiText("Доступно офлайн"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(top = if (tasks.isEmpty()) 0.dp else 12.dp),
                )
            }
            lazyItemsIndexed(
                offlineEntries,
                key = { index, entry -> "offline-entry:$index:${entry.anime.id}:${entry.anime.title}" },
            ) { _, entry ->
                val focusKey = "offline:${entry.anime.id}"
                OfflineAnimeRow(
                    entry = entry,
                    onOpenAnime = onOpenAnime,
                    modifier = Modifier
                        .then(
                            if (focusKey == activeDownloadFocusKey) {
                                Modifier.focusRequester(downloadFocusRequester)
                            } else {
                                Modifier
                            },
                        )
                        .onFocusChanged { focusState ->
                            if (focusState.hasFocus) {
                                focusedDownloadKey = focusKey
                            }
                        },
                )
            }
        }
    }
}

@Composable
internal fun DownloadTaskCard(
    task: me.yummydroid.app.DownloadTaskUi,
    onOpenAnime: () -> Unit,
    onCancelDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = YummyRadii.smallShape
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOf(task.episodeTitle, task.qualityTitle).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                if (task.state == DownloadTaskState.Running || task.state == DownloadTaskState.Queued) {
                    IconButton(
                        onClick = onPauseDownload,
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = uiText("Поставить на паузу"))
                    }
                }
                if (task.canResume) {
                    IconButton(
                        onClick = onResumeDownload,
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = uiText("Возобновить загрузку"))
                    }
                }
                if (task.isActive || task.state == DownloadTaskState.Paused || task.state == DownloadTaskState.Failed) {
                    IconButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = uiText("Отменить загрузку"))
                    }
                }
            }

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
        }
    }
}

@Composable
internal fun me.yummydroid.app.DownloadTaskUi.transferStatusText(): String {
    if (!isActive && state != DownloadTaskState.Completed && state != DownloadTaskState.Paused && state != DownloadTaskState.Failed) return ""
    val percent = "${(progress.coerceIn(0f, 1f) * 100f).roundToInt()}%"
    val size = when {
        totalBytes > 0L && downloadedBytes > 0L -> "${formatByteSize(downloadedBytes)} / ${formatByteSize(totalBytes)}"
        downloadedBytes > 0L && isActive -> "${formatByteSize(downloadedBytes)} / ${uiText("неизвестно")}"
        downloadedBytes > 0L -> formatByteSize(downloadedBytes)
        else -> ""
    }
    val speed = if (isActive && bytesPerSecond > 0L) "${formatByteSize(bytesPerSecond)}/${uiText("с")}" else ""
    return listOf(percent, size, speed)
        .filter { it.isNotBlank() }
        .joinToString(" • ")
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
                    text = "${entry.downloadedVideos.size} ${localizedEpisodesWord(entry.downloadedVideos.size)} • ${formatByteSize(entry.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun DownloadTaskState.localizedTitle(): String = when (this) {
    DownloadTaskState.Queued -> uiText("В очереди")
    DownloadTaskState.Running -> uiText("Загрузка")
    DownloadTaskState.Paused -> uiText("Пауза")
    DownloadTaskState.Added -> uiText("Добавлено")
    DownloadTaskState.Completed -> uiText("Скачано")
    DownloadTaskState.Failed -> uiText("Ошибка")
    DownloadTaskState.Cancelled -> uiText("Отменено")
}
