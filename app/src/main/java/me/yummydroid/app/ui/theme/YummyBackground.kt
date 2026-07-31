package me.yummydroid.app.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal fun Modifier.yummyAppBackground(): Modifier = drawWithCache {
    val widthPx = size.width.roundToInt().coerceAtLeast(1)
    val heightPx = size.height.roundToInt().coerceAtLeast(1)
    val lineSpacing = 34.dp.toPx()
    val lineShift = 128.dp.toPx()
    val lineStroke = 1.dp.toPx()
    val grainStepX = 58.dp.toPx()
    val grainStepY = 46.dp.toPx()
    val grainSize = 1.dp.toPx().coerceAtLeast(1f)
    val accentGrainSize = 1.4.dp.toPx().coerceAtLeast(1f)
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
        size.width,
        size.height,
        intArrayOf(
            Color(0xFF121926).toArgb(),
            Color(0xFF1F2A3A).toArgb(),
            Color(0xFF133140).toArgb(),
        ),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, size.width, size.height, paint)

    paint.shader = null
    paint.strokeWidth = lineStroke
    paint.style = Paint.Style.STROKE
    var lineX = -size.height - lineShift
    var lineIndex = 0
    while (lineX < size.width + lineShift) {
        paint.color = if (lineIndex % 5 == 0) cyanLine.toArgb() else diagonalLine.toArgb()
        canvas.drawLine(
            lineX,
            size.height,
            lineX + size.height + lineShift,
            0f,
            paint,
        )
        lineX += lineSpacing
        lineIndex++
    }

    paint.style = Paint.Style.FILL
    var row = 0
    var y = 30.dp.toPx()
    while (y < size.height) {
        var column = 0
        var x = 26.dp.toPx() + if (row % 2 == 0) 0f else grainStepX / 2f
        while (x < size.width) {
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
        size.height,
        intArrayOf(
            Color.White.copy(alpha = 0.020f).toArgb(),
            Color.Transparent.toArgb(),
            Color.Black.copy(alpha = 0.12f).toArgb(),
        ),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, size.width, size.height, paint)
    val backgroundImage = backgroundBitmap.asImageBitmap()

    onDrawBehind {
        drawImage(backgroundImage)
    }
}
