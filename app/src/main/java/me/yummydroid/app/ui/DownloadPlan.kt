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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.yummydroid.app.DownloadEpisodeSelection
import me.yummydroid.app.DownloadEpisodeSelectionError
import me.yummydroid.app.DownloadEpisodeSelectionParseResult
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
    Episodes,
    Quality,
}

internal fun DownloadPlanStep.previous(): DownloadPlanStep = when (this) {
    DownloadPlanStep.Voice -> DownloadPlanStep.Voice
    DownloadPlanStep.Episodes -> DownloadPlanStep.Voice
    DownloadPlanStep.Quality -> DownloadPlanStep.Episodes
}

internal fun DownloadPlanStep.next(): DownloadPlanStep = when (this) {
    DownloadPlanStep.Voice -> DownloadPlanStep.Episodes
    DownloadPlanStep.Episodes -> DownloadPlanStep.Quality
    DownloadPlanStep.Quality -> DownloadPlanStep.Quality
}

internal fun DownloadPlanStep.canProceed(
    voiceStepReady: Boolean,
    episodesStepReady: Boolean,
    qualityStepReady: Boolean,
): Boolean = when (this) {
    DownloadPlanStep.Voice -> voiceStepReady
    DownloadPlanStep.Episodes -> episodesStepReady
    DownloadPlanStep.Quality -> qualityStepReady
}

internal fun normalizeDownloadVoiceOrder(
    currentOrder: List<String>,
    coverages: List<DownloadVoiceCoverage>,
): List<String> {
    val available = coverages.map { it.voiceKey }.toSet()
    return (currentOrder.filter { it in available } + coverages.map { it.voiceKey }).distinct()
}

internal fun moveDownloadVoice(
    currentOrder: List<String>,
    voiceKey: String,
    delta: Int,
): List<String> {
    val current = currentOrder.toMutableList()
    val index = current.indexOf(voiceKey)
    if (index < 0 || current.isEmpty()) return currentOrder
    val target = (index + delta).coerceIn(current.indices)
    if (index == target) return currentOrder
    current.removeAt(index)
    current.add(target, voiceKey)
    return current
}

internal data class DownloadPlanDialogUiState(
    val step: DownloadPlanStep,
    val coveragesResult: List<DownloadVoiceCoverage>?,
    val orderedCoverages: List<DownloadVoiceCoverage>,
    val selectedOrderedCoverages: List<DownloadVoiceCoverage>,
    val selectedVoices: Set<String>,
    val normalizedVoiceOrder: List<String>,
    val voiceEpisodeRanges: Map<String, String>,
    val rangeErrorsByVoice: Map<String, DownloadEpisodeSelectionError>,
    val onlyMissing: Boolean,
    val qualityOptions: List<PreferredQuality>,
    val selectedQualities: Set<PreferredQuality>,
    val sampledQualitiesByVoice: Map<String, List<PreferredQuality>>?,
    val qualityError: String?,
    val planResult: DownloadPlanBuildResult?,
    val voiceStepReady: Boolean,
    val episodesStepReady: Boolean,
    val qualityStepReady: Boolean,
)

