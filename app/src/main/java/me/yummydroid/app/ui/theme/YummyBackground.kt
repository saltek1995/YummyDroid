package me.yummydroid.app.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

internal data class YummyBackgroundTextureSpec(
    val size: IntSize,
    val scale: Float,
)

internal fun Modifier.yummyAppBackground(): Modifier = drawWithCache {
    val outputSize = IntSize(
        width = size.width.roundToInt().coerceAtLeast(1),
        height = size.height.roundToInt().coerceAtLeast(1),
    )
    val texture = yummyBackgroundTextureSpec(outputSize.width, outputSize.height)
    val backgroundImage = yummyBackgroundImage(
        widthPx = texture.size.width,
        heightPx = texture.size.height,
        density = density * texture.scale,
    )

    onDrawBehind {
        drawImage(
            image = backgroundImage,
            srcOffset = IntOffset.Zero,
            srcSize = texture.size,
            dstOffset = IntOffset.Zero,
            dstSize = outputSize,
            filterQuality = FilterQuality.Low,
        )
    }
}

internal fun yummyBackgroundTextureSpec(
    widthPx: Int,
    heightPx: Int,
): YummyBackgroundTextureSpec {
    val safeWidth = widthPx.coerceAtLeast(1)
    val safeHeight = heightPx.coerceAtLeast(1)
    val scale = minOf(
        1f,
        YummyBackgroundMaxTextureWidthPx / safeWidth,
        YummyBackgroundMaxTextureHeightPx / safeHeight,
    )
    return YummyBackgroundTextureSpec(
        size = IntSize(
            width = (safeWidth * scale).roundToInt().coerceAtLeast(1),
            height = (safeHeight * scale).roundToInt().coerceAtLeast(1),
        ),
        scale = scale,
    )
}

private const val YummyBackgroundMaxTextureWidthPx = 960f
private const val YummyBackgroundMaxTextureHeightPx = 540f
