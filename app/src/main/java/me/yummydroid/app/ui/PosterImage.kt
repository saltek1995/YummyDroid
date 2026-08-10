package me.yummydroid.app.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size

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
