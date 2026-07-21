package me.yummydroid.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable

@Serializable
data class PlaybackProgress(
    val animeId: Long,
    val videoId: Long,
    val animeTitle: String = "",
    val posterUrl: String = "",
    val groupKey: String,
    val episode: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long,
)

class PlaybackProgressStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(animeId: Long): PlaybackProgress? {
        return readAnimeHistory(animeId).maxByOrNull { it.updatedAtMs }
    }

    fun readAll(): List<PlaybackProgress> {
        val histories = prefs.all.keys
            .filter { it.startsWith(HISTORY_KEY_PREFIX) }
            .flatMap { key -> prefs.getJsonOrNull<List<PlaybackProgress>>(key).orEmpty() }
        val legacy = prefs.all.values
            .mapNotNull { value -> (value as? String)?.decodeAppJsonOrNull<PlaybackProgress>() }
        return (histories + legacy)
            .filter { it.animeId > 0L && it.positionMs >= 0L }
            .distinctLatestByEpisode()
    }

    fun readAnimeHistory(animeId: Long): List<PlaybackProgress> {
        val history = prefs.getJsonOrNull<List<PlaybackProgress>>(animeId.historyKey).orEmpty()
        val legacy = prefs.getJsonOrNull<PlaybackProgress>(animeId.key)
            ?.takeIf { it.animeId == animeId && it.positionMs >= 0L }
        return (history + listOfNotNull(legacy))
            .filter { it.animeId == animeId && it.positionMs >= 0L }
            .distinctLatestByEpisode()
    }

    fun save(progress: PlaybackProgress) {
        val normalized = progress.normalized()
        val history = (readAnimeHistory(progress.animeId) + normalized).distinctLatestByEpisode()
        prefs.putJson(progress.animeId.historyKey, history)
        prefs.putJson(progress.animeId.key, history.maxBy { it.updatedAtMs })
    }

    fun saveIfNewer(progress: PlaybackProgress): PlaybackProgress {
        val normalized = progress.normalized()
        val current = readAnimeHistory(progress.animeId)
            .firstOrNull { it.sameProgressEpisodeAs(normalized) }
        val selected = if (progress.updatedAtMs > (current?.updatedAtMs ?: Long.MIN_VALUE)) {
            normalized
        } else {
            current
        }
        selected?.let(::save)
        return selected ?: normalized
    }

    fun clear() {
        prefs.edit {
            clear()
        }
    }

    private fun PlaybackProgress.normalized(): PlaybackProgress {
        return copy(
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
        )
    }

    private val Long.key: String
        get() = "anime_$this"

    private val Long.historyKey: String
        get() = "$HISTORY_KEY_PREFIX$this"

    private companion object {
        const val PREFS_NAME = "yummydroid_playback_progress"
        const val HISTORY_KEY_PREFIX = "anime_history_"
    }
}

internal fun List<PlaybackProgress>.distinctLatestByEpisode(): List<PlaybackProgress> {
    return groupBy { it.progressSyncKey() }
        .values
        .mapNotNull { entries -> entries.maxByOrNull { it.updatedAtMs } }
        .sortedWith(compareBy<PlaybackProgress> { it.episode.toDoubleOrNull() ?: Double.MAX_VALUE }.thenBy { it.videoId })
}

internal fun PlaybackProgress.sameProgressEpisodeAs(other: PlaybackProgress): Boolean {
    return animeId == other.animeId && progressSyncKey() == other.progressSyncKey()
}

internal fun PlaybackProgress.progressSyncKey(): String {
    return when {
        videoId > 0L -> "anime:$animeId:video:$videoId"
        episode.isNotBlank() -> "anime:$animeId:episode:${episode.trim()}"
        groupKey.isNotBlank() -> "anime:$animeId:group:$groupKey"
        else -> "anime:$animeId"
    }
}
