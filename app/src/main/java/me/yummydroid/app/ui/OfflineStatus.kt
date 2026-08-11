package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySizes
import me.yummydroid.app.ui.theme.YummySpacing

// OfflineFilterCatalog
internal fun List<OfflineAnimeEntry>.toOfflineFilterCatalog(): FilterCatalog = FilterCatalog(
    genres = flatMap { entry ->
        entry.details.genreTags.map { it.title }.ifEmpty { entry.anime.genres }
    }.toFilterOptions(),
    types = map { entry -> entry.details.type.ifBlank { entry.anime.type } }.toFilterOptions(),
    studios = flatMap { entry -> entry.details.studios }.toDistinctFilterOptions(),
    creators = flatMap { entry -> entry.details.creators }.toDistinctFilterOptions(),
)

private fun List<String>.toFilterOptions(): List<FilterOption> {
    return asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { value -> value.lowercase(Locale.ROOT) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { value -> value })
        .map { value -> FilterOption(title = value, value = value) }
        .toList()
}

private fun List<FilterOption>.toDistinctFilterOptions(): List<FilterOption> {
    return asSequence()
        .filter { option -> option.title.isNotBlank() && option.value.isNotBlank() }
        .distinctBy(FilterOption::value)
        .toList()
        .sortedByTitle()
}

// OfflineModeChip
@Composable
internal fun OfflineModeChip() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = YummyRadii.pillShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = YummySpacing.md, vertical = YummySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.xs),
        ) {
            Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(YummySizes.badgeIcon))
            Text(
                text = uiText(UiStringKey.Offline),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}
