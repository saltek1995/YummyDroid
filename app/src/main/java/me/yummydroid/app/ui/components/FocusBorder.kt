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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import me.yummydroid.app.ui.LocalUiControlCoordinator
import me.yummydroid.app.ui.UiControlOperation
import me.yummydroid.app.ui.theme.YummyColors

// AnimatedFocusBorderModifier
private const val FocusBorderFadeDurationMillis = 80
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
    val visible = active && LocalInputModeManager.current.inputMode != InputMode.Touch
    if (!visible) return@composed this

    val borderProgress = remember { Animatable(0f) }
    var borderFrameIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(visible) {
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
    }

    drawAnimatedFocusBorder(
        cornerRadius = cornerRadius,
        borderWidth = borderWidth,
        highlightWidth = highlightWidth,
        alpha = { borderProgress.value },
        frameIndex = { borderFrameIndex },
    )
}

// FocusBorderDrawing
internal fun Modifier.drawAnimatedFocusBorder(
    cornerRadius: Dp,
    borderWidth: Dp,
    highlightWidth: Dp,
    alpha: () -> Float,
    frameIndex: () -> Int,
): Modifier = drawWithCache {
    val strokeWidth = borderWidth.toPx()
    val highlightStrokeWidth = highlightWidth.toPx()
    val geometry = focusBorderGeometry(
        widthPx = size.width,
        heightPx = size.height,
        strokeWidthPx = strokeWidth,
        highlightStrokeWidthPx = highlightStrokeWidth,
        cornerRadiusPx = cornerRadius.toPx(),
    )
    var frames: FocusBorderFrameSequence? = null

    fun cachedFrames(): FocusBorderFrameSequence {
        frames?.let { return it }
        return cachedFocusBorderFrames(
            frameKey = geometry.frameKey,
            inset = geometry.inset,
            radius = geometry.radius,
            strokeWidth = strokeWidth,
            highlightStrokeWidth = highlightStrokeWidth,
        ).also { cached -> frames = cached }
    }

    onDrawWithContent {
        drawContent()
        val boundedAlpha = alpha().coerceIn(0f, 1f)
        if (boundedAlpha <= 0.001f || !geometry.hasArea) return@onDrawWithContent

        val nativeCanvas = drawContext.canvas.nativeCanvas
        val frame = cachedFrames().frame(frameIndex())
        if (boundedAlpha >= 0.999f) {
            nativeCanvas.drawPicture(frame)
        } else {
            val checkpoint = nativeCanvas.saveLayerAlpha(
                0f,
                0f,
                geometry.frameKey.widthPx.toFloat(),
                geometry.frameKey.heightPx.toFloat(),
                focusBorderLayerAlpha(boundedAlpha),
            )
            nativeCanvas.drawPicture(frame)
            nativeCanvas.restoreToCount(checkpoint)
        }
    }
}

// FocusBorderFrames
internal const val FocusBorderFrameCount = 84

private val FocusBorderPurple = Color(0xFF5A2A78)
private val FocusBorderDeepPurple = Color(0xFF26113A)
private val FocusBorderOrange = Color(0xFFFFB454)
private val FocusBorderFrameCache = LruCache<FocusBorderFrameKey, FocusBorderFrameSequence>(24)

internal data class FocusBorderFrameKey(
    val widthPx: Int,
    val heightPx: Int,
    val strokeWidthPx: Int,
    val highlightStrokeWidthPx: Int,
    val cornerRadiusPx: Int,
)

internal fun cachedFocusBorderFrames(
    frameKey: FocusBorderFrameKey,
    inset: Float,
    radius: Float,
    strokeWidth: Float,
    highlightStrokeWidth: Float,
): FocusBorderFrameSequence {
    synchronized(FocusBorderFrameCache) {
        FocusBorderFrameCache.get(frameKey)?.let { return it }
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
    return created
}

internal class FocusBorderFrameSequence(
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
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = FocusBorderPurple.toArgb()
        alpha = (255f * 0.82f).roundToInt().coerceIn(0, 255)
    }
    val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.strokeWidth = highlightStrokeWidth
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        alpha = (255f * 0.95f).roundToInt().coerceIn(0, 255)
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
    highlightPaint.shader = sweepShader

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

// FocusBorderGeometry
internal data class FocusBorderGeometry(
    val inset: Float,
    val right: Float,
    val bottom: Float,
    val radius: Float,
    val frameKey: FocusBorderFrameKey,
) {
    val hasArea: Boolean
        get() = right > inset && bottom > inset
}

internal fun focusBorderGeometry(
    widthPx: Float,
    heightPx: Float,
    strokeWidthPx: Float,
    highlightStrokeWidthPx: Float,
    cornerRadiusPx: Float,
): FocusBorderGeometry {
    val inset = maxOf(strokeWidthPx, highlightStrokeWidthPx) / 2f
    val right = (widthPx - inset).coerceAtLeast(inset)
    val bottom = (heightPx - inset).coerceAtLeast(inset)
    val radius = cornerRadiusPx.coerceAtMost(minOf(right - inset, bottom - inset) / 2f)
    return FocusBorderGeometry(
        inset = inset,
        right = right,
        bottom = bottom,
        radius = radius,
        frameKey = FocusBorderFrameKey(
            widthPx = widthPx.roundToInt().coerceAtLeast(1),
            heightPx = heightPx.roundToInt().coerceAtLeast(1),
            strokeWidthPx = strokeWidthPx.roundToInt().coerceAtLeast(1),
            highlightStrokeWidthPx = highlightStrokeWidthPx.roundToInt().coerceAtLeast(1),
            cornerRadiusPx = radius.roundToInt().coerceAtLeast(0),
        ),
    )
}

internal fun focusBorderLayerAlpha(alpha: Float): Int =
    (255f * alpha.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)

// FocusRingModifier
private const val FOCUS_RING_FADE_MS = 90
private val FocusFillColor = YummyColors.focusOverlay

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.focusRing(shape: Shape): Modifier = composed {
    val inputModeManager = LocalInputModeManager.current
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val uiControls = LocalUiControlCoordinator.current
    val controlOwner = remember { Any() }
    var focused by remember { mutableStateOf(false) }
    val focusVisible = focused && inputModeManager.inputMode != InputMode.Touch
    val focusProgress = remember { Animatable(0f) }

    LaunchedEffect(focusVisible) {
        focusProgress.animateTo(
            targetValue = if (focusVisible) 1f else 0f,
            animationSpec = tween(
                durationMillis = FOCUS_RING_FADE_MS,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    clearFocusAfterTouch()
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusChanged { focusState ->
            focused = focusState.isFocused
            if (focusState.isFocused && inputModeManager.inputMode != InputMode.Touch) {
                uiControls.launch(scope, controlOwner, UiControlOperation.RelocationLatest) {
                    withFrameNanos { }
                    bringIntoViewRequester.bringIntoView()
                }
            } else {
                uiControls.cancel(controlOwner, UiControlOperation.RelocationLatest)
            }
        }
        .clip(shape)
        .drawWithContent {
            drawContent()
            val progress = focusProgress.value
            if (progress > 0f) {
                drawRect(FocusFillColor.copy(alpha = 0.22f * progress))
            }
        }
}
