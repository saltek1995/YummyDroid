package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoSubscriptionMatchingTest {
    @Test
    fun subscriptionVoiceKeyUsesDubbingBeforePlayer() {
        val subscription = matchingSubscription(
            animeId = 1,
            player = "Kodik",
            dubbing = "$RU_VOICE_LABEL AniLibria",
            videoId = 42,
        )

        assertEquals("anilibria", subscription.matchingVoiceKey)
    }

    @Test
    fun matchingVoiceKeyNormalizesRussianPrefixesAndYo() {
        assertEquals(RU_YOLKA_KEY, "$RU_VOICE_LABEL $RU_YOLKA_LABEL".normalizedVoiceKey())
        assertEquals("crunchyroll", "$RU_SUBTITLES_LABEL Crunchyroll".normalizedVoiceKey())
    }

    @Test
    fun activeSubscriptionMatchesWholeVoice() {
        val subscription = matchingSubscription(player = "Alloha", dubbing = "AniLibria")

        assertTrue(listOf(subscription).hasSubscriptionForVoice(7, "$RU_VOICE_LABEL AniLibria"))
    }

    @Test
    fun subscriptionWithoutDubbingDoesNotUsePlayerAsVoice() {
        val subscription = matchingSubscription(player = "Kodik")

        assertEquals("", subscription.matchingVoiceKey)
        assertFalse(listOf(subscription).hasSubscriptionForVoice(7, "Kodik"))
    }

    @Test
    fun subscriptionWithoutDubbingCanMatchPlayerId() {
        val subscription = matchingSubscription(playerId = 4, videoId = 0)
        val video = matchingVideoVariant(dubbing = "AniLibria")

        assertTrue(subscription.matchesVideoPlayer(video))
        assertEquals("", subscription.matchingVoiceKey)
    }
}
