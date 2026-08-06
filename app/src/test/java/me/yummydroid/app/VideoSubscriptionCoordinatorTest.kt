package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoSubscriptionHint
import me.yummydroid.app.data.VideoVariant

class VideoSubscriptionCoordinatorTest {
    @Test
    fun restoredHintsAreAvailableBeforeSubscriptionResolutionStarts() = runBlocking {
        val events = mutableListOf<String>()
        val restoredHint = hint(animeId = 10, playerId = 7, voiceKey = "miraidub")
        val coordinator = coordinator(
            readHints = {
                events += "read hints"
                listOf(restoredHint)
            },
            fetchSubscriptions = {
                events += "fetch subscriptions"
                listOf(subscription(animeId = 10, player = "Alloha", playerId = 7))
            },
        )

        coordinator.restoreHints(userId = 42)
        val subscriptions = coordinator.loadResolvedSubscriptions()

        assertEquals(listOf("read hints", "fetch subscriptions"), events)
        assertEquals("MiraiDUB", subscriptions.single().dubbing)
    }

    @Test
    fun voiceResolutionFetchesVideosOncePerAnime() = runBlocking {
        var videoFetches = 0
        val videos = listOf(
            video(id = 1, animeId = 10, player = "Alloha", playerId = 7, dubbing = "MiraiDUB"),
            video(id = 2, animeId = 10, player = "CVH", playerId = 9, dubbing = "AniDUB"),
        )
        val coordinator = coordinator(
            fetchSubscriptions = {
                listOf(
                    subscription(10, "Alloha", 7).copy(videoId = 1),
                    subscription(10, "CVH", 9).copy(videoId = 2),
                )
            },
            fetchVideos = {
                videoFetches += 1
                videos
            },
        )

        val subscriptions = coordinator.loadResolvedSubscriptions()

        assertEquals(1, videoFetches)
        assertEquals(listOf("MiraiDUB", "AniDUB"), subscriptions.map(VideoSubscription::dubbing))
    }

    @Test
    fun voiceMutationUpdatesEveryProviderAndPersistsHints() = runBlocking {
        val videos = listOf(
            video(id = 1, animeId = 10, player = "Alloha", playerId = 7, dubbing = "MiraiDUB"),
            video(id = 2, animeId = 10, player = "CVH", playerId = 9, dubbing = "MiraiDUB"),
        )
        val subscribedIds = mutableListOf<Long>()
        val savedHints = mutableListOf<List<VideoSubscriptionHint>>()
        val coordinator = coordinator(
            saveHints = { _, hints -> savedHints += hints },
            fetchSubscriptions = {
                listOf(subscription(10, "Alloha", 7).copy(videoId = 1))
            },
            fetchVideos = { videos.map { it.copy(subscribed = true) } },
            subscribeVideo = {
                subscribedIds += it
                true
            },
        )

        val subscriptions = coordinator.setVoiceSubscription(
            videos = videos,
            subscribed = true,
            title = "Anime",
            posterUrl = "poster.jpg",
            userId = 42,
        )

        assertEquals(listOf(1L, 2L), subscribedIds)
        assertEquals(setOf(1L, 2L), subscriptions.map(VideoSubscription::videoId).toSet())
        assertEquals(2, coordinator.hintSnapshot().size)
        assertEquals(coordinator.hintSnapshot(), savedHints.last())
    }

    @Test
    fun failedVoiceMutationRestoresPreviousHints() = runBlocking {
        val originalHint = hint(animeId = 11, playerId = 5, voiceKey = "anidub")
        val savedHints = mutableListOf<List<VideoSubscriptionHint>>()
        val coordinator = coordinator(
            readHints = { listOf(originalHint) },
            saveHints = { _, hints -> savedHints += hints },
            fetchSubscriptions = { error("subscription refresh failed") },
            subscribeVideo = { true },
        )
        coordinator.restoreHints(42)

        assertFailsWith<IllegalStateException> {
            coordinator.setVoiceSubscription(
                videos = listOf(video(1, 10, "Alloha", 7, "MiraiDUB")),
                subscribed = true,
                title = "Anime",
                posterUrl = "poster.jpg",
                userId = 42,
            )
        }

        assertEquals(listOf(originalHint), coordinator.hintSnapshot())
        assertEquals(listOf(originalHint), savedHints.last())
    }

