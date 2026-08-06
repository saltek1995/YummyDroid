package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.data.Anime
import me.yummydroid.app.ui.components.animatedFocusBorder
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySizes
import me.yummydroid.app.ui.theme.YummySpacing

private val AnimeCardTitleMinHeight = 48.dp
private val AnimeCardMetaHeight = 20.dp
private val AnimeCardInfoVerticalPadding = 8.dp
private val AnimeCardInfoItemSpacing = 2.dp

@Composable
internal fun AnimeCardSurface(
    anime: Anime,
    modifier: Modifier = Modifier,
    metaText: String? = null,
    expanded: Boolean = false,
    topEndContent: (@Composable () -> Unit)? = null,
    focusBorderActive: Boolean = false,
) {
    val shape = YummyRadii.smallShape
    val resolvedMetaText = metaText ?: remember(anime.year, anime.type, anime.status) {
        anime.meta
    }
    val overlayColor = MaterialTheme.colorScheme.surface
    val overlayBrush = remember(overlayColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                0.28f to overlayColor.copy(alpha = 0.78f),
                1f to overlayColor.copy(alpha = 0.96f),
            ),
        )
    }
    val bottomOverlayShape = RoundedCornerShape(
        bottomStart = YummyRadii.small,
        bottomEnd = YummyRadii.small,
    )
    Box(
        modifier = modifier.then(
            if (focusBorderActive) {
                Modifier.animatedFocusBorder(active = true)
            } else {
                Modifier
            },
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(AnimeCardPosterAspectRatio)
                .background(MaterialTheme.colorScheme.surfaceVariant, shape),
        ) {
            PosterImage(
                url = anime.posterUrl,
                contentDescription = anime.title,
                decodeToBounds = true,
                cornerRadius = YummyRadii.small,
                modifier = Modifier.fillMaxSize(),
            )
            if (anime.rating != null || anime.views > 0) {
                AnimeCardBadges(
                    rating = anime.rating,
                    views = anime.views,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(YummySpacing.sm),
                )
            }
            topEndContent?.let { content ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(YummySpacing.sm),
                ) {
                    content()
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .then(
                        if (expanded) {
                            Modifier.heightIn(min = YummySizes.animeCardInfoHeight)
                        } else {
                            Modifier.height(YummySizes.animeCardInfoHeight)
                        },
                    )
                    .background(overlayBrush, bottomOverlayShape)
                    .padding(
                        start = YummySpacing.md,
                        top = if (expanded) 18.dp else AnimeCardInfoVerticalPadding,
                        end = YummySpacing.md,
                        bottom = AnimeCardInfoVerticalPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(AnimeCardInfoItemSpacing, Alignment.Bottom),
            ) {
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) AnimeCardExpandedTitleLines else AnimeCardCollapsedTitleLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AnimeCardTitleMinHeight),
                )

                Text(
                    text = resolvedMetaText,
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
}

@Composable
private fun AnimeCardBadges(
    rating: Double?,
    views: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        rating?.let { value ->
            RatingBadge(rating = value, modifier = Modifier.widthIn(min = 62.dp))
        }

        if (views > 0) {
            ViewsBadge(
                views = views,
                modifier = Modifier.widthIn(max = 128.dp),
            )
        }
    }
}
