package me.yummydroid.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.CaptchaRequiredException
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.UserProfile
import me.yummydroid.app.data.VideoVariant

class AnimeMarkCoordinatorTest {
    @Test
    fun obsoleteReadCannotOverwriteWriteEvenWhenCancellationIsIgnored() = runBlocking {
        val finish = CompletableDeferred<Unit>()
        var writes = 0
        val harness = harness(authenticatedDetailsState(animeMark = UserAnimeMark()),
            getAnimeMark = { kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { finish.await() }; UserAnimeMark() },
            setFavorite = { _, value -> writes++; UserAnimeMark(isFavorite = value) })
        try {
            harness.coordinator.load(10)
            harness.coordinator.toggleFavorite()
            assertTrue(harness.state.animeMark.readyDataOrNull()!!.isFavorite)
            assertTrue(harness.coordinator.hasPendingMutation(10))
            assertEquals(0, writes)
            finish.complete(Unit)
            yield()
            assertEquals(1, writes)
            assertTrue(harness.state.animeMark.readyDataOrNull()!!.isFavorite)
            assertEquals(false, harness.coordinator.hasPendingMutation(10))
        } finally { finish.complete(Unit); harness.close() }
    }

    @Test
    fun readAfterWriteWaitsForSettlement() = runBlocking {
        val finish = CompletableDeferred<Unit>()
        var written = false
        var reads = 0
        val harness = harness(authenticatedDetailsState(animeMark = UserAnimeMark()),
            setFavorite = { _, value -> finish.await(); written = true; UserAnimeMark(isFavorite = value) },
            getAnimeMark = { assertTrue(written); reads++; UserAnimeMark(isFavorite = true) })
        try {
            harness.coordinator.toggleFavorite()
            harness.coordinator.load(10)
            assertEquals(0, reads)
            finish.complete(Unit)
            yield()
            assertEquals(1, reads)
            assertTrue(harness.state.animeMark.readyDataOrNull()!!.isFavorite)
        } finally { harness.close() }
    }

    @Test
    fun twoFailuresRestoreConfirmedBaseAndUnknownFailureNeverRestoresLoading() = runBlocking {
        val finish = CompletableDeferred<Unit>()
        val previous = UserAnimeMark(list = UserAnimeListMark.Watching)
        val harness = harness(authenticatedDetailsState(animeMark = previous),
            setFavorite = { _, _ -> finish.await(); error("favorite failed") },
            setAnimeListMark = { _, _ -> error("list failed") })
        try {
            harness.coordinator.toggleFavorite()
            harness.coordinator.toggleListMark(UserAnimeListMark.Planned)
            finish.complete(Unit)
            yield()
            assertEquals(previous, harness.state.animeMark.readyDataOrNull())
            assertEquals(listOf("favorite failed", "list failed"), harness.mutationErrors)
        } finally { harness.close() }
        val unknown = harness(authenticatedDetailsState().copy(animeMark = LoadState.Loading), setFavorite = { _, _ -> error("failed") })
        try {
            unknown.coordinator.toggleFavorite()
            assertIs<LoadState.Error>(unknown.state.animeMark)
            assertEquals(false, unknown.coordinator.hasPendingMutation(10))
        } finally { unknown.close() }
    }

    @Test
    fun captchaRetryRetainsAbsoluteTargetAndCannotCrossAccountEpoch() = runBlocking {
        var retry: (suspend () -> Unit)? = null
        val writes = mutableListOf<Pair<Long, Boolean>>()
        val harness = harness(authenticatedDetailsState(animeMark = UserAnimeMark()),
            setFavorite = { id, value -> writes += id to value; throw CaptchaRequiredException("captcha") },
            requestCaptchaRetry = { _, action -> retry = action; true })
        try {
            harness.coordinator.toggleFavorite()
            harness.state = harness.state.copy(route = AppRoute.Details(20), details = LoadState.Ready(details(id = 20)), animeMark = LoadState.Ready(null))
            requireNotNull(retry).invoke()
            yield()
            assertEquals(listOf(10L to true, 10L to true), writes)
            assertNull(harness.state.animeMark.readyDataOrNull())
            harness.coordinator.clear()
            harness.state = authenticatedDetailsState()
            requireNotNull(retry).invoke()
            yield()
            assertEquals(2, writes.size)
        } finally { harness.close() }
    }

