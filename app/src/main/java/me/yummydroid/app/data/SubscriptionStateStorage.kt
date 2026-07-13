package me.yummydroid.app.data

import android.content.Context
import kotlinx.serialization.Serializable

class SubscriptionStateStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(userId: Long): Map<Long, Set<String>> {
        return prefs.getJsonOrNull<StoredSubscriptionVoices>(key(userId))
            ?.items
            ?.mapNotNull { item ->
                val animeId = item.animeId.takeIf { it > 0L } ?: return@mapNotNull null
                val voices = item.voices
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
                animeId to voices
            }
            ?.filter { (_, voices) -> voices.isNotEmpty() }
            ?.toMap()
            .orEmpty()
    }

    fun save(userId: Long, voicesByAnime: Map<Long, Set<String>>) {
        val items = voicesByAnime
            .mapNotNull { (animeId, voices) ->
                if (animeId <= 0L) return@mapNotNull null
                val normalizedVoices = voices
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                if (normalizedVoices.isEmpty()) return@mapNotNull null
                StoredSubscriptionVoice(animeId = animeId, voices = normalizedVoices)
            }
            .sortedBy { it.animeId }
        prefs.putJson(key(userId), StoredSubscriptionVoices(items))
    }

    private fun key(userId: Long): String = "$KEY_PREFIX$userId"

    private companion object {
        const val PREFS_NAME = "yummydroid_subscription_state"
        const val KEY_PREFIX = "voices_"
    }
}

@Serializable
private data class StoredSubscriptionVoices(
    val items: List<StoredSubscriptionVoice> = emptyList(),
)

@Serializable
private data class StoredSubscriptionVoice(
    val animeId: Long,
    val voices: List<String> = emptyList(),
)
