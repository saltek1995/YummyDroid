package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.DownloadEpisodeSelectionError
import me.yummydroid.app.DownloadPlanBuildResult
import me.yummydroid.app.DownloadVoiceCoverage
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceColor

@Composable
internal fun DownloadPlanStep.title(): String = when (this) {
    DownloadPlanStep.Voice -> uiText(UiStringKey.ChooseVoice)
    DownloadPlanStep.Episodes -> uiText(UiStringKey.Episodes)
    DownloadPlanStep.Quality -> uiText(UiStringKey.Quality)
}

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
internal fun DownloadPlanSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = YummyColors.focus,
        fontWeight = FontWeight.Black,
    )
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
                modifier = Modifier
                    .padding(5.dp)
                    .size(16.dp),
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
internal fun DownloadPlanSummary(result: DownloadPlanBuildResult) {
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (selectionEnabled) {
                    Modifier.dpadClickable(shape) { onSelectedChange(!selected) }
                } else {
                    Modifier
                },
            ),
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        ) {
            if (showSelectionMark) {
                DownloadPlanToggleMark(selected = selected)
            }
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
                    text = coverage.subtitle(
                        qualityStateText = qualityStateText,
                        includeQualities = includeQualitiesInSubtitle,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showRanges && coverage.ranges.isNotEmpty()) {
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
                if (showEpisodeRangeField) {
                    DownloadEpisodeRangeField(
                        value = episodeRangeText,
                        error = episodeRangeError,
                        onValueChange = onEpisodeRangeChange,
                    )
                }
            }
            if (showPriorityControls) {
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
}

@Composable
internal fun DownloadEpisodeSelectionError.localizedMessage(): String {
    return when (this) {
        is DownloadEpisodeSelectionError.InvalidEpisodeNumber ->
            uiText(UiStringKey.EpisodeNumberInvalid, token)
        is DownloadEpisodeSelectionError.InvalidEpisodeRange ->
            uiText(UiStringKey.EpisodeRangeInvalid, token)
        is DownloadEpisodeSelectionError.MissingEpisodes ->
            uiText(UiStringKey.VoiceHasNoEpisodes, ranges)
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
