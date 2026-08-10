package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.matchingVoiceKey

class DownloadPlanSelectionTest {
    @Test
    fun missingVoiceAndMissingQualityAreCountedSeparately() {
        val selectedVoiceLowQuality = downloadPlanTestVideo(
            id = 1,
            player = "CVH",
            dubbing = "Voice A",
            episode = "1",
            quality = 720,
        )
        val otherVoice = downloadPlanTestVideo(
            id = 2,
            player = "Kodik",
            dubbing = "Voice B",
            episode = "2",
            quality = 1080,
        )

        val result = buildDownloadPlan(
            animeId = 100,
            animeTitle = "Anime",
            videos = listOf(selectedVoiceLowQuality, otherVoice),
            acceptableQualities = listOf(PreferredQuality.P1080),
            selectedVoiceKeys = setOf(selectedVoiceLowQuality.matchingVoiceKey),
            voiceOrder = listOf(selectedVoiceLowQuality.matchingVoiceKey),
            onlyMissing = false,
        )

        assertEquals(1, result.missingSelectedQuality)
        assertEquals(1, result.missingInSelectedVoices)
        assertEquals(0, result.scheduledCount)
    }

    @Test
    fun episodeSelectionCountsExcludedEpisodes() {
        val first = downloadPlanTestVideo(id = 1, player = "CVH", dubbing = "Voice A", episode = "1", quality = 1080)
        val second = downloadPlanTestVideo(id = 2, player = "CVH", dubbing = "Voice A", episode = "2", quality = 1080)

        val result = buildDownloadPlan(
            animeId = 100,
            animeTitle = "Anime",
            videos = listOf(first, second),
            acceptableQualities = listOf(PreferredQuality.P1080),
            selectedVoiceKeys = setOf(first.matchingVoiceKey),
            voiceOrder = listOf(first.matchingVoiceKey),
            onlyMissing = false,
            episodeSelectionsByVoice = mapOf(
                first.matchingVoiceKey to DownloadEpisodeSelection(listOf(1..1)),
            ),
        )

        assertEquals(1, result.excludedByEpisodeSelection)
        assertEquals(listOf(first.id), result.plan.items.map { it.videoId })
    }
}
