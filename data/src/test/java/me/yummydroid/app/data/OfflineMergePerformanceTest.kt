package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class OfflineMergePerformanceTest {
    private fun video(id: Long) = VideoVariant(
        id = id, animeId = 10, player = "Alloha", dubbing = "Voice", episode = id.toString(),
        url = "https://example.test/$id", index = id.toInt(), durationSeconds = 1440, views = 0,
    )

    @Test fun emptyDownloadsPreserveOnlineVariantsWithoutCopyingLargeTitle() {
        val videos = (1L..17715L).map(::video)
        val merged = videos.withOfflineDownloads(emptyList())
        assertEquals(videos.size, merged.size)
        videos.indices.forEach { assertSame(videos[it], merged[it]) }
    }

    @Test fun emptyDownloadsStillClearEveryStaleLocalField() {
        val online = video(1)
        val stale = online.copy(localPlaybackUrl = "file:///stale.mp4", localMimeType = "video/mp4", localBytes = 1)
        assertEquals(listOf(online), listOf(stale).withOfflineDownloads(emptyList()))
        assertEquals(online, online.copy(localMimeType = "video/mp4").withoutOfflinePlayback())
        assertEquals(online, online.copy(localBytes = 5).withoutOfflinePlayback())
    }
}
