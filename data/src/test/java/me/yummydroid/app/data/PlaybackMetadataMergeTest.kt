package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackMetadataMergeTest {
    @Test
    fun normalizesDuplicateSubtitleUrlsAndPrefersReadableLabel() {
        val subtitles = listOf(
            ResolvedSubtitleTrack(
                uri = "https://example.test/sub_rus-2.vtt",
                label = "sub rus 2",
                mimeType = "text/vtt",
            ),
            ResolvedSubtitleTrack(
                uri = "https://example.test/sub_rus-2.vtt",
                label = "(Russian) Субтитры",
                language = "rus",
                mimeType = "text/vtt",
            ),
        ).normalizedSubtitleTracks()

        assertEquals(1, subtitles.size)
        assertEquals("(Russian) Субтитры", subtitles.single().label)
        assertEquals("rus", subtitles.single().language)
    }

    @Test
    fun normalizesDuplicateSubtitleUrlsAndRejectsNumericTechnicalLabel() {
        val subtitles = listOf(
            ResolvedSubtitleTrack(
                uri = "https://example.test/subtitles/8219.ass",
                label = "8219",
                mimeType = "text/x-ssa",
            ),
            ResolvedSubtitleTrack(
                uri = "https://example.test/subtitles/8219.ass",
                label = "Alloha signs",
                language = "rus",
                mimeType = "text/x-ssa",
            ),
        ).normalizedSubtitleTracks()

        assertEquals(1, subtitles.size)
        assertEquals("Alloha signs", subtitles.single().label)
    }

    @Test
    fun mergesQualitiesAndSubtitleSourceFlagsWithoutMovingSubtitlesOrSkipSegments() {
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
        assertEquals(setOf(metadataVideo.matchingSourceKey), merged.stream.sourceSubtitleSourceKeys)
        assertTrue(merged.stream.availableQualities.any { it.height == 1080 })
        assertTrue(merged.stream.availableQualities.any { it.height == 720 })
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
