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
            onAutoMarkFailure = {},
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

        fun details(status: String = "ongoing"): AnimeDetails {
            return AnimeDetails(
                id = 10,
                title = "Anime 10",
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
