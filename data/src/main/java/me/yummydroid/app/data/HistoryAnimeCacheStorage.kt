package me.yummydroid.app.data

import android.content.Context
import androidx.core.content.edit

class HistoryAnimeCacheStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(animeId: Long): Anime? {
        if (animeId <= 0L) return null
        return prefs.getJsonOrNull<Anime>(animeId.key)
    }

    fun readMany(animeIds: Collection<Long>): Map<Long, Anime> {
        return animeIds
            .asSequence()
            .distinct()
            .mapNotNull { animeId -> read(animeId)?.let { animeId to it } }
            .toMap()
    }

    fun save(anime: Anime) {
        if (anime.id <= 0L) return
        prefs.putJson(anime.id.key, anime)
    }

    fun clear() {
        prefs.edit { clear() }
    }

    private val Long.key: String
        get() = "anime_$this"

    private companion object {
        const val PREFS_NAME = "yummydroid_history_anime_cache"
    }
}
