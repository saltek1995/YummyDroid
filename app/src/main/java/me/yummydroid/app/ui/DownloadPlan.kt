package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.yummydroid.app.R
import me.yummydroid.app.localizedString
import me.yummydroid.app.DownloadEpisodeSelection
import me.yummydroid.app.DownloadEpisodeSelectionError
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.DownloadPlanBuildResult
import me.yummydroid.app.DownloadVoiceCoverage
import me.yummydroid.app.buildDownloadPlan
import me.yummydroid.app.downloadPlanSelectedVideos
import me.yummydroid.app.buildDownloadVoiceCoverages
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.DownloadSourceCoolingDown
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.downloadSourceKey
import me.yummydroid.app.data.downloadQualitySamples
import me.yummydroid.app.data.withoutLocalPlayback
import me.yummydroid.app.data.sourceProviderRank
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.downloadPlanVoiceKey
import me.yummydroid.app.data.siteDefaultVoiceKey
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.validateDownloadEpisodeSelection

// DownloadEpisodeRangeField
@Composable
internal fun DownloadEpisodeSelectionError.localizedMessage(): String = when (this) {
    is DownloadEpisodeSelectionError.InvalidEpisodeNumber ->
        uiText(UiStringKey.EpisodeNumberInvalid, token)
    is DownloadEpisodeSelectionError.InvalidEpisodeRange ->
        uiText(UiStringKey.EpisodeRangeInvalid, token)
    is DownloadEpisodeSelectionError.MissingEpisodes ->
        uiText(UiStringKey.VoiceHasNoEpisodes, ranges)
}

@Composable
internal fun DownloadEpisodeRangeField(
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = uiText(UiStringKey.Episodes),
            style = MaterialTheme.typography.labelSmall,
            color = if (error == null) YummyColors.focus else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Black,
        )
        Surface(
            color = if (error == null) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.32f)
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = YummyRadii.smallShape,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 42.dp)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        if (value.isBlank()) {
                            Text(
                                text = uiText(UiStringKey.AllEf8ff2),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// DownloadPlanDialogComponents
@Composable
internal fun DownloadPlanStep.title(): String = when (this) {
    DownloadPlanStep.Voice -> uiText(UiStringKey.ChooseVoice)
    DownloadPlanStep.Source -> uiText(UiStringKey.Source)
    DownloadPlanStep.Episodes -> uiText(UiStringKey.Episodes)
    DownloadPlanStep.Quality -> uiText(UiStringKey.Quality)
}

@Composable
internal fun DownloadPlanSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = YummyColors.focus,
        fontWeight = FontWeight.Black,
    )
}

