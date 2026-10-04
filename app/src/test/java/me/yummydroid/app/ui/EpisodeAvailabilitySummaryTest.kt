package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.VideoVariant

class EpisodeAvailabilitySummaryTest {
    @Test
    fun maximumVoiceCountsUniqueEpisodesAcrossProviders() {
        val videos = (1..23).flatMap { episode ->
            listOf(video(episode, "Alloha", "Voice A")) +
                if (episode <= 21) listOf(video(episode, "Kodik", "Voice A")) else emptyList()
        } + (1..21).map { episode -> video(episode, "CVH", "Voice B") }

        assertEquals(23, episodeAvailabilityCount(LoadState.Ready(videos), false))
        assertEquals(23, episodeAvailabilityCount(LoadState.Ready(videos.reversed()), false))
    }

    @Test
    fun maximumDoesNotDependOnFirstVoiceOrCombineDifferentVoices() {
        val videos = listOf(
            video(2, "CVH", "Voice B"),
            video(1, "Alloha", "Voice A"),
            video(1, "Kodik", "Voice A"),
            video(3, "Alloha", "Voice A"),
        )

        assertEquals(2, episodeAvailabilityCount(LoadState.Ready(videos), false))
    }

    @Test
    fun countsEpisodesRatherThanTheLargestEpisodeNumber() {
        val videos = listOf(
            video(1, "Alloha", "Voice A"),
            video(7, "Alloha", "Voice A"),
            video(12, "Alloha", "Voice A"),
        )

        assertEquals(3, episodeAvailabilityCount(LoadState.Ready(videos), false))
    }

    @Test
    fun loadingAndErrorsHaveNoAvailabilityCount() {
        assertNull(episodeAvailabilityCount(LoadState.Loading, false))
        assertNull(episodeAvailabilityCount(LoadState.Error("unavailable"), false))
    }

    @Test
    fun readyEmptyVideosHaveZeroAvailability() {
        assertEquals(0, episodeAvailabilityCount(LoadState.Ready(emptyList()), false))
    }

    @Test
    fun offlineModeUsesMaximumDownloadedVoiceAcrossProviders() {
        val videos = listOf(
            video(1, "Alloha", "Voice A", offline = true),
            video(2, "Alloha", "Voice A"),
            video(2, "Kodik", "Voice A", offline = true),
            video(3, "Alloha", "Voice A", offline = true),
            video(1, "CVH", "Voice B", offline = true),
        ) + (2..6).map { video(it, "CVH", "Voice B") }

        assertEquals(3, episodeAvailabilityCount(LoadState.Ready(videos), true))
        assertEquals(6, episodeAvailabilityCount(LoadState.Ready(videos), false))
    }

    private fun video(
        episode: Int,
        player: String,
        dubbing: String,
        offline: Boolean = false,
    ): VideoVariant = VideoVariant(
        id = (episode * 1000L) + player.hashCode(),
        animeId = 30_118L,
        player = player,
        dubbing = dubbing,
        episode = episode.toString(),
        url = "https://example.test/$player/$dubbing/$episode",
        index = episode,
        durationSeconds = null,
        views = 0L,
        localPlaybackUrl = if (offline) "file:///episodes/$player-$episode.mp4" else "",
    )
}
