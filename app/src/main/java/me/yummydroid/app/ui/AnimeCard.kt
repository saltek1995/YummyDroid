package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import me.yummydroid.app.data.Anime
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySizes
import me.yummydroid.app.ui.theme.YummySpacing

private const val AnimeCardPosterAspectRatio = 2f / 3f
private const val AnimeCardPosterHeightRatio = 1f / AnimeCardPosterAspectRatio
private const val AnimeCardCollapsedTitleLines = 2
private const val AnimeCardExpandedTitleLines = 8
private val AnimeCardTitleMinHeight = 48.dp
private val AnimeCardMetaHeight = 20.dp
private val AnimeCardInfoVerticalPadding = 8.dp
private val AnimeCardInfoItemSpacing = 2.dp

@Composable
internal fun AnimeCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean? = null,
    reserveExpandedSpace: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var localFocused by remember { mutableStateOf(false) }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused = focused ?: localFocused
    val expanded = isFocused || isPressed

    val cardModifier = modifier
        .zIndex(if (expanded) 8f else 0f)
        .fillMaxWidth()
        .onFocusChanged { state ->
            if (focused == null) {
                localFocused = state.isFocused || state.hasFocus
            }
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )

    if (reserveExpandedSpace) {
        BoxWithConstraints(modifier = cardModifier) {
            AnimeCardContent(
                anime = anime,
                expanded = expanded,
                focused = isFocused,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(animeCardExpandedHeight(anime, maxWidth)),
            )
        }
    } else {
        AnimeCardContent(
            anime = anime,
            expanded = expanded,
            focused = isFocused,
            modifier = cardModifier,
        )
    }
}

@Composable
private fun AnimeCardContent(
    anime: Anime,
    expanded: Boolean,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        AnimeCardSurface(
            anime = anime,
            expanded = false,
            focused = focused,
            modifier = Modifier.fillMaxWidth(),
        )

        if (expanded) {
            AnimeCardSurface(
                anime = anime,
                expanded = true,
                focused = focused,
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minHeight = 0))
                        layout(placeable.width, 0) {
                            placeable.place(0, 0)
                        }
                    },
            )
        }
    }
}

@Composable
private fun animeCardExpandedHeight(
    anime: Anime,
    cardWidth: Dp,
): Dp {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val titleStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    val titleWidth = if (cardWidth > YummySpacing.md * 2) {
        cardWidth - YummySpacing.md * 2
    } else {
        0.dp
    }
    val titleHeight = with(density) {
        textMeasurer.measure(
            text = AnnotatedString(anime.title),
            style = titleStyle,
            overflow = TextOverflow.Ellipsis,
            maxLines = AnimeCardExpandedTitleLines,
            constraints = Constraints(maxWidth = titleWidth.roundToPx()),
        ).size.height.toDp()
    }
    val expandedInfoHeight = maxOf(
        YummySizes.animeCardInfoHeight,
        titleHeight + AnimeCardInfoVerticalPadding * 2 + AnimeCardInfoItemSpacing + AnimeCardMetaHeight,
    )
    return cardWidth * AnimeCardPosterHeightRatio + expandedInfoHeight
}

@Composable
internal fun AnimeCardSurface(
    anime: Anime,
    expanded: Boolean,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = YummyRadii.smallShape
    ElevatedCard(
        modifier = modifier.then(
            if (focused) {
                Modifier.border(BorderStroke(3.dp, YummyColors.focus), shape)
            } else {
                Modifier
            },
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = shape,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(AnimeCardPosterAspectRatio)
                .clip(RoundedCornerShape(topStart = YummyRadii.small, topEnd = YummyRadii.small))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            PosterImage(
                url = anime.posterUrl,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
            )

            if (anime.rating != null || anime.views > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(YummySpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(YummySpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    anime.rating?.let { rating ->
                        RatingBadge(rating = rating, modifier = Modifier.widthIn(min = 62.dp))
                    }

                    if (anime.views > 0) {
                        ViewsBadge(
                            views = anime.views,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .widthIn(max = 128.dp),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (expanded) {
                        Modifier.heightIn(min = YummySizes.animeCardInfoHeight)
                    } else {
                        Modifier.height(YummySizes.animeCardInfoHeight)
                    },
                )
                .padding(horizontal = YummySpacing.md, vertical = AnimeCardInfoVerticalPadding),
            verticalArrangement = Arrangement.spacedBy(AnimeCardInfoItemSpacing),
        ) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (expanded) AnimeCardExpandedTitleLines else AnimeCardCollapsedTitleLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AnimeCardTitleMinHeight),
            )

            Text(
                text = anime.meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AnimeCardMetaHeight),
            )
        }
    }
}
