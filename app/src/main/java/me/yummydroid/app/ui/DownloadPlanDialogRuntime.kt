package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.DownloadPlanBuildResult
import me.yummydroid.app.DownloadVoiceCoverage
import me.yummydroid.app.buildDownloadPlan
import me.yummydroid.app.buildDownloadVoiceCoverages
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.downloadPlanVoiceKey
import me.yummydroid.app.data.siteDefaultVoiceKey
import me.yummydroid.app.parseDownloadEpisodeSelection
import me.yummydroid.app.validateDownloadEpisodeSelection

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
    var step by remember(videos) { mutableStateOf(DownloadPlanStep.Voice) }
    var onlyMissing by remember { mutableStateOf(true) }
    var sampledQualitiesByVoice by remember(videos) {
        mutableStateOf<Map<String, List<PreferredQuality>>?>(null)
    }
    var qualityError by remember(videos) { mutableStateOf<String?>(null) }
    var planResult by remember(videos) { mutableStateOf<DownloadPlanBuildResult?>(null) }
    var selectedQualities by remember(videos, selected) {
        mutableStateOf(setOfNotNull(selected.takeIf { it.height != null }))
    }
    var voiceEpisodeRanges by remember(videos) { mutableStateOf<Map<String, String>>(emptyMap()) }
    val selectedVoiceKey = remember(selectedVideo) {
        selectedVideo?.downloadPlanVoiceKey?.takeIf { it.isNotBlank() }
    }
    var selectedVoices by remember(videos, selectedVoiceKey) {
        mutableStateOf(
            selectedVoiceKey
                ?.takeIf { it.isNotBlank() }
                ?.let(::setOf)
                ?: videos.siteDefaultVoiceKey()?.let(::setOf)
                ?: emptySet(),
        )
    }
    val qualityProbeVoiceKeys = remember(selectedVoices) {
        selectedVoices.filter { it.isNotBlank() }.toSet()
    }
    val resolvedQualitiesByVoice = sampledQualitiesByVoice.orEmpty()
    val qualityOptions = remember(resolvedQualitiesByVoice, selectedVoices) {
        selectedVoices
            .flatMap { voiceKey -> resolvedQualitiesByVoice[voiceKey].orEmpty() }
            .filter { it.height != null }
            .distinctBy { it.height }
            .sortedByDescending { it.height ?: 0 }
    }
    val planQualities = if (sampledQualitiesByVoice == null) emptySet() else selectedQualities
    var coveragesResult by remember(videos) { mutableStateOf<List<DownloadVoiceCoverage>?>(null) }
    val coverages = coveragesResult.orEmpty()
    var voiceOrder by remember(videos, selectedVoiceKey) { mutableStateOf<List<String>>(emptyList()) }
    val coverageByKey = remember(coverages) { coverages.associateBy { it.voiceKey } }
    val normalizedVoiceOrder = remember(voiceOrder, coverages) {
        normalizeDownloadVoiceOrder(voiceOrder, coverages)
    }
    val voiceEpisodeSelectionResults = remember(voiceEpisodeRanges, coverageByKey) {
        voiceEpisodeRanges.mapValues { (voiceKey, value) ->
            coverageByKey[voiceKey]?.let { coverage ->
                validateDownloadEpisodeSelection(value, coverage.availableEpisodeRanges)
            } ?: parseDownloadEpisodeSelection(value)
        }
    }
    val rangeErrorsByVoice = remember(voiceEpisodeSelectionResults) {
        voiceEpisodeSelectionResults.mapNotNull { (voiceKey, result) ->
            result.error?.let { error -> voiceKey to error }
        }.toMap()
    }
    val episodeSelectionsByVoice = remember(voiceEpisodeSelectionResults) {
        voiceEpisodeSelectionResults.mapNotNull { (voiceKey, result) ->
            result.selection.takeIf { selection -> result.error == null && selection.isRestricted }
                ?.let { selection -> voiceKey to selection }
        }.toMap()
    }
    val orderedCoverages = remember(normalizedVoiceOrder, coverageByKey) {
        normalizedVoiceOrder.mapNotNull { coverageByKey[it] }
    }
    val selectedOrderedCoverages = remember(orderedCoverages, selectedVoices) {
        orderedCoverages.filter { it.voiceKey in selectedVoices }
    }
    val voiceStepReady = coveragesResult != null && orderedCoverages.isNotEmpty() && selectedVoices.isNotEmpty()
    val episodesStepReady = rangeErrorsByVoice.isEmpty() && selectedOrderedCoverages.isNotEmpty()
    val qualityStepReady = selectedQualities.isNotEmpty() &&
        rangeErrorsByVoice.isEmpty() &&
        planResult?.scheduledCount?.let { it > 0 } == true

    LaunchedEffect(videos, selectedVoices) {
        sampledQualitiesByVoice = null
        planResult = null
        qualityError = null
    }

    LaunchedEffect(step, qualityProbeVoiceKeys, videos) {
        if (step != DownloadPlanStep.Quality) return@LaunchedEffect
        sampledQualitiesByVoice = null
        planResult = null
        qualityError = null
        if (qualityProbeVoiceKeys.isEmpty()) {
            sampledQualitiesByVoice = emptyMap()
            return@LaunchedEffect
        }
        runCatching { onResolveSampledQualities(qualityProbeVoiceKeys, videos) }
            .onSuccess { qualities -> sampledQualitiesByVoice = qualities }
            .onFailure { throwable ->
                sampledQualitiesByVoice = emptyMap()
                qualityError = throwable.message?.takeIf { it.isNotBlank() }
            }
    }

    LaunchedEffect(videos, selectedQualities, selectedVoiceKey, resolvedQualitiesByVoice) {
        coveragesResult = null
        coveragesResult = withContext(Dispatchers.Default) {
            buildDownloadVoiceCoverages(
                videos = videos,
                acceptableQualities = selectedQualities,
                selectedVoiceKey = selectedVoiceKey,
                resolvedQualitiesByVoice = resolvedQualitiesByVoice,
            )
        }
    }

    LaunchedEffect(qualityOptions, selected, sampledQualitiesByVoice) {
        if (sampledQualitiesByVoice == null) return@LaunchedEffect
        if (qualityOptions.isEmpty()) return@LaunchedEffect
        val retained = selectedQualities.filterTo(mutableSetOf()) { it in qualityOptions }
        selectedQualities = retained.ifEmpty {
            selected.takeIf { quality -> quality.height != null && quality in qualityOptions }
                ?.let(::setOf)
                ?: setOf(qualityOptions.first())
        }
    }

    LaunchedEffect(
        animeId,
        animeTitle,
        videos,
        planQualities,
        selectedVoices,
        normalizedVoiceOrder,
        onlyMissing,
        episodeSelectionsByVoice,
        rangeErrorsByVoice,
        sampledQualitiesByVoice,
        coveragesResult,
        step,
    ) {
        planResult = null
        if (
            step != DownloadPlanStep.Quality ||
            sampledQualitiesByVoice == null ||
            coveragesResult == null ||
            rangeErrorsByVoice.isNotEmpty()
        ) {
            return@LaunchedEffect
        }
        planResult = withContext(Dispatchers.Default) {
            buildDownloadPlan(
                animeId = animeId,
                animeTitle = animeTitle,
                videos = videos,
                acceptableQualities = planQualities,
                selectedVoiceKeys = selectedVoices,
                voiceOrder = normalizedVoiceOrder,
                onlyMissing = onlyMissing,
                episodeSelectionsByVoice = episodeSelectionsByVoice,
            )
        }
    }

    DownloadPlanDialogContent(
        state = DownloadPlanDialogUiState(
            step = step,
            coveragesResult = coveragesResult,
            orderedCoverages = orderedCoverages,
            selectedOrderedCoverages = selectedOrderedCoverages,
            selectedVoices = selectedVoices,
            normalizedVoiceOrder = normalizedVoiceOrder,
            voiceEpisodeRanges = voiceEpisodeRanges,
            rangeErrorsByVoice = rangeErrorsByVoice,
            onlyMissing = onlyMissing,
            qualityOptions = qualityOptions,
            selectedQualities = selectedQualities,
            sampledQualitiesByVoice = sampledQualitiesByVoice,
            qualityError = qualityError,
            planResult = planResult,
            voiceStepReady = voiceStepReady,
            episodesStepReady = episodesStepReady,
            qualityStepReady = qualityStepReady,
        ),
        actions = DownloadPlanDialogUiActions(
            onDismiss = onDismiss,
            onStepChange = { newStep -> step = newStep },
            onOnlyMissingToggle = { onlyMissing = !onlyMissing },
            onVoiceSelectedChange = { voiceKey, checked ->
                selectedVoices = if (checked) selectedVoices + voiceKey else selectedVoices - voiceKey
            },
            onMoveVoice = { voiceKey, delta ->
                voiceOrder = moveDownloadVoice(normalizedVoiceOrder, voiceKey, delta)
            },
            onEpisodeRangeChange = { voiceKey, value ->
                voiceEpisodeRanges = voiceEpisodeRanges + (voiceKey to value)
            },
            onQualityToggle = { quality ->
                selectedQualities = if (quality in selectedQualities) {
                    selectedQualities - quality
                } else {
                    selectedQualities + quality
                }
            },
            onConfirm = { result -> result.plan?.let(onConfirm) },
        ),
    )
}
