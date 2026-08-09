package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.DownloadVoiceCoverage

class DownloadPlanDialogTest {
    @Test
    fun wizardMovesForwardAndBackwardWithoutSkippingSteps() {
        assertEquals(DownloadPlanStep.Episodes, DownloadPlanStep.Voice.next())
        assertEquals(DownloadPlanStep.Quality, DownloadPlanStep.Episodes.next())
        assertEquals(DownloadPlanStep.Quality, DownloadPlanStep.Quality.next())

        assertEquals(DownloadPlanStep.Voice, DownloadPlanStep.Voice.previous())
        assertEquals(DownloadPlanStep.Voice, DownloadPlanStep.Episodes.previous())
        assertEquals(DownloadPlanStep.Episodes, DownloadPlanStep.Quality.previous())
    }

    @Test
    fun eachWizardStepUsesOnlyItsOwnReadinessGate() {
        assertTrue(DownloadPlanStep.Voice.canProceed(true, false, false))
        assertFalse(DownloadPlanStep.Voice.canProceed(false, true, true))
        assertTrue(DownloadPlanStep.Episodes.canProceed(false, true, false))
        assertFalse(DownloadPlanStep.Episodes.canProceed(true, false, true))
        assertTrue(DownloadPlanStep.Quality.canProceed(false, false, true))
        assertFalse(DownloadPlanStep.Quality.canProceed(true, true, false))
    }

    @Test
    fun normalizationDropsUnavailableVoicesAndAppendsNewOnes() {
        val result = normalizeDownloadVoiceOrder(
            currentOrder = listOf("second", "stale", "second", "first"),
            coverages = listOf(coverage("first"), coverage("second"), coverage("new")),
        )

        assertEquals(listOf("second", "first", "new"), result)
    }

    @Test
    fun voiceMovementHonorsBoundsAndMissingKeys() {
        val order = listOf("first", "second", "third")

        assertEquals(listOf("second", "first", "third"), moveDownloadVoice(order, "second", -1))
        assertEquals(listOf("first", "third", "second"), moveDownloadVoice(order, "second", 1))
        assertEquals(order, moveDownloadVoice(order, "first", -1))
        assertEquals(order, moveDownloadVoice(order, "third", 1))
        assertEquals(order, moveDownloadVoice(order, "missing", 1))
        assertEquals(emptyList(), moveDownloadVoice(emptyList(), "missing", 1))
    }

    private fun coverage(voiceKey: String): DownloadVoiceCoverage {
        return DownloadVoiceCoverage(
            voiceKey = voiceKey,
            title = voiceKey,
            episodeCount = 0,
            downloadedCount = 0,
            ranges = emptyList(),
            availableEpisodeRanges = emptyList(),
            qualities = emptyList(),
        )
    }
}
