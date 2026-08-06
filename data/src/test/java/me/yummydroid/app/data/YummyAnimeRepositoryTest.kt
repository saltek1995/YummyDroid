package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

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

    private fun video(
        id: Long,
        episode: String,
        sourceQualities: List<SourceQuality> = emptyList(),
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
        )
    }
}
