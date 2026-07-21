package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackMetadataMergeTest {
    @Test
    fun mergesSubtitlesQualitiesAndSkipSegmentsFromSameVoiceSources() {
        val currentVideo = testVideo(
            id = 1L,
            player = "Aksor",
            skipSegments = emptyList(),
        )
        val metadataVideo = testVideo(
            id = 2L,
            player = "Kodik",
            skipSegments = listOf(VideoSkipSegment(VideoSkipKind.Opening, 12_000L, 88_000L)),
        )
        val currentPlayback = ResolvedPlayback(
            video = currentVideo,
            stream = ResolvedVideoStream(
                url = "https://example.com/aksor/episode.m3u8",
                mimeType = "application/x-mpegURL",
                headers = emptyMap(),
                maxVideoHeight = 720,
                availableQualities = listOf(SourceQuality(height = 720)),
            ),
        )
        val metadataPlayback = ResolvedPlayback(
            video = metadataVideo,
            stream = ResolvedVideoStream(
                url = "https://example.com/kodik/episode.m3u8",
                mimeType = "application/x-mpegURL",
                headers = emptyMap(),
                maxVideoHeight = 1080,
                availableQualities = listOf(SourceQuality(height = 1080)),
                subtitles = listOf(
                    ResolvedSubtitleTrack(
                        uri = "file:///tmp/kodik-subtitles.vtt",
                        label = "Kodik",
                        language = "ru",
                        mimeType = "text/vtt",
                    ),
                ),
            ),
        )

        val merged = currentPlayback.withMergedPlaybackMetadata(
            metadataVideos = listOf(metadataVideo),
            metadataPlaybacks = listOf(metadataPlayback),
        )

        assertEquals(metadataVideo.skipSegments, merged.video.skipSegments)
        assertEquals(metadataPlayback.stream.subtitles, merged.stream.subtitles)
        assertTrue(merged.stream.availableQualities.any { it.height == 1080 })
        assertTrue(merged.stream.availableQualities.any { it.height == 720 })
    }

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
            metadataVideos = listOf(otherVoiceVideo),
            metadataPlaybacks = listOf(otherVoicePlayback),
        )

        assertEquals(emptyList(), merged.video.skipSegments)
        assertEquals(emptyList(), merged.stream.subtitles)
    }
}

private fun testVideo(
    id: Long,
    player: String = "Player",
    dubbing: String = "Voice",
    episode: String = "1",
    skipSegments: List<VideoSkipSegment> = emptyList(),
): VideoVariant {
    return VideoVariant(
        id = id,
        animeId = 100L,
        player = player,
        dubbing = dubbing,
        episode = episode,
        url = "https://example.com/$player/$episode",
        index = id.toInt(),
        durationSeconds = 1_400,
        views = 0L,
        skipSegments = skipSegments,
    )
}
