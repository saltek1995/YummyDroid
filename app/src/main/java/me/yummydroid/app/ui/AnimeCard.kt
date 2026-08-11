package me.yummydroid.app.ui

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import me.yummydroid.app.data.Anime
import me.yummydroid.app.formatRating
import me.yummydroid.app.ui.components.animatedFocusBorder
import me.yummydroid.app.ui.components.clearFocusAfterTouch
import me.yummydroid.app.ui.theme.YummyAlpha
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySizes
import me.yummydroid.app.ui.theme.YummySpacing

// AnimeCardPresentation
internal const val AnimeCardPosterAspectRatio = 2f / 3f
internal const val AnimeCardTouchScale = 1.035f
internal const val AnimeCardScaleDurationMillis = 90
internal const val AnimeCardCollapsedTitleLines = 2
internal const val AnimeCardExpandedTitleLines = 8

internal fun animeCardTouchScaleEnabled(uiMode: Int): Boolean {
    return (uiMode and Configuration.UI_MODE_TYPE_MASK) != Configuration.UI_MODE_TYPE_TELEVISION
}

internal fun animeCardExpanded(dpadFocused: Boolean, touchHeld: Boolean): Boolean {
    return dpadFocused || touchHeld
}

internal fun animeCardScaled(touchScaleEnabled: Boolean, touchHeld: Boolean): Boolean {
    return touchScaleEnabled && touchHeld
}

internal fun animeCardMetaText(anime: Anime, overrideText: String?): String {
    return overrideText ?: anime.meta
}

// AnimeCardRuntime
@Composable
internal fun AnimeCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    metaText: String? = null,
    topEndContent: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var touchHeld by remember { mutableStateOf(false) }
    var localFocused by remember { mutableStateOf(false) }
    val inputModeManager = LocalInputModeManager.current
    val configuration = LocalConfiguration.current
    val touchScaleEnabled = remember(configuration.uiMode) {
        animeCardTouchScaleEnabled(configuration.uiMode)
    }
    val dpadFocused = localFocused && inputModeManager.inputMode != InputMode.Touch
    val expanded = animeCardExpanded(dpadFocused = dpadFocused, touchHeld = touchHeld)
    val scaled = animeCardScaled(touchScaleEnabled = touchScaleEnabled, touchHeld = touchHeld)
    val focusScale = remember(touchScaleEnabled) {
        if (touchScaleEnabled) Animatable(1f) else null
    }
    val resolvedMetaText = remember(metaText, anime.year, anime.type, anime.status) {
        animeCardMetaText(anime, metaText)
    }

    if (focusScale != null) {
        LaunchedEffect(scaled) {
            focusScale.animateTo(
                targetValue = if (scaled) AnimeCardTouchScale else 1f,
                animationSpec = tween(
                    durationMillis = AnimeCardScaleDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    Box(
        modifier = modifier
            .then(if (expanded) Modifier.zIndex(8f) else Modifier)
            .fillMaxWidth()
            .onFocusChanged { state ->
                localFocused = state.isFocused || state.hasFocus
            }
            .animeCardTouchHold(
                enabled = touchScaleEnabled,
                onTouchHeldChange = { touchHeld = it },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        val scale = focusScale?.value ?: 1f
        val touchScaleModifier = if (scaled || scale != 1f) {
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                clip = false
            }
        } else {
            Modifier
        }
        AnimeCardSurface(
            anime = anime,
            metaText = resolvedMetaText,
            expanded = expanded,
            topEndContent = topEndContent,
            modifier = Modifier
                .fillMaxWidth()
                .then(touchScaleModifier),
            focusBorderActive = dpadFocused,
        )
    }
}

// AnimeCardSurface
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

// AnimeCardTouchInput
internal fun Modifier.animeCardTouchHold(
    enabled: Boolean,
    onTouchHeldChange: (Boolean) -> Unit,
): Modifier {
    if (!enabled) return this
    return pointerInput(Unit) {
        try {
            awaitEachGesture {
                val down = awaitPointerEvent(PointerEventPass.Initial)
                    .changes
                    .firstOrNull { it.pressed }
                    ?: return@awaitEachGesture
                onTouchHeldChange(true)
                var pointerId = down.id
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val tracked = event.changes.firstOrNull { it.id == pointerId }
                    when {
                        tracked == null -> {
                            val replacement = event.changes.firstOrNull { it.pressed }
                            if (replacement == null) {
                                onTouchHeldChange(false)
                                break
                            }
                            pointerId = replacement.id
                        }
                        tracked.changedToUpIgnoreConsumed() || !tracked.pressed -> {
                            onTouchHeldChange(false)
                            break
                        }
                    }
                }
            }
        } finally {
            onTouchHeldChange(false)
        }
    }.clearFocusAfterTouch()
}

// AnimeMetricBadges
@Composable
internal fun RatingBadge(
    rating: Double,
    modifier: Modifier = Modifier,
) {
    val ratingText = remember(rating) { formatRating(rating) }
    val contentColor = Color(0xFF211200)
    Row(
        modifier = modifier
            .background(YummyColors.rating, YummyRadii.smallShape)
            .padding(horizontal = YummySpacing.sm, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.xs),
    ) {
        Icon(
            Icons.Default.Star,
            contentDescription = null,
            modifier = Modifier.size(YummySizes.badgeIcon),
            tint = contentColor,
        )
        Text(
            text = ratingText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor,
        )
    }
}

@Composable
internal fun ViewsBadge(
    views: Long,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = YummyAlpha.badgeSurface)
    val contentColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .background(backgroundColor, YummyRadii.smallShape)
            .padding(horizontal = YummySpacing.sm, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.xs),
    ) {
        Icon(
            Icons.Default.Visibility,
            contentDescription = null,
            modifier = Modifier.size(YummySizes.badgeIcon),
            tint = contentColor,
        )
        Text(
            text = localizedViews(views),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// PosterImage
@Composable
internal fun PosterImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    decodeToBounds: Boolean = false,
    cornerRadius: Dp = 0.dp,
) {
    val context = LocalContext.current
    val requestSize = if (decodeToBounds) PosterCardTextureSize else Size.ORIGINAL
    val cardMemoryCacheKey = remember(url, decodeToBounds) {
        if (decodeToBounds) {
            "$PosterCardMemoryCacheKeyPrefix$url"
        } else {
            null
        }
    }
    val imageModifier = if (cornerRadius > 0.dp) {
        modifier.clip(RoundedCornerShape(cornerRadius))
    } else {
        modifier
    }
    val model = remember(context, url, decodeToBounds, requestSize, cardMemoryCacheKey) {
        ImageRequest.Builder(context)
            .data(url)
            .apply {
                size(requestSize)
                if (decodeToBounds) {
                    precision(Precision.EXACT)
                    memoryCachePolicy(CachePolicy.ENABLED)
                    diskCachePolicy(CachePolicy.ENABLED)
                    cardMemoryCacheKey?.let(::memoryCacheKey)
                    allowHardware(true)
                }
            }
            .crossfade(false)
            .build()
    }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = imageModifier,
    )
}

private const val PosterCardTextureWidthPx = 320
private const val PosterCardTextureHeightPx = 480
private const val PosterCardMemoryCacheKeyPrefix = "poster-card-320:"
private val PosterCardTextureSize = Size(PosterCardTextureWidthPx, PosterCardTextureHeightPx)