    @Test
    fun committedWriteRetainsIntentWhenRefreshFails() {
        val base = UserAnimeMark(list = UserAnimeListMark.Watching)
        val harness = harness(authenticatedDetailsState(animeMark = base), setFavorite = { _, _ ->
            throw me.yummydroid.app.data.CommittedMutationRefreshException(IllegalStateException("refresh failed"))
        })
        try {
            harness.coordinator.toggleFavorite()
            assertEquals(base.copy(isFavorite = true), harness.state.animeMark.readyDataOrNull())
            assertEquals(listOf("refresh failed"), harness.mutationErrors)
            assertEquals(listOf(10L, 10L), harness.invalidatedAnimeIds)
        } finally { harness.close() }
    }

    @Test
    fun offRouteSettlementEvictsCacheAndReturnLoadsConfirmedState() = runBlocking {
        val finish = CompletableDeferred<Unit>()
        val base = UserAnimeMark(list = UserAnimeListMark.Watching)
        val harness = harness(authenticatedDetailsState(animeMark = base), getAnimeMark = { base },
            setFavorite = { _, _ -> finish.await(); error("failed") })
        try {
            harness.coordinator.toggleFavorite()
            harness.state = harness.state.copy(route = AppRoute.Home)
            finish.complete(Unit)
            yield()
            assertEquals(listOf(10L, 10L), harness.invalidatedAnimeIds)
            assertEquals(emptyList(), harness.mutationErrors)
            harness.state = authenticatedDetailsState().copy(animeMark = LoadState.Loading)
            harness.coordinator.load(10)
            yield()
            assertEquals(base, harness.state.animeMark.readyDataOrNull())
        } finally { harness.close() }
    }

    @Test
    fun autoNoOpPublishesFetchedConfirmedState() {
        val watched = UserAnimeMark(list = UserAnimeListMark.Watched, isFavorite = true)
        val harness = harness(authenticatedDetailsState(settings = AppSettings(autoMarkWatchingOnPlayback = true)).copy(animeMark = LoadState.Loading),
            getAnimeMark = { watched }, setAnimeListMark = { _, _ -> error("unexpected write") })
        try {
            harness.coordinator.maybeMarkWatching(video())
            assertEquals(watched, harness.state.animeMark.readyDataOrNull())
            assertEquals(false, harness.coordinator.hasPendingMutation(10))
        } finally { harness.close() }
    }

    @Test
    fun loadPublishesLoadingThenReadyAndCachesCurrentRoute() {
        val loaded = UserAnimeMark(list = UserAnimeListMark.Planned, isFavorite = true)
        val harness = harness(
            initialState = authenticatedDetailsState(),
            getAnimeMark = { loaded },
        )

        harness.coordinator.load(animeId = 10)

        assertTrue(harness.states.first().animeMark is LoadState.Loading)
        assertEquals(loaded, harness.state.animeMark.readyDataOrNull())
        assertEquals(listOf(10L), harness.cachedAnimeIds)
        harness.close()
    }

    @Test
    fun completedLoadCannotPopulateAnotherDetailsRoute() = runBlocking {
        val loadStarted = CompletableDeferred<Unit>()
        val finishLoad = CompletableDeferred<Unit>()
        val harness = harness(
            initialState = authenticatedDetailsState(),
            getAnimeMark = {
                loadStarted.complete(Unit)
                finishLoad.await()
                UserAnimeMark(list = UserAnimeListMark.Watching)
            },
        )

        harness.coordinator.load(animeId = 10)
        loadStarted.await()
        harness.state = harness.state.copy(route = AppRoute.Details(20))
        finishLoad.complete(Unit)
        yield()

        assertIs<LoadState.Loading>(harness.state.animeMark)
        harness.close()
    }

    @Test
    fun listMutationPublishesOptimisticThenConfirmedState() {
        val requests = mutableListOf<Pair<Long, UserAnimeListMark>>()
        val confirmed = UserAnimeMark(list = UserAnimeListMark.Planned, isFavorite = true)
        val harness = harness(
            initialState = authenticatedDetailsState(
                animeMark = UserAnimeMark(list = UserAnimeListMark.Watching),
            ),
            setAnimeListMark = { animeId, mark ->
                requests += animeId to mark
                confirmed
            },
        )

        harness.coordinator.toggleListMark(UserAnimeListMark.Planned)

        assertEquals(listOf(10L to UserAnimeListMark.Planned), requests)
        assertEquals(UserAnimeListMark.Planned, harness.states.first().animeMark.readyDataOrNull()?.list)
        assertEquals(confirmed, harness.state.animeMark.readyDataOrNull())
        assertEquals(listOf(10L, 10L), harness.cachedAnimeIds)
        harness.close()
    }

