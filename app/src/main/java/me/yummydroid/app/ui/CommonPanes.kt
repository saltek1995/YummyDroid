package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.formatRating
import me.yummydroid.app.LoadState
import me.yummydroid.app.ui.theme.YummyAlpha
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySizes
import me.yummydroid.app.ui.theme.YummySpacing

@Composable
internal fun <T> AnimeListStateContent(
    state: LoadState<List<T>>,
    onRetry: () -> Unit,
    emptyMessage: String,
    content: @Composable (List<T>) -> Unit,
) {
    when (state) {
        LoadState.Loading -> LoadingPane(Modifier.fillMaxSize())
        is LoadState.Error -> ErrorPane(
            message = state.message,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
        )
        is LoadState.Ready -> {
            if (state.data.isEmpty()) {
                EmptyPane(emptyMessage, Modifier.fillMaxSize())
            } else {
                content(state.data)
            }
        }
    }
}

@Composable
internal fun DetailsStateContent(
    state: LoadState<AnimeDetails>,
    onRetry: () -> Unit,
    emptyMessage: String,
    content: @Composable (AnimeDetails) -> Unit,
) {
    when (state) {
        LoadState.Loading -> LoadingPane(Modifier.fillMaxSize())
        is LoadState.Error -> ErrorPane(
            message = state.message.ifBlank { emptyMessage },
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
        )
        is LoadState.Ready -> content(state.data)
    }
}

@Composable
internal fun LoadingPane(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun InlineErrorMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    if (message.isBlank()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.34f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = YummyRadii.smallShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = YummySpacing.sm, vertical = YummySpacing.sm),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                overflow = TextOverflow.Visible,
            )
        }
    }
}

@Composable
internal fun ErrorPane(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            DialogActionButton(
                text = uiText(UiStringKey.Retry),
                primary = true,
                onClick = onRetry,
            )
        }
    }
}

@Composable
internal fun EmptyPane(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

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