    @Test
    fun failedRemovalRestoresStagedHints() = runBlocking {
        val originalHint = hint(animeId = 10, playerId = 7, voiceKey = "miraidub")
        val savedHints = mutableListOf<List<VideoSubscriptionHint>>()
        val current = subscription(10, "Alloha", 7).copy(dubbing = "MiraiDUB", videoId = 1)
        val coordinator = coordinator(
            readHints = { listOf(originalHint) },
            saveHints = { _, hints -> savedHints += hints },
            unsubscribeVideo = { error("captcha required") },
        )
        coordinator.restoreHints(42)
        val staged = coordinator.stageRemoval(current.unsubscribeTarget(listOf(current))!!)
        assertTrue(coordinator.hintSnapshot().isEmpty())

        assertFailsWith<IllegalStateException> {
            coordinator.removeSubscription(
                staged = staged,
                fallbackVideos = listOf(video(1, 10, "Alloha", 7, "MiraiDUB")),
                userId = 42,
            )
        }

        assertEquals(listOf(originalHint), coordinator.hintSnapshot())
        assertEquals(listOf(originalHint), savedHints.last())
    }

    @Test
    fun synchronizationRemovesOnlyCompletedAnimeSubscriptions() = runBlocking {
        val completedHint = hint(animeId = 10, playerId = 7, voiceKey = "miraidub")
        val ongoingHint = hint(animeId = 11, playerId = 8, voiceKey = "anidub")
        val removedVideoIds = mutableListOf<Long>()
        val savedHints = mutableListOf<List<VideoSubscriptionHint>>()
        val coordinator = coordinator(
            readHints = { listOf(completedHint, ongoingHint) },
            saveHints = { _, hints -> savedHints += hints },
            fetchSubscriptions = {
                listOf(
                    subscription(10, "Alloha", 7).copy(dubbing = "MiraiDUB", videoId = 1),
                    subscription(11, "CVH", 8).copy(dubbing = "AniDUB", videoId = 2),
                )
            },
            fetchVideos = { animeId ->
                when (animeId) {
                    10L -> listOf(video(1, 10, "Alloha", 7, "MiraiDUB"))
                    else -> listOf(video(2, 11, "CVH", 8, "AniDUB"))
                }
            },
            fetchAnime = { animeId -> animeDetails(animeId, if (animeId == 10L) "released" else "ongoing") },
            unsubscribeVideo = {
                removedVideoIds += it
                true
            },
        )
        coordinator.restoreHints(42)

        val subscriptions = coordinator.synchronize(userId = 42)

        assertEquals(listOf(1L), removedVideoIds)
        assertEquals(listOf(11L), subscriptions.map(VideoSubscription::animeId))
        assertEquals(listOf(ongoingHint), coordinator.hintSnapshot())
        assertEquals(listOf(ongoingHint), savedHints.last())
    }

    @Test
    fun cancellationFromProviderResolutionIsPropagated() {
        val coordinator = coordinator(
            fetchSubscriptions = { listOf(subscription(10, "Alloha", 7)) },
            fetchVideos = { throw CancellationException("cancelled") },
        )

        assertFailsWith<CancellationException> {
            runBlocking { coordinator.loadResolvedSubscriptions() }
        }
    }

    private fun coordinator(
        readHints: (Long) -> List<VideoSubscriptionHint> = { emptyList() },
        saveHints: (Long, List<VideoSubscriptionHint>) -> Unit = { _, _ -> },
        fetchSubscriptions: suspend () -> List<VideoSubscription> = { emptyList() },
        fetchVideos: suspend (Long) -> List<VideoVariant> = { emptyList() },
        fetchAnime: suspend (Long) -> AnimeDetails = { animeDetails(it, "ongoing") },
        subscribeVideo: suspend (Long) -> Boolean = { true },
        unsubscribeVideo: suspend (Long) -> Boolean = { true },
    ): VideoSubscriptionCoordinator {
        return VideoSubscriptionCoordinator(
            readHints = readHints,
            saveHints = saveHints,
            fetchSubscriptions = fetchSubscriptions,
            fetchVideos = fetchVideos,
            fetchAnime = fetchAnime,
            subscribeVideo = subscribeVideo,
            unsubscribeVideo = unsubscribeVideo,
            ioDispatcher = Dispatchers.Unconfined,
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

    private fun hint(animeId: Long, playerId: Long, voiceKey: String): VideoSubscriptionHint {
        return VideoSubscriptionHint(
            animeId = animeId,
            playerId = playerId,
            playerKey = "player-$playerId",
            voiceKey = voiceKey,
            voiceTitle = when (voiceKey) {
                "miraidub" -> "MiraiDUB"
                else -> "AniDUB"
            },
            title = "Anime $animeId",
            posterUrl = "poster-$animeId.jpg",
        )
    }

    private fun video(
        id: Long,
        animeId: Long,
        player: String,
        playerId: Long,
        dubbing: String,
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = animeId,
            player = player,
            playerId = playerId,
            dubbing = dubbing,
            episode = "1",
            url = "https://example.test/$id",
            index = id.toInt(),
            durationSeconds = null,
            views = 0,
        )
    }

    private fun animeDetails(id: Long, status: String): AnimeDetails {
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
