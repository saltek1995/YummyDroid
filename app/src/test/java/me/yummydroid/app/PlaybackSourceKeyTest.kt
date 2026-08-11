package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import me.yummydroid.app.data.matchingEpisodeKey

class PlaybackSourceKeyTest {
    @Test
    fun sourceSelectionIsProviderStableButPlaybackKeyIsEpisodeConcrete() {
        val episode13 = playbackSourceVideo(
            id = 843499,
            player = "CVH",
            index = 511,
            url = "https://ru.yummyani.me/iframeCVH.html?dubbing_code=MiraiDUB&anime_id=5680&episode=13",
        ).copy(episode = "13")
        val episode14 = playbackSourceVideo(
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
        val episode13 = playbackSourceVideo(
            id = 0,
            player = "Alloha",
            index = 1,
            url = "https://alloha.yani.tv/?translation=210&season=1&episode=13",
        ).copy(episode = "13")
        val episode14 = playbackSourceVideo(
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
        val video = playbackSourceVideo(
            id = 1,
            player = "Alloha",
            index = 1,
            url = "https://alloha.example/episode-14",
        ).copy(episode = "Episode 14")

        assertEquals("14", video.matchingEpisodeKey)
    }
}
