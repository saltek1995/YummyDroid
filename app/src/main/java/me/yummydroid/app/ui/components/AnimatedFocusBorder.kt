package me.yummydroid.app.ui.components

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.LruCache
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val FocusBorderFadeDurationMillis = 80
private const val FocusBorderRotationDurationMillis = 4200
private const val FocusBorderRotationFrameMillis = 50L
private const val FocusBorderFrameCount = 84
private val DefaultFocusBorderWidth = 2.dp
private val DefaultFocusHighlightWidth = 2.5.dp
private val DefaultFocusBorderCorner = 8.dp
private val FocusBorderPurple = Color(0xFF5A2A78)
private val FocusBorderDeepPurple = Color(0xFF26113A)
private val FocusBorderOrange = Color(0xFFFFB454)
private val FocusBorderFrameCache = LruCache<FocusBorderFrameKey, FocusBorderFrameSequence>(24)

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
            synchronized(FocusBorderFrameCache) {
                FocusBorderFrameCache.get(frameKey)?.let { cached ->
                    frames = cached
                    return cached
                }
            }
            val created = FocusBorderFrameSequence(
                key = frameKey,
                inset = inset,
                radius = radius,
                strokeWidth = strokeWidth,
                highlightStrokeWidth = highlightStrokeWidth,
            )
            synchronized(FocusBorderFrameCache) {
                FocusBorderFrameCache.put(frameKey, created)
            }
            frames = created
            return created
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

private data class FocusBorderFrameKey(
    val widthPx: Int,
    val heightPx: Int,
    val strokeWidthPx: Int,
    val highlightStrokeWidthPx: Int,
    val cornerRadiusPx: Int,
)

private class FocusBorderFrameSequence(
    private val key: FocusBorderFrameKey,
    private val inset: Float,
    private val radius: Float,
    private val strokeWidth: Float,
    private val highlightStrokeWidth: Float,
) {
    private val frames = arrayOfNulls<Picture>(FocusBorderFrameCount)

    fun frame(index: Int): Picture {
        val boundedIndex = index.floorMod(FocusBorderFrameCount)
        frames[boundedIndex]?.let { return it }
        synchronized(this) {
            frames[boundedIndex]?.let { return it }
            val created = buildFocusBorderFrame(
                key = key,
                inset = inset,
                radius = radius,
                strokeWidth = strokeWidth,
                highlightStrokeWidth = highlightStrokeWidth,
                frameIndex = boundedIndex,
            )
            frames[boundedIndex] = created
            return created
        }
    }
}

private fun buildFocusBorderFrame(
    key: FocusBorderFrameKey,
    inset: Float,
    radius: Float,
    strokeWidth: Float,
    highlightStrokeWidth: Float,
    frameIndex: Int,
): Picture {
    val rect = RectF(
        inset,
        inset,
        (key.widthPx - inset).coerceAtLeast(inset),
        (key.heightPx - inset).coerceAtLeast(inset),
    )
    val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        setStyle(Paint.Style.STROKE)
        setStrokeWidth(strokeWidth)
        setStrokeJoin(Paint.Join.ROUND)
        setStrokeCap(Paint.Cap.ROUND)
        setColor(FocusBorderPurple.toArgb())
        setAlpha((255f * 0.82f).roundToInt().coerceIn(0, 255))
    }
    val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        setStyle(Paint.Style.STROKE)
        setStrokeWidth(highlightStrokeWidth)
        setStrokeJoin(Paint.Join.ROUND)
        setStrokeCap(Paint.Cap.ROUND)
        setAlpha((255f * 0.95f).roundToInt().coerceIn(0, 255))
    }
    val sweepShader = SweepGradient(
        rect.centerX(),
        rect.centerY(),
        intArrayOf(
            FocusBorderDeepPurple.toArgb(),
            FocusBorderPurple.toArgb(),
            FocusBorderOrange.toArgb(),
            FocusBorderPurple.toArgb(),
            FocusBorderDeepPurple.toArgb(),
            FocusBorderOrange.toArgb(),
            FocusBorderPurple.toArgb(),
            FocusBorderDeepPurple.toArgb(),
        ),
        floatArrayOf(0f, 0.16f, 0.30f, 0.44f, 0.58f, 0.72f, 0.86f, 1f),
    )
    val shaderMatrix = Matrix()
    highlightPaint.setShader(sweepShader)

    val picture = Picture()
    val canvas = picture.beginRecording(key.widthPx, key.heightPx)
    val rotation = 360f * frameIndex / FocusBorderFrameCount
    shaderMatrix.setRotate(rotation, rect.centerX(), rect.centerY())
    sweepShader.setLocalMatrix(shaderMatrix)
    canvas.drawRoundRect(rect, radius, radius, basePaint)
    canvas.drawRoundRect(rect, radius, radius, highlightPaint)
    picture.endRecording()
    return picture
}

private fun Int.floorMod(divisor: Int): Int {
    val remainder = this % divisor
    return if (remainder >= 0) remainder else remainder + divisor
}
