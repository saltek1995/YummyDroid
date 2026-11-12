package me.yummydroid.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.RelatedAnime
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.RepositoryContent
import me.yummydroid.app.data.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NextTitlePlaybackTest {
    @Test
    fun immediateOrderedSuccessorIsOfferedAndOnlyStartsAfterConfirmation() {
        val current = details(10, related = listOf(related(10, "Current"), related(30, "Ordered"), related(20, "Later")))
        val next = loaded(30, "Loaded title", listOf(video(301, 30, episode = "1")))
        var state = playerState(current)
        var calls = 0
        var starts = 0
        val coordinator = coordinator({ state }, { state = it(state) }, { id, _ ->
            calls++
            if (id == 30L) next else error("current details should be reused")
        }, { _, _ -> starts++ })

        assertTrue(coordinator.onPlaybackCompleted(video(101, 10, episode = "1")))
        assertEquals(1, calls)
        assertEquals(0, starts)
        assertEquals(30L, state.nextTitleSuggestion?.nextAnimeId)
        assertEquals("Loaded title", state.nextTitleSuggestion?.title)

        coordinator.confirm(state.nextTitleSuggestion!!.requestId)
        assertEquals(1, starts)
        assertNull(state.nextTitleSuggestion)
    }

    @Test
    fun noSuccessorOrEligibleVideosDoesNotSkipAhead() {
        val current = details(10, related = listOf(related(10, "Current"), related(20, "No videos"), related(30, "Must not load")))
        var state = playerState(current)
        val requested = mutableListOf<Long>()
        var unavailable = 0
        val coordinator = coordinator({ state }, { state = it(state) }, { id, _ ->
            requested += id
            loaded(id, "Anime $id", emptyList())
        }, { _, _ -> error("must not start") }, { unavailable++ })

        assertTrue(coordinator.onPlaybackCompleted(video(101, 10, episode = "1")))
        assertEquals(listOf(20L), requested)
        assertEquals(1, unavailable)
        assertNull(state.nextTitleSuggestion)
    }

    @Test
    fun duplicateCompletionUsesOneLookupAndWrongConfirmationIsIgnored() {
        val current = details(10, related = listOf(related(10, "Current"), related(20, "Next")))
        var state = playerState(current)
        var calls = 0
        var starts = 0
        val coordinator = coordinator({ state }, { state = it(state) }, { _, _ ->
            calls++
            loaded(20, "Next", listOf(video(201, 20, episode = "1")))
        }, { _, _ -> starts++ })

        coordinator.onPlaybackCompleted(video(101, 10, episode = "1"))
        coordinator.onPlaybackCompleted(video(101, 10, episode = "1"))
        assertEquals(1, calls)
        coordinator.confirm(-1)
        assertEquals(0, starts)
        coordinator.confirm(state.nextTitleSuggestion!!.requestId)
        assertEquals(1, starts)
    }

    @Test
    fun dismissClearsOfferWithoutChangingPlayerOrStartingPlayback() {
        val current = details(10, related = listOf(related(10, "Current"), related(20, "Next")))
        var state = playerState(current)
        val route = state.route
        var starts = 0
        var calls = 0
        val coordinator = coordinator({ state }, { state = it(state) }, { _, _ ->
            calls++
            loaded(20, "Next", listOf(video(201, 20, episode = "1")))
        }, { _, _ -> starts++ })

        coordinator.onPlaybackCompleted(video(101, 10, episode = "1"))
        coordinator.dismiss(state.nextTitleSuggestion!!.requestId)
        assertEquals(route, state.route)
        assertNull(state.nextTitleSuggestion)
        assertEquals(0, starts)
        assertTrue(coordinator.onPlaybackCompleted(video(101, 10, episode = "1")))
        assertEquals(1, calls)
    }

    @Test
    fun incompleteVoiceWithAnotherEpisodeDoesNotFetchNextTitle() {
        val current = details(10, related = listOf(related(10, "Current"), related(20, "Next")))
        val completed = video(101, 10, player = "A", dubbing = "One", episode = "1")
        val later = video(102, 10, player = "B", dubbing = "Two", episode = "2")
        var state = playerState(current, completed, listOf(completed, later))
        var calls = 0
        val coordinator = coordinator({ state }, { state = it(state) }, { _, _ -> calls++; error("must not load") }, { _, _ -> error("must not start") })

        assertFalse(coordinator.onPlaybackCompleted(completed))
        assertEquals(0, calls)
    }

    @Test
    fun offlineModeRejectsNonDownloadableSuccessor() {
        val current = details(10, related = listOf(related(10, "Current"), related(20, "Next")))
        var state = playerState(current).copy(forcedOfflineMode = true)
        var unavailable = 0
        val coordinator = coordinator({ state }, { state = it(state) }, { _, _ -> loaded(20, "Next", listOf(video(201, 20, episode = "1"))) }, { _, _ -> error("must not start") }, { unavailable++ })

        coordinator.onPlaybackCompleted(video(101, 10, episode = "1"))
        assertNull(state.nextTitleSuggestion)
        assertEquals(1, unavailable)
    }

    @Test
    fun preferredVoiceNeverSkipsTheGlobalFirstEpisode() {
        val current = details(10, related = listOf(related(10, "Current"), related(20, "Next")))
        var state = playerState(current)
        var started: VideoVariant? = null
        val nextVideos = listOf(
            video(202, 20, player = "Preferred", dubbing = "Voice", episode = "2"),
            video(203, 20, player = "Preferred", dubbing = "Voice", episode = "3"),
            video(201, 20, player = "Other", dubbing = "Voice", episode = "1"),
            video(204, 20, player = "Other", dubbing = "Voice", episode = "2"),
        )
        val coordinator = coordinator({ state }, { state = it(state) }, { _, _ ->
            LoadedAnimeDetails(details(20, "Next"), nextVideos, false, nextVideos.first().groupKey)
        }, { _, video -> started = video })

        coordinator.onPlaybackCompleted(video(101, 10, episode = "1"))
        coordinator.confirm(state.nextTitleSuggestion!!.requestId)
        assertEquals("1", started?.episode)
    }

    @Test
    fun heldLookupCannotOfferAfterRouteOrContentContextChanges() {
        val current = details(10, related = listOf(related(10, "Current"), related(20, "Next")))
        var state = playerState(current)
        val response = CompletableDeferred<LoadedAnimeDetails>()
        var starts = 0
        val coordinator = coordinator({ state }, { state = it(state) }, { id, _ ->
            if (id == 20L) response.await() else error("current details should be reused")
        }, { _, _ -> starts++ })

        coordinator.onPlaybackCompleted(video(101, 10, episode = "1"))
        state = state.copy(
            route = (state.route as AppRoute.Player).copy(startPositionMs = 1L),
            contentSessionRevision = 1L,
        )
        response.complete(loaded(20, "Next", listOf(video(201, 20, episode = "1"))))
        assertNull(state.nextTitleSuggestion)
        assertEquals(0, starts)
    }

    @Test
    fun heldRealDetailsLoadCannotPersistThePreviousAccountsRating() = runBlocking {
        val current = details(10, related = listOf(related(10, "Current"), related(20, "Next")))
        var state = playerState(current).copy(auth = AuthUiState(profile = UserProfile(1, "A", "")))
        val saved = mutableListOf<Pair<Long, Map<Long, Int?>>>()
        val ratings = AnimeRatingCoordinator(
            readRatings = { userId -> mapOf(20L to if (userId == 1L) 2 else 4) },
            saveRatings = { id, values -> saved += id to values.toMap() },
            setRating = { _, _ -> error("not a rating mutation") },
            deleteRating = { error("not a rating mutation") },
            fetchUserRating = { error("not a rating mutation") },
            ioDispatcher = Dispatchers.Unconfined,
        )
        ratings.restore(1)
        val response = CompletableDeferred<Pair<AnimeDetails, List<VideoVariant>>>()
        var ratingsResolved = 0
        val detailsLoader = AnimeDetailsLoadCoordinator(
            fetchAnimeWithVideos = { RepositoryContent(response.await()) },
            fetchAnimeWithVideosByAlias = { error("not an alias") },
            resolveEffectiveRating = { id, rating, trusted ->
                ratingsResolved++
                ratings.effectiveRating(id, rating, trusted)
            },
            saveAnimeSummary = {},
            ioDispatcher = Dispatchers.Unconfined,
        )
        val coordinator = coordinator({ state }, { state = it(state) }, { id, offline ->
            detailsLoader.loadNextTitleDetails(id, offline) { state }
        }, { _, _ -> error("stale title must not start") })
        coordinator.onPlaybackCompleted(video(101, 10, episode = "1"))
        ratings.restore(2)
        state = state.copy(auth = AuthUiState(profile = UserProfile(2, "B", "")), contentSessionRevision = 1)
        response.complete(details(20, "Next").copy(userRating = 9) to listOf(video(201, 20, episode = "1")))

        assertEquals(1, ratingsResolved)
        assertEquals(mapOf(20L to 4), ratings.snapshot())
        assertTrue(saved.isEmpty())
        assertNull(state.nextTitleSuggestion)
    }

    private fun coordinator(
        current: () -> YummyDroidUiState,
        update: (((YummyDroidUiState) -> YummyDroidUiState) -> Unit),
        load: suspend (Long, Boolean) -> LoadedAnimeDetails,
        start: (LoadedAnimeDetails, VideoVariant) -> Unit,
        unavailable: (Long) -> Unit = {},
    ) = NextTitlePlaybackCoordinator(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined), current, update, load, start, unavailable)

    private fun playerState(details: AnimeDetails, video: VideoVariant = video(101, 10, episode = "1"), videos: List<VideoVariant> = listOf(video)): YummyDroidUiState {
        return YummyDroidUiState(
            route = AppRoute.Player(video, "Current"),
            details = LoadState.Ready(details),
            videos = LoadState.Ready(videos),
            playerStream = LoadState.Ready(ResolvedVideoStream("https://stream.test/current", null, emptyMap())),
        )
    }

    private fun loaded(id: Long, title: String, videos: List<VideoVariant>) = LoadedAnimeDetails(details(id, title), videos, false, null)

    private fun details(id: Long, title: String = "Anime $id", related: List<RelatedAnime> = emptyList()) = AnimeDetails(
        id, title, emptyList(), "", "", null, 2026, null, null, 0, "", "", "", emptyList(), emptyList(), "", 0, 0, "", 0,
        RatingDetails(), emptyList(), emptyList(), "", 0, 0, emptyList(), related, emptyList(), emptyList(),
    )

    private fun related(id: Long, title: String) = RelatedAnime(id, title, "", null, null, "", "", "", id == 10L)

    private fun video(id: Long, animeId: Long, player: String = "Player", dubbing: String = "Voice", episode: String) = VideoVariant(
        id, animeId, player, 0L, dubbing, episode, "https://video.test/$id", id.toInt(), null, 0,
    )
}
