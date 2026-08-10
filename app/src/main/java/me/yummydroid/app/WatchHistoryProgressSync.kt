package me.yummydroid.app

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.PlaybackProgress

internal class WatchHistoryProgressSync(
    private val readProgress: () -> List<PlaybackProgress>,
    private val saveProgressIfNewer: (PlaybackProgress) -> Unit,
    private val fetchHistoryPage: suspend (limit: Int, offset: Int) -> List<PlaybackProgress>,
    private val uploadProgress: suspend (PlaybackProgress) -> Boolean,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun readLatestLocalProgress(): List<PlaybackProgress> = withContext(ioDispatcher) {
        readProgress().latestHistoryByAnime()
    }

    suspend fun readAllLocalProgress(): List<PlaybackProgress> = withContext(ioDispatcher) {
        readProgress()
    }

    suspend fun fetchRemoteHistory(): Result<List<PlaybackProgress>> = runCatching {
        collectWatchHistoryPages(fetchPage = fetchHistoryPage)
    }

    suspend fun storeRemoteHistory(history: List<PlaybackProgress>) {
        withContext(ioDispatcher) {
            history.forEach(saveProgressIfNewer)
        }
    }

    suspend fun uploadNewerLocalProgress(
        localHistory: List<PlaybackProgress>,
        remoteHistory: List<PlaybackProgress>,
    ) {
        newerLocalHistoryEntries(localHistory, remoteHistory).forEach { progress ->
            runCatching { uploadProgress(progress) }
        }
    }
}
