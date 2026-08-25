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
    fun matchingVoiceKeyAcceptsCurrentAndLegacyBulletSeparators() {
        assertEquals("anilibriadub", "AniLibria \u2022 Dub".normalizedVoiceKey())
        assertEquals("anilibriadub", "AniLibria \u0432\u0402\u045e Dub".normalizedVoiceKey())
    }

    @Test
    fun subscriptionWithoutDubbingDoesNotUsePlayerAsVoice() {
        val subscription = matchingSubscription(player = "Kodik")

        assertEquals("", subscription.matchingVoiceKey)
    }

    @Test
    fun subscriptionWithoutDubbingCanMatchPlayerId() {
        val subscription = matchingSubscription(playerId = 4, videoId = 0)
        val video = matchingVideoVariant(dubbing = "AniLibria")

        assertTrue(subscription.matchesVideoPlayer(video))
        assertEquals("", subscription.matchingVoiceKey)
    }

    @Test
    fun activeSubscriptionRequiresTheSamePlayerAndDubbing() {
        val subscription = matchingSubscription(
            player = "Alloha",
            playerId = 4,
            dubbing = "AniLibria",
        )

        assertTrue(
            listOf(subscription).isSubscribedTo(
                matchingVideoVariant(player = "Alloha", playerId = 4, dubbing = "AniLibria"),
            ),
        )
        assertFalse(
            listOf(subscription).isSubscribedTo(
                matchingVideoVariant(player = "CVH", playerId = 5, dubbing = "AniLibria"),
            ),
        )
    }
}
