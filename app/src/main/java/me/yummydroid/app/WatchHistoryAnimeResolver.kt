package me.yummydroid.app

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.toAnimeSummary

// WatchHistoryAnimeResolver
internal class WatchHistoryAnimeResolver(
    private val readCachedAnime: (Collection<Long>) -> Map<Long, Anime>,
    private val saveCachedAnime: (Anime) -> Unit,
    private val fetchAnimeSummary: suspend (animeId: Long) -> Anime,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun readCachedAnimeSummaries(history: List<PlaybackProgress>): List<Anime> {
        val cachedById = withContext(ioDispatcher) {
            readCachedAnime(history.map { it.animeId })
        }
        return history.toAnimeSummaries(cachedById)
    }

    suspend fun resolveAnimeSummaries(history: List<PlaybackProgress>): List<Anime> {
        val cachedById = withContext(ioDispatcher) {
            readCachedAnime(history.map { it.animeId }).toMutableMap()
        }
        val resolved = history.map { progress ->
            cachedById[progress.animeId] ?: runCatching {
                fetchAnimeSummary(progress.animeId)
            }.onSuccess { anime ->
                if (anime.id > 0L) {
                    withContext(ioDispatcher) { saveCachedAnime(anime) }
                    cachedById[anime.id] = anime
                }
            }.getOrElse {
                progress.toAnimeSummary()
            }
        }
        return resolved.distinctBy { it.id }
    }
}
