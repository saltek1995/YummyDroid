package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingEpisodeKey

class PlaybackSourceSelectionTest {
    @Test
    fun higherEstimatedSourceQualityWinsOverSiteOrderWithoutManualChoice() {
        val kodik = sourceVideo(
            id = 593472,
            player = "Kodik",
            index = 30,
            url = "https://kodikplayer.com/season/95032/hash/720p?episode=5",
        )
        val cvh = sourceVideo(
            id = 843499,
            player = "CVH",
            index = 511,
            url = "https://ru.yummyani.me/iframeCVH.html?dubbing_code=AniLibria&anime_id=51215&episode=5",
        )

        val ordered = listOf(kodik, cvh).sortedForPlaybackSource(
            requested = kodik,
            manualSourceKey = null,
        )

        assertEquals(cvh.id, ordered.first().id)
    }

    @Test
    fun manualSourceWinsOverHigherEstimatedSourceQuality() {
        val kodik = sourceVideo(
            id = 593472,
            player = "Kodik",
            index = 30,
            url = "https://kodikplayer.com/season/95032/hash/720p?episode=5",
        )
        val cvh = sourceVideo(
            id = 843499,
            player = "CVH",
            index = 511,
            url = "https://ru.yummyani.me/iframeCVH.html?dubbing_code=AniLibria&anime_id=51215&episode=5",
        )

        val ordered = listOf(cvh, kodik).sortedForPlaybackSource(
            requested = cvh,
            manualSourceKey = kodik.sourceSelectionKey,
            cachedSourceKey = cvh.sourceSelectionKey,
        )

        assertEquals(kodik.id, ordered.first().id)
    }

    @Test
    fun manualSourceIgnoresBufferingTimeoutFallback() {
        val cvh = sourceVideo(
            id = 843499,
            player = "CVH",
            index = 511,
            url = "https://ru.yummyani.me/iframeCVH.html?dubbing_code=AniLibria&anime_id=51215&episode=5",
        )

        assertFalse(
            shouldUseAutomaticPlaybackFallback(
                currentVideo = cvh,
                failedVideo = cvh,
                manualSourceKey = cvh.sourceSelectionKey,
                failure = PlaybackFailure(PlaybackFailureKind.BufferingTimeout),
            ),
        )
    }

    @Test
    fun manualSourceAllowsPlayerErrorFallback() {
        val cvh = sourceVideo(
            id = 843499,
            player = "CVH",
            index = 511,
            url = "https://ru.yummyani.me/iframeCVH.html?dubbing_code=AniLibria&anime_id=51215&episode=5",
        )

        assertTrue(
            shouldUseAutomaticPlaybackFallback(
                currentVideo = cvh,
                failedVideo = cvh,
                manualSourceKey = cvh.sourceSelectionKey,
                failure = PlaybackFailure(PlaybackFailureKind.PlayerError, "HTTP 500"),
            ),
        )
    }

    @Test
    fun staleSourceFailureIsIgnored() {
        val kodik = sourceVideo(
            id = 593472,
            player = "Kodik",
            index = 30,
            url = "https://kodikplayer.com/season/95032/hash/720p?episode=5",
        )
        val cvh = sourceVideo(
            id = 843499,
            player = "CVH",
            index = 511,
            url = "https://ru.yummyani.me/iframeCVH.html?dubbing_code=AniLibria&anime_id=51215&episode=5",
        )

        assertFalse(
            shouldUseAutomaticPlaybackFallback(
                currentVideo = cvh,
                failedVideo = kodik,
                manualSourceKey = cvh.sourceSelectionKey,
                failure = PlaybackFailure(PlaybackFailureKind.PlayerError, "HTTP 500"),
            ),
        )
    }

    @Test
    fun sourceSelectionIsProviderStableButPlaybackKeyIsEpisodeConcrete() {
        val episode13 = sourceVideo(
            id = 843499,
            player = "CVH",
            index = 511,
            url = "https://ru.yummyani.me/iframeCVH.html?dubbing_code=MiraiDUB&anime_id=5680&episode=13",
        ).copy(episode = "13")
        val episode14 = sourceVideo(
            id = 843500,
            player = "CVH",
            index = 512,
            url = "https://ru.yummyani.me/iframeCVH.html?dubbing_code=MiraiDUB&anime_id=5680&episode=14",
        ).copy(episode = "14")

        assertEquals(episode13.sourceSelectionKey, episode14.sourceSelectionKey)
        assertNotEquals(episode13.playbackSourceKey, episode14.playbackSourceKey)
    }

    @Test
    fun sourceResolveKeysKeepQueryOnlyEpisodeDifferencesConcrete() {
        val episode13 = sourceVideo(
            id = 0,
            player = "Alloha",
            index = 1,
            url = "https://alloha.yani.tv/?translation=210&season=1&episode=13",
        ).copy(episode = "13")
        val episode14 = sourceVideo(
            id = 0,
            player = "Alloha",
            index = 1,
            url = "https://alloha.yani.tv/?translation=210&season=1&episode=14",
        ).copy(episode = "14")

        assertEquals(episode13.sourceSelectionKey, episode14.sourceSelectionKey)
        assertNotEquals(episode13.playbackSourceKey, episode14.playbackSourceKey)
    }

    @Test
    fun decoratedEpisodeLabelsNormalizeToNumericEpisodeKeys() {
        val video = sourceVideo(
            id = 1,
            player = "Alloha",
            index = 1,
            url = "https://alloha.example/episode-14",
        ).copy(episode = "Episode 14")

        assertEquals("14", video.matchingEpisodeKey)
    }

    private fun sourceVideo(
        id: Long,
        player: String,
        index: Int,
        url: String,
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 10669,
            player = player,
            playerId = 0L,
            dubbing = "AniLibria",
            episode = "5",
            url = url,
            index = index,
            durationSeconds = 1_421,
            views = 0L,
        )
    }
}
