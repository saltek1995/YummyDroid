package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackMetadataVoiceIsolationTest {
    @Test
    fun ignoresMetadataFromAnotherVoice() {
        val currentVideo = testVideo(id = 1L, dubbing = "Voice A")
        val otherVoiceVideo = testVideo(
            id = 2L,
            dubbing = "Voice B",
            skipSegments = listOf(VideoSkipSegment(VideoSkipKind.Ending, 100_000L, 120_000L)),
        )
        val currentPlayback = ResolvedPlayback(
            video = currentVideo,
            stream = ResolvedVideoStream(
                url = "https://example.com/current.m3u8",
                mimeType = "application/x-mpegURL",
                headers = emptyMap(),
            ),
        )
        val otherVoicePlayback = ResolvedPlayback(
            video = otherVoiceVideo,
            stream = ResolvedVideoStream(
                url = "https://example.com/other.m3u8",
                mimeType = "application/x-mpegURL",
                headers = emptyMap(),
                subtitles = listOf(ResolvedSubtitleTrack(uri = "file:///tmp/other.vtt")),
            ),
        )

        val merged = currentPlayback.withMergedPlaybackMetadata(
            metadataPlaybacks = listOf(otherVoicePlayback),
        )

        assertEquals(emptyList(), merged.video.skipSegments)
        assertEquals(emptyList(), merged.stream.subtitles)
    }
}
