package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
