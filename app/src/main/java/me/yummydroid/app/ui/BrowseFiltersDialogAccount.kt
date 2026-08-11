package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.data.userMarkFilterOptions
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.YummySurfaceRole

@Composable
internal fun AdvancedAccountFilterSections(
    state: FiltersDialogContentState,
    callbacks: FiltersDialogContentCallbacks,
) {
    val filters = state.filters
    if (state.isAuthorized) {
        FiltersDialogSelectionSection(
            id = "user_marks",
            title = uiText(UiStringKey.Marks),
            options = userMarkFilterOptions,
            selected = filters.userMarks,
            state = state,
            callbacks = callbacks,
            onToggle = { filters.copy(userMarks = filters.userMarks.toggle(it)) },
        )
        FiltersDialogSelectionSection(
            id = "excluded_user_marks",
            title = uiText(UiStringKey.ExcludeMarks),
            options = userMarkFilterOptions,
            selected = filters.excludedUserMarks,
            state = state,
            callbacks = callbacks,
            onToggle = { filters.copy(excludedUserMarks = filters.excludedUserMarks.toggle(it)) },
        )
    }
    if (state.forcedOfflineMode) {
        OfflineFilterNotice()
    } else {
        SettingsSwitchRow(
            title = uiText(UiStringKey.AvailableOffline),
            checked = filters.offlineOnly,
            onCheckedChange = { callbacks.onFiltersChange(filters.copy(offlineOnly = it)) },
        )
    }
}

@Composable
private fun OfflineFilterNotice() {
    Surface(
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = uiText(UiStringKey.OfflineOnlyDownloadedAnimeAreShown),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
