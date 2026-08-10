package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.yummydroid.app.buildDownloadPlan
import me.yummydroid.app.buildDownloadVoiceCoverages
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant

@Composable
internal fun DownloadPlanDialogEffects(
    animeId: Long,
    animeTitle: String,
    videos: List<VideoVariant>,
    selected: PreferredQuality,
    selectedVoiceKey: String?,
    state: DownloadPlanDialogMutableState,
    derived: DownloadPlanDialogDerivedState,
    onResolveSampledQualities: suspend (Set<String>, List<VideoVariant>) -> Map<String, List<PreferredQuality>>,
) {
    DownloadPlanSelectionResetEffect(videos, state)
    DownloadPlanQualityProbeEffect(videos, state, derived.qualityProbeVoiceKeys, onResolveSampledQualities)
    DownloadPlanCoverageEffect(videos, selectedVoiceKey, state, derived.resolvedQualitiesByVoice)
    DownloadPlanQualityRetentionEffect(selected, state, derived.qualityOptions)
    DownloadPlanBuildEffect(animeId, animeTitle, videos, state, derived)
}

@Composable
private fun DownloadPlanSelectionResetEffect(
    videos: List<VideoVariant>,
    state: DownloadPlanDialogMutableState,
) {
    LaunchedEffect(videos, state.selectedVoices) {
        state.sampledQualitiesByVoice = null
        state.planResult = null
        state.qualityError = null
    }
}

@Composable
private fun DownloadPlanQualityProbeEffect(
    videos: List<VideoVariant>,
    state: DownloadPlanDialogMutableState,
    qualityProbeVoiceKeys: Set<String>,
    onResolveSampledQualities: suspend (Set<String>, List<VideoVariant>) -> Map<String, List<PreferredQuality>>,
) {
    LaunchedEffect(state.step, qualityProbeVoiceKeys, videos) {
        if (state.step != DownloadPlanStep.Quality) return@LaunchedEffect
        state.sampledQualitiesByVoice = null
        state.planResult = null
        state.qualityError = null
        if (qualityProbeVoiceKeys.isEmpty()) {
            state.sampledQualitiesByVoice = emptyMap()
            return@LaunchedEffect
        }
        runCatching { onResolveSampledQualities(qualityProbeVoiceKeys, videos) }
            .onSuccess { qualities -> state.sampledQualitiesByVoice = qualities }
            .onFailure { throwable ->
                state.sampledQualitiesByVoice = emptyMap()
                state.qualityError = throwable.message?.takeIf { it.isNotBlank() }
            }
    }
}

@Composable
private fun DownloadPlanCoverageEffect(
    videos: List<VideoVariant>,
    selectedVoiceKey: String?,
    state: DownloadPlanDialogMutableState,
    resolvedQualitiesByVoice: Map<String, List<PreferredQuality>>,
) {
    LaunchedEffect(videos, state.selectedQualities, selectedVoiceKey, resolvedQualitiesByVoice) {
        state.coveragesResult = null
        state.coveragesResult = withContext(Dispatchers.Default) {
            buildDownloadVoiceCoverages(
                videos = videos,
                acceptableQualities = state.selectedQualities,
                selectedVoiceKey = selectedVoiceKey,
                resolvedQualitiesByVoice = resolvedQualitiesByVoice,
            )
        }
    }
}

@Composable
private fun DownloadPlanQualityRetentionEffect(
    selected: PreferredQuality,
    state: DownloadPlanDialogMutableState,
    qualityOptions: List<PreferredQuality>,
) {
    LaunchedEffect(qualityOptions, selected, state.sampledQualitiesByVoice) {
        if (state.sampledQualitiesByVoice == null || qualityOptions.isEmpty()) return@LaunchedEffect
        val retained = state.selectedQualities.filterTo(mutableSetOf()) { it in qualityOptions }
        state.selectedQualities = retained.ifEmpty {
            selected.takeIf { quality -> quality.height != null && quality in qualityOptions }
                ?.let(::setOf)
                ?: setOf(qualityOptions.first())
        }
    }
}

@Composable
private fun DownloadPlanBuildEffect(
    animeId: Long,
    animeTitle: String,
    videos: List<VideoVariant>,
    state: DownloadPlanDialogMutableState,
    derived: DownloadPlanDialogDerivedState,
) {
    LaunchedEffect(
        animeId,
        animeTitle,
        videos,
        derived.planQualities,
        state.selectedVoices,
        derived.normalizedVoiceOrder,
        state.onlyMissing,
        derived.episodeSelectionsByVoice,
        derived.rangeErrorsByVoice,
        state.sampledQualitiesByVoice,
        state.coveragesResult,
        state.step,
    ) {
        state.planResult = null
        if (
            state.step != DownloadPlanStep.Quality ||
            state.sampledQualitiesByVoice == null ||
            state.coveragesResult == null ||
            derived.rangeErrorsByVoice.isNotEmpty()
        ) {
            return@LaunchedEffect
        }
        state.planResult = withContext(Dispatchers.Default) {
            buildDownloadPlan(
                animeId = animeId,
                animeTitle = animeTitle,
                videos = videos,
                acceptableQualities = derived.planQualities,
                selectedVoiceKeys = state.selectedVoices,
                voiceOrder = derived.normalizedVoiceOrder,
                onlyMissing = state.onlyMissing,
                episodeSelectionsByVoice = derived.episodeSelectionsByVoice,
            )
        }
    }
}
