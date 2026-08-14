package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingVoiceKey

class DetailsPlaybackPolicyTest {
    @Test
    fun episodeMatchingNormalizesDecimalSeparator() {
        assertTrue("1,5".matchesProgressEpisode("1.5"))
        assertTrue(" 12 ".matchesProgressEpisode("12"))
        assertFalse("1.5".matchesProgressEpisode("2"))
        assertFalse("special".matchesProgressEpisode("special-2"))
    }

    @Test
    fun progressMatchingPrefersIdThenGroupAndVoice() {
        val video = video(id = 7, player = "Player A", dubbing = "Voice A", episode = "1.5")

        assertTrue(video.matchesPlaybackProgress(progress(videoId = 7, groupKey = "wrong"), true))
        assertTrue(
            video.matchesPlaybackProgress(
                progress(videoId = 0, groupKey = video.groupKey, episode = "1,5"),
                requireGroup = true,
            ),
        )
        assertTrue(
            video.matchesPlaybackProgress(
                progress(
                    videoId = 0,
                    groupKey = "Other|${video.matchingVoiceKey}",
                    episode = "1.5",
                ),
                requireGroup = false,
            ),
        )
        assertFalse(
            video.matchesPlaybackProgress(
                progress(videoId = 0, groupKey = "Other|Voice B", episode = "1.5"),
                requireGroup = false,
            ),
        )
    }

    @Test
    fun progressMatchingRejectsMissingIdentityParts() {
        val video = video(id = 7, player = "Player A", dubbing = "Voice A", episode = "1.5")

        assertFalse(
            video.matchesPlaybackProgress(
                progress(videoId = 9, groupKey = "", episode = "1.5"),
                requireGroup = true,
            ),
        )
        assertFalse(
            video.matchesPlaybackProgress(
                progress(videoId = 9, groupKey = video.groupKey, episode = ""),
                requireGroup = true,
            ),
        )
        assertTrue(
            video.matchesPlaybackProgress(
                progress(videoId = 9, groupKey = video.groupKey, episode = "1,5"),
                requireGroup = true,
            ),
        )
    }

    @Test
    fun resumeTargetClampsPositionBeforeEpisodeEnd() {
        val video = video(id = 7)
        val target = progress(
            videoId = 7,
            positionMs = 19_000,
            durationMs = 20_000,
        ).resolveResumeTarget(listOf(video))

        assertSame(video, target?.video)
        assertEquals(15_000, target?.positionMs)
        assertNull(
            progress(videoId = 7, positionMs = 3_000, durationMs = 4_000)
                .resolveResumeTarget(listOf(video)),
        )
        assertNull(progress(videoId = 7, positionMs = 0).resolveResumeTarget(listOf(video)))
    }

    @Test
    fun latestResumeTargetCanUseHistoryWhenCurrentProgressIsMissing() {
        val video = video(id = 7)
        val target = listOf(
            progress(videoId = 7, positionMs = 12_000, updatedAtMs = 20_000),
        ).resolveLatestResumeTarget(listOf(video))

        assertSame(video, target?.video)
        assertEquals(12_000, target?.positionMs)
    }

    @Test
    fun latestResumeTargetSkipsUnusableNewerProgress() {
        val video = video(id = 7)
        val target = listOf(
            progress(videoId = 7, positionMs = 15_000, updatedAtMs = 10_000),
            progress(videoId = 7, positionMs = 0, updatedAtMs = 20_000),
        ).resolveLatestResumeTarget(listOf(video))

        assertSame(video, target?.video)
        assertEquals(15_000, target?.positionMs)
    }

    @Test
    fun remoteHistoryWithoutGroupCanResumeByEpisode() {
        val video = video(id = 7, episode = "3")
        val target = listOf(
            progress(
                videoId = 0,
                groupKey = "",
                episode = "3",
                positionMs = 18_000,
                updatedAtMs = 20_000,
            ),
        ).resolveLatestResumeTarget(listOf(video))

        assertSame(video, target?.video)
        assertEquals(18_000, target?.positionMs)
    }

    @Test
    fun selectedGroupControlsHeroStartVideo() {
        val first = video(id = 1, player = "Player A", dubbing = "Voice A")
        val selected = video(id = 2, player = "Player B", dubbing = "Voice B")

        assertSame(selected, listOf(first, selected).heroStartVideo(selected.groupKey))
        assertNull(emptyList<VideoVariant>().heroStartVideo(selected.groupKey))
    }

    @Test
    fun episodeLabelsUseEpisodeThenIndexThenId() {
        assertEquals("Episode 3", video(id = 3, episode = "3").shortEpisodeLabel("Episode"))
        assertEquals("fallback", video(id = 3, episode = "").localizedEpisodeTitle("Episode", "fallback"))
        assertEquals("5", video(id = 3, episode = "", index = 5).shortEpisodeNumberLabel())
        assertEquals("3", video(id = 3, episode = "", index = 0).shortEpisodeNumberLabel())
    }

    private fun progress(
        videoId: Long,
        groupKey: String = "Player|Voice",
        episode: String = "1",
        positionMs: Long = 10_000,
        durationMs: Long = 30_000,
        updatedAtMs: Long = 1,
    ): PlaybackProgress {
        return PlaybackProgress(
            animeId = 100,
            videoId = videoId,
            groupKey = groupKey,
            episode = episode,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAtMs = updatedAtMs,
        )
    }

    private fun video(
        id: Long,
        player: String = "Player",
        dubbing: String = "Voice",
        episode: String = "1",
        index: Int = id.toInt(),
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 100,
            player = player,
            dubbing = dubbing,
            episode = episode,
            url = "https://example.test/$id",
            index = index,
            durationSeconds = 1_400,
            views = 0,
        )
    }
}
