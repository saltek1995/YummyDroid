package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.formatRating
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.YummyColors

// DetailsHeroProgressSummary
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
// DetailsHeroRatingBadge
@Composable
internal fun HeroRatingBadge(
    rating: Double,
    enabled: Boolean,
    onClick: () -> Unit,
    heroFocusGridState: VisualFocusGridState?,
) {
    val shape = RoundedCornerShape(8.dp)
    val ratingColor = ratingColorForSiteScale(rating)
    val textStyle = MaterialTheme.typography.headlineSmall.withoutFontPadding()
    val focusModifier = if (enabled && heroFocusGridState != null) {
        Modifier.visualFocusGridItem(
            state = heroFocusGridState,
            index = DetailsHeroFocusIndex.RatingBadge,
            horizontal = true,
            vertical = true,
            blockKey = DetailsFocusBlockKey.HeroStats,
            blockEntryIndex = DetailsHeroFocusIndex.RatingBadge,
        )
    } else {
        Modifier
    }
    Surface(
        modifier = Modifier
            .then(focusModifier)
            .then(if (enabled) Modifier.dpadClickable(shape, onClick) else Modifier),
        shape = shape,
        color = Color.Transparent,
        contentColor = ratingColor,
    ) {
        Row(
            modifier = Modifier.height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(25.dp))
            Text(
                text = formatRating(rating),
                style = textStyle,
                fontWeight = FontWeight.Black,
            )
        }
    }
}
internal fun ratingColorForSiteScale(rating: Double): Color = when {
    rating < 5.0 -> Color(0xFFFF6666)
    rating < 7.0 -> Color(0xFFF2B800)
    else -> Color(0xFF3CCE7B)
}
// DetailsHeroRatingDialog
@Composable
internal fun DetailsHeroRatingDialogInputEffect(
    open: Boolean,
    onDismiss: () -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
) {
    val ratingDialogInputHandler by rememberUpdatedState { action: InputAction ->
        if (action == InputAction.Back && open) {
            onDismiss()
            true
        } else {
            false
        }
    }
    DisposableEffect(open, onRegisterModalInputActionHandler) {
        if (open) {
            onRegisterModalInputActionHandler { action -> ratingDialogInputHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }
}

@Composable
internal fun DetailsHeroRatingDialog(
    open: Boolean,
    detailsId: Long,
    ratingSummary: AnimeRatingSummary?,
    onDismiss: () -> Unit,
    onSelected: (Int?) -> Unit,
) {
    if (!open || ratingSummary == null) return
    val inputModeManager = LocalInputModeManager.current
    val dialogFocusGridState = rememberVisualFocusGridState(
        size = 10,
        key = detailsId to ratingSummary.userRating,
    )
    UiControlEffect(
        dialogFocusGridState,
        inputModeManager.inputMode,
        enabled = inputModeManager.inputMode != InputMode.Touch,
    ) {
        withFrameNanos { }
        val focusIndex = ((ratingSummary.userRating ?: 1).coerceIn(1, 10) - 1)
        dialogFocusGridState.requester(focusIndex)?.requestFocusSafely()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 460.dp)
                .yummyDialogMotion(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(18.dp),
            ) {
                Text(
                    text = uiText(UiStringKey.RateAnime),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
                RatingScale(
                    selected = ratingSummary.userRating,
                    onSelected = onSelected,
                    focusGridState = dialogFocusGridState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
// DetailsHeroRatings
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
    interactive: Boolean,
    compact: Boolean,
    heroFocusGridState: VisualFocusGridState? = null,
) {
    var ratingDialogOpen by remember(details.id) { mutableStateOf(false) }
    LaunchedEffect(interactive) {
        if (!interactive) ratingDialogOpen = false
    }
    val ratingSummary = (detailsExtras as? LoadState.Ready)?.data?.rating
    val canRate = showHeroRating && auth.profile != null && ratingSummary != null
    DetailsHeroRatingDialogInputEffect(
        open = interactive && ratingDialogOpen,
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
        open = interactive && ratingDialogOpen,
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
