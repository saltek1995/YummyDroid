package me.yummydroid.app.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal fun Modifier.yummyAppBackground(): Modifier = drawWithCache {
    val lineSpacing = 34.dp.toPx()
    val lineShift = 128.dp.toPx()
    val lineStroke = 1.dp.toPx()
    val grainStepX = 58.dp.toPx()
    val grainStepY = 46.dp.toPx()
    val grainSize = 1.dp.toPx().coerceAtLeast(1f)
    val accentGrainSize = 1.4.dp.toPx().coerceAtLeast(1f)
    val baseBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF121926),
            Color(0xFF1F2A3A),
            Color(0xFF133140),
        ),
        start = Offset.Zero,
        end = Offset(size.width, size.height),
    )
    val depthBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.020f),
            Color.Transparent,
            Color.Black.copy(alpha = 0.12f),
        ),
    )
    val diagonalLine = Color.White.copy(alpha = 0.035f)
    val cyanLine = Color(0xFF00E5FF).copy(alpha = 0.030f)
    val grain = Color(0xFFEAF2FF).copy(alpha = 0.10f)
    val amberGrain = YummyColors.focus.copy(alpha = 0.18f)

    onDrawBehind {
        drawRect(baseBrush)

        var lineX = -size.height - lineShift
        var lineIndex = 0
        while (lineX < size.width + lineShift) {
            drawLine(
                color = if (lineIndex % 5 == 0) cyanLine else diagonalLine,
                start = Offset(lineX, size.height),
                end = Offset(lineX + size.height + lineShift, 0f),
                strokeWidth = lineStroke,
            )
            lineX += lineSpacing
            lineIndex++
        }

        var row = 0
        var y = 30.dp.toPx()
        while (y < size.height) {
            var column = 0
            var x = 26.dp.toPx() + if (row % 2 == 0) 0f else grainStepX / 2f
            while (x < size.width) {
                if ((row + column) % 4 == 0) {
                    drawRect(
                        color = grain,
                        topLeft = Offset(x, y),
                        size = Size(grainSize, grainSize),
                    )
                }
                if ((row * 5 + column * 3) % 19 == 0) {
                    drawRect(
                        color = amberGrain,
                        topLeft = Offset(x + grainSize * 3f, y + grainSize),
                        size = Size(accentGrainSize, accentGrainSize),
                    )
                }
                x += grainStepX
                column++
            }
            y += grainStepY
            row++
        }

        drawRect(depthBrush)
    }
}
