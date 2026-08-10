package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackMetadataSubtitleSourceTest {
    @Test
    fun updatesSubtitlesOnlyFromTheSamePlaybackSource() {
        val currentVideo = testVideo(
            id = 1L,
            player = "Alloha",
        )
        val subtitles = listOf(
            ResolvedSubtitleTrack(
                uri = "file:///tmp/alloha-subtitles.vtt",
                label = "Alloha",
                language = "ru",
                mimeType = "text/vtt",
            ),
        )
        val currentPlayback = ResolvedPlayback(
            video = currentVideo,
            stream = ResolvedVideoStream(
                url = "https://example.com/alloha/episode.m3u8",
                mimeType = "application/x-mpegURL",
                headers = emptyMap(),
            ),
        )
        val refreshedPlayback = ResolvedPlayback(
            video = currentVideo,
            stream = currentPlayback.stream.copy(subtitles = subtitles),
        )

        val merged = currentPlayback.withMergedPlaybackMetadata(
            metadataPlaybacks = listOf(refreshedPlayback),
        )

        assertEquals(subtitles, merged.stream.subtitles)
        assertEquals(setOf(currentVideo.matchingSourceKey), merged.stream.sourceSubtitleSourceKeys)
    }

    @Test
    fun updatesEmbeddedSubtitlesOnlyFromTheSamePlaybackSource() {
        val currentVideo = testVideo(
            id = 1L,
            player = "CVH",
        )
        val embeddedSubtitles = listOf(
            ResolvedEmbeddedSubtitleTrack(
                id = "CC1",
                label = "Signs",
                language = "ru",
            ),
        )
        val currentPlayback = ResolvedPlayback(
            video = currentVideo,
            stream = ResolvedVideoStream(
                url = "https://example.com/cvh/episode.mpd",
                mimeType = "application/dash+xml",
                headers = emptyMap(),
            ),
        )
        val refreshedPlayback = ResolvedPlayback(
            video = currentVideo,
            stream = currentPlayback.stream.copy(
                embeddedSubtitles = embeddedSubtitles,
                hasEmbeddedSubtitles = true,
            ),
        )

        val merged = currentPlayback.withMergedPlaybackMetadata(
            metadataPlaybacks = listOf(refreshedPlayback),
        )

        assertEquals(embeddedSubtitles, merged.stream.embeddedSubtitles)
        assertTrue(merged.stream.hasSubtitles)
        assertEquals(setOf(currentVideo.matchingSourceKey), merged.stream.sourceSubtitleSourceKeys)
    }

    @Test
    fun unresolvedEmbeddedSubtitleFlagDoesNotMarkSourceAsHavingSubtitles() {
        val currentVideo = testVideo(
            id = 1L,
            player = "CVH",
        )
        val currentPlayback = ResolvedPlayback(
            video = currentVideo,
            stream = ResolvedVideoStream(
                url = "https://example.com/cvh/episode.mpd",
                mimeType = "application/dash+xml",
                headers = emptyMap(),
            ),
        )
        val refreshedPlayback = ResolvedPlayback(
            video = currentVideo,
            stream = currentPlayback.stream.copy(hasEmbeddedSubtitles = true),
        )

        val merged = currentPlayback.withMergedPlaybackMetadata(
            metadataPlaybacks = listOf(refreshedPlayback),
        )

        assertTrue(merged.stream.hasSubtitles)
        assertEquals(emptySet(), merged.stream.sourceSubtitleSourceKeys)
    }
}
