package me.yummydroid.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class PlaybackProgress(
    val animeId: Long,
    val videoId: Long,
    val groupKey: String,
    val episode: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long,
)

class PlaybackProgressStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(animeId: Long): PlaybackProgress? {
        return prefs.getString(animeId.key, null)
            ?.let { encoded -> runCatching { AppJson.decodeFromString<PlaybackProgress>(encoded) }.getOrNull() }
            ?.takeIf { it.animeId == animeId && it.positionMs >= 0L }
    }

    fun readAll(): List<PlaybackProgress> {
        return prefs.all.values
            .mapNotNull { value ->
                (value as? String)
                    ?.let { encoded -> runCatching { AppJson.decodeFromString<PlaybackProgress>(encoded) }.getOrNull() }
                    ?.takeIf { it.animeId > 0L && it.positionMs >= 0L }
            }
    }

    fun save(progress: PlaybackProgress) {
        prefs.edit {
            putString(progress.animeId.key, AppJson.encodeToString(progress.normalized()))
        }
    }

    fun saveIfNewer(progress: PlaybackProgress): PlaybackProgress {
        val current = read(progress.animeId)
        val selected = if (progress.updatedAtMs > (current?.updatedAtMs ?: Long.MIN_VALUE)) {
            progress
        } else {
            current
        }
        selected?.let(::save)
        return selected ?: progress
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

    private companion object {
        const val PREFS_NAME = "yummydroid_playback_progress"
    }
}
