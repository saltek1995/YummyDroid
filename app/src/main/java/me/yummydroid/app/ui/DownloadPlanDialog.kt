package me.yummydroid.app.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.DownloadPlanBuildResult
import me.yummydroid.app.DownloadVoiceCoverage
import me.yummydroid.app.buildDownloadPlan
import me.yummydroid.app.buildDownloadVoiceCoverages
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.downloadPlanVoiceKey
import me.yummydroid.app.parseDownloadEpisodeSelection
import me.yummydroid.app.validateDownloadEpisodeSelection
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.YummySurfaceRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
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
    var onlyMissing by remember { mutableStateOf(true) }
    var sampledQualitiesByVoice by remember(videos) { mutableStateOf<Map<String, List<PreferredQuality>>?>(null) }
    var qualityError by remember(videos) { mutableStateOf<String?>(null) }
    var planResult by remember(videos) { mutableStateOf<DownloadPlanBuildResult?>(null) }
    var selectedQualities by remember(videos, selected) {
        mutableStateOf(setOfNotNull(selected.takeIf { it.height != null }))
    }
    var voiceEpisodeRanges by remember(videos) { mutableStateOf<Map<String, String>>(emptyMap()) }
    val selectedVoiceKey = remember(selectedVideo) {
        selectedVideo?.downloadPlanVoiceKey?.takeIf { it.isNotBlank() }
    }
    val qualityProbeVoiceKeys = remember(videos) {
        videos
            .map { video -> video.downloadPlanVoiceKey }
            .filter { it.isNotBlank() }
            .toSet()
    }
    var selectedVoices by remember(videos, selectedVoiceKey) {
        mutableStateOf(
            selectedVoiceKey
                ?.takeIf { it.isNotBlank() }
                ?.let(::setOf)
                ?: videos.firstOrNull()?.downloadPlanVoiceKey?.takeIf { it.isNotBlank() }?.let(::setOf)
                ?: emptySet(),
        )
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
        val available = coverages.map { it.voiceKey }.toSet()
        (voiceOrder.filter { it in available } + coverages.map { it.voiceKey }).distinct()
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

    fun moveVoice(voiceKey: String, delta: Int) {
        val current = normalizedVoiceOrder.toMutableList()
        val index = current.indexOf(voiceKey)
        val target = (index + delta).coerceIn(current.indices)
        if (index < 0 || index == target) return
        current.removeAt(index)
        current.add(target, voiceKey)
        voiceOrder = current
    }

    LaunchedEffect(qualityProbeVoiceKeys, videos) {
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
    ) {
        planResult = null
        if (sampledQualitiesByVoice == null || coveragesResult == null || rangeErrorsByVoice.isNotEmpty()) {
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.DownloadPlan)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(YummySpacing.md),
            ) {
                item("quality") {
                    Column(verticalArrangement = Arrangement.spacedBy(YummySpacing.sm)) {
                        DownloadPlanSectionTitle(uiText(UiStringKey.Quality))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                        ) {
                            qualityOptions.forEach { quality ->
                                DownloadPlanQualityChip(
                                    quality = quality,
                                    selected = quality in selectedQualities,
                                    onClick = {
                                        selectedQualities = if (quality in selectedQualities) {
                                            selectedQualities - quality
                                        } else {
                                            selectedQualities + quality
                                        }
                                    },
                                )
                            }
                        }
                        when {
                            sampledQualitiesByVoice == null && selectedVoices.isNotEmpty() -> DownloadPlanProgressMessage(
                                text = uiText(UiStringKey.CheckingAvailableQuality),
                            )
                            qualityOptions.isEmpty() -> InlineErrorMessage(
                                message = qualityError
                                    ?: uiText(UiStringKey.NoAvailableQualityFoundForSelectedVoices),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            qualityError != null -> Text(
                                text = uiText(UiStringKey.SomeSourcesDidNotRespond),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item("only-missing") {
                    val shape = YummyRadii.smallShape
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .dpadClickable(shape) { onlyMissing = !onlyMissing }
                            .padding(horizontal = 2.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                    ) {
                        DownloadPlanToggleMark(selected = onlyMissing)
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
                item("summary") {
                    val result = planResult
                    when {
                        rangeErrorsByVoice.isNotEmpty() -> InlineErrorMessage(
                            message = uiText(UiStringKey.FixEpisodeRanges),
                        )
                        result == null -> DownloadPlanProgressMessage(
                            text = uiText(UiStringKey.PreparingDownloadPlan),
                        )
                        else -> DownloadPlanSummary(result = result)
                    }
                }
                item("voices-title") {
                    DownloadPlanSectionTitle(uiText(UiStringKey.VoicesAndPriority))
                }
                if (coveragesResult == null) {
                    item("voices-loading") {
                        DownloadPlanProgressMessage(text = uiText(UiStringKey.CollectingVoicesAndRanges))
                    }
                } else if (orderedCoverages.isEmpty()) {
                    item("voices-empty") {
                        InlineErrorMessage(
                            message = uiText(UiStringKey.NoVoicesAreAvailableForDownload),
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                } else {
                    items(orderedCoverages, key = { it.voiceKey }) { coverage ->
                        DownloadVoiceCoverageRow(
                            coverage = coverage,
                            selected = coverage.voiceKey in selectedVoices,
                            canMoveUp = normalizedVoiceOrder.indexOf(coverage.voiceKey) > 0,
                            canMoveDown = normalizedVoiceOrder.indexOf(coverage.voiceKey) < normalizedVoiceOrder.lastIndex,
                            onSelectedChange = { checked ->
                                selectedVoices = if (checked) {
                                    selectedVoices + coverage.voiceKey
                                } else {
                                    selectedVoices - coverage.voiceKey
                                }
                            },
                            onMoveUp = { moveVoice(coverage.voiceKey, -1) },
                            onMoveDown = { moveVoice(coverage.voiceKey, 1) },
                            episodeRangeText = voiceEpisodeRanges[coverage.voiceKey].orEmpty(),
                            episodeRangeError = rangeErrorsByVoice[coverage.voiceKey],
                            onEpisodeRangeChange = { value ->
                                voiceEpisodeRanges = voiceEpisodeRanges + (coverage.voiceKey to value)
                            },
                            qualityStateText = when {
                                coverage.qualities.isNotEmpty() -> null
                                sampledQualitiesByVoice == null -> uiText(UiStringKey.CheckingQuality)
                                else -> uiText(UiStringKey.QualityNotFound)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText(UiStringKey.Cancel),
                    onClick = onDismiss,
                )
                DialogActionButton(
                    text = uiText(UiStringKey.Download),
                    primary = true,
                    enabled = selectedQualities.isNotEmpty() &&
                        rangeErrorsByVoice.isEmpty() &&
                        planResult?.scheduledCount?.let { it > 0 } == true,
                    onClick = { planResult?.plan?.let(onConfirm) },
                )
            }
        },
    )
}

@Composable
private fun DownloadPlanSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = YummyColors.focus,
        fontWeight = FontWeight.Black,
    )
}

@Composable
private fun DownloadPlanQualityChip(
    quality: PreferredQuality,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = YummyRadii.pillShape
    Surface(
        modifier = Modifier.dpadClickable(shape, onClick),
        color = if (selected) YummyColors.focus else yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = quality.localizedTitle(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DownloadPlanToggleMark(selected: Boolean) {
    Surface(
        color = if (selected) YummyColors.focus else yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = YummyRadii.smallShape,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier
                    .padding(5.dp)
                    .size(16.dp),
            )
        } else {
            androidx.compose.foundation.layout.Box(modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun DownloadPlanProgressMessage(text: String) {
    Surface(
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = YummyRadii.smallShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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

@Composable
private fun DownloadPlanSummary(result: me.yummydroid.app.DownloadPlanBuildResult) {
    Surface(
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = YummyRadii.smallShape,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DownloadPlanSummaryLine(
                title = uiText(UiStringKey.ToQueue),
                value = "${result.scheduledCount} ${localizedEpisodesWord(result.scheduledCount)}",
                accent = result.scheduledCount > 0,
            )
            DownloadPlanSummaryLine(
                title = uiText(UiStringKey.TotalEpisodes),
                value = result.totalEpisodes.toString(),
            )
            if (result.alreadyDownloaded > 0) {
                DownloadPlanSummaryLine(
                    title = uiText(UiStringKey.AlreadyDownloaded),
                    value = result.alreadyDownloaded.toString(),
                )
            }
            if (result.missingInSelectedVoices > 0) {
                DownloadPlanSummaryLine(
                    title = uiText(UiStringKey.NotAvailableInSelectedVoices),
                    value = result.missingInSelectedVoices.toString(),
                )
            }
            if (result.missingSelectedQuality > 0) {
                DownloadPlanSummaryLine(
                    title = uiText(UiStringKey.SelectedQualityIsUnavailable),
                    value = result.missingSelectedQuality.toString(),
                )
            }
            if (result.excludedByEpisodeSelection > 0) {
                DownloadPlanSummaryLine(
                    title = uiText(UiStringKey.ExcludedByRanges),
                    value = result.excludedByEpisodeSelection.toString(),
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
private fun DownloadVoiceCoverageRow(
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
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .dpadClickable(shape) { onSelectedChange(!selected) },
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        ) {
            DownloadPlanToggleMark(selected = selected)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = coverage.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = coverage.subtitle(qualityStateText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (coverage.ranges.isNotEmpty()) {
                    Text(
                        text = coverage.ranges.joinToString(", ").let { ranges ->
                            if (ranges.length > 120) ranges.take(117).trimEnd() + "..." else ranges
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DownloadEpisodeRangeField(
                    value = episodeRangeText,
                    error = episodeRangeError,
                    onValueChange = onEpisodeRangeChange,
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = uiText(UiStringKey.MoveUp))
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = uiText(UiStringKey.MoveDown))
                }
            }
        }
    }
}

@Composable
private fun DownloadEpisodeRangeField(
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

@Composable
private fun DownloadVoiceCoverage.subtitle(qualityStateText: String?): String {
    val parts = buildList {
        add("$episodeCount ${localizedEpisodesWord(episodeCount)}")
        if (downloadedCount > 0) add("${uiText(UiStringKey.DownloadedFae287)} $downloadedCount")
        if (qualities.isNotEmpty()) {
            add(qualities.joinToString(", "))
        } else if (!qualityStateText.isNullOrBlank()) {
            add(qualityStateText)
        }
    }
    return parts.joinToString(" • ")
}
