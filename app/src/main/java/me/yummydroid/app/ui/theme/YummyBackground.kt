package me.yummydroid.app.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
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
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt

// YummyBackground
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

// YummyBackgroundBitmap
private data class YummyBackgroundBitmapKey(
    val widthPx: Int,
    val heightPx: Int,
    val densityKey: Int,
)

private data class YummyBackgroundPattern(
    val density: Float,
    val width: Float,
    val height: Float,
    val lineSpacing: Float = 34f * density,
    val lineShift: Float = 128f * density,
    val lineStroke: Float = density,
    val grainStepX: Float = 58f * density,
    val grainStepY: Float = 46f * density,
    val grainSize: Float = density.coerceAtLeast(1f),
    val accentGrainSize: Float = (1.4f * density).coerceAtLeast(1f),
)

private val bitmapCacheLock = Any()
private val bitmapCache = LruCache<YummyBackgroundBitmapKey, ImageBitmap>(3)

internal fun yummyBackgroundImage(
    widthPx: Int,
    heightPx: Int,
    density: Float,
): ImageBitmap {
    val key = YummyBackgroundBitmapKey(widthPx, heightPx, (density * 100f).roundToInt())
    synchronized(bitmapCacheLock) {
        bitmapCache.get(key)?.let { return it }
    }
    val created = createYummyBackgroundImage(widthPx, heightPx, density)
    synchronized(bitmapCacheLock) {
        bitmapCache.put(key, created)
    }
    return created
}

private fun createYummyBackgroundImage(
    widthPx: Int,
    heightPx: Int,
    density: Float,
): ImageBitmap {
    val bitmap = createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val pattern = YummyBackgroundPattern(density, widthPx.toFloat(), heightPx.toFloat())
    canvas.drawBaseGradient(paint, pattern)
    canvas.drawDiagonalLines(paint, pattern)
    canvas.drawGrain(paint, pattern)
    canvas.drawVerticalShade(paint, pattern)
    return bitmap.asImageBitmap()
}

private fun Canvas.drawBaseGradient(paint: Paint, pattern: YummyBackgroundPattern) {
    paint.shader = LinearGradient(
        0f,
        0f,
        pattern.width,
        pattern.height,
        intArrayOf(
            Color(0xFF121926).toArgb(),
            Color(0xFF1F2A3A).toArgb(),
            Color(0xFF133140).toArgb(),
        ),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    drawRect(0f, 0f, pattern.width, pattern.height, paint)
}

private fun Canvas.drawDiagonalLines(paint: Paint, pattern: YummyBackgroundPattern) {
    val diagonalLine = Color.White.copy(alpha = 0.035f).toArgb()
    val cyanLine = Color(0xFF00E5FF).copy(alpha = 0.030f).toArgb()
    paint.shader = null
    paint.strokeWidth = pattern.lineStroke
    paint.style = Paint.Style.STROKE
    var lineX = -pattern.height - pattern.lineShift
    var lineIndex = 0
    while (lineX < pattern.width + pattern.lineShift) {
        paint.color = if (lineIndex % 5 == 0) cyanLine else diagonalLine
        drawLine(lineX, pattern.height, lineX + pattern.height + pattern.lineShift, 0f, paint)
        lineX += pattern.lineSpacing
        lineIndex++
    }
}

private fun Canvas.drawGrain(paint: Paint, pattern: YummyBackgroundPattern) {
    val grainColor = Color(0xFFEAF2FF).copy(alpha = 0.10f).toArgb()
    val accentColor = YummyColors.focus.copy(alpha = 0.18f).toArgb()
    paint.style = Paint.Style.FILL
    var row = 0
    var y = 30f * pattern.density
    while (y < pattern.height) {
        drawGrainRow(paint, pattern, row, y, grainColor, accentColor)
        y += pattern.grainStepY
        row++
    }
}

private fun Canvas.drawGrainRow(
    paint: Paint,
    pattern: YummyBackgroundPattern,
    row: Int,
    y: Float,
    grainColor: Int,
    accentColor: Int,
) {
    var column = 0
    var x = 26f * pattern.density + if (row % 2 == 0) 0f else pattern.grainStepX / 2f
    while (x < pattern.width) {
        if ((row + column) % 4 == 0) {
            paint.color = grainColor
            drawRect(x, y, x + pattern.grainSize, y + pattern.grainSize, paint)
        }
        if ((row * 5 + column * 3) % 19 == 0) {
            paint.color = accentColor
            val accentX = x + pattern.grainSize * 3f
            val accentY = y + pattern.grainSize
            drawRect(
                accentX,
                accentY,
                accentX + pattern.accentGrainSize,
                accentY + pattern.accentGrainSize,
                paint,
            )
        }
        x += pattern.grainStepX
        column++
    }
}

private fun Canvas.drawVerticalShade(paint: Paint, pattern: YummyBackgroundPattern) {
    paint.shader = LinearGradient(
        0f,
        0f,
        0f,
        pattern.height,
        intArrayOf(
            Color.White.copy(alpha = 0.020f).toArgb(),
            Color.Transparent.toArgb(),
            Color.Black.copy(alpha = 0.12f).toArgb(),
        ),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    drawRect(0f, 0f, pattern.width, pattern.height, paint)
}
