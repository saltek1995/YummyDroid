package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.RatingDetails

class AnimeRatingStateTest {
    @Test
    fun optimisticRatingUpdatesDetailsAndExistingSummary() {
        val state = detailsState(
            animeId = 10,
            detailsExtras = LoadState.Ready(AnimeDetailsExtras(rating = AnimeRatingSummary(userRating = 4))),
        )

        val updated = state.withOptimisticAnimeRating(animeId = 10, rating = 8)

        assertEquals(8, updated.details.readyDataOrNull()?.userRating)
        assertEquals(8, updated.detailsExtras.readyDataOrNull()?.rating?.userRating)
    }

    @Test
    fun confirmedRatingCreatesMissingSummaryState() {
        val state = detailsState(animeId = 10, detailsExtras = LoadState.Loading)
        val summary = AnimeRatingSummary(userRating = 9)

        val updated = state.withConfirmedAnimeRating(
            animeId = 10,
            update = AnimeRatingUpdate(summary = summary, userRating = 9),
        )

        assertEquals(9, updated.details.readyDataOrNull()?.userRating)
        assertEquals(summary, updated.detailsExtras.readyDataOrNull()?.rating)
    }

    @Test
    fun lateResultCannotOverwriteAnotherDetailsRoute() {
        val state = detailsState(animeId = 20, detailsExtras = LoadState.Ready(AnimeDetailsExtras()))

        val updated = state.withConfirmedAnimeRating(
            animeId = 10,
            update = AnimeRatingUpdate(AnimeRatingSummary(userRating = 9), userRating = 9),
        )

        assertEquals(state, updated)
    }

    @Test
    fun rollbackRestoresSnapshotAndReportsMutationError() {
        val previousDetails = LoadState.Ready(animeDetails(id = 10, userRating = 4))
        val previousExtras = LoadState.Ready(
            AnimeDetailsExtras(rating = AnimeRatingSummary(userRating = 4)),
        )
        val optimistic = detailsState(animeId = 10).withOptimisticAnimeRating(10, 9)

        val restored = optimistic.withRestoredAnimeRating(
            animeId = 10,
            previousDetails = previousDetails,
            previousExtras = previousExtras,
            error = "mutation failed",
        )

        assertEquals(previousDetails, restored.details)
        assertEquals(previousExtras, restored.detailsExtras)
        assertEquals("mutation failed", restored.auth.error)
    }

    private fun detailsState(
        animeId: Long,
        detailsExtras: LoadState<AnimeDetailsExtras> = LoadState.Ready(AnimeDetailsExtras()),
    ): YummyDroidUiState {
        return YummyDroidUiState(
            route = AppRoute.Details(animeId),
            details = LoadState.Ready(animeDetails(animeId, userRating = 4)),
            detailsExtras = detailsExtras,
        )
    }

    private fun animeDetails(id: Long, userRating: Int?): AnimeDetails {
        return AnimeDetails(
            id = id,
            title = "Anime $id",
            otherTitles = emptyList(),
            description = "",
            posterUrl = "",
            backdropUrl = null,
            year = 2026,
            rating = null,
            views = 0,
            status = "ongoing",
            type = "Series",
            minAge = "",
            genreTags = emptyList(),
            genres = emptyList(),
            episodeSummary = "",
            episodeAired = 0,
            episodeCount = 0,
            nextEpisodeText = "",
            durationSeconds = 0,
            ratingDetails = RatingDetails(),
            studios = emptyList(),
            creators = emptyList(),
            original = "",
            commentsCount = 0,
            listsCount = 0,
            translations = emptyList(),
            relatedAnime = emptyList(),
            screenshots = emptyList(),
            blockedIn = emptyList(),
            userRating = userRating,
        )
    }
}
