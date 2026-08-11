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
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
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
import androidx.compose.ui.graphics.vector.ImageVector
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
    val presentation = rememberAnimeCardPresentation(anime = anime, metaText = metaText)

    Box(
        modifier = modifier.animeCardInteraction(
            presentation = presentation,
            interactionSource = interactionSource,
            onClick = onClick,
        ),
    ) {
        AnimeCardSurface(
            anime = anime,
            metaText = presentation.metaText,
            expanded = presentation.expanded,
            topEndContent = topEndContent,
            modifier = Modifier
                .fillMaxWidth()
                .then(animeCardScaleModifier(presentation)),
            focusBorderActive = presentation.dpadFocused,
        )
    }
}

private data class AnimeCardPresentation(
    val dpadFocused: Boolean,
    val expanded: Boolean,
    val scaled: Boolean,
    val touchScaleEnabled: Boolean,
    val scale: Float,
    val metaText: String,
    val onFocusChanged: (Boolean) -> Unit,
    val onTouchHeldChange: (Boolean) -> Unit,
)

@Composable
private fun rememberAnimeCardPresentation(
    anime: Anime,
    metaText: String?,
): AnimeCardPresentation {
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

    return AnimeCardPresentation(
        dpadFocused = dpadFocused,
        expanded = expanded,
        scaled = scaled,
        touchScaleEnabled = touchScaleEnabled,
        scale = focusScale?.value ?: 1f,
        metaText = resolvedMetaText,
        onFocusChanged = { localFocused = it },
        onTouchHeldChange = { touchHeld = it },
    )
}

private fun Modifier.animeCardInteraction(
    presentation: AnimeCardPresentation,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = this
    .then(if (presentation.expanded) Modifier.zIndex(8f) else Modifier)
    .fillMaxWidth()
    .onFocusChanged { state ->
        presentation.onFocusChanged(state.isFocused || state.hasFocus)
    }
    .animeCardTouchHold(
        enabled = presentation.touchScaleEnabled,
        onTouchHeldChange = presentation.onTouchHeldChange,
    )
    .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
    )

private fun animeCardScaleModifier(presentation: AnimeCardPresentation): Modifier {
    if (!presentation.scaled && presentation.scale == 1f) return Modifier
    return Modifier.graphicsLayer {
        scaleX = presentation.scale
        scaleY = presentation.scale
        clip = false
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
        modifier = modifier.then(animeCardFocusBorder(focusBorderActive)),
    ) {
        AnimeCardArtwork(
            anime = anime,
            metaText = resolvedMetaText,
            expanded = expanded,
            overlayBrush = overlayBrush,
            bottomOverlayShape = bottomOverlayShape,
            topEndContent = topEndContent,
        )
    }
}

private fun animeCardFocusBorder(active: Boolean): Modifier {
    return if (active) Modifier.animatedFocusBorder(active = true) else Modifier
}

@Composable
private fun AnimeCardArtwork(
    anime: Anime,
    metaText: String,
    expanded: Boolean,
    overlayBrush: Brush,
    bottomOverlayShape: RoundedCornerShape,
    topEndContent: (@Composable () -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(AnimeCardPosterAspectRatio)
            .background(MaterialTheme.colorScheme.surfaceVariant, YummyRadii.smallShape),
    ) {
        PosterImage(
            url = anime.posterUrl,
            contentDescription = anime.title,
            decodeToBounds = true,
            cornerRadius = YummyRadii.small,
            modifier = Modifier.fillMaxSize(),
        )
        AnimeCardMetricOverlay(anime)
        topEndContent?.let { content -> AnimeCardTopEndContent(content) }
        AnimeCardInfo(
            title = anime.title,
            metaText = metaText,
            expanded = expanded,
            overlayBrush = overlayBrush,
            bottomOverlayShape = bottomOverlayShape,
        )
    }
}

@Composable
private fun AnimeCardMetricOverlay(anime: Anime) {
    if (anime.rating == null && anime.views <= 0) return
    AnimeCardBadges(
        rating = anime.rating,
        views = anime.views,
        modifier = Modifier
            .fillMaxWidth()
            .padding(YummySpacing.sm),
    )
}

@Composable
private fun BoxScope.AnimeCardTopEndContent(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(YummySpacing.sm),
    ) {
        content()
    }
}

@Composable
private fun BoxScope.AnimeCardInfo(
    title: String,
    metaText: String,
    expanded: Boolean,
    overlayBrush: Brush,
    bottomOverlayShape: RoundedCornerShape,
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .then(animeCardInfoHeight(expanded))
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
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (expanded) AnimeCardExpandedTitleLines else AnimeCardCollapsedTitleLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().heightIn(min = AnimeCardTitleMinHeight),
        )
        Text(
            text = metaText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().height(AnimeCardMetaHeight),
        )
    }
}

private fun animeCardInfoHeight(expanded: Boolean): Modifier {
    return if (expanded) {
        Modifier.heightIn(min = YummySizes.animeCardInfoHeight)
    } else {
        Modifier.height(YummySizes.animeCardInfoHeight)
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
                awaitAnimeCardTouchEnd(down.id)
                onTouchHeldChange(false)
            }
        } finally {
            onTouchHeldChange(false)
        }
    }.clearFocusAfterTouch()
}

private suspend fun AwaitPointerEventScope.awaitAnimeCardTouchEnd(initialPointerId: PointerId) {
    var pointerId = initialPointerId
    while (true) {
        val changes = awaitPointerEvent(PointerEventPass.Initial).changes
        val tracked = changes.firstOrNull { it.id == pointerId }
        if (tracked?.changedToUpIgnoreConsumed() == true || tracked?.pressed == false) return
        if (tracked == null) {
            pointerId = changes.firstOrNull { it.pressed }?.id ?: return
        }
    }
}

// AnimeMetricBadges
@Composable
internal fun RatingBadge(
    rating: Double,
    modifier: Modifier = Modifier,
) {
    val ratingText = remember(rating) { formatRating(rating) }
    MetricBadge(
        icon = Icons.Default.Star,
        text = ratingText,
        backgroundColor = YummyColors.rating,
        contentColor = Color(0xFF211200),
        modifier = modifier,
    )
}

@Composable
internal fun ViewsBadge(
    views: Long,
    modifier: Modifier = Modifier,
) {
    MetricBadge(
        icon = Icons.Default.Visibility,
        text = localizedViews(views),
        backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = YummyAlpha.badgeSurface),
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

@Composable
private fun MetricBadge(
    icon: ImageVector,
    text: String,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(backgroundColor, YummyRadii.smallShape)
            .padding(horizontal = YummySpacing.sm, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.xs),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(YummySizes.badgeIcon),
            tint = contentColor,
        )
        Text(
            text = text,
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
