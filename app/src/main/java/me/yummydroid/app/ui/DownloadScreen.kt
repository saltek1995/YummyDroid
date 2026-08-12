package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import me.yummydroid.app.DownloadTaskState
import me.yummydroid.app.DownloadTaskUi
import me.yummydroid.app.LoadState
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.readyListOrEmpty
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummySurfaceBorder
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor

// DownloadCards
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
            DownloadTaskHeader(task)
            DownloadTaskDetails(task)
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
private fun DownloadTaskHeader(task: DownloadTaskUi) {
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
}

@Composable
private fun DownloadTaskDetails(task: DownloadTaskUi) {
    DownloadTaskSecondaryText(
        text = listOf(task.episodeTitle, task.qualityTitle).joinToString(" \u2022 "),
        maxLines = 1,
    )
    if (task.isActive || task.state == DownloadTaskState.Completed) {
        LinearProgressIndicator(
            progress = { task.progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (task.message.isNotBlank()) {
        DownloadTaskSecondaryText(text = task.message, maxLines = 2)
    }
    val transferText = task.transferStatusText()
    if (transferText.isNotBlank()) {
        DownloadTaskSecondaryText(text = transferText, maxLines = 1)
    }
}

@Composable
private fun DownloadTaskSecondaryText(text: String, maxLines: Int) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
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

// DownloadListContent
@Composable
internal fun DownloadsList(
    model: DownloadScreenModel,
    offlineEntries: List<OfflineAnimeEntry>,
    listState: LazyListState,
    focusBinding: DownloadFocusBinding,
    contentBottomPadding: Dp,
    offlineEntriesError: String?,
    onRetry: () -> Unit,
    onClearHistory: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onResumeDownload: (Long) -> Unit,
    onOpenAnime: (Long) -> Unit,
) {
    val clearText = uiText(UiStringKey.Clear)
    val downloadPlansTitle = uiText(UiStringKey.DownloadPlans)
    val downloadQueueTitle = uiText(UiStringKey.DownloadQueue)
    val availableOfflineTitle = uiText(UiStringKey.AvailableOffline)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = downloadListContentPadding(contentBottomPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        offlineEntriesError?.let { message ->
            item(key = "offline-entries-error") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InlineErrorMessage(message = message)
                    DialogActionButton(
                        text = uiText(UiStringKey.Retry),
                        primary = true,
                        onClick = onRetry,
                    )
                }
            }
        }
        downloadTaskSection(
            title = downloadPlansTitle,
            tasks = model.planTasks,
            topPadding = 0.dp,
            showClearAction = model.canClearHistory,
            clearText = clearText,
            focusBinding = focusBinding,
            onClearHistory = onClearHistory,
            onCancelDownload = onCancelDownload,
            onPauseDownload = onPauseDownload,
            onResumeDownload = onResumeDownload,
            onOpenAnime = onOpenAnime,
        )
        downloadTaskSection(
            title = downloadQueueTitle,
            tasks = model.queueTasks,
            topPadding = if (model.planTasks.isEmpty()) 0.dp else 12.dp,
            showClearAction = model.planTasks.isEmpty() && model.canClearHistory,
            clearText = clearText,
            focusBinding = focusBinding,
            onClearHistory = onClearHistory,
            onCancelDownload = onCancelDownload,
            onPauseDownload = onPauseDownload,
            onResumeDownload = onResumeDownload,
            onOpenAnime = onOpenAnime,
        )
        offlineAnimeSection(
            title = availableOfflineTitle,
            entries = offlineEntries,
            topPadding = if (model.visibleTasks.isEmpty()) 0.dp else 12.dp,
            focusBinding = focusBinding,
            onOpenAnime = onOpenAnime,
        )
    }
}

private fun downloadListContentPadding(contentBottomPadding: Dp): PaddingValues = PaddingValues(
    start = 24.dp,
    top = 24.dp,
    end = 24.dp,
    bottom = 24.dp + contentBottomPadding,
)

private fun LazyListScope.offlineAnimeSection(
    title: String,
    entries: List<OfflineAnimeEntry>,
    topPadding: Dp,
    focusBinding: DownloadFocusBinding,
    onOpenAnime: (Long) -> Unit,
) {
    if (entries.isEmpty()) return
    item {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = topPadding),
        )
    }
    itemsIndexed(
        entries,
        key = { index, entry -> "offline-entry:$index:${entry.anime.id}:${entry.anime.title}" },
    ) { _, entry ->
        val focusKey = downloadOfflineFocusKey(entry.anime.id)
        OfflineAnimeRow(
            entry = entry,
            onOpenAnime = onOpenAnime,
            modifier = Modifier.downloadFocusTarget(focusKey, focusBinding),
        )
    }
}

