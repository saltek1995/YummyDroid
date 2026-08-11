package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.AnimeDetails

@Composable
internal fun DetailsHeroAlternateTitles(
    details: AnimeDetails,
    compact: Boolean,
) {
    val alternateTitle = details.otherTitles
        .filter { it.isPresentFactValue() && !it.equals(details.title, ignoreCase = true) }
        .take(if (compact) 2 else 3)
        .joinToString(" | ")
        .ifBlank { details.meta }
    if (alternateTitle.isBlank()) return
    Text(
        text = alternateTitle,
        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun DetailsHeroRatingAndStats(
    details: AnimeDetails,
    detailsExtras: LoadState<AnimeDetailsExtras>,
    auth: AuthUiState,
    showHeroRating: Boolean,
    onSetAnimeRating: (Int?) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    compact: Boolean,
    heroFocusGridState: VisualFocusGridState? = null,
) {
    var ratingDialogOpen by remember(details.id) { mutableStateOf(false) }
    val ratingSummary = (detailsExtras as? LoadState.Ready)?.data?.rating
    val canRate = showHeroRating && auth.profile != null && ratingSummary != null
    DetailsHeroRatingDialogInputEffect(
        open = ratingDialogOpen,
        onDismiss = { ratingDialogOpen = false },
        onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
    )
    DetailsHeroMetrics(
        details = details,
        canRate = canRate,
        compact = compact,
        heroFocusGridState = heroFocusGridState,
        onOpenRatingDialog = { ratingDialogOpen = true },
    )
    DetailsHeroRatingDialog(
        open = ratingDialogOpen,
        detailsId = details.id,
        ratingSummary = ratingSummary,
        onDismiss = { ratingDialogOpen = false },
        onSelected = { rating ->
            ratingDialogOpen = false
            onSetAnimeRating(rating)
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsHeroMetrics(
    details: AnimeDetails,
    canRate: Boolean,
    compact: Boolean,
    heroFocusGridState: VisualFocusGridState?,
    onOpenRatingDialog: () -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        details.rating?.let { rating ->
            HeroRatingBadge(
                rating = rating,
                enabled = canRate,
                heroFocusGridState = heroFocusGridState,
                onClick = onOpenRatingDialog,
            )
        }
        HeroMetricItem(
            icon = Icons.Default.Visibility,
            text = localizedViews(details.views),
        )
        details.listsCount.takeIf { it > 0L }?.let { count ->
            HeroMetricItem(
                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                text = localizedViews(count),
            )
        }
        if (details.ratingDetails.hasExternalRatings()) {
            HeroMetricSeparator()
        }
        HeroExternalRatingBadges(ratingDetails = details.ratingDetails)
    }
}

@Composable
private fun HeroMetricItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    val textStyle = MaterialTheme.typography.titleMedium.withoutFontPadding()
    Row(
        modifier = Modifier.height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = text,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeroMetricSeparator() {
    val textStyle = MaterialTheme.typography.titleLarge.withoutFontPadding()
    Box(
        modifier = Modifier.height(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "|",
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Black,
        )
    }
}

internal fun androidx.compose.ui.text.TextStyle.withoutFontPadding(): androidx.compose.ui.text.TextStyle =
    copy(platformStyle = PlatformTextStyle(includeFontPadding = false))
