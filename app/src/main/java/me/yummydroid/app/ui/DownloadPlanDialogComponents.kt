package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
