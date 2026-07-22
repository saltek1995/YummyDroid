package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.formatRating
import me.yummydroid.app.formatViews
import me.yummydroid.app.LoadState
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyAlpha
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
            Button(onClick = onRetry, modifier = Modifier.focusRing(RoundedCornerShape(8.dp))) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(uiText("Повторить"))
            }
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
) {
    val context = LocalContext.current

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Composable
internal fun RatingBadge(
    rating: Double,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = YummyRadii.smallShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = YummySpacing.sm, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.xs),
        ) {
            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(YummySizes.badgeIcon))
            Text(
                text = formatRating(rating),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
internal fun ViewsBadge(
    views: Long,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = YummyRadii.smallShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = YummyAlpha.badgeSurface),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = YummySpacing.sm, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.xs),
        ) {
            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(YummySizes.badgeIcon))
            Text(
                text = formatViews(views),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