    @Test
    fun captchaFailureRollsBackBeforePublishingRetry() {
        val previous = UserAnimeMark(list = UserAnimeListMark.Watching, isFavorite = true)
        var retry: (suspend () -> Unit)? = null
        val harness = harness(
            initialState = authenticatedDetailsState(animeMark = previous),
            setAnimeListMark = { _, _ -> throw CaptchaRequiredException("captcha") },
            requestCaptchaRetry = { throwable, action ->
                assertIs<CaptchaRequiredException>(throwable)
                retry = action
                true
            },
        )

        harness.coordinator.toggleListMark(UserAnimeListMark.Planned)

        assertEquals(previous, harness.state.animeMark.readyDataOrNull())
        assertTrue(retry != null)
        assertNull(harness.state.auth.error)
        harness.close()
    }

    @Test
    fun mutationFailureRollsBackAndPublishesLocalError() {
        val previous = UserAnimeMark(list = UserAnimeListMark.Watching, isFavorite = true)
        val harness = harness(
            initialState = authenticatedDetailsState(animeMark = previous),
            setAnimeListMark = { _, _ -> error("mutation failed") },
        )

        harness.coordinator.toggleListMark(UserAnimeListMark.Planned)

        assertEquals(previous, harness.state.animeMark.readyDataOrNull())
        assertEquals(listOf("mutation failed"), harness.mutationErrors)
        assertNull(harness.state.auth.error)
        harness.close()
    }

    @Test
    fun completedMutationCannotPopulateAnotherDetailsRoute() = runBlocking {
        val mutationStarted = CompletableDeferred<Unit>()
        val finishMutation = CompletableDeferred<Unit>()
        val harness = harness(
            initialState = authenticatedDetailsState(),
            setAnimeListMark = { _, mark ->
                mutationStarted.complete(Unit)
                finishMutation.await()
                UserAnimeMark(list = mark)
            },
        )

        harness.coordinator.toggleListMark(UserAnimeListMark.Planned)
        mutationStarted.await()
        harness.state = harness.state.copy(
            route = AppRoute.Details(20),
            details = LoadState.Ready(details(id = 20)),
            animeMark = LoadState.Ready(null),
        )
        finishMutation.complete(Unit)
        yield()

        assertNull(harness.state.animeMark.readyDataOrNull())
        assertEquals(emptyList(), harness.mutationErrors)
        harness.close()
    }

    @Test
    fun unauthenticatedMutationOnlyPublishesAuthError() {
        var mutationCalls = 0
        val harness = harness(
            initialState = authenticatedDetailsState().copy(auth = AuthUiState()),
            setAnimeListMark = { _, _ ->
                mutationCalls += 1
                UserAnimeMark()
            },
        )

        harness.coordinator.toggleListMark(UserAnimeListMark.Planned)

        assertEquals(0, mutationCalls)
        assertEquals(AUTH_REQUIRED_ERROR_KEY, harness.state.auth.error)
        harness.close()
    }

    @Test
    fun playbackAutoMarkDoesNotReplaceWatchedState() {
        var mutationCalls = 0
        val harness = harness(
            initialState = authenticatedDetailsState(
                animeMark = UserAnimeMark(list = UserAnimeListMark.Watched),
                settings = AppSettings(autoMarkWatchingOnPlayback = true),
            ),
            setAnimeListMark = { _, _ ->
                mutationCalls += 1
                UserAnimeMark()
            },
        )

        harness.coordinator.maybeMarkWatching(video())

        assertEquals(0, mutationCalls)
        assertEquals(UserAnimeListMark.Watched, harness.state.animeMark.readyDataOrNull()?.list)
        harness.close()
    }

    @Test
    fun completedFinalEpisodeAutomaticallyMarksReleasedAnimeWatched() {
        val requests = mutableListOf<UserAnimeListMark>()
        val finalVideo = video(episode = "12", index = 12)
        val state = authenticatedDetailsState(
            animeMark = UserAnimeMark(list = UserAnimeListMark.Watching),
            settings = AppSettings(autoMarkWatchedOnCompletedFinalEpisode = true),
        ).copy(
            details = LoadState.Ready(details(status = "released")),
            videos = LoadState.Ready(listOf(finalVideo)),
        )
        val harness = harness(
            initialState = state,
            setAnimeListMark = { _, mark ->
                requests += mark
                UserAnimeMark(list = mark)
            },
        )

        harness.coordinator.maybeMarkWatchedOnCompletion(finalVideo, state)

        assertEquals(listOf(UserAnimeListMark.Watched), requests)
        assertEquals(UserAnimeListMark.Watched, harness.state.animeMark.readyDataOrNull()?.list)
        harness.close()
    }

