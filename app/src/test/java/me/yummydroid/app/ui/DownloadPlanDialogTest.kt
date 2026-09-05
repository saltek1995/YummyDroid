package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.DownloadEpisodeSelection
import me.yummydroid.app.downloadPlanTestVideo
import me.yummydroid.app.preferredQuality
import me.yummydroid.app.data.downloadPlanVoiceKey
import me.yummydroid.app.data.downloadSourceKey
import me.yummydroid.app.data.PreferredQuality

class DownloadPlanDialogTest {
    @Test
    fun wizardMovesForwardAndBackwardWithoutSkippingSteps() {
        assertEquals(DownloadPlanStep.Source, DownloadPlanStep.Voice.next())
        assertEquals(DownloadPlanStep.Episodes, DownloadPlanStep.Source.next())
        assertEquals(DownloadPlanStep.Quality, DownloadPlanStep.Episodes.next())
        assertEquals(DownloadPlanStep.Quality, DownloadPlanStep.Quality.next())

        assertEquals(DownloadPlanStep.Voice, DownloadPlanStep.Voice.previous())
        assertEquals(DownloadPlanStep.Source, DownloadPlanStep.Episodes.previous())
        assertEquals(DownloadPlanStep.Voice, DownloadPlanStep.Source.previous())
        assertEquals(DownloadPlanStep.Episodes, DownloadPlanStep.Quality.previous())
    }

    @Test
    fun eachWizardStepUsesOnlyItsOwnReadinessGate() {
        assertTrue(DownloadPlanStep.Source.canProceed(false, true, false, false))
        assertFalse(DownloadPlanStep.Source.canProceed(true, false, true, true))
        assertTrue(DownloadPlanStep.Voice.canProceed(true, false, false, false))
        assertFalse(DownloadPlanStep.Voice.canProceed(false, true, true, true))
        assertTrue(DownloadPlanStep.Episodes.canProceed(false, false, true, false))
        assertFalse(DownloadPlanStep.Episodes.canProceed(true, true, false, true))
        assertTrue(DownloadPlanStep.Quality.canProceed(false, false, false, true))
        assertFalse(DownloadPlanStep.Quality.canProceed(true, true, true, false))
    }

    @Test
    fun voiceAndSourceChangesReplaceSelectionAndIgnoreUnavailableChoices() {
        val first = downloadPlanTestVideo(1, "CVH", "Voice A", "1", 720)
        val alternate = first.copy(id = 2, player = "Kodik")
        val second = first.copy(id = 3, player = "Alloha", dubbing = "Voice B")
        val state = DownloadPlanDialogMutableState(listOf(first, alternate, second), alternate, PreferredQuality.P720)
        assertEquals(first.downloadPlanVoiceKey, state.selectedVoiceKey)
        assertEquals(alternate.downloadSourceKey, state.selectedSourceKey)
        state.selectSource(first.downloadSourceKey)
        state.selectSource(first.downloadSourceKey)
        assertEquals(first.downloadSourceKey, state.selectedSourceKey)
        state.selectVoice(second.downloadPlanVoiceKey)
        assertEquals(second.downloadPlanVoiceKey, state.selectedVoiceKey)
        assertEquals(second.downloadSourceKey, state.selectedSourceKey)
        state.selectSource(first.downloadSourceKey)
        state.selectVoice("missing")
        assertEquals(second.downloadPlanVoiceKey, state.selectedVoiceKey)
        assertEquals(second.downloadSourceKey, state.selectedSourceKey)
    }

    @Test
    fun wizardBuildKeepsOnlyChosenVoiceSourceQualityAndEpisodes() {
        val chosen = downloadPlanTestVideo(1, "CVH", "Voice A", "0", 720)
        val otherSource = chosen.copy(id = 2, player = "Kodik")
        val otherVoice = chosen.copy(id = 3, dubbing = "Voice B")
        val outsideRange = chosen.copy(id = 4, episode = "1")
        val result = DownloadPlanBuildRequest(
            animeId = chosen.animeId,
            animeTitle = "Anime",
            videos = listOf(chosen, otherSource, otherVoice, outsideRange),
            quality = PreferredQuality.P720,
            voice = chosen.downloadPlanVoiceKey,
            source = chosen.downloadSourceKey,
            episodes = DownloadEpisodeSelection(listOf(0..0)),
            onlyMissing = false,
        ).build()
        val item = result.plan.items.single()
        assertEquals(chosen.id, item.videoId)
        assertEquals(chosen.downloadPlanVoiceKey, item.voiceKey)
        assertEquals(setOf(chosen.downloadSourceKey), item.sourceKeys)
        assertEquals(PreferredQuality.P720, item.preferredQuality)
        assertEquals(1, result.excludedByEpisodeSelection)
    }

    @Test
    fun qualityOptionsUseOneVoiceAndUniqueDescendingHeights() {
        val options = downloadPlanQualityOptions(
            resolvedQualitiesByVoice = mapOf(
                "first" to listOf(PreferredQuality.P720, PreferredQuality.Auto, PreferredQuality.P1080, PreferredQuality.P720),
                "second" to listOf(PreferredQuality.P1080, PreferredQuality.P720),
                "ignored" to listOf(PreferredQuality.P480),
            ),
            selectedVoice = "first",
        )

        assertEquals(listOf(PreferredQuality.P1080, PreferredQuality.P720), options)
    }

    @Test
    fun planBuildGuardRequiresQualityStepAndCompleteValidInputs() {
        assertTrue(
            shouldBuildDownloadPlan(
                step = DownloadPlanStep.Quality,
                qualitiesResolved = true,
                coveragesLoaded = true,
                hasRangeErrors = false,
            ),
        )
        assertFalse(shouldBuildDownloadPlan(DownloadPlanStep.Episodes, true, true, false))
        assertFalse(shouldBuildDownloadPlan(DownloadPlanStep.Quality, false, true, false))
        assertFalse(shouldBuildDownloadPlan(DownloadPlanStep.Quality, true, false, false))
        assertFalse(shouldBuildDownloadPlan(DownloadPlanStep.Quality, true, true, true))
    }

}
