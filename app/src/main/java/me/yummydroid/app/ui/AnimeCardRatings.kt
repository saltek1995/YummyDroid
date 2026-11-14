package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.Anime
import me.yummydroid.app.readyDataOrNull

internal val LocalAnimeCardRatings = compositionLocalOf<Map<Long, Int?>> { emptyMap() }

internal fun animeCardUserRating(anime: Anime, ratings: Map<Long, Int?>): Int? =
    animeCardUserRating(anime.id, anime.userRating, ratings)

internal fun animeCardUserRating(animeId: Long, payloadRating: Int?, ratings: Map<Long, Int?>): Int? =
    if (ratings.containsKey(animeId)) ratings[animeId] else payloadRating

/** Share ratings supplied by list endpoints as well as the account's persisted/mutating ratings. */
@Composable
internal fun rememberAnimeCardRatings(state: YummyDroidUiState): Map<Long, Int?> {
    val featured = state.featured.readyDataOrNull().orEmpty()
    val search = state.searchResults.readyDataOrNull().orEmpty()
    val history = state.historyAnime.readyDataOrNull().orEmpty()
    val recommendations = state.detailsExtras.readyDataOrNull()?.recommendations.orEmpty()
    val payloadRatings = remember(state.auth.profile?.id, state.contentSessionRevision, featured, search, history, recommendations) {
        buildMap {
            for (list in listOf(history, recommendations, featured, search)) {
                for (anime in list) anime.userRating?.let { put(anime.id, it) }
            }
        }
    }
    return remember(payloadRatings, state.cardAnimeRatings) { payloadRatings + state.cardAnimeRatings }
}
