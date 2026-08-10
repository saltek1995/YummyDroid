package me.yummydroid.app.ui.components

import kotlin.math.roundToInt

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