private fun LazyListScope.downloadTaskSection(
    title: String,
    tasks: List<DownloadTaskUi>,
    topPadding: Dp,
    showClearAction: Boolean,
    clearText: String,
    focusBinding: DownloadFocusBinding,
    onClearHistory: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onResumeDownload: (Long) -> Unit,
    onOpenAnime: (Long) -> Unit,
) {
    if (tasks.isEmpty()) return
    item {
        DownloadSectionHeader(
            title = title,
            topPadding = topPadding,
            showClearAction = showClearAction,
            clearText = clearText,
            onClearHistory = onClearHistory,
        )
    }
    items(tasks, key = DownloadTaskUi::id) { task ->
        val focusKey = downloadTaskFocusKey(task.id)
        DownloadTaskCard(
            task = task,
            onOpenAnime = { onOpenAnime(task.animeId) },
            onCancelDownload = { onCancelDownload(task.id) },
            onPauseDownload = { onPauseDownload(task.id) },
            onResumeDownload = { onResumeDownload(task.id) },
            modifier = Modifier.downloadFocusTarget(focusKey, focusBinding),
        )
    }
}

@Composable
private fun DownloadSectionHeader(
    title: String,
    topPadding: Dp,
    showClearAction: Boolean,
    clearText: String,
    onClearHistory: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f),
        )
        if (showClearAction) {
            DialogActionButton(
                text = clearText,
                onClick = onClearHistory,
            )
        }
    }
}

private fun Modifier.downloadFocusTarget(
    focusKey: String,
    focusBinding: DownloadFocusBinding,
): Modifier {
    return then(
        if (focusKey == focusBinding.activeKey) {
            Modifier.focusRequester(focusBinding.requester)
        } else {
            Modifier
        },
    ).onFocusChanged { focusState ->
        if (focusState.hasFocus) {
            focusBinding.onFocused(focusKey)
        }
    }
}

// DownloadScreenFocus
internal data class DownloadFocusBinding(
    val requester: FocusRequester,
    val activeKey: String?,
    val onFocused: (String) -> Unit,
)

@Composable
internal fun rememberDownloadFocusBinding(
    model: DownloadScreenModel,
    listState: LazyListState,
    focusCurrentRequestNonce: Long,
): DownloadFocusBinding {
    val focusRequester = remember { FocusRequester() }
    var focusedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val activeKey = model.activeFocusKey(focusedKey)
    var handledRequestNonce by remember { mutableLongStateOf(0L) }

    UiControlEffect(
        focusCurrentRequestNonce,
        model.focusKeys,
        enabled = focusCurrentRequestNonce > 0L &&
            focusCurrentRequestNonce != handledRequestNonce &&
            activeKey != null,
    ) {
        val currentActiveKey = activeKey ?: return@UiControlEffect
        val firstVisibleFocusKey = listState.layoutInfo.visibleItemsInfo
            .asSequence()
            .mapNotNull { item -> model.focusKeysByListIndex[item.index] }
            .firstOrNull()
        val targetFocusKey = firstVisibleFocusKey ?: currentActiveKey
        focusedKey = targetFocusKey
        val targetListIndex = model.listIndexesByFocusKey[targetFocusKey]
        val targetIsVisible = targetListIndex == null ||
            listState.layoutInfo.visibleItemsInfo.any { item -> item.index == targetListIndex }
        if (targetListIndex != null && !targetIsVisible) {
            listState.scrollToItem(targetListIndex, 0)
        }
        listState.focusItemWhenVisible(targetListIndex, focusRequester)
        handledRequestNonce = focusCurrentRequestNonce
    }

    return DownloadFocusBinding(
        requester = focusRequester,
        activeKey = activeKey,
        onFocused = { key -> focusedKey = key },
    )
}

