package me.yummydroid.app.data

import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class YummyAnimeRepositoryTest {
    @Test
    fun sampleSelectionUsesRepresentativeEpisodeInsteadOfFirstEpisode() {
        val firstEpisode = video(id = 1, episode = "1")
        val latestEpisode = video(id = 12, episode = "12")

        val selected = listOf(firstEpisode, latestEpisode).selectDownloadQualitySampleCandidate()

        assertEquals(latestEpisode.id, selected?.id)
    }

    @Test
    fun sampleSelectionPrefersCandidateWithKnownQualities() {
        val latestWithoutQualities = video(id = 12, episode = "12")
        val knownQualityEpisode = video(
            id = 5,
            episode = "5",
            sourceQualities = listOf(SourceQuality(height = 1080)),
        )

        val selected = listOf(latestWithoutQualities, knownQualityEpisode).selectDownloadQualitySampleCandidate()

        assertEquals(knownQualityEpisode.id, selected?.id)
    }

    @Test
    fun repositoryWithoutContextOrSessionUsesSafeLocalResults() = runBlocking {
        val repository = YummyAnimeRepository()
        val requested = video(id = 1, episode = "1")

        assertEquals(emptyList(), repository.offlineAnime())
        assertFailsWith<IllegalStateException> { repository.getVideoSubscriptions(userId = 42) }
        assertEquals(
            emptyList(),
            repository.resolveAvailableDownloadQualities(
                requested = requested,
                videos = emptyList(),
                allEpisodes = false,
            ),
        )
        assertEquals(
            emptyMap(),
            repository.resolveSampledDownloadQualities(
                voiceKeys = emptySet(),
                videos = listOf(requested),
            ),
        )
        assertNull(repository.getAnimeMark(animeId = 100))
        assertFalse(
            repository.saveWatchProgress(
                PlaybackProgress(
                    animeId = 100,
                    videoId = 1,
                    groupKey = "voice",
                    episode = "1",
                    positionMs = 0,
                    durationMs = 1_000,
                    updatedAtMs = 1,
                ),
            ),
        )
        assertFalse(repository.isOfflineFallbackActive())
    }

    @Test
    fun downloadFailureMessageIncludesAtMostThreeSourceFailures() {
        assertEquals("Could not download episode", downloadFailureMessage(emptyList()))
        assertEquals(
            "Could not download episode: A: first; B: second; C: third",
            downloadFailureMessage(
                listOf(
                    "A: first",
                    "B: second",
                    "C: third",
                    "D: fourth",
                ),
            ),
        )
    }

    @Test
    fun offlineStreamResolutionAndEmptyPlaybackSelectionDoNotUseNetwork() = runBlocking {
        val repository = YummyAnimeRepository()
        val offlineVideo = video(
            id = 7,
            episode = "7",
            offlineFiles = listOf(
                OfflineVideoFile(
                    playbackUrl = "file:///offline/episode-7.mp4",
                    mimeType = "video/mp4",
                    bytes = 1_024,
                    qualityTitle = "1080p",
                ),
            ),
        )

        val stream = repository.resolveVideoStream(offlineVideo)

        assertEquals("file:///offline/episode-7.mp4", stream.url)
        assertEquals("video/mp4", stream.mimeType)
        assertEquals(emptyMap(), stream.headers)
        assertNull(stream.maxVideoHeight)
        assertEquals(
            "No sources are available for the episode",
            assertFailsWith<IOException> {
                repository.resolveBestPlaybackSource(
                    candidates = emptyList(),
                    preferredQuality = PreferredQuality.Auto,
                )
            }.message,
        )
    }

    private fun video(
        id: Long,
        episode: String,
        sourceQualities: List<SourceQuality> = emptyList(),
        offlineFiles: List<OfflineVideoFile> = emptyList(),
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 100,
            player = "Player",
            playerId = 1,
            dubbing = "Voice",
            episode = episode,
            url = "https://example.test/$id",
            index = id.toInt(),
            durationSeconds = 1_400,
            views = 0,
            sourceQualities = sourceQualities,
            localFiles = offlineFiles,
        )
    }
}
