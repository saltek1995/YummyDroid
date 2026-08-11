package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
import me.yummydroid.app.DownloadVoiceCoverage
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummySurfaceColor

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
