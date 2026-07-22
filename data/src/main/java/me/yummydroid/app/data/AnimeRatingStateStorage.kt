package me.yummydroid.app.data

import android.content.Context
import kotlinx.serialization.Serializable

class AnimeRatingStateStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(userId: Long): Map<Long, Int> {
        return prefs.getJsonOrNull<StoredAnimeRatings>(key(userId))
            ?.items
            ?.mapNotNull { item ->
                val animeId = item.animeId.takeIf { it > 0L } ?: return@mapNotNull null
                val rating = item.rating.takeIf { it in 1..10 } ?: return@mapNotNull null
                animeId to rating
            }
            ?.toMap()
            .orEmpty()
    }

    fun save(userId: Long, ratingsByAnime: Map<Long, Int?>) {
        val items = ratingsByAnime
            .mapNotNull { (animeId, rating) ->
                if (animeId <= 0L) return@mapNotNull null
                val normalizedRating = rating?.takeIf { it in 1..10 } ?: return@mapNotNull null
                StoredAnimeRating(animeId = animeId, rating = normalizedRating)
            }
            .sortedBy { it.animeId }
        prefs.putJson(key(userId), StoredAnimeRatings(items))
    }

    private fun key(userId: Long): String = "$KEY_PREFIX$userId"

    private companion object {
        const val PREFS_NAME = "yummydroid_anime_rating_state"
        const val KEY_PREFIX = "ratings_"
    }
}

@Serializable
private data class StoredAnimeRatings(
    val items: List<StoredAnimeRating> = emptyList(),
)

@Serializable
private data class StoredAnimeRating(
    val animeId: Long,
    val rating: Int,
)
