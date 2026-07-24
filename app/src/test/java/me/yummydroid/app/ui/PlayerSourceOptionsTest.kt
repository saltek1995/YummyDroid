package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingVoiceKey

class PlayerSourceOptionsTest {
    @Test
    fun sourceOptionsUseOnlySelectedVoiceAndCurrentEpisode() {
        val current = sourceVideo(
            id = 1,
            player = "Alloha",
            dubbing = "AniLibria",
            episode = "2",
            url = "https://alloha.example/player?episode=2",
        )
        val duplicateAlloha = sourceVideo(
            id = 2,
            player = "Alloha",
            dubbing = "AniLibria",
            episode = "2",
            url = "https://alloha.example/player?episode=2&mirror=1",
        )
        val cvh = sourceVideo(
            id = 3,
            player = "CVH",
            dubbing = "AniLibria",
            episode = "2",
            url = "https://cvh.example/hls/episode-2.m3u8",
        )
        val otherVoice = sourceVideo(
            id = 4,
            player = "Kodik",
            dubbing = "DreamCast",
            episode = "2",
            url = "https://kodik.example/episode-2",
        )
        val otherEpisode = sourceVideo(
            id = 5,
            player = "Aksor",
            dubbing = "AniLibria",
            episode = "3",
            url = "https://aksor.example/episode-3",
        )

        val options = listOf(current, duplicateAlloha, cvh, otherVoice, otherEpisode)
            .sourceOptionsFor(current, current.matchingVoiceKey)

        assertEquals(listOf("CVH", "Alloha"), options.map { it.label })
        assertEquals(2, options.size)
        assertTrue(options.all { it.video.dubbing == "AniLibria" && it.video.episode == "2" })
    }

    @Test
    fun sourceOptionsFallBackToCurrentVideoWhenSelectedVoiceIsMissing() {
        val current = sourceVideo(
            id = 1,
            player = "Alloha",
            dubbing = "AniLibria",
            episode = "2",
            url = "https://alloha.example/player?episode=2",
        )

        val options = listOf(current).sourceOptionsFor(current, selectedVoiceKey = "missing")

        assertEquals(listOf("Alloha"), options.map { it.label })
    }

    private fun sourceVideo(
        id: Long,
        player: String,
        dubbing: String,
        episode: String,
        url: String,
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 10,
            player = player,
            dubbing = dubbing,
            episode = episode,
            url = url,
            index = episode.toIntOrNull() ?: id.toInt(),
            durationSeconds = 1_440,
            views = 0,
        )
    }
}