private suspend fun LazyListState.focusItemWhenVisible(
    listIndex: Int?,
    focusRequester: FocusRequester,
) {
    if (listIndex != null) {
        withTimeoutOrNull(1_000L) {
            snapshotFlow {
                layoutInfo.visibleItemsInfo.any { item -> item.index == listIndex }
            }
                .filter { isVisible -> isVisible }
                .first()
        }
    }
    repeat(6) {
        withFrameNanos { }
        if (focusRequester.requestFocusSafely()) return
    }
}

// DownloadScreenModel
internal data class DownloadScreenModel(
    val planTasks: List<DownloadTaskUi>,
    val queueTasks: List<DownloadTaskUi>,
    val visibleTasks: List<DownloadTaskUi>,
    val offlineAnimeIds: List<Long>,
    val focusKeys: List<String>,
    val listIndexesByFocusKey: Map<String, Int>,
    val focusKeysByListIndex: Map<Int, String>,
    val canClearHistory: Boolean,
) {
    val isEmpty: Boolean
        get() = visibleTasks.isEmpty() && offlineAnimeIds.isEmpty()

    fun activeFocusKey(previousFocusKey: String?): String? {
        return previousFocusKey
            ?.takeIf { it in focusKeys }
            ?: focusKeys.firstOrNull()
    }
}

internal fun buildDownloadScreenModel(
    tasks: List<DownloadTaskUi>,
    offlineAnimeIds: List<Long>,
): DownloadScreenModel {
    val planTasks = tasks.filter { it.isBatchSummary }
    val queueTasks = tasks
        .filterNot { it.isBatchSummary }
        .filter(DownloadTaskUi::isVisibleQueueTask)
    val visibleTasks = planTasks + queueTasks
    val focusKeys = visibleTasks.map { task -> downloadTaskFocusKey(task.id) } +
        offlineAnimeIds.map(::downloadOfflineFocusKey)
    val listIndexesByFocusKey = buildDownloadFocusIndexes(
        planTasks = planTasks,
        queueTasks = queueTasks,
        offlineAnimeIds = offlineAnimeIds,
    )
    return DownloadScreenModel(
        planTasks = planTasks,
        queueTasks = queueTasks,
        visibleTasks = visibleTasks,
        offlineAnimeIds = offlineAnimeIds,
        focusKeys = focusKeys,
        listIndexesByFocusKey = listIndexesByFocusKey,
        focusKeysByListIndex = listIndexesByFocusKey.entries.associate { (key, index) -> index to key },
        canClearHistory = tasks.any { task ->
            !task.isActive && task.state != DownloadTaskState.Paused
        },
    )
}

internal fun downloadTaskFocusKey(taskId: Long): String = "task:$taskId"

internal fun downloadOfflineFocusKey(animeId: Long): String = "offline:$animeId"

private fun DownloadTaskUi.isVisibleQueueTask(): Boolean {
    return isActive || state == DownloadTaskState.Paused || state == DownloadTaskState.Failed
}

