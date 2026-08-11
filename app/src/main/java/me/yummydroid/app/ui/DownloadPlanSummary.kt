package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.yummydroid.app.DownloadPlanBuildResult
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummySurfaceColor

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
