package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
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
import me.yummydroid.app.DownloadVoiceCoverage
import me.yummydroid.app.buildDownloadPlan
import me.yummydroid.app.buildDownloadVoiceCoverages
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.YummySurfaceRole

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DownloadPlanDialog(
    animeId: Long,
    animeTitle: String,
    videos: List<VideoVariant>,
    selectedVideo: VideoVariant?,
    selected: PreferredQuality,
    onResolveSampledQualities: suspend (Set<String>, List<VideoVariant>) -> List<PreferredQuality>,
    onConfirm: (DownloadPlan) -> Unit,
    onDismiss: () -> Unit,
) {
    var onlyMissing by remember { mutableStateOf(true) }
    var sampledQualities by remember(videos) { mutableStateOf<List<PreferredQuality>?>(null) }
    var qualityError by remember(videos) { mutableStateOf<String?>(null) }
    var selectedQualities by remember(videos, selected) {
        mutableStateOf(setOfNotNull(selected.takeIf { it.height != null }))
    }
    val selectedVoiceKey = remember(selectedVideo) {
        selectedVideo?.matchingVoiceKey?.takeIf { it.isNotBlank() }
    }
    val qualityOptions = sampledQualities.orEmpty()
    val planQualities = if (sampledQualities == null) emptySet() else selectedQualities
    val coverages = remember(videos, selectedQualities, selectedVoiceKey) {
        buildDownloadVoiceCoverages(
            videos = videos,
            acceptableQualities = selectedQualities,
            selectedVoiceKey = selectedVoiceKey,
        )
    }
    var voiceOrder by remember(videos, selectedVoiceKey) { mutableStateOf<List<String>>(emptyList()) }
    var selectedVoices by remember(videos, selectedVoiceKey) {
        mutableStateOf(
            selectedVoiceKey
                ?.takeIf { it.isNotBlank() }
                ?.let(::setOf)
                ?: videos.firstOrNull()?.matchingVoiceKey?.takeIf { it.isNotBlank() }?.let(::setOf)
                ?: emptySet(),
        )
    }
    val normalizedVoiceOrder = remember(voiceOrder, coverages) {
        val available = coverages.map { it.voiceKey }.toSet()
        (voiceOrder.filter { it in available } + coverages.map { it.voiceKey }).distinct()
    }
    val result = remember(animeId, animeTitle, videos, planQualities, selectedVoices, normalizedVoiceOrder, onlyMissing) {
        buildDownloadPlan(
            animeId = animeId,
            animeTitle = animeTitle,
            videos = videos,
            acceptableQualities = planQualities,
            selectedVoiceKeys = selectedVoices,
            voiceOrder = normalizedVoiceOrder,
            onlyMissing = onlyMissing,
        )
    }
    val coverageByKey = remember(coverages) { coverages.associateBy { it.voiceKey } }
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

    LaunchedEffect(selectedVoices, videos) {
        sampledQualities = null
        qualityError = null
        if (selectedVoices.isEmpty()) {
            sampledQualities = emptyList()
            return@LaunchedEffect
        }
        runCatching { onResolveSampledQualities(selectedVoices, videos) }
            .onSuccess { qualities -> sampledQualities = qualities }
            .onFailure { throwable ->
                sampledQualities = emptyList()
                qualityError = throwable.message?.takeIf { it.isNotBlank() }
            }
    }

    LaunchedEffect(qualityOptions, selected) {
        if (qualityOptions.isEmpty()) return@LaunchedEffect
        val retained = selectedQualities.filterTo(mutableSetOf()) { it in qualityOptions }
        selectedQualities = retained.ifEmpty {
            selected.takeIf { quality -> quality.height != null && quality in qualityOptions }
                ?.let(::setOf)
                ?: setOf(qualityOptions.first())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("План загрузки")) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(YummySpacing.md),
            ) {
                item("quality") {
                    Column(verticalArrangement = Arrangement.spacedBy(YummySpacing.sm)) {
                        DownloadPlanSectionTitle(uiText("Качество"))
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
                            sampledQualities == null && selectedVoices.isNotEmpty() -> Text(
                                text = uiText("Проверяем доступное качество"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            qualityOptions.isEmpty() -> InlineErrorMessage(
                                message = qualityError
                                    ?: uiText("Не найдено доступное качество для выбранных озвучек"),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            qualityError != null -> Text(
                                text = uiText("Часть источников не ответила"),
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
                                text = uiText("Скачивать только отсутствующие серии"),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = uiText("Уже скачанные серии с тем же качеством будут пропущены"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item("summary") {
                    DownloadPlanSummary(result = result)
                }
                item("voices-title") {
                    DownloadPlanSectionTitle(uiText("Озвучки и приоритет"))
                }
                if (orderedCoverages.isEmpty()) {
                    item("voices-empty") {
                        InlineErrorMessage(
                            message = uiText("Нет доступных озвучек для загрузки"),
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
                        )
                    }
                }
            }
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(
                    text = uiText("Отмена"),
                    onClick = onDismiss,
                )
                DialogActionButton(
                    text = uiText("Скачать"),
                    primary = true,
                    enabled = selectedQualities.isNotEmpty() && result.scheduledCount > 0,
                    onClick = { onConfirm(result.plan) },
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
                title = uiText("В очередь"),
                value = "${result.scheduledCount} ${localizedEpisodesWord(result.scheduledCount)}",
                accent = result.scheduledCount > 0,
            )
            DownloadPlanSummaryLine(
                title = uiText("Всего серий"),
                value = result.totalEpisodes.toString(),
            )
            if (result.alreadyDownloaded > 0) {
                DownloadPlanSummaryLine(
                    title = uiText("Уже скачано"),
                    value = result.alreadyDownloaded.toString(),
                )
            }
            if (result.missingInSelectedVoices > 0) {
                DownloadPlanSummaryLine(
                    title = uiText("Нет в выбранных озвучках"),
                    value = result.missingInSelectedVoices.toString(),
                )
            }
            if (result.missingSelectedQuality > 0) {
                DownloadPlanSummaryLine(
                    title = uiText("Нет выбранного качества"),
                    value = result.missingSelectedQuality.toString(),
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
                    text = coverage.subtitle(),
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
                    Icon(Icons.Default.ArrowUpward, contentDescription = uiText("Выше"))
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(34.dp),
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = uiText("Ниже"))
                }
            }
        }
    }
}

@Composable
private fun DownloadVoiceCoverage.subtitle(): String {
    val parts = buildList {
        add("$episodeCount ${localizedEpisodesWord(episodeCount)}")
        if (downloadedCount > 0) add("${uiText("скачано")} $downloadedCount")
        if (qualities.isNotEmpty()) add(qualities.joinToString(", "))
    }
    return parts.joinToString(" • ")
}