internal class DownloadPlanDialogUiActions(
    val onDismiss: () -> Unit,
    val onStepChange: (DownloadPlanStep) -> Unit,
    val onOnlyMissingToggle: () -> Unit,
    val onVoiceSelectedChange: (String, Boolean) -> Unit,
    val onMoveVoice: (String, Int) -> Unit,
    val onEpisodeRangeChange: (String, String) -> Unit,
    val onQualityToggle: (PreferredQuality) -> Unit,
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
            text = "${step.ordinal + 1}/3 • ${step.title()}",
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
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(YummySpacing.md),
    ) {
        when (state.step) {
            DownloadPlanStep.Voice -> downloadPlanVoiceItems(state, actions)
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
    DialogActionRow {
        DialogActionButton(
            text = uiText(UiStringKey.Cancel),
            onClick = actions.onDismiss,
        )
        if (state.step != DownloadPlanStep.Voice) {
            DialogActionButton(
                text = uiText(UiStringKey.Back),
                onClick = { actions.onStepChange(state.step.previous()) },
            )
        }
        DialogActionButton(
            text = if (state.step == DownloadPlanStep.Quality) {
                uiText(UiStringKey.Download)
            } else {
                uiText(UiStringKey.Next6ff11d)
            },
            primary = true,
            enabled = state.step.canProceed(
                voiceStepReady = state.voiceStepReady,
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
    item("voices-title") {
        DownloadPlanSectionTitle(uiText(UiStringKey.VoicesAndPriority))
    }
    when {
        state.coveragesResult == null -> item("voices-loading") {
            DownloadPlanProgressMessage(text = uiText(UiStringKey.CollectingVoicesAndRanges))
        }
        state.orderedCoverages.isEmpty() -> item("voices-empty") {
            InlineErrorMessage(
                message = uiText(UiStringKey.NoVoicesAreAvailableForDownload),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        else -> items(state.orderedCoverages, key = { "voice:${it.voiceKey}" }) { coverage ->
            DownloadVoiceCoverageRow(
                coverage = coverage,
                selected = coverage.voiceKey in state.selectedVoices,
                canMoveUp = state.normalizedVoiceOrder.indexOf(coverage.voiceKey) > 0,
                canMoveDown = state.normalizedVoiceOrder.indexOf(coverage.voiceKey) <
                    state.normalizedVoiceOrder.lastIndex,
                onSelectedChange = { checked -> actions.onVoiceSelectedChange(coverage.voiceKey, checked) },
                onMoveUp = { actions.onMoveVoice(coverage.voiceKey, -1) },
                onMoveDown = { actions.onMoveVoice(coverage.voiceKey, 1) },
                episodeRangeText = state.voiceEpisodeRanges[coverage.voiceKey].orEmpty(),
                episodeRangeError = state.rangeErrorsByVoice[coverage.voiceKey]?.localizedMessage(),
                onEpisodeRangeChange = { value -> actions.onEpisodeRangeChange(coverage.voiceKey, value) },
                qualityStateText = null,
                includeQualitiesInSubtitle = false,
                showRanges = false,
                showEpisodeRangeField = false,
                showPriorityControls = true,
            )
        }
    }
}

private fun LazyListScope.downloadPlanEpisodeItems(
    state: DownloadPlanDialogUiState,
    actions: DownloadPlanDialogUiActions,
) {
    item("only-missing") {
        DownloadMissingOnlyRow(
            selected = state.onlyMissing,
            onClick = actions.onOnlyMissingToggle,
        )
    }
    item("episodes-title") {
        DownloadPlanSectionTitle(uiText(UiStringKey.Episodes))
    }
    if (state.selectedOrderedCoverages.isEmpty()) {
        item("episodes-empty") {
            InlineErrorMessage(
                message = uiText(UiStringKey.NoVoicesAreAvailableForDownload),
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        return
    }
    items(state.selectedOrderedCoverages, key = { "episodes:${it.voiceKey}" }) { coverage ->
        DownloadVoiceCoverageRow(
            coverage = coverage,
            selected = true,
            canMoveUp = false,
            canMoveDown = false,
            onSelectedChange = {},
            onMoveUp = {},
            onMoveDown = {},
            episodeRangeText = state.voiceEpisodeRanges[coverage.voiceKey].orEmpty(),
            episodeRangeError = state.rangeErrorsByVoice[coverage.voiceKey]?.localizedMessage(),
            onEpisodeRangeChange = { value -> actions.onEpisodeRangeChange(coverage.voiceKey, value) },
            qualityStateText = null,
            includeQualitiesInSubtitle = false,
            selectionEnabled = false,
            showSelectionMark = false,
            showRanges = true,
            showEpisodeRangeField = true,
            showPriorityControls = false,
        )
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
                        selected = quality in state.selectedQualities,
                        onClick = { actions.onQualityToggle(quality) },
                    )
                }
            }
            when {
                state.sampledQualitiesByVoice == null && state.selectedVoices.isNotEmpty() ->
                    DownloadPlanProgressMessage(text = uiText(UiStringKey.CheckingAvailableQuality))
                state.qualityOptions.isEmpty() -> InlineErrorMessage(
                    message = state.qualityError
                        ?: uiText(UiStringKey.NoAvailableQualityFoundForSelectedVoices),
                    modifier = Modifier.padding(top = 4.dp),
                )
                state.qualityError != null -> Text(
                    text = uiText(UiStringKey.SomeSourcesDidNotRespond),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    item("summary") {
        when {
            state.rangeErrorsByVoice.isNotEmpty() -> InlineErrorMessage(
                message = uiText(UiStringKey.FixEpisodeRanges),
            )
            state.sampledQualitiesByVoice == null || state.planResult == null ->
                DownloadPlanProgressMessage(text = uiText(UiStringKey.PreparingDownloadPlan))
            else -> DownloadPlanSummary(result = state.planResult)
        }
    }
}

// DownloadPlanDialogDerivedState
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
        downloadPlanQualityOptions(resolvedQualitiesByVoice, state.selectedVoices)
    }
    val planQualities = if (state.sampledQualitiesByVoice == null) emptySet() else state.selectedQualities
    val coverages = state.coveragesResult.orEmpty()
    val coverageByKey = remember(coverages) { coverages.associateBy { it.voiceKey } }
    val normalizedVoiceOrder = remember(state.voiceOrder, coverages) {
        normalizeDownloadVoiceOrder(state.voiceOrder, coverages)
    }
    val selectionResults = remember(state.voiceEpisodeRanges, coverageByKey) {
        downloadPlanSelectionResults(state.voiceEpisodeRanges, coverageByKey)
    }
    val rangeErrorsByVoice = remember(selectionResults) {
        downloadPlanRangeErrors(selectionResults)
    }
    val episodeSelectionsByVoice = remember(selectionResults) {
        downloadPlanEpisodeSelections(selectionResults)
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

internal fun downloadPlanQualityOptions(
    resolvedQualitiesByVoice: Map<String, List<PreferredQuality>>,
    selectedVoices: Set<String>,
): List<PreferredQuality> = selectedVoices
    .flatMap { voiceKey -> resolvedQualitiesByVoice[voiceKey].orEmpty() }
    .filter { it.height != null }
    .distinctBy { it.height }
    .sortedByDescending { it.height ?: 0 }

private fun downloadPlanSelectionResults(
    voiceEpisodeRanges: Map<String, String>,
    coverageByKey: Map<String, DownloadVoiceCoverage>,
): Map<String, DownloadEpisodeSelectionParseResult> = voiceEpisodeRanges.mapValues { (voiceKey, value) ->
    coverageByKey[voiceKey]?.let { coverage ->
        validateDownloadEpisodeSelection(value, coverage.availableEpisodeRanges)
    } ?: parseDownloadEpisodeSelection(value)
}

private fun downloadPlanRangeErrors(
    selectionResults: Map<String, DownloadEpisodeSelectionParseResult>,
): Map<String, DownloadEpisodeSelectionError> = selectionResults.mapNotNull { (voiceKey, result) ->
    result.error?.let { error -> voiceKey to error }
}.toMap()

private fun downloadPlanEpisodeSelections(
    selectionResults: Map<String, DownloadEpisodeSelectionParseResult>,
): Map<String, DownloadEpisodeSelection> = selectionResults.mapNotNull { (voiceKey, result) ->
    result.selection.takeIf { selection -> result.error == null && selection.isRestricted }
        ?.let { selection -> voiceKey to selection }
}.toMap()

// DownloadPlanDialogEffects
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
        val result = runCatching { onResolveSampledQualities(qualityProbeVoiceKeys, videos) }
        currentCoroutineContext().ensureActive()
        result
            .onSuccess { qualities -> state.sampledQualitiesByVoice = qualities }
            .onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
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
        val coverages = withContext(Dispatchers.Default) {
            buildDownloadVoiceCoverages(
                videos = videos,
                acceptableQualities = state.selectedQualities,
                selectedVoiceKey = selectedVoiceKey,
                resolvedQualitiesByVoice = resolvedQualitiesByVoice,
            )
        }
        currentCoroutineContext().ensureActive()
        state.coveragesResult = coverages
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
        val canBuildPlan = shouldBuildDownloadPlan(
            step = state.step,
            qualitiesResolved = state.sampledQualitiesByVoice != null,
            coveragesLoaded = state.coveragesResult != null,
            hasRangeErrors = derived.rangeErrorsByVoice.isNotEmpty(),
        )
        if (!canBuildPlan) return@LaunchedEffect
        val planResult = withContext(Dispatchers.Default) {
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
        currentCoroutineContext().ensureActive()
        state.planResult = planResult
    }
}

internal fun shouldBuildDownloadPlan(
    step: DownloadPlanStep,
    qualitiesResolved: Boolean,
    coveragesLoaded: Boolean,
    hasRangeErrors: Boolean,
): Boolean {
    if (step != DownloadPlanStep.Quality) return false
    if (!qualitiesResolved) return false
    if (!coveragesLoaded) return false
    return !hasRangeErrors
}

// DownloadPlanDialogEntry
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
    DownloadPlanDialogRuntime(
        animeId = animeId,
        animeTitle = animeTitle,
        videos = videos,
        selectedVideo = selectedVideo,
        selected = selected,
        onResolveSampledQualities = onResolveSampledQualities,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

// DownloadPlanDialogState
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

// DownloadPlanDialogWorkflow
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
        modifier = Modifier.dpadClickable(shape, onClick),
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
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            }
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

// DownloadVoiceCoverageRow
private data class DownloadVoiceCoverageUiState(
    val coverage: DownloadVoiceCoverage,
    val selected: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val episodeRangeText: String,
    val episodeRangeError: String?,
    val qualityStateText: String?,
    val includeQualitiesInSubtitle: Boolean,
    val showSelectionMark: Boolean,
    val showRanges: Boolean,
    val showEpisodeRangeField: Boolean,
    val showPriorityControls: Boolean,
)

private data class DownloadVoiceCoverageActions(
    val onMoveUp: () -> Unit,
    val onMoveDown: () -> Unit,
    val onEpisodeRangeChange: (String) -> Unit,
)

@Composable
internal fun DownloadVoiceCoverageRow(
    coverage: DownloadVoiceCoverage,
    selected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    episodeRangeText: String,
    episodeRangeError: String?,
    onEpisodeRangeChange: (String) -> Unit,
    qualityStateText: String?,
    modifier: Modifier = Modifier,
    includeQualitiesInSubtitle: Boolean = true,
    selectionEnabled: Boolean = true,
    showSelectionMark: Boolean = true,
    showRanges: Boolean = true,
    showEpisodeRangeField: Boolean = true,
    showPriorityControls: Boolean = true,
) {
    val shape = RoundedCornerShape(8.dp)
    val clickModifier = if (selectionEnabled) {
        Modifier.dpadClickable(shape) { onSelectedChange(!selected) }
    } else {
        Modifier
    }
    Surface(
        modifier = modifier.fillMaxWidth().then(clickModifier),
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
    ) {
        DownloadVoiceCoverageContent(
            state = DownloadVoiceCoverageUiState(
                coverage = coverage,
                selected = selected,
                canMoveUp = canMoveUp,
                canMoveDown = canMoveDown,
                episodeRangeText = episodeRangeText,
                episodeRangeError = episodeRangeError,
                qualityStateText = qualityStateText,
                includeQualitiesInSubtitle = includeQualitiesInSubtitle,
                showSelectionMark = showSelectionMark,
                showRanges = showRanges,
                showEpisodeRangeField = showEpisodeRangeField,
                showPriorityControls = showPriorityControls,
            ),
            actions = DownloadVoiceCoverageActions(
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onEpisodeRangeChange = onEpisodeRangeChange,
            ),
        )
    }
}

@Composable
private fun DownloadVoiceCoverageContent(
    state: DownloadVoiceCoverageUiState,
    actions: DownloadVoiceCoverageActions,
) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
    ) {
        if (state.showSelectionMark) DownloadPlanToggleMark(selected = state.selected)
        DownloadVoiceCoverageDetails(
            state = state,
            onEpisodeRangeChange = actions.onEpisodeRangeChange,
            modifier = Modifier.weight(1f),
        )
        if (state.showPriorityControls) {
            DownloadVoicePriorityControls(state, actions)
        }
    }
}

@Composable
private fun DownloadVoiceCoverageDetails(
    state: DownloadVoiceCoverageUiState,
    onEpisodeRangeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = state.coverage.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = state.coverage.subtitle(state.qualityStateText, state.includeQualitiesInSubtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.showRanges && state.coverage.ranges.isNotEmpty()) {
            DownloadVoiceRanges(state.coverage.ranges)
        }
        if (state.showEpisodeRangeField) {
            DownloadEpisodeRangeField(
                value = state.episodeRangeText,
                error = state.episodeRangeError,
                onValueChange = onEpisodeRangeChange,
            )
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
private fun DownloadVoicePriorityControls(
    state: DownloadVoiceCoverageUiState,
    actions: DownloadVoiceCoverageActions,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        IconButton(onClick = actions.onMoveUp, enabled = state.canMoveUp, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.ArrowUpward, contentDescription = uiText(UiStringKey.MoveUp))
        }
        IconButton(onClick = actions.onMoveDown, enabled = state.canMoveDown, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.ArrowDownward, contentDescription = uiText(UiStringKey.MoveDown))
        }
    }
}

@Composable
private fun DownloadVoiceCoverage.subtitle(
    qualityStateText: String?,
    includeQualities: Boolean,
): String {
    val parts = buildList {
        add("$episodeCount ${localizedEpisodesWord(episodeCount)}")
        if (downloadedCount > 0) add("${uiText(UiStringKey.DownloadedFae287)} $downloadedCount")
        if (includeQualities && qualities.isNotEmpty()) {
            add(qualities.joinToString(", "))
        } else if (includeQualities && !qualityStateText.isNullOrBlank()) {
            add(qualityStateText)
        }
    }
    return parts.joinToString(" • ")
}
