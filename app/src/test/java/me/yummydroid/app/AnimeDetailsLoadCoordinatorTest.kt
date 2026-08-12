package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.VideoVariant

class AnimeDetailsLoadCoordinatorTest {
    @Test
    fun onlineLoadPublishesContentBeforeIndependentProgressSynchronization() = runBlocking {
        val events = mutableListOf<String>()
        val videos = listOf(
            video(id = 1, player = "Alloha", dubbing = "Voice A"),
            video(id = 2, player = "CVH", dubbing = "Voice B"),
        )
        val coordinator = coordinator(
            fetchAnimeWithVideos = {
                events += "fetch"
                details(userRating = 6) to videos
            },
            isOfflineFallbackActive = {
                events += "offline"
                false
            },
            resolveEffectiveRating = { animeId, remoteRating, trustRemote ->
                events += "rating:$animeId:$remoteRating:$trustRemote"
                9
            },
        )

        val loaded = coordinator.load(animeId = 10) {
            events += "auth"
            true
        }

        assertEquals(
            listOf("fetch", "offline", "auth", "rating:10:6:true"),
            events,
        )
        assertEquals(9, loaded.details.userRating)
        assertEquals("Alloha|Voice A", loaded.selectedVideoGroup)
        assertFalse(loaded.offlineMode)
    }

    @Test
    fun offlineLoadIgnoresUnavailableProgressGroupAndUsesFirstDownloadedGroup() = runBlocking {
        var trustedRemote = true
        val online = video(id = 1, player = "Alloha", dubbing = "Online")
        val downloaded = video(
            id = 2,
            player = "CVH",
            dubbing = "Downloaded",
            localPlaybackUrl = "file:///episode.mp4",
        )
        val coordinator = coordinator(
            fetchAnimeWithVideos = { details() to listOf(online, downloaded) },
            isOfflineFallbackActive = { true },
            resolveEffectiveRating = { _, rating, trustRemote ->
                trustedRemote = trustRemote
                rating
            },
        )

        val loaded = coordinator.load(animeId = 10) { true }

        assertTrue(loaded.offlineMode)
        assertEquals(downloaded.groupKey, loaded.selectedVideoGroup)
        assertFalse(trustedRemote)
    }

    @Test
    fun onlineLoadUsesSiteFirstGroupWhenProgressDoesNotMatch() = runBlocking {
        val first = video(id = 1, player = "Alloha", dubbing = "First")
        val second = video(id = 2, player = "CVH", dubbing = "Second")
        val coordinator = coordinator(
            fetchAnimeWithVideos = { details() to listOf(first, second) },
        )

        val loaded = coordinator.load(animeId = 10) { false }

        assertEquals(first.groupKey, loaded.selectedVideoGroup)
    }

    @Test
    fun cacheWritesTheRatedAnimeSummary() = runBlocking {
        val saved = mutableListOf<Anime>()
        val coordinator = coordinator(saveAnimeSummary = saved::add)

        coordinator.cache(details(userRating = 8))

        assertEquals(1, saved.size)
        assertEquals(10L, saved.single().id)
        assertEquals(8, saved.single().userRating)
    }

    @Test
    fun cancellationFromRepositoryPropagates() = runBlocking {
        val coordinator = coordinator(
            fetchAnimeWithVideos = { throw CancellationException("cancelled") },
        )

        assertFailsWith<CancellationException> {
            coordinator.load(animeId = 10) { false }
        }
        Unit
    }

    private fun coordinator(
        fetchAnimeWithVideos: suspend (Long) -> Pair<AnimeDetails, List<VideoVariant>> = {
            details() to emptyList()
        },
        isOfflineFallbackActive: () -> Boolean = { false },
        resolveEffectiveRating: suspend (Long, Int?, Boolean) -> Int? = { _, rating, _ -> rating },
        saveAnimeSummary: (Anime) -> Unit = {},
        ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ): AnimeDetailsLoadCoordinator {
        return AnimeDetailsLoadCoordinator(
            fetchAnimeWithVideos = fetchAnimeWithVideos,
            isOfflineFallbackActive = isOfflineFallbackActive,
            resolveEffectiveRating = resolveEffectiveRating,
            saveAnimeSummary = saveAnimeSummary,
            ioDispatcher = ioDispatcher,
        )
    }

    private fun details(userRating: Int? = null): AnimeDetails {
        return AnimeDetails(
            id = 10,
            title = "Anime 10",
            otherTitles = emptyList(),
            description = "",
            posterUrl = "poster-10",
            backdropUrl = null,
            year = 2026,
            rating = null,
            userRating = userRating,
            views = 0,
            status = "",
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

    private fun video(
        id: Long,
        player: String,
        dubbing: String,
        localPlaybackUrl: String = "",
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 10,
            player = player,
            dubbing = dubbing,
            episode = "1",
            url = "https://example.test/$id",
            index = id.toInt(),
            durationSeconds = null,
            views = 0,
            localPlaybackUrl = localPlaybackUrl,
        )
    }

}
