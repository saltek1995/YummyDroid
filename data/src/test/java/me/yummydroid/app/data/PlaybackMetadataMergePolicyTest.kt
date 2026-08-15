package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackMetadataMergePolicyTest {
    @Test
    fun keepsAllMetadataSourceSpecific() {
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
            metadataPlaybacks = listOf(metadataPlayback),
        )

        assertEquals(emptyList(), merged.video.skipSegments)
        assertEquals(emptyList(), merged.stream.subtitles)
        assertEquals(emptySet(), merged.stream.sourceSubtitleSourceKeys)
        assertEquals(listOf(720), merged.stream.availableQualities.mapNotNull(SourceQuality::height))
    }

    @Test
    fun mergesQualitiesFromSameSourceMetadata() {
        val currentVideo = testVideo(
            id = 1L,
            player = "Aksor",
        )
        val metadataVideo = currentVideo
        val currentPlayback = ResolvedPlayback(
            video = currentVideo,
            stream = ResolvedVideoStream(
                url = "https://cdn.example.com/aksor/720.mpd",
                mimeType = "application/dash+xml",
                headers = emptyMap(),
                maxVideoHeight = 720,
                availableQualities = listOf(SourceQuality(height = 720)),
            ),
        )
        val metadataPlayback = ResolvedPlayback(
            video = metadataVideo,
            stream = ResolvedVideoStream(
                url = "https://cdn.example.com/aksor/1080.mpd",
                mimeType = "application/dash+xml",
                headers = emptyMap(),
                maxVideoHeight = 1080,
                availableQualities = listOf(SourceQuality(height = 1080), SourceQuality(height = 720)),
            ),
        )

        val merged = currentPlayback.withMergedPlaybackMetadata(
            metadataPlaybacks = listOf(metadataPlayback),
        )

        assertEquals(listOf(1080, 720), merged.stream.availableQualities.mapNotNull(SourceQuality::height))
    }

    @Test
    fun doesNotMoveSkipSegmentsFromMetadataSources() {
        val currentSegments = listOf(VideoSkipSegment(VideoSkipKind.Opening, 10_000L, 90_000L))
        val metadataSegments = listOf(VideoSkipSegment(VideoSkipKind.Opening, 20_000L, 120_000L))
        val currentVideo = testVideo(
            id = 1L,
            player = "Aksor",
            skipSegments = currentSegments,
        )
        val metadataVideo = testVideo(
            id = 2L,
            player = "Alloha",
            skipSegments = metadataSegments,
        )
        val currentPlayback = ResolvedPlayback(
            video = currentVideo,
            stream = ResolvedVideoStream(
                url = "https://example.com/aksor/episode.m3u8",
                mimeType = "application/x-mpegURL",
                headers = emptyMap(),
            ),
        )

        val merged = currentPlayback.withMergedPlaybackMetadata(
            metadataPlaybacks = listOf(
                ResolvedPlayback(
                    video = metadataVideo,
                    stream = ResolvedVideoStream(
                        url = "https://example.com/alloha/episode.m3u8",
                        mimeType = "application/x-mpegURL",
                        headers = emptyMap(),
                    ),
                ),
            ),
        )

        assertEquals(currentSegments, merged.video.skipSegments)
    }

    @Test
    fun leavesCurrentSkipSegmentsEmptyWhenOnlyMetadataSourcesHaveSkipSegments() {
        val firstMetadataSegments = listOf(VideoSkipSegment(VideoSkipKind.Opening, 12_000L, 88_000L))
        val secondMetadataSegments = listOf(VideoSkipSegment(VideoSkipKind.Opening, 30_000L, 140_000L))
        val currentVideo = testVideo(
            id = 1L,
            player = "Aksor",
            skipSegments = emptyList(),
        )
        val firstMetadataVideo = testVideo(
            id = 2L,
            player = "Kodik",
            skipSegments = firstMetadataSegments,
        )
        val secondMetadataVideo = testVideo(
            id = 3L,
            player = "Alloha",
            skipSegments = secondMetadataSegments,
        )
        val currentPlayback = ResolvedPlayback(
            video = currentVideo,
            stream = ResolvedVideoStream(
                url = "https://example.com/aksor/episode.m3u8",
                mimeType = "application/x-mpegURL",
                headers = emptyMap(),
            ),
        )

        val merged = currentPlayback.withMergedPlaybackMetadata(
            metadataPlaybacks = listOf(
                ResolvedPlayback(
                    video = firstMetadataVideo,
                    stream = ResolvedVideoStream(
                        url = "https://example.com/kodik/episode.m3u8",
                        mimeType = "application/x-mpegURL",
                        headers = emptyMap(),
                    ),
                ),
                ResolvedPlayback(
                    video = secondMetadataVideo,
                    stream = ResolvedVideoStream(
                        url = "https://example.com/alloha/episode.m3u8",
                        mimeType = "application/x-mpegURL",
                        headers = emptyMap(),
                    ),
                ),
            ),
        )

        assertEquals(emptyList(), merged.video.skipSegments)
    }
}
