package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.PlaybackProgress

// WatchHistoryProgressSync
internal class WatchHistoryProgressSync(
    private val readProgress: () -> List<PlaybackProgress>,
    private val saveProgressIfNewer: (PlaybackProgress) -> Unit,
    private val replaceProgressHistory: (List<PlaybackProgress>) -> Unit = { history ->
        history.forEach(saveProgressIfNewer)
    },
    private val replaceAnimeProgressHistory: (Long, List<PlaybackProgress>) -> Unit = { _, history ->
        history.forEach(saveProgressIfNewer)
    },
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
    }.onFailure { throwable ->
        if (throwable is CancellationException) throw throwable
    }

    suspend fun storeRemoteHistory(history: List<PlaybackProgress>) {
        withContext(ioDispatcher) {
            replaceProgressHistory(history)
        }
    }

    suspend fun storeRemoteAnimeHistory(animeId: Long, history: List<PlaybackProgress>) {
        withContext(ioDispatcher) {
            replaceAnimeProgressHistory(animeId, history)
        }
    }

    suspend fun uploadSupplementalLocalProgress(
        localHistory: List<PlaybackProgress>,
        remoteHistory: List<PlaybackProgress>,
    ): Boolean {
        var success = true
        supplementalLocalHistoryEntries(localHistory, remoteHistory).forEach { progress ->
            val uploaded = runCatching { uploadProgress(progress) }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                }
                .getOrDefault(false)
            if (!uploaded) success = false
        }
        return success
    }
}
