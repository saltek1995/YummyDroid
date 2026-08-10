package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.VideoVariant

class PlayerVideoSelectionTest {
    @Test
    fun selectionStaysInsidePreferredVoiceAndFallsBackToAnotherSource() {
        val videos = listOf(
            video(id = 1, player = "CVH", dubbing = "MiraiDub", episode = "13"),
            video(id = 2, player = "Alloha", dubbing = "MiraiDUB", episode = "14"),
            video(id = 3, player = "CVH", dubbing = "AniDUB", episode = "14"),
        )
        val selectedGroup = videos.first().groupKey
        val selectedVoice = videos.matchingVoiceKeyForGroup(selectedGroup)

        val selectedEpisodes = videos.sortedForPlayer(selectedGroup, selectedVoice)

        assertEquals(listOf("13", "14"), selectedEpisodes.map(VideoVariant::episode))
        assertEquals("Alloha", selectedEpisodes.first { it.episode == "14" }.player)
        assertTrue(selectedEpisodes.all { it.matchingVoiceKey == selectedVoice })
    }

    @Test
    fun baseSortingUsesEpisodeOrderBeforeSourceAvailability() {
        val videos = listOf(
            video(id = 3, player = "CVH", dubbing = "AniDUB", episode = "12"),
            video(id = 1, player = "Alloha", dubbing = "AniDUB", episode = "2"),
            video(id = 2, player = "CVH", dubbing = "AniDUB", episode = "10"),
        )

        assertEquals(listOf("2", "10", "12"), videos.sortedForPlayer().map(VideoVariant::episode))
    }

    private fun video(
        id: Long,
        player: String,
        dubbing: String,
        episode: String,
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 10,
            player = player,
            dubbing = dubbing,
            episode = episode,
            url = "https://example.test/$id",
            index = episode.toInt(),
            durationSeconds = 1_440,
            views = 0,
        )
    }
}
