package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val RU_VOICE_LABEL = "\u041e\u0437\u0432\u0443\u0447\u043a\u0430"
private const val RU_SUBTITLES_LABEL = "\u0421\u0443\u0431\u0442\u0438\u0442\u0440\u044b"
private const val RU_YOLKA_LABEL = "\u0401\u043b\u043a\u0430"
private const val RU_YOLKA_KEY = "\u0435\u043b\u043a\u0430"

class VideoMatchingTest {
    @Test
    fun subscriptionVoiceKeyUsesDubbingBeforePlayer() {
        val subscription = VideoSubscription(
            animeId = 1,
            title = "Anime",
            posterUrl = "",
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
        val subscription = VideoSubscription(
            animeId = 7,
            title = "Anime",
            posterUrl = "",
            player = "Alloha",
            dubbing = "AniLibria",
            videoId = 101,
        )

        assertTrue(listOf(subscription).hasSubscriptionForVoice(7, "$RU_VOICE_LABEL AniLibria"))
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
        assertEquals("Voice", video.matchingVoiceTitle)
    }

    @Test
    fun downloadVoiceKeyFallsBackToSourceGroupWhenVoiceIsUnknown() {
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

        assertEquals("", video.matchingVoiceKey)
        assertEquals(video.groupKey.lowercase(), video.downloadPlanVoiceKey)
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
