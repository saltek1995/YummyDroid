package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.data.PreferredQuality

class DownloadRetryCandidatesTest {
    @Test
    fun downloadRetryCandidatesRotateSourcesForSameEpisodeVoiceAndQuality() {
        val requested = downloadPlanTestVideo(id = 1, player = "CVH", dubbing = "Voice A", episode = "1", quality = 1080)
        val alternate = downloadPlanTestVideo(id = 2, player = "Kodik", dubbing = "Voice A", episode = "1", quality = 1080)
        val wrongQuality = downloadPlanTestVideo(id = 3, player = "Aksor", dubbing = "Voice A", episode = "1", quality = 720)
        val otherVoice = downloadPlanTestVideo(id = 4, player = "Alloha", dubbing = "Voice B", episode = "1", quality = 1080)
        val otherEpisode = downloadPlanTestVideo(id = 5, player = "Sibnet", dubbing = "Voice A", episode = "2", quality = 1080)

        val candidates = listOf(wrongQuality, otherVoice, alternate, otherEpisode, requested)
            .downloadRetryCandidatesFor(requested, PreferredQuality.P1080)

        assertEquals(listOf(requested.id, alternate.id), candidates.map { it.id })
        assertEquals(requested.id, candidates.downloadRetryCandidateForAttempt(1)?.id)
        assertEquals(alternate.id, candidates.downloadRetryCandidateForAttempt(2)?.id)
        assertEquals(requested.id, candidates.downloadRetryCandidateForAttempt(3)?.id)
        assertEquals(
            "Voice A \u2022 Kodik \u2022 1080p",
            candidates.downloadRetryCandidateForAttempt(2)?.downloadTaskSubtitle("1080p", "Voice A"),
        )
    }

    @Test
    fun downloadRetryCandidatesKeepUnknownQualitySourcesForRuntimeCheck() {
        val requested = downloadPlanTestVideo(id = 1, player = "CVH", dubbing = "Voice A", episode = "1", quality = 1080)
        val unknownQuality = downloadPlanTestVideo(id = 2, player = "Kodik", dubbing = "Voice A", episode = "1", quality = 1080)
            .copy(sourceQualities = emptyList())

        val candidates = listOf(unknownQuality, requested)
            .downloadRetryCandidatesFor(requested, PreferredQuality.P1080)

        assertEquals(listOf(requested.id, unknownQuality.id), candidates.map { it.id })
    }
}
