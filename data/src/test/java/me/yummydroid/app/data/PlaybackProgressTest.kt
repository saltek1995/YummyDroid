package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackProgressTest {
    @Test
    fun progressSyncKeyPrefersStableVideoId() {
        val progress = PlaybackProgress(
            animeId = 42,
            videoId = 1001,
            groupKey = "voice:a",
            episode = "7",
            positionMs = 10_000,
            durationMs = 20_000,
            updatedAtMs = 30_000,
        )

        assertEquals("anime:42:video:1001", progress.progressSyncKey())
    }

    @Test
    fun distinctLatestByEpisodeKeepsNewestEntry() {
        val older = PlaybackProgress(
            animeId = 42,
            videoId = 0,
            groupKey = "voice:a",
            episode = "7",
            positionMs = 1_000,
            durationMs = 20_000,
            updatedAtMs = 10_000,
        )
        val newer = older.copy(positionMs = 5_000, updatedAtMs = 20_000)

        assertEquals(listOf(newer), listOf(older, newer).distinctLatestByEpisode())
    }
}
