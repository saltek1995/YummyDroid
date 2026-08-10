package me.yummydroid.app.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.data.downloadPlanVoiceKey
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.VideoVariant

class VideoPickerTest {
    @Test
    fun presentationKeepsSelectedSourceVoiceFallbackAndEpisodeViews() {
        val preferred = video(1, "CVH", "MiraiDub", "1", views = 10)
        val voiceFallback = video(2, "Alloha", "MiraiDUB", "2", views = 20)
        val anotherVoice = video(3, "Alloha", "AniDUB", "1", views = 30)

        val presentation = buildVideoPickerPresentation(
            videos = listOf(preferred, voiceFallback, anotherVoice),
            selectedGroup = preferred.groupKey,
        )

        assertEquals(preferred.groupKey, presentation.selectedSourceKey)
        assertEquals(preferred.downloadPlanVoiceKey, presentation.selectedVoiceKey)
        assertEquals(listOf("1", "2"), presentation.displayVideos.map(VideoVariant::episode))
        assertEquals(40L, presentation.episodeViewsByKey[preferred.matchingEpisodeKey])
        assertEquals(20L, presentation.episodeViewsByKey[voiceFallback.matchingEpisodeKey])
    }

    @Test
    fun presentationAcceptsVoiceKeyWhenSourceGroupIsUnavailable() {
        val preferred = video(1, "CVH", "MiraiDub", "1")
        val voiceFallback = video(2, "Alloha", "MiraiDUB", "2")
        val anotherVoice = video(3, "Alloha", "AniDUB", "1")

        val presentation = buildVideoPickerPresentation(
            videos = listOf(preferred, voiceFallback, anotherVoice),
            selectedGroup = preferred.downloadPlanVoiceKey,
        )

        assertNull(presentation.selectedSourceKey)
        assertEquals(preferred.downloadPlanVoiceKey, presentation.selectedVoiceKey)
        assertEquals(listOf("1", "2"), presentation.displayVideos.map(VideoVariant::episode))
    }

    @Test
    fun gridLayoutPreservesColumnLimitsAndFourRowPages() {
        assertEquals(1, episodeGridColumns(100.dp))
        assertEquals(2, episodeGridColumns(304.dp))
        assertEquals(5, episodeGridColumns(2_000.dp))

        val layout = episodeGridLayout(
            width = 1_920.dp,
            itemCount = 41,
            requestedPage = 99,
        )

        assertEquals(5, layout.columns)
        assertEquals(20, layout.pageSize)
        assertEquals(3, layout.pageCount)
        assertEquals(2, layout.normalizedPage)
        assertEquals(40, layout.pageStart)
        assertEquals(41, layout.pageEnd)
        assertEquals(256.dp, layout.pageContentHeight)
        assertEquals(1, layout.itemCount(page = 2, total = 41))
    }

    @Test
    fun singlePageGridUsesActualRowsAndCompactCardHeight() {
        val layout = episodeGridLayout(
            width = 320.dp,
            itemCount = 3,
            requestedPage = 0,
        )

        assertEquals(2, layout.columns)
        assertTrue(layout.compactCards)
        assertEquals(1, layout.pageCount)
        assertEquals(120.dp, layout.pageContentHeight)
    }

    @Test
    fun progressFractionKeepsMinimumVisibilityAndBounds() {
        assertEquals(0f, progress(positionMs = 0L, durationMs = 100_000L).watchProgressFraction())
        assertEquals(0.08f, progress(positionMs = 1_000L, durationMs = 100_000L).watchProgressFraction())
        assertEquals(0.08f, progress(positionMs = 1_000L, durationMs = 0L).watchProgressFraction())
        assertEquals(1f, progress(positionMs = 150_000L, durationMs = 100_000L).watchProgressFraction())
    }

    @Test
    fun resumePositionStaysBeforeKnownDurationEnd() {
        assertEquals(
            95_000L,
            progress(positionMs = 99_000L, durationMs = 100_000L).safeResumePositionMs(),
        )
        assertNull(progress(positionMs = 3_000L, durationMs = 4_000L).safeResumePositionMs())
        assertEquals(
            12_000L,
            progress(positionMs = 12_000L, durationMs = 0L).safeResumePositionMs(),
        )
        assertNull(progress(positionMs = -1L, durationMs = 0L).safeResumePositionMs())
    }

    @Test
    fun playbackBindingPreservesDisabledResumeAndDirectActions() {
        val video = video(1, "CVH", "MiraiDub", "1")
        val directPlays = mutableListOf<Long>()
        val resumePlays = mutableListOf<Pair<Long, Long>>()
        val binding = EpisodePlaybackBinding(
            forcedOfflineMode = false,
            onPlayVideo = { selected -> directPlays += selected.id },
            onPlayVideoWithResumeChoice = { selected, position -> resumePlays += selected.id to position },
        )

        binding.play(video, progress(positionMs = 20_000L, durationMs = 100_000L), enabled = false)
        binding.play(video, progress(positionMs = 20_000L, durationMs = 100_000L), enabled = true)
        binding.play(video, progress(positionMs = 0L, durationMs = 100_000L), enabled = true)

        assertEquals(listOf(video.id to 20_000L), resumePlays)
        assertEquals(listOf(video.id), directPlays)
    }

    private fun video(
        id: Long,
        player: String,
        dubbing: String,
        episode: String,
        views: Long = 0L,
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 10L,
            player = player,
            dubbing = dubbing,
            episode = episode,
            url = "https://example.com/$id",
            index = episode.toInt(),
            durationSeconds = 1_440,
            views = views,
        )
    }

    private fun progress(positionMs: Long, durationMs: Long): PlaybackProgress {
        return PlaybackProgress(
            animeId = 10L,
            videoId = 1L,
            groupKey = "CVH|MiraiDub",
            episode = "1",
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAtMs = 0L,
        )
    }
}
