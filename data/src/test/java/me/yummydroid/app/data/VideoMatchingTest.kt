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
}
