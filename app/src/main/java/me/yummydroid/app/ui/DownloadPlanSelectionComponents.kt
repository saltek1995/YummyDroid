package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

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
