package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoSiteOrderTest {
    @Test
    fun defaultProviderFollowsPriorityAndFallsThroughMissingProviders() {
        val providers = listOf("CVH", "Askor", "Kodik", "VK", "Alloha", "Sibnet", "Other")
        val videos = providers.mapIndexed { index, player ->
            video(index + 1L, "Voice $player", "1", 0).copy(player = player)
        }
        providers.indices.forEach { index ->
            val available = videos.drop(index).reversed()
            assertEquals(providers[index], available.siteDefaultVideo()?.player)
            assertEquals(videos[index].downloadPlanVoiceKey, available.siteDefaultVoiceKey())
        }
        assertEquals(sourceProviderRank("Askor"), sourceProviderRank("Aksor"))
        assertEquals(sourceProviderRank("CVH"), sourceProviderRank("cdnvideohub"))
        assertEquals(sourceProviderRank("VK"), sourceProviderRank("\u0432\u043a"))
    }

    @Test
    fun siteVoiceOrderKeepsFirstOccurrenceFromVideosResponse() {
        val siteFirst = video(id = 1, dubbing = "Voice B", episode = "1", views = 1)
        val siteSecondHighViews = video(id = 2, dubbing = "Voice A", episode = "1", views = 10_000)
        val siteFirstNextEpisode = video(id = 3, dubbing = "Voice B", episode = "2", views = 1)

        val videos = listOf(siteFirst, siteSecondHighViews, siteFirstNextEpisode)

        assertEquals(siteFirst.groupKey, videos.siteDefaultVideo()?.groupKey)
        assertEquals(
            listOf(siteFirst.downloadPlanVoiceKey, siteSecondHighViews.downloadPlanVoiceKey),
            videos.siteOrderedVoiceKeys(),
        )
        assertEquals(siteFirst.downloadPlanVoiceKey, videos.siteDefaultVoiceKey())
    }

    private fun video(
        id: Long,
        dubbing: String,
        episode: String,
        views: Long,
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 100,
            player = "Alloha",
            playerId = 2,
            dubbing = dubbing,
            episode = episode,
            url = "https://example.test/$id",
            index = id.toInt(),
            durationSeconds = 1_400,
            views = views,
        )
    }
}
