package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.yummydroid.app.DownloadEpisodeSelection
import me.yummydroid.app.DownloadEpisodeSelectionError
import me.yummydroid.app.DownloadVoiceCoverage
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.parseDownloadEpisodeSelection
import me.yummydroid.app.validateDownloadEpisodeSelection

internal data class DownloadPlanDialogDerivedState(
    val qualityProbeVoiceKeys: Set<String>,
    val resolvedQualitiesByVoice: Map<String, List<PreferredQuality>>,
    val qualityOptions: List<PreferredQuality>,
    val planQualities: Set<PreferredQuality>,
    val normalizedVoiceOrder: List<String>,
    val rangeErrorsByVoice: Map<String, DownloadEpisodeSelectionError>,
    val episodeSelectionsByVoice: Map<String, DownloadEpisodeSelection>,
    val orderedCoverages: List<DownloadVoiceCoverage>,
    val selectedOrderedCoverages: List<DownloadVoiceCoverage>,
    val voiceStepReady: Boolean,
    val episodesStepReady: Boolean,
    val qualityStepReady: Boolean,
)

@Composable
internal fun rememberDownloadPlanDialogDerivedState(
    state: DownloadPlanDialogMutableState,
): DownloadPlanDialogDerivedState {
    val qualityProbeVoiceKeys = remember(state.selectedVoices) {
        state.selectedVoices.filter { it.isNotBlank() }.toSet()
    }
    val resolvedQualitiesByVoice = state.sampledQualitiesByVoice.orEmpty()
    val qualityOptions = remember(resolvedQualitiesByVoice, state.selectedVoices) {
        state.selectedVoices
            .flatMap { voiceKey -> resolvedQualitiesByVoice[voiceKey].orEmpty() }
            .filter { it.height != null }
            .distinctBy { it.height }
            .sortedByDescending { it.height ?: 0 }
    }
    val planQualities = if (state.sampledQualitiesByVoice == null) emptySet() else state.selectedQualities
    val coverages = state.coveragesResult.orEmpty()
    val coverageByKey = remember(coverages) { coverages.associateBy { it.voiceKey } }
    val normalizedVoiceOrder = remember(state.voiceOrder, coverages) {
        normalizeDownloadVoiceOrder(state.voiceOrder, coverages)
    }
    val selectionResults = remember(state.voiceEpisodeRanges, coverageByKey) {
        state.voiceEpisodeRanges.mapValues { (voiceKey, value) ->
            coverageByKey[voiceKey]?.let { coverage ->
                validateDownloadEpisodeSelection(value, coverage.availableEpisodeRanges)
            } ?: parseDownloadEpisodeSelection(value)
        }
    }
    val rangeErrorsByVoice = remember(selectionResults) {
        selectionResults.mapNotNull { (voiceKey, result) ->
            result.error?.let { error -> voiceKey to error }
        }.toMap()
    }
    val episodeSelectionsByVoice = remember(selectionResults) {
        selectionResults.mapNotNull { (voiceKey, result) ->
            result.selection.takeIf { selection -> result.error == null && selection.isRestricted }
                ?.let { selection -> voiceKey to selection }
        }.toMap()
    }
    val orderedCoverages = remember(normalizedVoiceOrder, coverageByKey) {
        normalizedVoiceOrder.mapNotNull { coverageByKey[it] }
    }
    val selectedOrderedCoverages = remember(orderedCoverages, state.selectedVoices) {
        orderedCoverages.filter { it.voiceKey in state.selectedVoices }
    }
    return DownloadPlanDialogDerivedState(
        qualityProbeVoiceKeys = qualityProbeVoiceKeys,
        resolvedQualitiesByVoice = resolvedQualitiesByVoice,
        qualityOptions = qualityOptions,
        planQualities = planQualities,
        normalizedVoiceOrder = normalizedVoiceOrder,
        rangeErrorsByVoice = rangeErrorsByVoice,
        episodeSelectionsByVoice = episodeSelectionsByVoice,
        orderedCoverages = orderedCoverages,
        selectedOrderedCoverages = selectedOrderedCoverages,
        voiceStepReady = state.coveragesResult != null &&
            orderedCoverages.isNotEmpty() && state.selectedVoices.isNotEmpty(),
        episodesStepReady = rangeErrorsByVoice.isEmpty() && selectedOrderedCoverages.isNotEmpty(),
        qualityStepReady = state.selectedQualities.isNotEmpty() &&
            rangeErrorsByVoice.isEmpty() && state.planResult?.scheduledCount?.let { it > 0 } == true,
    )
}
