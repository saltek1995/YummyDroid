package me.yummydroid.app.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

internal fun Modifier.yummyAppBackground(): Modifier = drawWithCache {
    val widthPx = size.width.roundToInt().coerceAtLeast(1)
    val heightPx = size.height.roundToInt().coerceAtLeast(1)
    val textureScale = minOf(
        1f,
        YummyBackgroundMaxTextureWidthPx / widthPx.toFloat(),
        YummyBackgroundMaxTextureHeightPx / heightPx.toFloat(),
    )
    val textureWidthPx = (widthPx * textureScale).roundToInt().coerceAtLeast(1)
    val textureHeightPx = (heightPx * textureScale).roundToInt().coerceAtLeast(1)
    val backgroundImage = yummyBackgroundImage(
        widthPx = textureWidthPx,
        heightPx = textureHeightPx,
        density = density * textureScale,
    )

    onDrawBehind {
        drawImage(
            image = backgroundImage,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(textureWidthPx, textureHeightPx),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(widthPx, heightPx),
            filterQuality = FilterQuality.Low,
        )
    }
}

private fun yummyBackgroundImage(
    widthPx: Int,
    heightPx: Int,
    density: Float,
): ImageBitmap {
    val key = YummyBackgroundBitmapKey(
        widthPx = widthPx,
        heightPx = heightPx,
        densityKey = (density * 100f).roundToInt(),
    )
    synchronized(YummyBackgroundCacheLock) {
        YummyBackgroundBitmapCache.get(key)?.let { return it }
    }
    val created = createYummyBackgroundImage(
        widthPx = widthPx,
        heightPx = heightPx,
        density = density,
    )
    synchronized(YummyBackgroundCacheLock) {
        YummyBackgroundBitmapCache.put(key, created)
    }
    return created
}

private fun createYummyBackgroundImage(
    widthPx: Int,
    heightPx: Int,
    density: Float,
): ImageBitmap {
    val width = widthPx.toFloat()
    val height = heightPx.toFloat()
    val lineSpacing = 34f * density
    val lineShift = 128f * density
    val lineStroke = 1f * density
    val grainStepX = 58f * density
    val grainStepY = 46f * density
    val grainSize = (1f * density).coerceAtLeast(1f)
    val accentGrainSize = (1.4f * density).coerceAtLeast(1f)
    val diagonalLine = Color.White.copy(alpha = 0.035f)
    val cyanLine = Color(0xFF00E5FF).copy(alpha = 0.030f)
    val grain = Color(0xFFEAF2FF).copy(alpha = 0.10f)
    val amberGrain = YummyColors.focus.copy(alpha = 0.18f)

    val backgroundBitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(backgroundBitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f,
        0f,
        width,
        height,
        intArrayOf(
            Color(0xFF121926).toArgb(),
            Color(0xFF1F2A3A).toArgb(),
            Color(0xFF133140).toArgb(),
        ),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, width, height, paint)

    paint.shader = null
    paint.strokeWidth = lineStroke
    paint.style = Paint.Style.STROKE
    var lineX = -height - lineShift
    var lineIndex = 0
    while (lineX < width + lineShift) {
        paint.color = if (lineIndex % 5 == 0) cyanLine.toArgb() else diagonalLine.toArgb()
        canvas.drawLine(
            lineX,
            height,
            lineX + height + lineShift,
            0f,
            paint,
        )
        lineX += lineSpacing
        lineIndex++
    }

    paint.style = Paint.Style.FILL
    var row = 0
    var y = 30f * density
    while (y < height) {
        var column = 0
        var x = 26f * density + if (row % 2 == 0) 0f else grainStepX / 2f
        while (x < width) {
            if ((row + column) % 4 == 0) {
                paint.color = grain.toArgb()
                canvas.drawRect(x, y, x + grainSize, y + grainSize, paint)
            }
            if ((row * 5 + column * 3) % 19 == 0) {
                paint.color = amberGrain.toArgb()
                val accentX = x + grainSize * 3f
                val accentY = y + grainSize
                canvas.drawRect(
                    accentX,
                    accentY,
                    accentX + accentGrainSize,
                    accentY + accentGrainSize,
                    paint,
                )
            }
            x += grainStepX
            column++
        }
        y += grainStepY
        row++
    }

    paint.shader = LinearGradient(
        0f,
        0f,
        0f,
        height,
        intArrayOf(
            Color.White.copy(alpha = 0.020f).toArgb(),
            Color.Transparent.toArgb(),
            Color.Black.copy(alpha = 0.12f).toArgb(),
        ),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, width, height, paint)
    return backgroundBitmap.asImageBitmap()
}

private data class YummyBackgroundBitmapKey(
    val widthPx: Int,
    val heightPx: Int,
    val densityKey: Int,
)

private const val YummyBackgroundMaxTextureWidthPx = 960f
private const val YummyBackgroundMaxTextureHeightPx = 540f
private val YummyBackgroundCacheLock = Any()
private val YummyBackgroundBitmapCache = LruCache<YummyBackgroundBitmapKey, ImageBitmap>(3)
