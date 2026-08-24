package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant

class VideoSubscriptionCoordinatorTest {
    @Test
    fun subscriptionLoadingReturnsServerResponseWithoutLocalResolution() = runBlocking {
        val serverSubscription = subscription(animeId = 10, player = "Alloha", playerId = 7)
        val coordinator = coordinator(
            fetchSubscriptions = { listOf(serverSubscription) },
        )

        val subscriptions = coordinator.loadSubscriptions()

        assertEquals(listOf(serverSubscription), subscriptions)
    }

    @Test
    fun mutationUpdatesOneSelectedVideoAndReturnsServerState() = runBlocking {
        val serverSubscriptions = listOf(
            subscription(10, "Alloha", 7).copy(dubbing = "MiraiDUB", videoId = 1),
        )
        val subscribedIds = mutableListOf<Long>()
        val coordinator = coordinator(
            fetchSubscriptions = { serverSubscriptions },
            subscribeVideo = {
                subscribedIds += it
                true
            },
        )

        val subscriptions = coordinator.setSubscription(videoId = 1, subscribed = true)

        assertEquals(listOf(1L), subscribedIds)
        assertEquals(serverSubscriptions, subscriptions)
    }

    @Test
    fun subscriptionRefreshFailureAfterMutationIsPropagated() = runBlocking {
        val subscribedIds = mutableListOf<Long>()
        val coordinator = coordinator(
            fetchSubscriptions = { error("subscription refresh failed") },
            subscribeVideo = {
                subscribedIds += it
                true
            },
        )

        assertFailsWith<IllegalStateException> {
            coordinator.setSubscription(videoId = 1, subscribed = true)
        }

        assertEquals(listOf(1L), subscribedIds)
    }

    @Test
    fun removalFailureIsPropagatedWithoutPublishingLocalState() = runBlocking {
        val coordinator = coordinator(unsubscribeVideo = { error("captcha required") })

        assertFailsWith<IllegalStateException> {
            coordinator.setSubscription(videoId = 1, subscribed = false)
        }
        Unit
    }

    @Test
    fun subscriptionLoadingDoesNotPerformAutomaticUnsubscribe() = runBlocking {
        val removedVideoIds = mutableListOf<Long>()
        val serverSubscriptions = listOf(
            subscription(10, "Alloha", 7).copy(dubbing = "MiraiDUB", videoId = 1),
            subscription(11, "CVH", 8).copy(dubbing = "AniDUB", videoId = 2),
        )
        val coordinator = coordinator(
            fetchSubscriptions = { serverSubscriptions },
            unsubscribeVideo = {
                removedVideoIds += it
                true
            },
        )

        val subscriptions = coordinator.loadSubscriptions()

        assertEquals(emptyList(), removedVideoIds)
        assertEquals(serverSubscriptions, subscriptions)
    }

    @Test
    fun removalResolvesMissingServerVideoIdFromCurrentAnimeVideos() = runBlocking {
        val fetchedAnimeIds = mutableListOf<Long>()
        val unsubscribedIds = mutableListOf<Long>()
        val coordinator = coordinator(
            fetchSubscriptions = { emptyList() },
            fetchVideos = { animeId ->
                fetchedAnimeIds += animeId
                listOf(video(id = 42, player = "Alloha", playerId = 7, voice = "Voice"))
            },
            unsubscribeVideo = { videoId ->
                unsubscribedIds += videoId
                true
            },
        )

        val subscriptions = coordinator.removeSubscription(
            subscription = subscription(10, "Alloha", 7).copy(dubbing = "Voice"),
            fallbackVideos = emptyList(),
        )

        assertEquals(listOf(10L), fetchedAnimeIds)
        assertEquals(listOf(42L), unsubscribedIds)
        assertEquals(emptyList(), subscriptions)
    }

    @Test
    fun removalUsesAlreadyLoadedAnimeVideosWithoutAnotherRequest() = runBlocking {
        val fetchedAnimeIds = mutableListOf<Long>()
        val unsubscribedIds = mutableListOf<Long>()
        val coordinator = coordinator(
            fetchVideos = { animeId ->
                fetchedAnimeIds += animeId
                emptyList()
            },
            unsubscribeVideo = { videoId ->
                unsubscribedIds += videoId
                true
            },
        )

        coordinator.removeSubscription(
            subscription = subscription(10, "Alloha", 7).copy(dubbing = "Voice"),
            fallbackVideos = listOf(video(id = 41, player = "Alloha", playerId = 7, voice = "Voice")),
        )

        assertEquals(emptyList(), fetchedAnimeIds)
        assertEquals(listOf(41L), unsubscribedIds)
    }

    @Test
    fun removalDoesNotGuessFromAnotherSourceOrVoice() = runBlocking {
        val unsubscribedIds = mutableListOf<Long>()
        val coordinator = coordinator(
            fetchVideos = {
                listOf(video(id = 50, player = "CVH", playerId = 8, voice = "Other"))
            },
            unsubscribeVideo = { videoId ->
                unsubscribedIds += videoId
                true
            },
        )

        assertFailsWith<IllegalStateException> {
            coordinator.removeSubscription(
                subscription = subscription(10, "Alloha", 7).copy(dubbing = "Voice"),
                fallbackVideos = emptyList(),
            )
        }

        assertEquals(emptyList(), unsubscribedIds)
    }

    @Test
    fun cancellationFromSubscriptionRequestIsPropagated() {
        val coordinator = coordinator(
            fetchSubscriptions = { throw CancellationException("cancelled") },
        )

        assertFailsWith<CancellationException> {
            runBlocking { coordinator.loadSubscriptions() }
        }
    }

    private fun coordinator(
        fetchSubscriptions: suspend () -> List<VideoSubscription> = { emptyList() },
        fetchVideos: suspend (Long) -> List<VideoVariant> = { emptyList() },
        subscribeVideo: suspend (Long) -> Boolean = { true },
        unsubscribeVideo: suspend (Long) -> Boolean = { true },
    ): VideoSubscriptionCoordinator {
        return VideoSubscriptionCoordinator(
            fetchSubscriptions = fetchSubscriptions,
            fetchVideos = fetchVideos,
            subscribeVideo = subscribeVideo,
            unsubscribeVideo = unsubscribeVideo,
        )
    }

    private fun subscription(animeId: Long, player: String, playerId: Long): VideoSubscription {
        return VideoSubscription(
            animeId = animeId,
            title = "Anime $animeId",
            posterUrl = "poster-$animeId.jpg",
            player = player,
            dubbing = "",
            playerId = playerId,
        )
    }

    private fun video(
        id: Long,
        player: String,
        playerId: Long,
        voice: String,
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 10,
            player = player,
            playerId = playerId,
            dubbing = voice,
            episode = "1",
            url = "https://video.test/$id",
            index = 1,
            durationSeconds = null,
            views = 0,
        )
    }

}
