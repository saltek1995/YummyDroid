package me.yummydroid.app

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PlaybackProgressStorage
import me.yummydroid.app.data.PlaybackSelection

internal data class AnimePlaybackSnapshot(
    val history: List<PlaybackProgress>,
    val selection: PlaybackSelection?,
) {
    val progress: PlaybackProgress? get() = history.maxByOrNull { it.updatedAtMs }
}

/** Card reads use the persisted history populated by overall synchronization and local playback. */
internal class AnimePlaybackSnapshotLoader(
    private val storage: PlaybackProgressStorage,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun publish(
        animeId: Long,
        isCurrent: () -> Boolean,
        onSnapshot: (AnimePlaybackSnapshot) -> Unit,
    ) {
        while (isCurrent()) {
            val revision = storage.readHistoryRevision()
            val snapshot = withContext(ioDispatcher) {
                AnimePlaybackSnapshot(storage.readAnimeHistory(animeId), storage.readSelection(animeId))
            }
            // A save/reset or overall refresh may have completed during the read.
            // Retry from the current cache instead of publishing an obsolete snapshot.
            if (storage.withHistoryRevision(revision) {
                    if (isCurrent()) onSnapshot(snapshot)
                }) return
        }
    }
}
