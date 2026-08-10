package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackProgressIdentityTest {
    @Test
    fun blankEpisodeFallsBackFromGroupToVideoAndAnimeKeys() {
        val base = progress(groupKey = "Player|Voice", videoId = 7L)

        assertEquals("anime:42:group:Player|Voice", base.progressSyncKey())
        assertEquals("anime:42:video:7", base.copy(groupKey = "").progressSyncKey())
        assertEquals("anime:42", base.copy(groupKey = "", videoId = 0L).progressSyncKey())
    }

    @Test
    fun distinctEpisodesAreSortedNumericallyThenByVideoId() {
        val episodeTen = progress(episode = "10", videoId = 3L)
        val episodeTwo = progress(episode = "2", videoId = 2L)
        val unknownEpisode = progress(episode = "special", videoId = 1L)

        assertEquals(
            listOf(episodeTwo, episodeTen, unknownEpisode),
            listOf(episodeTen, unknownEpisode, episodeTwo).distinctLatestByEpisode(),
        )
    }

    private fun progress(
        groupKey: String = "",
        episode: String = "",
        videoId: Long,
    ): PlaybackProgress {
        return PlaybackProgress(
            animeId = 42L,
            videoId = videoId,
            groupKey = groupKey,
            episode = episode,
            positionMs = 1_000L,
            durationMs = 2_000L,
            updatedAtMs = 3_000L,
        )
    }
}
