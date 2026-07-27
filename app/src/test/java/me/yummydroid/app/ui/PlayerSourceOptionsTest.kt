package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.yummydroid.app.data.SourceQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.maxSourceEpisodeCount
import me.yummydroid.app.data.matchingSourceKey
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

        assertEquals(listOf("CVH (1)", "Alloha (1)"), options.map { it.label })
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

        assertEquals(listOf("Alloha (1)"), options.map { it.label })
    }

    @Test
    fun sourceOptionsMarkSourcesWithResolvedSubtitles() {
        val alloha = sourceVideo(
            id = 1,
            player = "Alloha",
            dubbing = "AniLibria",
            episode = "2",
            url = "https://alloha.example/player?episode=2",
        )
        val cvh = sourceVideo(
            id = 2,
            player = "CVH",
            dubbing = "AniLibria",
            episode = "2",
            url = "https://cvh.example/hls/episode-2.m3u8",
        )

        val options = listOf(alloha, cvh)
            .sourceOptionsFor(
                alloha,
                alloha.matchingVoiceKey,
                sourceSubtitleSourceKeys = setOf(alloha.matchingSourceKey),
            )

        assertEquals(listOf("CVH (1)", "Alloha (1, Has subtitles)"), options.map { it.label })
    }

    @Test
    fun sourceOptionsShowPerSourceEpisodeCountsInsideSelectedVoice() {
        val allohaVideos = (1..3).map { episode ->
            sourceVideo(
                id = episode.toLong(),
                player = "Alloha",
                dubbing = "MiraiDUB",
                episode = episode.toString(),
                url = "https://alloha.example/miraidub-$episode",
            )
        }
        val kodikVideos = (1..2).map { episode ->
            sourceVideo(
                id = 100L + episode,
                player = "Kodik",
                dubbing = "MiraiDUB",
                episode = episode.toString(),
                url = "https://kodik.example/miraidub-$episode",
            )
        }
        val otherVoiceVideos = (1..5).map { episode ->
            sourceVideo(
                id = 200L + episode,
                player = "CVH",
                dubbing = "AniDUB",
                episode = episode.toString(),
                url = "https://cvh.example/anidub-$episode",
            )
        }
        val current = allohaVideos.first { it.episode == "2" }

        val options = (allohaVideos + kodikVideos + otherVoiceVideos)
            .sourceOptionsFor(current, current.matchingVoiceKey)

        assertEquals(listOf("Alloha (3)", "Kodik (2)"), options.map { it.label })
    }

    @Test
    fun voiceEpisodeCountUsesLargestSourceSet() {
        val videos = listOf(
            sourceVideo(
                id = 1,
                player = "CVH",
                dubbing = "MiraiDUB",
                episode = "13",
                url = "https://cvh.example/miraidub-13",
            ),
            sourceVideo(
                id = 2,
                player = "Alloha",
                dubbing = "MiraiDUB",
                episode = "13",
                url = "https://alloha.example/miraidub-13",
            ),
            sourceVideo(
                id = 3,
                player = "Alloha",
                dubbing = "MiraiDUB",
                episode = "14",
                url = "https://alloha.example/miraidub-14",
            ),
        )

        assertEquals(2, videos.maxSourceEpisodeCount())
    }

    @Test
    fun sortedForPlayerFallsBackInsideSelectedVoiceWhenPreferredSourceMissesEpisode() {
        val videos = listOf(
            sourceVideo(
                id = 1,
                player = "CVH",
                dubbing = "MiraiDub",
                episode = "13",
                url = "https://cvh.example/miraidub-13",
            ),
            sourceVideo(
                id = 2,
                player = "Alloha",
                dubbing = "MiraiDUB",
                episode = "14",
                url = "https://alloha.example/miraidub-14",
            ),
            sourceVideo(
                id = 3,
                player = "CVH",
                dubbing = "AniDUB",
                episode = "14",
                url = "https://cvh.example/anidub-14",
            ),
        )
        val selectedGroup = videos.first { it.player == "CVH" && it.dubbing == "MiraiDub" }.groupKey
        val selectedVoice = videos.matchingVoiceKeyForGroup(selectedGroup)
        val selectedEpisodes = videos.sortedForPlayer(selectedGroup, selectedVoice)

        assertEquals(listOf("13", "14"), selectedEpisodes.map { it.episode })
        assertEquals("Alloha", selectedEpisodes.first { it.episode == "14" }.player)
        assertTrue(selectedEpisodes.all { it.matchingVoiceKey == selectedVoice })
    }

    @Test
    fun adjacentEpisodeUsesAnotherSourceFromSelectedVoiceBeforeOtherVoices() {
        val videos = listOf(
            sourceVideo(
                id = 1,
                player = "CVH",
                dubbing = "MiraiDub",
                episode = "13",
                url = "https://cvh.example/miraidub-13",
            ),
            sourceVideo(
                id = 2,
                player = "Alloha",
                dubbing = "MiraiDUB",
                episode = "14",
                url = "https://alloha.example/miraidub-14",
            ),
            sourceVideo(
                id = 3,
                player = "CVH",
                dubbing = "AniDUB",
                episode = "14",
                url = "https://cvh.example/anidub-14",
            ),
        )
        val current = videos.first { it.episode == "13" }
        val next = findAdjacentPlayerVideo(
            currentVideo = current,
            allVideos = videos,
            selectedGroup = current.groupKey,
            forward = true,
        )

        assertEquals("14", next?.episode)
        assertEquals("Alloha", next?.player)
        assertEquals(current.matchingVoiceKey, next?.matchingVoiceKey)
    }

    @Test
    fun sourceQualityOptionsUseResolutionOnlyKeys() {
        val options = listOf(
            SourceQuality(height = 1080, bitrate = 6_000_000),
            SourceQuality(height = 1080, bitrate = 2_500_000),
        ).sourceQualityOptions()

        assertEquals(1, options.size)
        assertEquals("1080p", options.single().label)
        assertEquals("source:1080", options.single().key)
        assertEquals("height:1080", options.single().qualityOptionIdentity())
    }

    @Test
    fun playbackSubtitleShowsCurrentEpisodeOutOfUniqueEpisodeCount() {
        val videos = (1..13).flatMap { episode ->
            listOf(
                sourceVideo(
                    id = episode.toLong(),
                    player = "CVH",
                    dubbing = "AniLibria",
                    episode = episode.toString(),
                    url = "https://cvh.example/episode-$episode.m3u8",
                ),
                sourceVideo(
                    id = 100L + episode,
                    player = "Kodik",
                    dubbing = "AniLibria",
                    episode = episode.toString(),
                    url = "https://kodik.example/episode-$episode",
                ),
            )
        }
        val current = videos.first { it.episode == "8" }

        assertEquals(
            "AniLibria • Episode 8 of 13",
            current.playbackSubtitle(defaultPlayerControlTexts, videos),
        )
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
