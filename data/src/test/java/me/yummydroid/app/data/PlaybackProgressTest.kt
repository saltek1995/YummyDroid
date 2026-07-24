package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackProgressTest {
    @Test
    fun progressSyncKeyGroupsSameEpisodeVoiceAcrossVideoIds() {
        val progress = PlaybackProgress(
            animeId = 42,
            videoId = 1001,
            groupKey = "Alloha|AniLibria",
            episode = "7",
            positionMs = 10_000,
            durationMs = 20_000,
            updatedAtMs = 30_000,
        )

        assertEquals("anime:42:episode:7:voice:anilibria", progress.progressSyncKey())
    }

    @Test
    fun distinctLatestByEpisodeKeepsNewestEntry() {
        val older = PlaybackProgress(
            animeId = 42,
            videoId = 1001,
            groupKey = "Alloha|AniLibria",
            episode = "7",
            positionMs = 1_000,
            durationMs = 20_000,
            updatedAtMs = 10_000,
        )
        val newer = older.copy(videoId = 2002, groupKey = "Kodik|AniLibria", positionMs = 5_000, updatedAtMs = 20_000)

        assertEquals(listOf(newer), listOf(older, newer).distinctLatestByEpisode())
    }
}
