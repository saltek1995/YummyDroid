package me.yummydroid.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp

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