@Composable
internal fun DownloadPlanToggleMark(selected: Boolean) {
    Surface(
        color = yummyActionSurfaceColor(selected = selected),
        contentColor = yummyActionContentColor(selected = selected),
        border = yummyActionBorder(selected = selected),
        shape = YummyRadii.smallShape,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.padding(5.dp).size(16.dp),
            )
        } else {
            Box(modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
internal fun DownloadPlanProgressMessage(text: String) {
    Surface(
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = YummyRadii.smallShape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = YummyColors.focus,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// DownloadPlanDialogContent
internal enum class DownloadPlanStep {
    Voice,
    Source,
    Episodes,
    Quality,
}

internal fun DownloadPlanStep.previous(): DownloadPlanStep = when (this) {
    DownloadPlanStep.Voice -> DownloadPlanStep.Voice
    DownloadPlanStep.Source -> DownloadPlanStep.Voice
    DownloadPlanStep.Episodes -> DownloadPlanStep.Source
    DownloadPlanStep.Quality -> DownloadPlanStep.Episodes
}

internal fun DownloadPlanStep.next(): DownloadPlanStep = when (this) {
    DownloadPlanStep.Voice -> DownloadPlanStep.Source
    DownloadPlanStep.Source -> DownloadPlanStep.Episodes
    DownloadPlanStep.Episodes -> DownloadPlanStep.Quality
    DownloadPlanStep.Quality -> DownloadPlanStep.Quality
}

internal fun DownloadPlanStep.canProceed(
    voiceStepReady: Boolean,
    sourceStepReady: Boolean,
    episodesStepReady: Boolean,
    qualityStepReady: Boolean,
): Boolean = when (this) {
    DownloadPlanStep.Voice -> voiceStepReady
    DownloadPlanStep.Source -> sourceStepReady
    DownloadPlanStep.Episodes -> episodesStepReady
    DownloadPlanStep.Quality -> qualityStepReady
}

internal data class DownloadPlanDialogUiState(
    val step: DownloadPlanStep,
    val coverages: List<DownloadVoiceCoverage>,
    val selectedCoverage: DownloadVoiceCoverage?,
    val selectedVoiceKey: String?,
    val sources: List<DownloadPlanSourceChoice>,
    val selectedSourceKey: String?,
    val episodeRange: String,
    val rangeError: DownloadEpisodeSelectionError?,
    val onlyMissing: Boolean,
    val qualityOptions: List<PreferredQuality>,
    val selectedQuality: PreferredQuality?,
    val qualitiesResolved: Boolean,
    val qualityError: String?,
    val planResult: DownloadPlanBuildResult?,
    val voiceStepReady: Boolean,
    val sourceStepReady: Boolean,
    val episodesStepReady: Boolean,
    val qualityStepReady: Boolean,
)

internal data class DownloadPlanSourceChoice(
    val key: String,
    val title: String,
    val coverage: DownloadVoiceCoverage,
)

internal fun downloadPlanSourceChoices(voiceVideos: List<VideoVariant>): List<DownloadPlanSourceChoice> =
    voiceVideos.groupBy { it.downloadSourceKey }.map { (key, videos) ->
        DownloadPlanSourceChoice(
            key = key,
            title = videos.first().player.cleanVideoSourceLabel(),
            coverage = buildDownloadVoiceCoverages(videos, emptyList()).single(),
        )
    }.sortedBy { sourceProviderRank(it.title) }

internal class DownloadPlanDialogUiActions(
    val onDismiss: () -> Unit,
    val onStepChange: (DownloadPlanStep) -> Unit,
    val onOnlyMissingToggle: () -> Unit,
    val onVoiceSelected: (String) -> Unit,
    val onSourceSelected: (String) -> Unit,
    val onEpisodeRangeChange: (String) -> Unit,
    val onQualitySelected: (PreferredQuality) -> Unit,
    val onConfirm: (DownloadPlanBuildResult) -> Unit,
)

@Composable
internal fun DownloadPlanDialogContent(
    state: DownloadPlanDialogUiState,
    actions: DownloadPlanDialogUiActions,
) {
    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = actions.onDismiss,
        title = { DownloadPlanDialogTitle(state.step) },
        text = { DownloadPlanDialogStepContent(state, actions) },
        confirmButton = { DownloadPlanDialogActions(state, actions) },
    )
}

@Composable
private fun DownloadPlanDialogTitle(step: DownloadPlanStep) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(uiText(UiStringKey.DownloadPlan))
        Text(
            text = "${step.ordinal + 1}/${DownloadPlanStep.entries.size} • ${step.title()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DownloadPlanDialogStepContent(
    state: DownloadPlanDialogUiState,
    actions: DownloadPlanDialogUiActions,
) {
    val navigationScrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyColumn(
        state = navigationScrollState,
        modifier = Modifier.navigationScrollRegion(navigationScrollState)
            .fillMaxWidth()
            .heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(YummySpacing.md),
    ) {
        when (state.step) {
            DownloadPlanStep.Voice -> downloadPlanVoiceItems(state, actions)
            DownloadPlanStep.Source -> downloadPlanSourceItems(state, actions)
            DownloadPlanStep.Episodes -> downloadPlanEpisodeItems(state, actions)
            DownloadPlanStep.Quality -> downloadPlanQualityItems(state, actions)
        }
    }
}

@Composable
private fun DownloadPlanDialogActions(
    state: DownloadPlanDialogUiState,
    actions: DownloadPlanDialogUiActions,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DialogActionButton(
            text = uiText(UiStringKey.Cancel),
            onClick = actions.onDismiss,
            modifier = Modifier.weight(1f),
            compact = true,
        )
        if (state.step != DownloadPlanStep.Voice) {
            DialogActionButton(
                text = uiText(UiStringKey.Back),
                onClick = { actions.onStepChange(state.step.previous()) },
                modifier = Modifier.weight(1f),
                compact = true,
            )
        }
        DialogActionButton(
            text = if (state.step == DownloadPlanStep.Quality) {
                uiText(UiStringKey.Download)
            } else {
                uiText(UiStringKey.Next6ff11d)
            },
            primary = true,
            modifier = Modifier.weight(1f),
            compact = true,
            enabled = state.step.canProceed(
                voiceStepReady = state.voiceStepReady,
                sourceStepReady = state.sourceStepReady,
                episodesStepReady = state.episodesStepReady,
                qualityStepReady = state.qualityStepReady,
            ),
            onClick = {
                if (state.step == DownloadPlanStep.Quality) {
                    state.planResult?.let(actions.onConfirm)
                } else {
                    actions.onStepChange(state.step.next())
                }
            },
        )
    }
}

private fun LazyListScope.downloadPlanVoiceItems(
    state: DownloadPlanDialogUiState,
    actions: DownloadPlanDialogUiActions,
) {
    if (state.coverages.isEmpty()) {
        item("voices-empty") {
            InlineErrorMessage(message = uiText(UiStringKey.NoVoicesAreAvailableForDownload))
        }
    }
    items(state.coverages, key = { "voice:${it.voiceKey}" }) { coverage ->
        DownloadPlanChoiceRow(
            title = coverage.title,
            subtitle = coverage.subtitle(),
            selected = coverage.voiceKey == state.selectedVoiceKey,
            onClick = { actions.onVoiceSelected(coverage.voiceKey) },
        )
    }
}

private fun LazyListScope.downloadPlanSourceItems(
    state: DownloadPlanDialogUiState,
    actions: DownloadPlanDialogUiActions,
) {
    items(state.sources, key = { "source:${it.key}" }) { source ->
        DownloadPlanChoiceRow(
            title = source.title,
            subtitle = source.coverage.subtitle(),
            selected = source.key == state.selectedSourceKey,
            onClick = { actions.onSourceSelected(source.key) },
        )
    }
}

private fun LazyListScope.downloadPlanEpisodeItems(
    state: DownloadPlanDialogUiState,
    actions: DownloadPlanDialogUiActions,
) {
    item("only-missing") {
        DownloadMissingOnlyRow(selected = state.onlyMissing, onClick = actions.onOnlyMissingToggle)
    }
    item("episodes") {
        Column(verticalArrangement = Arrangement.spacedBy(YummySpacing.sm)) {
            state.selectedCoverage?.let { coverage ->
                DownloadPlanSectionTitle(coverage.title)
                Text(coverage.subtitle(), style = MaterialTheme.typography.bodySmall)
                DownloadVoiceRanges(coverage.ranges)
            }
            DownloadEpisodeRangeField(
                value = state.episodeRange,
                error = state.rangeError?.localizedMessage(),
                onValueChange = actions.onEpisodeRangeChange,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.downloadPlanQualityItems(
    state: DownloadPlanDialogUiState,
    actions: DownloadPlanDialogUiActions,
) {
    item("quality") {
        Column(verticalArrangement = Arrangement.spacedBy(YummySpacing.sm)) {
            DownloadPlanSectionTitle(uiText(UiStringKey.Quality))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
            ) {
                state.qualityOptions.forEach { quality ->
                    DownloadPlanQualityChip(
                        quality = quality,
                        selected = quality == state.selectedQuality,
                        onClick = { actions.onQualitySelected(quality) },
                    )
                }
            }
            when {
                !state.qualitiesResolved ->
                    DownloadPlanProgressMessage(text = uiText(UiStringKey.CheckingAvailableQuality))
                state.qualityError != null -> InlineErrorMessage(
                    message = state.qualityError,
                    modifier = Modifier.padding(top = 4.dp),
                )
                state.qualityOptions.isEmpty() -> InlineErrorMessage(
                    message = state.qualityError
                        ?: uiText(UiStringKey.NoAvailableQualityFoundForSelectedVoices),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
    item("summary") {
        when {
            state.qualityError != null || (state.qualitiesResolved && state.qualityOptions.isEmpty()) -> Unit
            state.rangeError != null -> InlineErrorMessage(
                message = uiText(UiStringKey.FixEpisodeRanges),
            )
            !state.qualitiesResolved || state.planResult == null ->
                DownloadPlanProgressMessage(text = uiText(UiStringKey.PreparingDownloadPlan))
            else -> DownloadPlanSummary(result = state.planResult)
        }
    }
}

// DownloadPlanDialogState
internal data class DownloadPlanQualityResult(
    val samples: List<VideoVariant>,
    val qualities: Map<String, List<PreferredQuality>>,
    val error: String? = null,
)

internal data class DownloadPlanBuildRequest(
    val animeId: Long,
    val animeTitle: String,
    val videos: List<VideoVariant>,
    val quality: PreferredQuality,
    val voice: String,
    val source: String,
    val episodes: DownloadEpisodeSelection,
    val onlyMissing: Boolean,
) {
    fun build(): DownloadPlanBuildResult = buildDownloadPlan(
        animeId = animeId,
        animeTitle = animeTitle,
        videos = videos,
        acceptableQualities = listOf(quality),
        selectedVoiceKeys = setOf(voice),
        voiceOrder = listOf(voice),
        onlyMissing = onlyMissing,
        episodeSelectionsByVoice = mapOf(voice to episodes),
        selectedSourcesByVoice = mapOf(voice to setOf(source)),
    )
}

internal class DownloadPlanDialogMutableState(
    private val videos: List<VideoVariant>,
    selectedVideo: VideoVariant?,
    selectedQuality: PreferredQuality,
) {
    var step by mutableStateOf(DownloadPlanStep.Voice)
    var onlyMissing by mutableStateOf(true)
    var selectedVoiceKey by mutableStateOf(
        selectedVideo?.downloadPlanVoiceKey?.takeIf { key -> videos.any { it.downloadPlanVoiceKey == key } }
            ?: videos.siteDefaultVoiceKey(),
    )
        private set
    var selectedSourceKey by mutableStateOf(initialSource(selectedVoiceKey, selectedVideo))
        private set
    var episodeRange by mutableStateOf("")
    var selectedQuality by mutableStateOf(selectedQuality.takeIf { it.height != null })
    var qualityResult by mutableStateOf<DownloadPlanQualityResult?>(null)
    var builtPlan by mutableStateOf<Pair<DownloadPlanBuildRequest, DownloadPlanBuildResult>?>(null)

    fun selectVoice(voice: String) {
        if (voice == selectedVoiceKey || videos.none { it.downloadPlanVoiceKey == voice }) return
        selectedVoiceKey = voice
        selectedSourceKey = initialSource(voice)
    }

    fun selectSource(source: String) {
        if (videos.any { it.downloadPlanVoiceKey == selectedVoiceKey && it.downloadSourceKey == source }) {
            selectedSourceKey = source
        }
    }

    private fun initialSource(voice: String?, selectedVideo: VideoVariant? = null): String? =
        videos.filter { it.downloadPlanVoiceKey == voice }
            .minWithOrNull(compareBy<VideoVariant> { if (it.id == selectedVideo?.id) 0 else 1 }
                .thenBy { sourceProviderRank(it.player) }.thenBy { it.index })?.downloadSourceKey
}

internal fun downloadPlanQualityOptions(
    resolvedQualitiesByVoice: Map<String, List<PreferredQuality>>,
    selectedVoice: String?,
): List<PreferredQuality> = resolvedQualitiesByVoice[selectedVoice].orEmpty()
    .filter { it.height != null }.distinctBy { it.height }.sortedByDescending { it.height ?: 0 }

internal fun shouldBuildDownloadPlan(
    step: DownloadPlanStep,
    qualitiesResolved: Boolean,
    coveragesLoaded: Boolean,
    hasRangeErrors: Boolean,
): Boolean = step == DownloadPlanStep.Quality && qualitiesResolved && coveragesLoaded && !hasRangeErrors

// Async results include their inputs so stale work cannot enable Download.
@Composable
private fun downloadPlanPresentation(
    animeId: Long,
    animeTitle: String,
    videos: List<VideoVariant>,
    state: DownloadPlanDialogMutableState,
): Pair<DownloadPlanDialogUiState, List<VideoVariant>> {
    val allCoverages = remember(videos) { buildDownloadVoiceCoverages(videos, emptyList()) }
    val voice = state.selectedVoiceKey
    val source = state.selectedSourceKey
    val voiceVideos = remember(videos, voice) { videos.filter { it.downloadPlanVoiceKey == voice } }
    val sources = remember(voiceVideos) { downloadPlanSourceChoices(voiceVideos) }
    val selectedVideos = remember(voiceVideos, source) { voiceVideos.filter { it.downloadSourceKey == source } }
    val coverage = sources.firstOrNull { it.key == source }?.coverage
    val selection = validateDownloadEpisodeSelection(state.episodeRange, coverage?.availableEpisodeRanges.orEmpty())
    val samples = remember(selectedVideos, selection) {
        if (selection.error != null || voice == null || source == null) emptyList() else downloadPlanSelectedVideos(
            selectedVideos, setOf(voice), mapOf(voice to setOf(source)), mapOf(voice to selection.selection),
        ).downloadQualitySamples().map(VideoVariant::withoutLocalPlayback)
    }
    val qualities = state.qualityResult?.takeIf { it.samples == samples }
    val options = downloadPlanQualityOptions(qualities?.qualities.orEmpty(), voice)
    val quality = state.selectedQuality?.takeIf { it in options }
    val sourceReady = coverage != null && voice != null && source != null
    val canBuild = shouldBuildDownloadPlan(state.step, qualities != null && qualities.error == null, sourceReady, selection.error != null)
    val request = if (canBuild && voice != null && source != null && quality != null) DownloadPlanBuildRequest(
        animeId, animeTitle, videos, quality, voice, source, selection.selection, state.onlyMissing,
    ) else null
    LaunchedEffect(request) {
        if (request == null || state.builtPlan?.first == request) return@LaunchedEffect
        val built = withContext(Dispatchers.Default) { request.build() }
        currentCoroutineContext().ensureActive()
        state.builtPlan = request to built
    }
    val plan = state.builtPlan?.takeIf { it.first == request }?.second
    return DownloadPlanDialogUiState(
        step = state.step,
        coverages = allCoverages,
        selectedCoverage = coverage,
        selectedVoiceKey = voice,
        sources = sources,
        selectedSourceKey = source,
        episodeRange = state.episodeRange,
        rangeError = selection.error,
        onlyMissing = state.onlyMissing,
        qualityOptions = options,
        selectedQuality = quality,
        qualitiesResolved = qualities != null,
        qualityError = qualities?.error,
        planResult = plan,
        voiceStepReady = voiceVideos.isNotEmpty(),
        sourceStepReady = sourceReady,
        episodesStepReady = sourceReady && selection.error == null && samples.isNotEmpty(),
        qualityStepReady = canBuild && quality != null && plan?.scheduledCount?.let { it > 0 } == true,
    ) to samples
}

@Composable
private fun DownloadPlanQualityProbeEffect(
    state: DownloadPlanDialogMutableState,
    samples: List<VideoVariant>,
    selected: PreferredQuality,
    onResolveSampledQualities: suspend (Set<String>, List<VideoVariant>) -> Map<String, List<PreferredQuality>>,
) {
    val context = LocalContext.current
    val language = LocalUiLanguage.current
    val failureMessage = uiText(UiStringKey.SomeSourcesDidNotRespond)
    LaunchedEffect(state.step, samples) {
        if (state.step != DownloadPlanStep.Quality) return@LaunchedEffect
        if (state.qualityResult?.let { it.samples == samples && it.error == null } == true) return@LaunchedEffect
        val voices = samples.mapTo(mutableSetOf()) { it.downloadPlanVoiceKey }
        val result = runCatching { onResolveSampledQualities(voices, samples) }
        currentCoroutineContext().ensureActive()
        val error = result.exceptionOrNull()
        if (error is CancellationException) throw error
        val qualities = result.getOrDefault(emptyMap())
        val options = downloadPlanQualityOptions(qualities, state.selectedVoiceKey)
        state.selectedQuality = state.selectedQuality?.takeIf { it in options }
            ?: selected.takeIf { it in options } ?: options.firstOrNull()
        state.qualityResult = DownloadPlanQualityResult(samples, qualities, when (error) {
            is DownloadSourceCoolingDown -> context.localizedString(R.string.ui_download_source_cooldown, language, ((error.retryAtMs - System.currentTimeMillis()).coerceAtLeast(0L) + 59_999L) / 60_000L)
            null -> null
            else -> failureMessage
        })
    }
}

@Composable
internal fun DownloadPlanDialog(
    animeId: Long,
    animeTitle: String,
    videos: List<VideoVariant>,
    selectedVideo: VideoVariant?,
    selected: PreferredQuality,
    onResolveSampledQualities: suspend (Set<String>, List<VideoVariant>) -> Map<String, List<PreferredQuality>>,
    onConfirm: (DownloadPlan) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = remember(videos) { videos.map(VideoVariant::withoutLocalPlayback) }
    val state = remember(animeId, choices) { DownloadPlanDialogMutableState(choices, selectedVideo, selected) }
    val (presentation, samples) = downloadPlanPresentation(animeId, animeTitle, videos, state)
    DownloadPlanQualityProbeEffect(state, samples, selected, onResolveSampledQualities)
    DownloadPlanDialogContent(presentation, DownloadPlanDialogUiActions(
        onDismiss = onDismiss,
        onStepChange = { state.step = it },
        onOnlyMissingToggle = { state.onlyMissing = !state.onlyMissing },
        onVoiceSelected = state::selectVoice,
        onSourceSelected = state::selectSource,
        onEpisodeRangeChange = { state.episodeRange = it },
        onQualitySelected = { state.selectedQuality = it },
        onConfirm = { result -> result.plan?.let(onConfirm) },
    ))
}

// DownloadPlanSelectionComponents
@Composable
internal fun DownloadMissingOnlyRow(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = YummyRadii.smallShape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier
                        .background(yummyActionSurfaceColor(selected = true), shape)
                        .border(yummyActionBorder(selected = true), shape)
                } else {
                    Modifier
                },
            )
            .dpadClickable(shape, onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
    ) {
        DownloadPlanToggleMark(selected = selected)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = uiText(UiStringKey.DownloadMissingEpisodesOnly),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = uiText(UiStringKey.AlreadyDownloadedEpisodesWithTheSameQualityWillBeSkipped),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun DownloadPlanQualityChip(
    quality: PreferredQuality,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = YummyRadii.pillShape
    Surface(
        modifier = Modifier.semantics { role = Role.RadioButton; this.selected = selected }.dpadClickable(shape, onClick),
        color = yummyActionSurfaceColor(selected = selected),
        contentColor = yummyActionContentColor(selected = selected),
        border = yummyActionBorder(selected = selected),
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.size(20.dp))
            Text(
                text = quality.localizedTitle(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// DownloadPlanSummary
@Composable
internal fun DownloadPlanSummary(result: DownloadPlanBuildResult) {
    Surface(
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = YummyRadii.smallShape,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DownloadPlanSummaryLine(
                title = uiText(UiStringKey.ToQueue),
                value = "${result.scheduledCount} ${localizedEpisodesWord(result.scheduledCount)}",
                accent = result.scheduledCount > 0,
            )
            DownloadPlanSummaryLine(uiText(UiStringKey.TotalEpisodes), result.totalEpisodes.toString())
            if (result.alreadyDownloaded > 0) {
                DownloadPlanSummaryLine(uiText(UiStringKey.AlreadyDownloaded), result.alreadyDownloaded.toString())
            }
            if (result.missingInSelectedVoices > 0) {
                DownloadPlanSummaryLine(
                    uiText(UiStringKey.NotAvailableInSelectedVoices),
                    result.missingInSelectedVoices.toString(),
                )
            }
            if (result.missingSelectedQuality > 0) {
                DownloadPlanSummaryLine(
                    uiText(UiStringKey.SelectedQualityIsUnavailable),
                    result.missingSelectedQuality.toString(),
                )
            }
            if (result.excludedByEpisodeSelection > 0) {
                DownloadPlanSummaryLine(
                    uiText(UiStringKey.ExcludedByRanges),
                    result.excludedByEpisodeSelection.toString(),
                )
            }
        }
    }
}

@Composable
private fun DownloadPlanSummaryLine(
    title: String,
    value: String,
    accent: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (accent) YummyColors.focus else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun DownloadPlanChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    val shape = YummyRadii.smallShape
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(yummyActionSurfaceColor(selected = selected), shape)
            .semantics { role = Role.RadioButton; this.selected = selected }
            .dpadClickable(shape, onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DownloadVoiceRanges(ranges: List<String>) {
    val text = ranges.joinToString(", ").let { joined ->
        if (joined.length > 120) joined.take(117).trimEnd() + "..." else joined
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DownloadVoiceCoverage.subtitle(): String = buildList {
    add("$episodeCount ${localizedEpisodesWord(episodeCount)}")
    if (downloadedCount > 0) add("${uiText(UiStringKey.DownloadedFae287)} $downloadedCount")
}.joinToString(" \u2022 ")
