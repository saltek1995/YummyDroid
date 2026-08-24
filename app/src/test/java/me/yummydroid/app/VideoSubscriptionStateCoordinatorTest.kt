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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.CaptchaRequiredException
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.UserProfile
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant

class VideoSubscriptionStateCoordinatorTest {
    @Test
    fun offlineSynchronizationPublishesEmptyReadyStateWithoutRequest() {
        var requests = 0
        val harness = harness(
            initialState = detailsState().copy(
                forcedOfflineMode = true,
                globalSubscriptions = LoadState.Ready(listOf(subscription(1))),
            ),
            fetchSubscriptions = {
                requests += 1
                emptyList()
            },
        )

        harness.coordinator.synchronize()

        assertEquals(0, requests)
        assertEquals(emptyList(), harness.state.globalSubscriptions.readyListOrEmpty())
        harness.close()
    }

    @Test
    fun synchronizationPublishesLoadingThenExactGlobalAndDetailsState() {
        val resolved = subscription(videoId = 1)
        val harness = harness(
            initialState = detailsState(),
            fetchSubscriptions = { listOf(resolved) },
        )

        harness.coordinator.synchronize()

        assertIs<LoadState.Loading>(harness.states.first().globalSubscriptions)
        assertEquals(listOf(resolved), harness.state.globalSubscriptions.readyListOrEmpty())
        assertEquals(1L, harness.state.detailsExtras.readyDataOrNull()?.subscriptions?.single()?.videoId)
        assertEquals(1, harness.cacheCurrentCalls)
        harness.close()
    }

    @Test
    fun synchronizationKeepsServerEntryWithoutLocalResolution() {
        val resolved = subscription(videoId = 0, voice = "MiraiDUB")
        val harness = harness(
            initialState = detailsState(),
            fetchSubscriptions = { listOf(resolved) },
        )

        harness.coordinator.synchronize()

        assertEquals(listOf(resolved), harness.state.globalSubscriptions.readyListOrEmpty())
        harness.close()
    }

