package me.yummydroid.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.yummydroid.app.readyListOrEmpty
import me.yummydroid.app.YummyDroidUiState

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
) {
    val offlineEntries = state.offlineEntries.readyListOrEmpty()
    val tasks = state.downloadQueue.tasks
    val model = remember(tasks, offlineEntries) {
        buildDownloadScreenModel(
            tasks = tasks,
            offlineAnimeIds = offlineEntries.map { entry -> entry.anime.id },
        )
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
        onClearHistory = onClearHistory,
        onCancelDownload = onCancelDownload,
        onPauseDownload = onPauseDownload,
        onResumeDownload = onResumeDownload,
        onOpenAnime = onOpenAnime,
    )
}
