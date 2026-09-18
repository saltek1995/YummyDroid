package me.yummydroid.app.data

import java.text.Collator
import java.util.Locale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal data class SearchSnapshotKey(
    val query: String,
    val filters: BrowseFilters,
    val marks: UserMarkFilterIds?,
    val language: ContentLanguage,
    val revision: Long,
    val userId: Long?,
    val token: String?,
    val cacheGeneration: Long?,
)

internal data class SearchSnapshot(val key: SearchSnapshotKey, val items: List<Anime>)

// The API orders q results by relevance first, even when an explicit sort is sent.
// Sort the complete result set before exposing any pages to the UI.
internal fun List<AnimeDto>.sortedSearchResults(sort: AnimeSort, locale: Locale): List<AnimeDto> {
    if (sort == AnimeSort.Random) return shuffled()
    val comparator: Comparator<AnimeDto> = when (sort) {
        AnimeSort.Title -> {
            val collator = Collator.getInstance(locale)
            Comparator { a, b -> collator.compare(a.title, b.title) }
        }
        AnimeSort.Year -> compareBy { it.year }
        AnimeSort.Rating -> compareBy { it.rating.ratingValue() ?: 0.0 }
        AnimeSort.RatingCounters -> compareBy { (it.rating as? JsonObject)?.get("counters")?.jsonPrimitive?.longOrNull ?: 0L }
        AnimeSort.Views -> compareBy { it.views }
        AnimeSort.Top -> compareBy { it.top?.get("global")?.jsonPrimitive?.longOrNull ?: 0L }
        AnimeSort.Id -> compareBy { it.animeId }
        AnimeSort.Random -> error("Random order is handled above")
    }
    val directed = if (sort.forward) comparator else comparator.reversed()
    return sortedWith(directed.thenByDescending { it.animeId })
}