    @Test
    fun newerSynchronizationCancelsOlderRequest() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var request = 0
        val harness = harness(
            initialState = detailsState(),
            fetchSubscriptions = {
                request += 1
                if (request == 1) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                    listOf(subscription(videoId = 1))
                } else {
                    listOf(subscription(videoId = 2))
                }
            },
        )

        harness.coordinator.synchronize()
        firstStarted.await()
        harness.coordinator.synchronize()
        releaseFirst.complete(Unit)
        yield()

        assertEquals(listOf(2L), harness.state.globalSubscriptions.readyListOrEmpty().map { it.videoId })
        harness.close()
    }

    @Test
    fun toggleMutatesSelectedVideoAndPublishesServerState() {
        val targetVideos = listOf(
            video(id = 1, player = "Alloha", playerId = 7),
            video(id = 2, player = "CVH", playerId = 8),
        )
        val subscribedIds = mutableListOf<Long>()
        val harness = harness(
            initialState = detailsState(videos = targetVideos),
            fetchSubscriptions = {
                listOf(subscription(videoId = 1))
            },
            subscribeVideo = { id ->
                subscribedIds += id
                true
            },
        )

        harness.coordinator.toggle(targetVideos.first(), showNotice = true)

        assertEquals(listOf(1L), subscribedIds)
        assertEquals(listOf(1L), harness.state.globalSubscriptions.readyListOrEmpty().map { it.videoId })
        assertEquals(1, harness.state.detailsExtras.readyDataOrNull()?.subscriptions?.size)
        assertEquals(listOf(true), harness.notices)
        assertEquals(listOf(10L), harness.cachedAnimeIds)
        harness.close()
    }

    @Test
    fun toggleUnsubscribesUsingCurrentSelectedVideoIdWhenServerOmitsIt() {
        val selectedVideo = video(id = 1)
        val serverSubscription = subscription(videoId = 0)
        val unsubscribedIds = mutableListOf<Long>()
        val harness = harness(
            initialState = detailsState(
                videos = listOf(selectedVideo),
                detailsSubscriptions = listOf(serverSubscription),
            ),
            fetchSubscriptions = { emptyList() },
            unsubscribeVideo = { videoId ->
                unsubscribedIds += videoId
                true
            },
        )

        harness.coordinator.toggle(selectedVideo, showNotice = true)

        assertEquals(listOf(1L), unsubscribedIds)
        assertEquals(emptyList(), harness.state.globalSubscriptions.readyListOrEmpty())
        assertEquals(listOf(false), harness.notices)
        harness.close()
    }

    @Test
    fun captchaFailureKeepsServerStateAndPublishesRetry() {
        val existing = subscription(videoId = 9, voice = "Existing")
        val target = video(1)
        val harness = harness(
            initialState = detailsState(
                videos = listOf(target),
                detailsSubscriptions = listOf(existing),
            ),
            subscribeVideo = { throw CaptchaRequiredException("captcha") },
        )

        harness.coordinator.toggle(target, showNotice = true)

        assertEquals(listOf(existing), harness.state.detailsExtras.readyDataOrNull()?.subscriptions)
        assertIs<CaptchaRequiredException>(harness.captchaThrowable)
        assertNotNull(harness.captchaRetry)
        assertTrue(harness.states.none { state ->
            state.detailsExtras.readyDataOrNull()?.subscriptions?.any { it.videoId == target.id } == true
        })
        harness.close()
    }

    @Test
    fun unsubscribeFailureReloadsServerStateAndPublishesLocalError() {
        val removed = subscription(videoId = 1)
        val retained = subscription(videoId = 2, voice = "Other")
        val harness = harness(
            initialState = detailsState(
                videos = listOf(video(1), video(2, voice = "Other")),
                detailsSubscriptions = listOf(removed, retained),
            ).copy(globalSubscriptions = LoadState.Ready(listOf(removed, retained))),
            fetchSubscriptions = { listOf(removed, retained) },
            unsubscribeVideo = { error("unsubscribe failed") },
        )

        harness.coordinator.unsubscribe(removed)

        assertEquals(setOf(1L, 2L), harness.state.globalSubscriptions.readyListOrEmpty().map { it.videoId }.toSet())
        assertEquals(listOf("unsubscribe failed"), harness.errorNotices)
        assertNull(harness.state.auth.error)
        assertTrue(harness.states.any { state ->
            state.globalSubscriptions.readyListOrEmpty().none { it.videoId == removed.videoId }
        })
        harness.close()
    }

    @Test
    fun profileUnsubscribeResolvesMissingVideoIdWithOneTargetedAnimeRequest() {
        val removed = subscription(videoId = 0)
        val fetchedAnimeIds = mutableListOf<Long>()
        val unsubscribedIds = mutableListOf<Long>()
        val harness = harness(
            initialState = detailsState(
                videos = emptyList(),
                detailsSubscriptions = listOf(removed),
            ),
            fetchSubscriptions = { emptyList() },
            fetchVideos = { animeId ->
                fetchedAnimeIds += animeId
                listOf(video(id = 31))
            },
            unsubscribeVideo = { videoId ->
                unsubscribedIds += videoId
                true
            },
        )

        harness.coordinator.unsubscribe(removed)

        assertEquals(listOf(10L), fetchedAnimeIds)
        assertEquals(listOf(31L), unsubscribedIds)
        assertEquals(emptyList(), harness.state.globalSubscriptions.readyListOrEmpty())
        harness.close()
    }

    @Test
    fun clearCancelsInFlightMutationBeforeAnotherAccountCanBePopulated() = runBlocking {
        val mutationStarted = CompletableDeferred<Unit>()
        val releaseMutation = CompletableDeferred<Unit>()
        val target = video(1)
        val harness = harness(
            initialState = detailsState(videos = listOf(target)),
            subscribeVideo = {
                mutationStarted.complete(Unit)
                releaseMutation.await()
                true
            },
        )

        harness.coordinator.toggle(target, showNotice = false)
        mutationStarted.await()
        harness.coordinator.clear()
        harness.state = harness.state.copy(
            auth = AuthUiState(),
            globalSubscriptions = LoadState.Ready(emptyList()),
            detailsExtras = LoadState.Ready(AnimeDetailsExtras()),
        )
        releaseMutation.complete(Unit)
        yield()

        assertEquals(emptyList(), harness.state.globalSubscriptions.readyListOrEmpty())
        assertEquals(emptyList(), harness.state.detailsExtras.readyDataOrNull()?.subscriptions)
        harness.close()
    }

    private fun harness(
        initialState: YummyDroidUiState,
        fetchSubscriptions: suspend () -> List<VideoSubscription> = { emptyList() },
        fetchVideos: suspend (Long) -> List<VideoVariant> = { emptyList() },
        subscribeVideo: suspend (Long) -> Boolean = { true },
        unsubscribeVideo: suspend (Long) -> Boolean = { true },
    ): Harness {
        return Harness(
            initialState = initialState,
            fetchSubscriptions = fetchSubscriptions,
            fetchVideos = fetchVideos,
            subscribeVideo = subscribeVideo,
            unsubscribeVideo = unsubscribeVideo,
        )
    }

    private class Harness(
        initialState: YummyDroidUiState,
        fetchSubscriptions: suspend () -> List<VideoSubscription>,
        fetchVideos: suspend (Long) -> List<VideoVariant>,
        subscribeVideo: suspend (Long) -> Boolean,
        unsubscribeVideo: suspend (Long) -> Boolean,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var state = initialState
        val states = mutableListOf<YummyDroidUiState>()
        val cachedAnimeIds = mutableListOf<Long>()
        val notices = mutableListOf<Boolean>()
        val errorNotices = mutableListOf<String>()
        var cacheCurrentCalls = 0
        var captchaThrowable: Throwable? = null
        var captchaRetry: (suspend () -> Unit)? = null

        private val backend = VideoSubscriptionCoordinator(
            fetchSubscriptions = fetchSubscriptions,
            fetchVideos = fetchVideos,
            subscribeVideo = subscribeVideo,
            unsubscribeVideo = unsubscribeVideo,
        )
        val coordinator = VideoSubscriptionStateCoordinator(
            scope = scope,
            subscriptions = backend,
            currentState = { state },
            updateState = { transform ->
                state = transform(state)
                states += state
            },
            requestCaptchaRetry = { throwable, retry ->
                captchaThrowable = throwable
                captchaRetry = retry
                true
            },
            cacheDetailsRouteState = cachedAnimeIds::add,
            cacheCurrentDetailsRouteState = { cacheCurrentCalls += 1 },
            showToggleNotice = notices::add,
            showErrorNotice = errorNotices::add,
        )

        fun close() {
            scope.cancel()
        }
    }

    private companion object {
        fun detailsState(
            videos: List<VideoVariant> = listOf(video(1)),
            detailsSubscriptions: List<VideoSubscription> = emptyList(),
        ): YummyDroidUiState {
            return YummyDroidUiState(
                route = AppRoute.Details(10),
                auth = AuthUiState(profile = UserProfile(id = 42, nickname = "User", avatarUrl = "")),
                details = LoadState.Ready(details()),
                detailsExtras = LoadState.Ready(AnimeDetailsExtras(subscriptions = detailsSubscriptions)),
                videos = LoadState.Ready(videos),
                globalSubscriptions = LoadState.Ready(detailsSubscriptions),
            )
        }

        fun subscription(
            videoId: Long,
            voice: String = "Voice",
        ): VideoSubscription {
            return VideoSubscription(
                animeId = 10,
                title = "Anime 10",
                posterUrl = "poster.jpg",
                player = if (videoId == 2L) "CVH" else "Alloha",
                dubbing = voice,
                playerId = videoId + 6,
                videoId = videoId,
            )
        }

        fun video(
            id: Long,
            player: String = "Alloha",
            playerId: Long = id + 6,
            voice: String = "Voice",
        ): VideoVariant {
            return VideoVariant(
                id = id,
                animeId = 10,
                player = player,
                playerId = playerId,
                dubbing = voice,
                episode = "1",
                url = "https://video.test/$id",
                index = id.toInt(),
                durationSeconds = null,
                views = 0,
            )
        }

        fun details(): AnimeDetails {
            return AnimeDetails(
                id = 10,
                title = "Anime 10",
                otherTitles = emptyList(),
                description = "",
                posterUrl = "poster.jpg",
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
            )
        }
    }
}
