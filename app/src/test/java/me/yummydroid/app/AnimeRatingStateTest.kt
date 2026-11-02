package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.UserProfile

class AnimeRatingStateTest {
    @Test
    fun failedRatingSettlesAfterNavigationAndIsNotSuppressedByAnotherAnimeMutation() = runBlocking {
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        val coordinator = AnimeRatingCoordinator(
            readRatings = { mapOf(10L to 4, 20L to 4) }, saveRatings = { _, _ -> },
            setRating = { id, _ ->
                if (id == 10L) { first.await(); error("rejected") }
                second.await()
                me.yummydroid.app.data.AnimeRatingSummary(userRating = 8)
            },
            deleteRating = { error("unused") }, fetchUserRating = { 8 }, ioDispatcher = Dispatchers.Unconfined,
        )
        coordinator.restore(1L)
        val profile = AuthUiState(profile = UserProfile(1L, "User", ""))
        var state = detailsState(10L).copy(auth = profile)
        val invalidated = mutableListOf<Long>()
        val cached = mutableListOf<Long>()
        val runtime = AnimeRatingStateRuntime(
            scope = this, coordinator = coordinator, currentState = { state }, updateState = { state = it(state) },
            authenticatedDetailsAnimeId = { (state.route as? AppRoute.Details)?.animeId },
            cacheDetailsRouteState = cached::add, invalidateDetailsRouteState = invalidated::add,
            requestCaptchaRetry = { _, _ -> false }, showErrorNotice = { error("unrelated route must not show an error") },
        )
        runtime.setRating(10)
        yield()
        assertTrue(coordinator.hasPendingMutation(10L))
        assertTrue(cached.isEmpty())
        state = detailsState(20L).copy(auth = profile)
        runtime.setRating(8)
        first.complete(Unit)
        yield()
        assertFalse(coordinator.hasPendingMutation(10L))
        assertEquals(4, coordinator.snapshot()[10L])
        assertEquals(2, invalidated.count { it == 10L })
        assertEquals(8, state.details.readyDataOrNull()?.userRating)
        second.complete(Unit)
        yield()
        assertEquals(8, state.details.readyDataOrNull()?.userRating)
        assertFalse(coordinator.hasPendingMutation(20L))
    }

    @Test
    fun failedRatingRollsBackRetainedDetailsWhileThePlayerIsOpen() = runBlocking {
        val response = CompletableDeferred<Unit>()
        val coordinator = AnimeRatingCoordinator(
            readRatings = { mapOf(10L to 4) }, saveRatings = { _, _ -> },
            setRating = { _, _ -> response.await(); error("rejected") },
            deleteRating = { error("unused") }, fetchUserRating = { null }, ioDispatcher = Dispatchers.Unconfined,
        )
        coordinator.restore(1L)
        var state = detailsState(10L).copy(auth = AuthUiState(profile = UserProfile(1L, "User", "")))
        val invalidated = mutableListOf<Long>()
        val runtime = AnimeRatingStateRuntime(
            scope = this, coordinator = coordinator, currentState = { state }, updateState = { state = it(state) },
            authenticatedDetailsAnimeId = { 10L }, cacheDetailsRouteState = {},
            invalidateDetailsRouteState = invalidated::add, requestCaptchaRetry = { _, _ -> false }, showErrorNotice = {},
        )
        runtime.setRating(10)
        yield()
        state = state.copy(route = AppRoute.Player(cvhSourceVideo().copy(animeId = 10L), "Anime"))
        response.complete(Unit)
        yield()
        assertEquals(4, state.details.readyDataOrNull()?.userRating)
        assertEquals(listOf(10L, 10L), invalidated)
    }

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
    fun rollbackRestoresSnapshotWithoutPollutingAuthError() {
        val previousDetails = LoadState.Ready(animeDetails(id = 10, userRating = 4))
        val previousExtras = LoadState.Ready(
            AnimeDetailsExtras(rating = AnimeRatingSummary(userRating = 4)),
        )
        val optimistic = detailsState(animeId = 10).withOptimisticAnimeRating(10, 9)

        val restored = optimistic.withRestoredAnimeRating(
            animeId = 10,
            previousDetails = previousDetails,
            previousExtras = previousExtras,
        )

        assertEquals(previousDetails, restored.details)
        assertEquals(previousExtras, restored.detailsExtras)
        assertNull(restored.auth.error)
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
