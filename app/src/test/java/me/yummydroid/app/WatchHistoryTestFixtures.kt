package me.yummydroid.app

import kotlinx.coroutines.Dispatchers
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.progressSyncKey

internal fun watchHistoryCoordinator(
    stored: MutableList<PlaybackProgress>,
    uploaded: MutableList<PlaybackProgress> = mutableListOf(),
    fetchedAnime: suspend (Long) -> Anime = ::watchHistoryAnime,
    fetchHistoryPage: suspend (limit: Int, offset: Int) -> List<PlaybackProgress> = { _, _ -> emptyList() },
    monotonicClockMs: () -> Long = System::currentTimeMillis,
    refreshIntervalMs: Long = BROWSE_REMOTE_REFRESH_INTERVAL_MS,
): WatchHistoryCoordinator {
    val cachedAnime = mutableMapOf<Long, Anime>()
    return WatchHistoryCoordinator(
        readProgress = { stored.toList() },
        saveProgressIfNewer = { incoming ->
            val index = stored.indexOfFirst { it.progressSyncKey() == incoming.progressSyncKey() }
            if (index < 0) {
                stored += incoming
            } else if (incoming.updatedAtMs > stored[index].updatedAtMs) {
                stored[index] = incoming
            }
        },
        readCachedAnime = { animeIds -> cachedAnime.filterKeys { it in animeIds } },
        saveCachedAnime = { anime -> cachedAnime[anime.id] = anime },
        fetchHistoryPage = fetchHistoryPage,
        uploadProgress = { progress -> uploaded += progress; true },
        fetchAnimeSummary = fetchedAnime,
        ioDispatcher = Dispatchers.Unconfined,
        monotonicClockMs = monotonicClockMs,
        refreshIntervalMs = refreshIntervalMs,
    )
}

internal fun watchHistoryAnime(id: Long): Anime {
    return Anime(
        id = id,
        title = "Anime $id",
        description = "",
        posterUrl = "",
        animeUrl = "",
        year = 2026,
        rating = null,
        views = 0,
        status = "",
        type = "",
        genres = emptyList(),
        blockedIn = emptyList(),
    )
}

internal fun watchHistoryProgress(
    animeId: Long,
    videoId: Long,
    episode: String = "1",
    updatedAtMs: Long,
): PlaybackProgress {
    return PlaybackProgress(
        animeId = animeId,
        videoId = videoId,
        animeTitle = "Anime $animeId",
        posterUrl = "",
        groupKey = "CVH|Voice",
        episode = episode,
        positionMs = 1_000,
        durationMs = 2_000,
        updatedAtMs = updatedAtMs,
    )
}
