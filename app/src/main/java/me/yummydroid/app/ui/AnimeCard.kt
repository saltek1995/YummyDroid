package me.yummydroid.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import me.yummydroid.app.data.Anime
import me.yummydroid.app.ui.components.clearFocusAfterTouch
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySizes
import me.yummydroid.app.ui.theme.YummySpacing

private const val AnimeCardPosterAspectRatio = 2f / 3f
private const val AnimeCardCollapsedTitleLines = 2
private const val AnimeCardExpandedTitleLines = 8
private val AnimeCardTitleMinHeight = 48.dp
private val AnimeCardMetaHeight = 20.dp
private val AnimeCardInfoVerticalPadding = 8.dp
private val AnimeCardInfoItemSpacing = 2.dp
private const val AnimeCardFocusedScale = 1.035f
private const val AnimeCardScaleDurationMillis = 90

@Composable
internal fun AnimeCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean? = null,
    posterDecodeSizePx: IntSize? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var localFocused by remember { mutableStateOf(false) }
    var touchHeld by remember { mutableStateOf(false) }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused = focused ?: localFocused
    val expanded = isFocused || isPressed || touchHeld
    val focusScale = remember { Animatable(1f) }

    LaunchedEffect(expanded) {
        focusScale.animateTo(
            targetValue = if (expanded) AnimeCardFocusedScale else 1f,
            animationSpec = tween(
                durationMillis = AnimeCardScaleDurationMillis,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    Box(
        modifier = modifier
            .zIndex(if (expanded) 8f else 0f)
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (focused == null) {
                    localFocused = state.isFocused || state.hasFocus
                }
            }
            .pointerInput(Unit) {
                try {
                    awaitEachGesture {
                        val down = awaitPointerEvent(PointerEventPass.Initial)
                            .changes
                            .firstOrNull { it.pressed }
                            ?: return@awaitEachGesture
                        touchHeld = true
                        var pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val tracked = event.changes.firstOrNull { it.id == pointerId }
                            when {
                                tracked == null -> {
                                    val replacement = event.changes.firstOrNull { it.pressed }
                                    if (replacement == null) {
                                        touchHeld = false
                                        break
                                    }
                                    pointerId = replacement.id
                                }
                                tracked.changedToUpIgnoreConsumed() || !tracked.pressed -> {
                                    touchHeld = false
                                    break
                                }
                            }
                        }
                    }
                } finally {
                    touchHeld = false
                }
            }
            .clearFocusAfterTouch()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        AnimeCardSurface(
            anime = anime,
            expanded = expanded,
            posterDecodeSizePx = posterDecodeSizePx,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val scale = focusScale.value
                    scaleX = scale
                    scaleY = scale
                    shape = YummyRadii.smallShape
                    clip = false
                },
        )
    }
}

@Composable
internal fun AnimeCardSurface(
    anime: Anime,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    posterDecodeSizePx: IntSize? = null,
) {
    val shape = YummyRadii.smallShape
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
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = if (expanded) 2.dp else 1.dp,
            color = if (expanded) {
                YummyColors.focus
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
            },
        ),
        shape = shape,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(AnimeCardPosterAspectRatio)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            PosterImage(
                url = anime.posterUrl,
                contentDescription = anime.title,
                decodeSizePx = posterDecodeSizePx,
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
                    .background(overlayBrush)
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimeCardBadges(
    rating: Double?,
    views: Long,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.xs),
        verticalArrangement = Arrangement.spacedBy(YummySpacing.xs),
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
