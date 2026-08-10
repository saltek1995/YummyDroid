package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.DownloadTaskUi

@Composable
internal fun DownloadsList(
    model: DownloadScreenModel,
    offlineEntries: List<OfflineAnimeEntry>,
    listState: LazyListState,
    focusBinding: DownloadFocusBinding,
    contentBottomPadding: Dp,
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
        contentPadding = PaddingValues(
            start = 24.dp,
            top = 24.dp,
            end = 24.dp,
            bottom = 24.dp + contentBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
        if (offlineEntries.isNotEmpty()) {
            item {
                Text(
                    text = availableOfflineTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(top = if (model.visibleTasks.isEmpty()) 0.dp else 12.dp),
                )
            }
            itemsIndexed(
                offlineEntries,
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
