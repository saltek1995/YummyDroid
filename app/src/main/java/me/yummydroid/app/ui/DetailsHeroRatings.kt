package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.formatRating
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.R
import me.yummydroid.app.ui.components.dpadClickable

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

@OptIn(ExperimentalLayoutApi::class)
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
    val ratingDialogInputHandler by rememberUpdatedState { action: InputAction ->
        if (action == InputAction.Back && ratingDialogOpen) {
            ratingDialogOpen = false
            true
        } else {
            false
        }
    }

    DisposableEffect(ratingDialogOpen, onRegisterModalInputActionHandler) {
        if (ratingDialogOpen) {
            onRegisterModalInputActionHandler { action -> ratingDialogInputHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }

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
                onClick = { ratingDialogOpen = true },
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

    if (ratingDialogOpen && ratingSummary != null) {
        val inputModeManager = LocalInputModeManager.current
        val dialogFocusGridState = rememberVisualFocusGridState(
            size = 10,
            key = details.id to ratingSummary.userRating,
        )
        LaunchedEffect(dialogFocusGridState, inputModeManager.inputMode) {
            if (inputModeManager.inputMode == InputMode.Touch) return@LaunchedEffect
            withFrameNanos { }
            val focusIndex = ((ratingSummary.userRating ?: 1).coerceIn(1, 10) - 1)
            dialogFocusGridState.requester(focusIndex)?.requestFocusSafely()
        }
        Dialog(
            onDismissRequest = { ratingDialogOpen = false },
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
                        onSelected = { rating ->
                            ratingDialogOpen = false
                            onSetAnimeRating(rating)
                        },
                        focusGridState = dialogFocusGridState,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroRatingBadge(
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

@Composable
private fun HeroExternalRatingBadges(ratingDetails: RatingDetails) {
    detailsHeroExternalRatingDisplays(ratingDetails).forEach { entry ->
        HeroExternalRatingItem(entry = entry)
    }
}

internal data class ExternalRatingDisplay(
    val iconResId: Int,
    val iconSize: Dp,
    val title: String,
    val value: String,
)

internal fun detailsHeroExternalRatingDisplays(ratingDetails: RatingDetails): List<ExternalRatingDisplay> {
    return buildList {
        ratingDetails.worldArt?.let {
            add(ExternalRatingDisplay(R.drawable.ic_rating_world_art, 26.dp, "World Art", formatRating(it)))
        }
        ratingDetails.kinopoisk?.let {
            add(ExternalRatingDisplay(R.drawable.ic_rating_kinopoisk, 18.dp, "Kinopoisk", formatRating(it)))
        }
        ratingDetails.shikimori?.let {
            add(ExternalRatingDisplay(R.drawable.ic_rating_shikimori, 18.dp, "Shikimori", formatRating(it)))
        }
        ratingDetails.myAnimeList?.let {
            add(ExternalRatingDisplay(R.drawable.ic_rating_mal, 33.dp, "MyAnimeList", formatRating(it)))
        }
        ratingDetails.aniDub?.let {
            add(ExternalRatingDisplay(R.drawable.ic_rating_anilibria, 20.dp, "Anilibria", formatRating(it)))
        }
    }
}

@Composable
private fun HeroMetricItem(
    icon: ImageVector,
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

@Composable
private fun HeroExternalRatingItem(
    entry: ExternalRatingDisplay,
) {
    val textStyle = MaterialTheme.typography.titleMedium.withoutFontPadding()
    Row(
        modifier = Modifier.height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            painter = painterResource(id = entry.iconResId),
            contentDescription = entry.title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(entry.iconSize),
        )
        Text(
            text = entry.value,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

private fun androidx.compose.ui.text.TextStyle.withoutFontPadding(): androidx.compose.ui.text.TextStyle =
    copy(platformStyle = PlatformTextStyle(includeFontPadding = false))

internal fun RatingDetails.hasExternalRatings(): Boolean {
    return detailsHeroExternalRatingDisplays(this).isNotEmpty()
}

internal fun ratingColorForSiteScale(rating: Double): Color = when {
    rating < 5.0 -> Color(0xFFFF6666)
    rating < 7.0 -> Color(0xFFF2B800)
    else -> Color(0xFF3CCE7B)
}
