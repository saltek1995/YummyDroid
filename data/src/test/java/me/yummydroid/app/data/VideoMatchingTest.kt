package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoMatchingTest {
    @Test
    fun subscriptionVoiceKeyUsesDubbingBeforePlayer() {
        val subscription = VideoSubscription(
            animeId = 1,
            title = "Anime",
            posterUrl = "",
            player = "Kodik",
            dubbing = "Озвучка AniLibria",
            videoId = 42,
        )

        assertEquals("anilibria", subscription.matchingVoiceKey)
    }

    @Test
    fun matchingVoiceKeyNormalizesRussianPrefixesAndYo() {
        assertEquals("елка", "Озвучка Ёлка".normalizedVoiceKey())
        assertEquals("crunchyroll", "Субтитры Crunchyroll".normalizedVoiceKey())
    }

    @Test
    fun activeSubscriptionMatchesWholeVoice() {
        val subscription = VideoSubscription(
            animeId = 7,
            title = "Anime",
            posterUrl = "",
            player = "Alloha",
            dubbing = "AniLibria",
            videoId = 101,
        )

        assertTrue(listOf(subscription).hasSubscriptionForVoice(7, "Озвучка AniLibria"))
    }

    @Test
    fun playerNameIsNotExposedAsVoiceTitle() {
        val video = VideoVariant(
            id = 101,
            animeId = 7,
            player = "Alloha",
            playerId = 4,
            dubbing = "Alloha",
            episode = "1",
            url = "",
            index = 1,
            durationSeconds = null,
            views = 0,
        )

        assertEquals("", video.matchingDubbingTitle)
        assertEquals("", video.matchingVoiceKey)
        assertEquals("Озвучка", video.matchingVoiceTitle)
    }

    @Test
    fun realVoiceTitleIsKeptWhenPlayerIsAlloha() {
        val video = VideoVariant(
            id = 101,
            animeId = 7,
            player = "Alloha",
            playerId = 4,
            dubbing = "AniDUB",
            episode = "1",
            url = "",
            index = 1,
            durationSeconds = null,
            views = 0,
        )

        assertEquals("AniDUB", video.matchingDubbingTitle)
        assertEquals("anidub", video.matchingVoiceKey)
    }

    @Test
    fun subscriptionWithoutDubbingDoesNotUsePlayerAsVoice() {
        val subscription = VideoSubscription(
            animeId = 7,
            title = "Anime",
            posterUrl = "",
            player = "Kodik",
            dubbing = "",
            videoId = 101,
        )

        assertEquals("", subscription.matchingVoiceKey)
        assertFalse(listOf(subscription).hasSubscriptionForVoice(7, "Kodik"))
    }

    @Test
    fun subscriptionWithoutDubbingCanMatchPlayerId() {
        val subscription = VideoSubscription(
            animeId = 7,
            title = "Anime",
            posterUrl = "",
            player = "Kodik",
            dubbing = "",
            playerId = 4,
            videoId = 0,
        )
        val video = VideoVariant(
            id = 101,
            animeId = 7,
            player = "Alloha",
            playerId = 4,
            dubbing = "AniLibria",
            episode = "1",
            url = "",
            index = 1,
            durationSeconds = null,
            views = 0,
        )

        assertTrue(subscription.matchesVideoPlayer(video))
        assertEquals("", subscription.matchingVoiceKey)
    }

    @Test
    fun declaredEpisodeLimitRemovesIntegerEpisodesOutsideCurrentSeason() {
        val cvh = (1..40).map { episode ->
            video(
                id = 1_000L + episode,
                player = "CVH",
                dubbing = "AniLibria",
                episode = episode.toString(),
            )
        }
        val kodik = (1..12).map { episode ->
            video(
                id = 2_000L + episode,
                player = "Kodik",
                dubbing = "AniLibria",
                episode = episode.toString(),
            )
        }

        val normalized = (cvh + kodik).limitedToDeclaredEpisodes(
            episodeAired = 12,
            episodeCount = 12,
        )

        assertEquals(24, normalized.size)
        assertEquals((1..12).map(Int::toString), normalized.filter { it.player == "CVH" }.map { it.episode })
        assertFalse(normalized.any { it.episode.toIntOrNull()?.let { episode -> episode > 12 } == true })
    }

    @Test
    fun declaredEpisodeLimitUsesAiredCountForOngoingAnime() {
        val videos = (1..24).map { episode ->
            video(
                id = episode.toLong(),
                player = "Kodik",
                dubbing = "AniLibria",
                episode = episode.toString(),
            )
        }

        val normalized = videos.limitedToDeclaredEpisodes(
            episodeAired = 5,
            episodeCount = 24,
        )

        assertEquals((1..5).map(Int::toString), normalized.map { it.episode })
    }

    @Test
    fun declaredEpisodeLimitKeepsNonIntegerEpisodeLabels() {
        val videos = listOf(
            video(id = 1, player = "Kodik", dubbing = "AniLibria", episode = "12"),
            video(id = 2, player = "Kodik", dubbing = "AniLibria", episode = "13"),
            video(id = 3, player = "Kodik", dubbing = "AniLibria", episode = "12.5"),
            video(id = 4, player = "Kodik", dubbing = "AniLibria", episode = "OVA"),
        )

        val normalized = videos.limitedToDeclaredEpisodes(
            episodeAired = 12,
            episodeCount = 12,
        )

        assertEquals(listOf("12", "12.5", "OVA"), normalized.map { it.episode })
    }

    private fun video(
        id: Long,
        player: String,
        dubbing: String,
        episode: String,
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 10669,
            player = player,
            playerId = 0,
            dubbing = dubbing,
            episode = episode,
            url = "",
            index = episode.toIntOrNull() ?: id.toInt(),
            durationSeconds = 1_420,
            views = 0,
        )
    }
}
