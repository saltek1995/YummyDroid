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
        return prefs.all.keys
            .filter { it.startsWith(HISTORY_KEY_PREFIX) }
            .flatMap { key -> prefs.getJsonOrNull<List<PlaybackProgress>>(key).orEmpty() }
            .filter { it.animeId > 0L && it.positionMs >= 0L }
            .distinctLatestByEpisode()
    }

    fun readAnimeHistory(animeId: Long): List<PlaybackProgress> {
        return prefs.getJsonOrNull<List<PlaybackProgress>>(animeId.historyKey).orEmpty()
            .filter { it.animeId == animeId && it.positionMs >= 0L }
            .distinctLatestByEpisode()
    }

    fun save(progress: PlaybackProgress) {
        val normalized = progress.normalized()
        val history = (readAnimeHistory(progress.animeId) + normalized).distinctLatestByEpisode()
        prefs.putJson(progress.animeId.historyKey, history)
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

    fun clearAnime(animeId: Long) {
        prefs.edit {
            remove(animeId.historyKey)
        }
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

    private val Long.historyKey: String
        get() = "$HISTORY_KEY_PREFIX$this"

    private companion object {
        const val PREFS_NAME = "yummydroid_playback_progress"
        const val HISTORY_KEY_PREFIX = "anime_history_"
    }
}

fun List<PlaybackProgress>.distinctLatestByEpisode(): List<PlaybackProgress> {
    return groupBy { it.progressSyncKey() }
        .values
        .mapNotNull { entries -> entries.maxByOrNull { it.updatedAtMs } }
        .sortedWith(compareBy<PlaybackProgress> { it.episode.toDoubleOrNull() ?: Double.MAX_VALUE }.thenBy { it.videoId })
}

fun PlaybackProgress.sameProgressEpisodeAs(other: PlaybackProgress): Boolean {
    return animeId == other.animeId && progressSyncKey() == other.progressSyncKey()
}

fun PlaybackProgress.progressSyncKey(): String {
    val episodeKey = episode.trim()
    if (episodeKey.isNotBlank()) {
        val voiceKey = groupKey.substringAfter('|', groupKey).normalizedVoiceKey()
        return if (voiceKey.isNotBlank()) {
            "anime:$animeId:episode:$episodeKey:voice:$voiceKey"
        } else {
            "anime:$animeId:episode:$episodeKey"
        }
    }
    return when {
        groupKey.isNotBlank() -> "anime:$animeId:group:$groupKey"
        videoId > 0L -> "anime:$animeId:video:$videoId"
        else -> "anime:$animeId"
    }
}
