package me.yummydroid.app

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.yummydroid.app.data.Anime

internal data class AnimeCardRatingsSnapshot(
    val userId: Long? = null,
    val ratings: Map<Long, Int?> = emptyMap(),
)

internal fun LoadState<List<Anime>>.withoutCardUserRatings(): LoadState<List<Anime>> {
    val anime = readyDataOrNull() ?: return this
    return if (anime.none { it.userRating != null }) this else LoadState.Ready(
        anime.map { if (it.userRating == null) it else it.copy(userRating = null) },
    )
}

/** Null entries override stale list payloads after the user removes a rating. */
internal fun YummyDroidUiState.withCardAnimeRatings(snapshot: AnimeCardRatingsSnapshot): YummyDroidUiState {
    val ratings = snapshot.ratings.takeIf { snapshot.userId != null && snapshot.userId == auth.profile?.id }
        ?: emptyMap()
    return if (cardAnimeRatings === ratings) this else copy(cardAnimeRatings = ratings)
}

internal suspend fun observeAnimeCardRatings(
    state: StateFlow<YummyDroidUiState>,
    ratings: StateFlow<AnimeCardRatingsSnapshot>,
    updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
) {
    combine(state.map { it.auth.profile?.id to it.contentSessionRevision }.distinctUntilChanged(), ratings) { account, snapshot ->
        account to snapshot
    }.collect { (account, snapshot) ->
        updateState { current ->
            if (current.auth.profile?.id == account.first && current.contentSessionRevision == account.second) {
                current.withCardAnimeRatings(snapshot)
            } else current
        }
    }
}