private fun buildDownloadFocusIndexes(
    planTasks: List<DownloadTaskUi>,
    queueTasks: List<DownloadTaskUi>,
    offlineAnimeIds: List<Long>,
): Map<String, Int> {
    val indexes = linkedMapOf<String, Int>()
    var listIndex = 0
    if (planTasks.isNotEmpty()) {
        listIndex += 1
        planTasks.forEach { task ->
            indexes[downloadTaskFocusKey(task.id)] = listIndex
            listIndex += 1
        }
    }
    if (queueTasks.isNotEmpty()) {
        listIndex += 1
        queueTasks.forEach { task ->
            indexes[downloadTaskFocusKey(task.id)] = listIndex
            listIndex += 1
        }
    }
    if (offlineAnimeIds.isNotEmpty()) {
        listIndex += 1
        offlineAnimeIds.forEach { animeId ->
            indexes[downloadOfflineFocusKey(animeId)] = listIndex
            listIndex += 1
        }
    }
    return indexes
}

internal data class DownloadTaskActions(
    val showPause: Boolean,
    val showResume: Boolean,
    val showCancel: Boolean,
) {
    val hasAny: Boolean
        get() = showPause || showResume || showCancel
}

internal fun DownloadTaskUi.downloadTaskActions(): DownloadTaskActions {
    return DownloadTaskActions(
        showPause = state == DownloadTaskState.Running || state == DownloadTaskState.Queued,
        showResume = canResume,
        showCancel = isActive || state == DownloadTaskState.Paused || state == DownloadTaskState.Failed,
    )
}

internal data class DownloadTransferStatus(
    val percent: Int,
    val downloadedBytes: Long?,
    val totalBytes: Long?,
    val bytesPerSecond: Long?,
)

internal fun DownloadTaskUi.downloadTransferStatus(): DownloadTransferStatus? {
    val isVisible = isActive ||
        state == DownloadTaskState.Completed ||
        state == DownloadTaskState.Paused ||
        state == DownloadTaskState.Failed
    if (!isVisible) return null

    val positiveDownloadedBytes = downloadedBytes.takeIf { it > 0L }
    return DownloadTransferStatus(
        percent = (progress.coerceIn(0f, 1f) * 100f).roundToInt(),
        downloadedBytes = positiveDownloadedBytes,
        totalBytes = totalBytes.takeIf { it > 0L && positiveDownloadedBytes != null },
        bytesPerSecond = bytesPerSecond.takeIf { isActive && it > 0L },
    )
}

// DownloadScreensRuntime
@Composable
internal fun DownloadsSection(
    state: YummyDroidUiState,
    focusCurrentRequestNonce: Long,
    contentBottomPadding: Dp = 0.dp,
    onClearHistory: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onResumeDownload: (Long) -> Unit,
    onOpenAnime: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    val offlineEntries = state.offlineEntries.readyListOrEmpty()
    val tasks = state.downloadQueue.tasks
    val model = remember(tasks, offlineEntries) {
        buildDownloadScreenModel(
            tasks = tasks,
            offlineAnimeIds = offlineEntries.map { entry -> entry.anime.id },
        )
    }
    if (tasks.isEmpty()) {
        when (val entries = state.offlineEntries) {
            LoadState.Loading -> {
                LoadingPane(Modifier.fillMaxSize())
                return
            }
            is LoadState.Error -> {
                ErrorPane(
                    message = entries.message,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxSize(),
                )
                return
            }
            is LoadState.Ready -> Unit
        }
    }
    if (model.isEmpty) {
        EmptyPane(
            message = uiText(UiStringKey.NoDownloadedEpisodesYet),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val focusBinding = rememberDownloadFocusBinding(
        model = model,
        listState = listState,
        focusCurrentRequestNonce = focusCurrentRequestNonce,
    )
    DownloadsList(
        model = model,
        offlineEntries = offlineEntries,
        listState = listState,
        focusBinding = focusBinding,
        contentBottomPadding = contentBottomPadding,
        offlineEntriesError = (state.offlineEntries as? LoadState.Error)?.message,
        onRetry = onRetry,
        onClearHistory = onClearHistory,
        onCancelDownload = onCancelDownload,
        onPauseDownload = onPauseDownload,
        onResumeDownload = onResumeDownload,
        onOpenAnime = onOpenAnime,
    )
}
