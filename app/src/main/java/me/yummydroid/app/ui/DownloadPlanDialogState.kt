package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import me.yummydroid.app.DownloadPlanBuildResult
import me.yummydroid.app.DownloadVoiceCoverage
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.siteDefaultVoiceKey

internal class DownloadPlanDialogMutableState(
    stepState: MutableState<DownloadPlanStep>,
    onlyMissingState: MutableState<Boolean>,
    sampledQualitiesState: MutableState<Map<String, List<PreferredQuality>>?>,
    qualityErrorState: MutableState<String?>,
    planResultState: MutableState<DownloadPlanBuildResult?>,
    selectedQualitiesState: MutableState<Set<PreferredQuality>>,
    voiceEpisodeRangesState: MutableState<Map<String, String>>,
    selectedVoicesState: MutableState<Set<String>>,
    coveragesResultState: MutableState<List<DownloadVoiceCoverage>?>,
    voiceOrderState: MutableState<List<String>>,
) {
    var step by stepState
    var onlyMissing by onlyMissingState
    var sampledQualitiesByVoice by sampledQualitiesState
    var qualityError by qualityErrorState
    var planResult by planResultState
    var selectedQualities by selectedQualitiesState
    var voiceEpisodeRanges by voiceEpisodeRangesState
    var selectedVoices by selectedVoicesState
    var coveragesResult by coveragesResultState
    var voiceOrder by voiceOrderState
}

@Composable
internal fun rememberDownloadPlanDialogMutableState(
    videos: List<VideoVariant>,
    selected: PreferredQuality,
    selectedVoiceKey: String?,
): DownloadPlanDialogMutableState {
    val stepState = remember(videos) { mutableStateOf(DownloadPlanStep.Voice) }
    val onlyMissingState = remember { mutableStateOf(true) }
    val sampledQualitiesState = remember(videos) {
        mutableStateOf<Map<String, List<PreferredQuality>>?>(null)
    }
    val qualityErrorState = remember(videos) { mutableStateOf<String?>(null) }
    val planResultState = remember(videos) { mutableStateOf<DownloadPlanBuildResult?>(null) }
    val selectedQualitiesState = remember(videos, selected) {
        mutableStateOf(setOfNotNull(selected.takeIf { it.height != null }))
    }
    val voiceEpisodeRangesState = remember(videos) { mutableStateOf<Map<String, String>>(emptyMap()) }
    val selectedVoicesState = remember(videos, selectedVoiceKey) {
        mutableStateOf(initialDownloadPlanVoices(videos, selectedVoiceKey))
    }
    val coveragesResultState = remember(videos) {
        mutableStateOf<List<DownloadVoiceCoverage>?>(null)
    }
    val voiceOrderState = remember(videos, selectedVoiceKey) { mutableStateOf<List<String>>(emptyList()) }
    return DownloadPlanDialogMutableState(
        stepState,
        onlyMissingState,
        sampledQualitiesState,
        qualityErrorState,
        planResultState,
        selectedQualitiesState,
        voiceEpisodeRangesState,
        selectedVoicesState,
        coveragesResultState,
        voiceOrderState,
    )
}

internal fun initialDownloadPlanVoices(
    videos: List<VideoVariant>,
    selectedVoiceKey: String?,
): Set<String> {
    return selectedVoiceKey
        ?.takeIf { it.isNotBlank() }
        ?.let(::setOf)
        ?: videos.siteDefaultVoiceKey()?.let(::setOf)
        ?: emptySet()
}
