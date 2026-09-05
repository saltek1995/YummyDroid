package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import java.nio.file.Files
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.downloadPlanVoiceKey
import me.yummydroid.app.data.downloadSourceKey
import me.yummydroid.app.data.downloadQualitySamples
import me.yummydroid.app.data.matchingVoiceKey

class DownloadPlanSelectionTest {
    @Test
    fun qualitySamplingUsesOnlySelectedVoiceSourceAndEpisodesIncludingZero() {
        val zero = downloadPlanTestVideo(1, "CVH", "Voice A", "0", 720)
        val first = zero.copy(id = 2, episode = "1")
        val outsideRange = zero.copy(id = 3, episode = "100")
        val otherSource = first.copy(id = 4, player = "Kodik")
        val otherVoice = first.copy(id = 5, dubbing = "Voice B")
        val videos = listOf(zero, first, outsideRange, otherSource, otherVoice)
        val selected = downloadPlanSelectedVideos(
            videos, setOf(zero.downloadPlanVoiceKey),
            mapOf(zero.downloadPlanVoiceKey to setOf(zero.downloadSourceKey)),
            mapOf(zero.downloadPlanVoiceKey to DownloadEpisodeSelection(listOf(0..1))),
        )
        assertEquals(listOf(zero.id, first.id), selected.map { it.id })
        assertEquals(listOf(first.id), selected.downloadQualitySamples().map { it.id })
        assertEquals(listOf(0..1), buildDownloadVoiceCoverages(selected, listOf(PreferredQuality.P720)).single().availableEpisodeRanges)
    }

    @Test
    fun sourceSelectionSurvivesSavingAndRestrictsPlanRestorationAndFallback() {
        val chosen = downloadPlanTestVideo(1, "CVH", "Voice A", "1", 720)
        val other = chosen.copy(id = 2, player = "Kodik")
        val videos = listOf(chosen, other)
        val result = buildDownloadPlan(
            100, "Anime", videos, listOf(PreferredQuality.P720), setOf(chosen.downloadPlanVoiceKey),
            listOf(chosen.downloadPlanVoiceKey), false,
            selectedSourcesByVoice = mapOf(chosen.downloadPlanVoiceKey to setOf(chosen.downloadSourceKey)),
        )
        val directory = Files.createTempDirectory("plan-source-choice").toFile()
        try {
            DownloadPlanStorage(directory).save(result.plan)
            val item = DownloadPlanStorage(directory).read(result.plan.id)!!.items.single()
            assertEquals(setOf(chosen.downloadSourceKey), item.sourceKeys)
            assertEquals(listOf(chosen), item.sourceCandidates(videos))
            assertNull(item.resolveVideo(listOf(other)))
            val replacement = chosen.copy(id = 3)
            assertEquals(replacement, item.resolveVideo(listOf(other, replacement)))
        } finally {
            directory.deleteRecursively()
        }
    }

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
