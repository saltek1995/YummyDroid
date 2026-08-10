package me.yummydroid.app.ui.components

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Picture
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt

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
