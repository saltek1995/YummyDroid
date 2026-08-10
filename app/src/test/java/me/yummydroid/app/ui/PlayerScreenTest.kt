package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.LoadState

class PlayerScreenTest {
    @Test
    fun readyStreamUsesCurrentPlaybackRequest() {
        val video = video(id = 1L, animeId = 10L)
        val stream = stream("ready")

        val presentation = presentation(
            video = video,
            streamState = LoadState.Ready(stream),
            retained = null,
            startPositionMs = 12_000L,
            preferredQuality = PreferredQuality.P1080,
        )

        assertEquals(stream, presentation.playbackStream)
        assertEquals(video, presentation.playbackVideo)
        assertEquals(12_000L, presentation.playbackStartPositionMs)
        assertEquals(PreferredQuality.P1080, presentation.playbackPreferredQuality)
        assertFalse(presentation.useRetainedPlayback)
    }

    @Test
    fun loadingRetainsReadyPlaybackForSameAnime() {
        val retainedVideo = video(id = 1L, animeId = 10L)
        val requestedVideo = video(id = 2L, animeId = 10L, episode = "2")
        val retainedStream = stream("retained")
        val retained = RetainedReadyPlayback(
            stream = retainedStream,
            video = retainedVideo,
            startPositionMs = 34_000L,
            preferredQuality = PreferredQuality.P720,
        )

        val presentation = presentation(
            video = requestedVideo,
            streamState = LoadState.Loading,
            retained = retained,
            allVideos = listOf(retainedVideo, requestedVideo),
        )

        assertTrue(presentation.useRetainedPlayback)
        assertEquals(retainedStream, presentation.playbackStream)
        assertEquals(retainedVideo, presentation.playbackVideo)
        assertEquals(34_000L, presentation.playbackStartPositionMs)
        assertEquals(PreferredQuality.P720, presentation.playbackPreferredQuality)
    }

    @Test
    fun retainedPlaybackIsRejectedForAnotherAnimeOrResumeChoice() {
        val retainedVideo = video(id = 1L, animeId = 10L)
        val requestedVideo = video(id = 2L, animeId = 20L)
        val retained = RetainedReadyPlayback(
            stream = stream("retained"),
            video = retainedVideo,
            startPositionMs = 34_000L,
            preferredQuality = PreferredQuality.P720,
        )

        val anotherAnime = presentation(
            video = requestedVideo,
            streamState = LoadState.Loading,
            retained = retained,
        )
        val resumeChoice = presentation(
            video = retainedVideo,
            streamState = LoadState.Loading,
            retained = retained,
            resumeChoicePositionMs = 20_000L,
        )

        assertFalse(anotherAnime.useRetainedPlayback)
        assertNull(anotherAnime.playbackStream)
        assertEquals(requestedVideo, anotherAnime.playbackVideo)
        assertFalse(resumeChoice.useRetainedPlayback)
        assertNull(resumeChoice.playbackStream)
    }

    @Test
    fun forcedOfflineModeKeepsOnlyDownloadedVideosAndHidesSources() {
        val online = video(id = 1L, animeId = 10L)
        val offline = video(id = 2L, animeId = 10L, episode = "2", offline = true)

        val presentation = presentation(
            video = offline,
            streamState = LoadState.Ready(stream("offline")),
            retained = null,
            allVideos = listOf(online, offline),
            forcedOfflineMode = true,
        )

        assertEquals(listOf(offline), presentation.videos)
        assertTrue(presentation.sourceOptions.isEmpty())
    }

    private fun presentation(
        video: VideoVariant,
        streamState: LoadState<ResolvedVideoStream>,
        retained: RetainedReadyPlayback?,
        allVideos: List<VideoVariant> = listOf(video),
        startPositionMs: Long = 5_000L,
        preferredQuality: PreferredQuality = PreferredQuality.Auto,
        resumeChoicePositionMs: Long? = null,
        forcedOfflineMode: Boolean = false,
    ): PlayerScreenPresentation {
        return buildPlayerScreenPresentation(
            video = video,
            startPositionMs = startPositionMs,
            preferredQuality = preferredQuality,
            allVideos = allVideos,
            selectedGroup = video.groupKey,
            streamState = streamState,
            retainedReadyPlayback = retained,
            resumeChoicePositionMs = resumeChoicePositionMs,
            forcedOfflineMode = forcedOfflineMode,
            sourceSubtitleLabel = "Subtitles",
        )
    }

    private fun video(
        id: Long,
        animeId: Long,
        episode: String = "1",
        offline: Boolean = false,
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = animeId,
            player = "CVH",
            dubbing = "Voice",
            episode = episode,
            url = "https://example.com/$id",
            index = episode.toInt(),
            durationSeconds = 1_440,
            views = 0L,
            localPlaybackUrl = if (offline) "file:///video-$id.mp4" else "",
        )
    }

    private fun stream(name: String): ResolvedVideoStream {
        return ResolvedVideoStream(
            url = "https://example.com/$name.m3u8",
            mimeType = "application/x-mpegURL",
            headers = emptyMap(),
        )
    }
}