    @Test
    fun completionAutoMarkRequiresEveryPolicyCondition() {
        val completedVideo = video(episode = "12", index = 12)
        val laterVideo = video(episode = "13", index = 13)
        val eligibleState = authenticatedDetailsState(
            settings = AppSettings(autoMarkWatchedOnCompletedFinalEpisode = true),
        ).copy(
            details = LoadState.Ready(details(status = "released")),
            videos = LoadState.Ready(listOf(completedVideo)),
        )
        val cases = listOf(
            eligibleState.copy(settings = AppSettings()),
            eligibleState.copy(auth = AuthUiState()),
            eligibleState.copy(details = LoadState.Ready(details(status = "ongoing"))),
            eligibleState.copy(videos = LoadState.Ready(listOf(completedVideo, laterVideo))),
        )

        cases.forEach { state ->
            var mutationCalls = 0
            val harness = harness(
                initialState = state,
                setAnimeListMark = { _, _ ->
                    mutationCalls += 1
                    UserAnimeMark()
                },
            )

            harness.coordinator.maybeMarkWatchedOnCompletion(completedVideo, state)

            assertEquals(0, mutationCalls)
            harness.close()
        }
    }

    private fun harness(
        initialState: YummyDroidUiState,
        getAnimeMark: suspend (Long) -> UserAnimeMark? = { null },
        setAnimeListMark: suspend (Long, UserAnimeListMark) -> UserAnimeMark = { _, mark ->
            UserAnimeMark(list = mark)
        },
        removeAnimeListMark: suspend (Long) -> UserAnimeMark = { UserAnimeMark() },
        setFavorite: suspend (Long, Boolean) -> UserAnimeMark = { _, favorite ->
            UserAnimeMark(isFavorite = favorite)
        },
        requestCaptchaRetry: (Throwable, suspend () -> Unit) -> Boolean = { _, _ -> false },
    ): Harness {
        return Harness(
            initialState = initialState,
            getAnimeMark = getAnimeMark,
            setAnimeListMark = setAnimeListMark,
            removeAnimeListMark = removeAnimeListMark,
            setFavorite = setFavorite,
            requestCaptchaRetry = requestCaptchaRetry,
        )
    }

    private class Harness(
        initialState: YummyDroidUiState,
        getAnimeMark: suspend (Long) -> UserAnimeMark?,
        setAnimeListMark: suspend (Long, UserAnimeListMark) -> UserAnimeMark,
        removeAnimeListMark: suspend (Long) -> UserAnimeMark,
        setFavorite: suspend (Long, Boolean) -> UserAnimeMark,
        requestCaptchaRetry: (Throwable, suspend () -> Unit) -> Boolean,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var state: YummyDroidUiState = initialState
        val states = mutableListOf<YummyDroidUiState>()
        val cachedAnimeIds = mutableListOf<Long>()
        val invalidatedAnimeIds = mutableListOf<Long>()
        val mutationErrors = mutableListOf<String>()
        val coordinator = AnimeMarkCoordinator(
            scope = scope,
            currentState = { state },
            updateState = { transform ->
                state = transform(state)
                states += state
            },
            getAnimeMark = getAnimeMark,
            setAnimeListMark = setAnimeListMark,
            removeAnimeListMark = removeAnimeListMark,
            setFavorite = setFavorite,
            authenticatedDetailsAnimeId = {
                val animeId = (state.route as? AppRoute.Details)?.animeId
                if (animeId != null && state.auth.profile == null) {
                    state = state.copy(auth = state.auth.copy(error = AUTH_REQUIRED_ERROR_KEY))
                    states += state
                    null
                } else {
                    animeId
                }
            },
            requestCaptchaRetry = requestCaptchaRetry,
            cacheDetailsRouteState = cachedAnimeIds::add,
            onMutationFailure = mutationErrors::add,
            onAutoMarkFailure = {},
            invalidateDetailsRouteState = invalidatedAnimeIds::add,
        )

        fun close() {
            scope.cancel()
        }
    }

    private companion object {
        fun authenticatedDetailsState(
            animeMark: UserAnimeMark? = null,
            settings: AppSettings = AppSettings(),
        ): YummyDroidUiState {
            return YummyDroidUiState(
                route = AppRoute.Details(10),
                details = LoadState.Ready(details()),
                auth = AuthUiState(profile = UserProfile(id = 42, nickname = "User", avatarUrl = "")),
                animeMark = LoadState.Ready(animeMark),
                settings = settings,
            )
        }

        fun details(status: String = "ongoing", id: Long = 10): AnimeDetails {
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
                status = status,
                type = "",
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
            )
        }

        fun video(episode: String = "1", index: Int = 1): VideoVariant {
            return VideoVariant(
                id = index.toLong(),
                animeId = 10,
                player = "CVH",
                dubbing = "Voice",
                episode = episode,
                url = "https://video.test/$index",
                index = index,
                durationSeconds = null,
                views = 0,
            )
        }
    }
}
