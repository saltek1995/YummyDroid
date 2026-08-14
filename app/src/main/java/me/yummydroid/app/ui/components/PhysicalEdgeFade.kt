package me.yummydroid.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer

internal fun Modifier.physicalEdgeContentFade(
    offsetPx: Float,
    itemWidthPx: Float,
    viewportEndPx: Float,
    fadeWidthPx: Float,
    fadeBeforeLeftEdge: Boolean = true,
    fadeBeforeRightEdge: Boolean = true,
): Modifier {
    val fade = resolvePhysicalEdgeFade(
        offsetPx = offsetPx,
        itemWidthPx = itemWidthPx,
        viewportEndPx = viewportEndPx,
        fadeWidthPx = fadeWidthPx,
        fadeBeforeLeftEdge = fadeBeforeLeftEdge,
        fadeBeforeRightEdge = fadeBeforeRightEdge,
    )
    if (!fade.hasVisibleFade) return this
    return graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithContent {
        drawContent()
        drawPhysicalEdgeFade(fade)
    }
}

private data class PhysicalEdgeFade(
    val leftHiddenPx: Float,
    val rightHiddenPx: Float,
    val leftFraction: Float,
    val rightFraction: Float,
    val fadeWidthPx: Float,
) {
    val hasVisibleFade: Boolean = leftFraction > 0.001f || rightFraction > 0.001f
}

private fun resolvePhysicalEdgeFade(
    offsetPx: Float,
    itemWidthPx: Float,
    viewportEndPx: Float,
    fadeWidthPx: Float,
    fadeBeforeLeftEdge: Boolean,
    fadeBeforeRightEdge: Boolean,
): PhysicalEdgeFade {
    val leftHiddenPx = (-offsetPx).coerceIn(0f, itemWidthPx)
    val rightHiddenPx = (offsetPx + itemWidthPx - viewportEndPx).coerceIn(0f, itemWidthPx)
    return PhysicalEdgeFade(
        leftHiddenPx = leftHiddenPx,
        rightHiddenPx = rightHiddenPx,
        leftFraction = physicalEdgeFadeFraction(
            fadeBeforeEdge = fadeBeforeLeftEdge,
            distanceToEdgePx = offsetPx,
            hiddenPx = leftHiddenPx,
            fadeWidthPx = fadeWidthPx,
        ),
        rightFraction = physicalEdgeFadeFraction(
            fadeBeforeEdge = fadeBeforeRightEdge,
            distanceToEdgePx = viewportEndPx - (offsetPx + itemWidthPx),
            hiddenPx = rightHiddenPx,
            fadeWidthPx = fadeWidthPx,
        ),
        fadeWidthPx = fadeWidthPx,
    )
}

private fun physicalEdgeFadeFraction(
    fadeBeforeEdge: Boolean,
    distanceToEdgePx: Float,
    hiddenPx: Float,
    fadeWidthPx: Float,
): Float {
    if (fadeBeforeEdge) {
        return edgeFadeProgress(
            distanceToEdgePx = distanceToEdgePx,
            fadeWidthPx = fadeWidthPx,
        )
    }
    return (hiddenPx / fadeWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
}

private fun DrawScope.drawPhysicalEdgeFade(fade: PhysicalEdgeFade) {
    val width = size.width
    val resolvedFadeWidth = fade.fadeWidthPx.coerceIn(1f, width)
    val leftHidden = fade.leftHiddenPx.coerceIn(0f, width)
    if (leftHidden > 0f) drawHiddenLeftEdge(leftHidden)
    if (leftHidden < width && fade.leftFraction > 0.001f) {
        drawLeftEdgeFade(
            leftHidden = leftHidden,
            fadeWidth = resolvedFadeWidth,
            visibilityFraction = fade.leftFraction,
        )
    }
    val rightVisibleEnd = (width - fade.rightHiddenPx).coerceIn(0f, width)
    if (fade.rightFraction > 0.001f && rightVisibleEnd > 0f) {
        drawRightEdgeFade(
            rightVisibleEnd = rightVisibleEnd,
            fadeWidth = resolvedFadeWidth,
            visibilityFraction = fade.rightFraction,
        )
    }
}

private fun DrawScope.drawHiddenLeftEdge(leftHidden: Float) {
    drawRect(
        color = Color.Transparent,
        topLeft = Offset.Zero,
        size = Size(leftHidden, size.height),
        blendMode = BlendMode.DstIn,
    )
}

private fun DrawScope.drawLeftEdgeFade(
    leftHidden: Float,
    fadeWidth: Float,
    visibilityFraction: Float,
) {
    val fadeEnd = (leftHidden + fadeWidth).coerceAtMost(size.width)
    drawRect(
        brush = Brush.horizontalGradient(
            colorStops = edgeFadeColorStops(
                visibilityFraction = visibilityFraction,
                fadeFromStart = true,
            ),
            startX = leftHidden,
            endX = fadeEnd,
        ),
        topLeft = Offset(leftHidden, 0f),
        size = Size(fadeEnd - leftHidden, size.height),
        blendMode = BlendMode.DstIn,
    )
}

private fun DrawScope.drawRightEdgeFade(
    rightVisibleEnd: Float,
    fadeWidth: Float,
    visibilityFraction: Float,
) {
    val fadeStart = (rightVisibleEnd - fadeWidth).coerceAtLeast(0f)
    drawRect(
        brush = Brush.horizontalGradient(
            colorStops = edgeFadeColorStops(
                visibilityFraction = visibilityFraction,
                fadeFromStart = false,
            ),
            startX = fadeStart,
            endX = rightVisibleEnd,
        ),
        topLeft = Offset(fadeStart, 0f),
        size = Size(rightVisibleEnd - fadeStart, size.height),
        blendMode = BlendMode.DstIn,
    )
}
