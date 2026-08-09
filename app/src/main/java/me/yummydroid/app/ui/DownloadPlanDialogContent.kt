package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.yummydroid.app.DownloadEpisodeSelectionError
import me.yummydroid.app.DownloadPlanBuildResult
import me.yummydroid.app.DownloadVoiceCoverage
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.ui.theme.YummySpacing

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
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(uiText(UiStringKey.DownloadPlan))
                Text(
                    text = "${state.step.ordinal + 1}/3 • ${state.step.title()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        text = {
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
        },
        confirmButton = {
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
        },
    )
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
