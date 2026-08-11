package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.ui.theme.YummyColors

@Composable
internal fun DetailsHeroProgressSummary(
    episodeSummary: String,
    downloadedSummary: String?,
) {
    if (episodeSummary.isBlank() && downloadedSummary == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (episodeSummary.isNotBlank()) {
            Text(
                text = episodeSummary,
                style = MaterialTheme.typography.labelLarge,
                color = YummyColors.watched,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        downloadedSummary?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
