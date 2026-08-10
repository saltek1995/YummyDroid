package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.yummydroid.app.data.matchingVoiceKey

class DownloadPlanMissingQualityBuildTest {
    @Test
    fun emptyQualitySelectionReportsEveryEpisodeAsMissingQuality() {
        val first = downloadPlanTestVideo(id = 1, player = "CVH", dubbing = "Voice A", episode = "1", quality = 1080)
        val second = downloadPlanTestVideo(id = 2, player = "CVH", dubbing = "Voice A", episode = "2", quality = 1080)

        val result = buildDownloadPlan(
            animeId = 100,
            animeTitle = "Anime",
            videos = listOf(first, second),
            acceptableQualities = emptyList(),
            selectedVoiceKeys = setOf(first.matchingVoiceKey),
            voiceOrder = listOf(first.matchingVoiceKey),
            onlyMissing = false,
        )

        assertEquals(2, result.totalEpisodes)
        assertEquals(0, result.selectedVoiceCount)
        assertEquals(2, result.missingSelectedQuality)
        assertEquals(0, result.scheduledCount)
        assertTrue(result.plan.qualityNames.isEmpty())
    }
}
