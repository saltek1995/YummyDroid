package me.yummydroid.app.ui.components

import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val FocusBorderFadeDurationMillis = 80
private const val FocusBorderRotationDurationMillis = 4200
private const val FocusBorderRotationFrameMillis = 50L
private val DefaultFocusBorderWidth = 2.dp
private val DefaultFocusHighlightWidth = 2.5.dp
private val DefaultFocusBorderCorner = 8.dp

fun Modifier.animatedFocusBorder(
    active: Boolean,
    cornerRadius: Dp = DefaultFocusBorderCorner,
    borderWidth: Dp = DefaultFocusBorderWidth,
    highlightWidth: Dp = DefaultFocusHighlightWidth,
): Modifier = composed {
    val inputModeManager = LocalInputModeManager.current
    val visible = active && inputModeManager.inputMode != InputMode.Touch
    if (!visible) {
        return@composed this
    }

    val borderProgress = remember { Animatable(0f) }
    var borderFrameIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(visible) {
        if (visible) {
            borderProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = FocusBorderFadeDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
            while (true) {
                delay(FocusBorderRotationFrameMillis)
                borderFrameIndex = (borderFrameIndex + 1) % FocusBorderFrameCount
            }
        } else {
            borderProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = FocusBorderFadeDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
            )
            borderFrameIndex = 0
        }
    }

    drawWithCache {
        val strokeWidth = borderWidth.toPx()
        val highlightStrokeWidth = highlightWidth.toPx()
        val inset = maxOf(strokeWidth, highlightStrokeWidth) / 2f
        val rect = RectF(
            inset,
            inset,
            (size.width - inset).coerceAtLeast(inset),
            (size.height - inset).coerceAtLeast(inset),
        )
        val radius = cornerRadius.toPx().coerceAtMost(minOf(rect.width(), rect.height()) / 2f)
        val frameKey = FocusBorderFrameKey(
            widthPx = size.width.roundToInt().coerceAtLeast(1),
            heightPx = size.height.roundToInt().coerceAtLeast(1),
            strokeWidthPx = strokeWidth.roundToInt().coerceAtLeast(1),
            highlightStrokeWidthPx = highlightStrokeWidth.roundToInt().coerceAtLeast(1),
            cornerRadiusPx = radius.roundToInt().coerceAtLeast(0),
        )
        var frames: FocusBorderFrameSequence? = null

        fun cachedFrames(): FocusBorderFrameSequence {
            frames?.let { return it }
            return cachedFocusBorderFrames(
                frameKey = frameKey,
                inset = inset,
                radius = radius,
                strokeWidth = strokeWidth,
                highlightStrokeWidth = highlightStrokeWidth,
            ).also { cached -> frames = cached }
        }

        onDrawWithContent {
            drawContent()
            val alpha = borderProgress.value.coerceIn(0f, 1f)
            if (alpha <= 0.001f || rect.width() <= 0f || rect.height() <= 0f) {
                return@onDrawWithContent
            }

            val nativeCanvas = drawContext.canvas.nativeCanvas
            val frame = cachedFrames().frame(borderFrameIndex)
            if (alpha >= 0.999f) {
                nativeCanvas.drawPicture(frame)
            } else {
                val checkpoint = nativeCanvas.saveLayerAlpha(
                    0f,
                    0f,
                    frameKey.widthPx.toFloat(),
                    frameKey.heightPx.toFloat(),
                    (255f * alpha).roundToInt().coerceIn(0, 255),
                )
                nativeCanvas.drawPicture(frame)
                nativeCanvas.restoreToCount(checkpoint)
            }
        }
    }
}
