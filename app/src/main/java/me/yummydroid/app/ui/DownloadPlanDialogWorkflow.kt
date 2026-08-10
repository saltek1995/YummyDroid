package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.downloadPlanVoiceKey

@Composable
internal fun DownloadPlanDialogRuntime(
    animeId: Long,
    animeTitle: String,
    videos: List<VideoVariant>,
    selectedVideo: VideoVariant?,
    selected: PreferredQuality,
    onResolveSampledQualities: suspend (Set<String>, List<VideoVariant>) -> Map<String, List<PreferredQuality>>,
    onConfirm: (DownloadPlan) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedVoiceKey = remember(selectedVideo) {
        selectedVideo?.downloadPlanVoiceKey?.takeIf { it.isNotBlank() }
    }
    val state = rememberDownloadPlanDialogMutableState(videos, selected, selectedVoiceKey)
    val derived = rememberDownloadPlanDialogDerivedState(state)
    DownloadPlanDialogEffects(
        animeId = animeId,
        animeTitle = animeTitle,
        videos = videos,
        selected = selected,
        selectedVoiceKey = selectedVoiceKey,
        state = state,
        derived = derived,
        onResolveSampledQualities = onResolveSampledQualities,
    )
    DownloadPlanDialogPresentation(state, derived, onConfirm, onDismiss)
}

@Composable
private fun DownloadPlanDialogPresentation(
    state: DownloadPlanDialogMutableState,
    derived: DownloadPlanDialogDerivedState,
    onConfirm: (DownloadPlan) -> Unit,
    onDismiss: () -> Unit,
) {
    DownloadPlanDialogContent(
        state = DownloadPlanDialogUiState(
            step = state.step,
            coveragesResult = state.coveragesResult,
            orderedCoverages = derived.orderedCoverages,
            selectedOrderedCoverages = derived.selectedOrderedCoverages,
            selectedVoices = state.selectedVoices,
            normalizedVoiceOrder = derived.normalizedVoiceOrder,
            voiceEpisodeRanges = state.voiceEpisodeRanges,
            rangeErrorsByVoice = derived.rangeErrorsByVoice,
            onlyMissing = state.onlyMissing,
            qualityOptions = derived.qualityOptions,
            selectedQualities = state.selectedQualities,
            sampledQualitiesByVoice = state.sampledQualitiesByVoice,
            qualityError = state.qualityError,
            planResult = state.planResult,
            voiceStepReady = derived.voiceStepReady,
            episodesStepReady = derived.episodesStepReady,
            qualityStepReady = derived.qualityStepReady,
        ),
        actions = DownloadPlanDialogUiActions(
            onDismiss = onDismiss,
            onStepChange = { newStep -> state.step = newStep },
            onOnlyMissingToggle = { state.onlyMissing = !state.onlyMissing },
            onVoiceSelectedChange = { voiceKey, checked ->
                state.selectedVoices = if (checked) {
                    state.selectedVoices + voiceKey
                } else {
                    state.selectedVoices - voiceKey
                }
            },
            onMoveVoice = { voiceKey, delta ->
                state.voiceOrder = moveDownloadVoice(derived.normalizedVoiceOrder, voiceKey, delta)
            },
            onEpisodeRangeChange = { voiceKey, value ->
                state.voiceEpisodeRanges = state.voiceEpisodeRanges + (voiceKey to value)
            },
            onQualityToggle = { quality ->
                state.selectedQualities = if (quality in state.selectedQualities) {
                    state.selectedQualities - quality
                } else {
                    state.selectedQualities + quality
                }
            },
            onConfirm = { result -> result.plan?.let(onConfirm) },
        ),
    )
}
