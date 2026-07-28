package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoSiteOrderTest {
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
