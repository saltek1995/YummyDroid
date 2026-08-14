package me.yummydroid.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun Modifier.horizontalScrollEdgeContentFade(
    visibility: HorizontalScrollEdgeVisibility,
    edgeWidth: Dp = HorizontalScrollEdgeDefaultOverlayWidth,
    backwardEdgeInset: Dp = 0.dp,
): Modifier = composed {
    val density = LocalDensity.current
    val edgeWidthPx = remember(density, edgeWidth) { with(density) { edgeWidth.toPx() } }
    val backwardEdgeInsetPx = remember(density, backwardEdgeInset) {
        with(density) { backwardEdgeInset.toPx() }
    }
    val backwardProgress by animateFloatAsState(
        targetValue = visibility.backwardFraction.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = HorizontalScrollEdgeAnimationDurationMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "horizontal-scroll-content-backward-edge",
    )
    val forwardProgress by animateFloatAsState(
        targetValue = visibility.forwardFraction.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = HorizontalScrollEdgeAnimationDurationMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "horizontal-scroll-content-forward-edge",
    )
    graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithContent {
        drawContent()
        val resolvedEdgeWidth = edgeWidthPx.coerceIn(0f, size.width)
        if (resolvedEdgeWidth <= 0f) return@drawWithContent
        if (backwardProgress > 0.001f) {
            val resolvedBackwardInset = backwardEdgeInsetPx.coerceIn(0f, size.width)
            if (resolvedBackwardInset > 0f) {
                drawRect(
                    color = Color.Transparent,
                    topLeft = Offset.Zero,
                    size = Size(resolvedBackwardInset, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
            val resolvedBackwardEdgeWidth = resolvedEdgeWidth.coerceAtMost(size.width - resolvedBackwardInset)
            if (resolvedBackwardEdgeWidth > 0f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = edgeFadeColorStops(
                            visibilityFraction = backwardProgress,
                            fadeFromStart = true,
                        ),
                        startX = resolvedBackwardInset,
                        endX = resolvedBackwardInset + resolvedBackwardEdgeWidth,
                    ),
                    topLeft = Offset(resolvedBackwardInset, 0f),
                    size = Size(resolvedBackwardEdgeWidth, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
        if (forwardProgress > 0.001f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = edgeFadeColorStops(
                        visibilityFraction = forwardProgress,
                        fadeFromStart = false,
                    ),
                    startX = size.width - resolvedEdgeWidth,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - resolvedEdgeWidth, 0f),
                size = Size(resolvedEdgeWidth, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }
}

internal fun edgeFadeMaskAlpha(
    baseAlpha: Float,
    visibilityFraction: Float,
): Float {
    val progress = visibilityFraction.coerceIn(0f, 1f)
    val resolvedBase = baseAlpha.coerceIn(0f, 1f)
    return 1f - progress * (1f - resolvedBase)
}

internal fun edgeFadeProgress(
    distanceToEdgePx: Float,
    fadeWidthPx: Float,
): Float {
    val width = fadeWidthPx.coerceAtLeast(1f)
    val linearProgress = ((width - distanceToEdgePx) / width).coerceIn(0f, 1f)
    return smoothEdgeFadeProgress(linearProgress)
}

internal fun smoothEdgeFadeProgress(progress: Float): Float {
    val boundedProgress = progress.coerceIn(0f, 1f)
    return boundedProgress * boundedProgress * (3f - 2f * boundedProgress)
}

internal fun edgeFadeColorStops(
    visibilityFraction: Float,
    fadeFromStart: Boolean,
): Array<Pair<Float, Color>> {
    val stops = arrayOf(
        0f to Color.White.copy(alpha = edgeFadeMaskAlpha(0f, visibilityFraction)),
        0.18f to Color.White.copy(alpha = edgeFadeMaskAlpha(0.18f, visibilityFraction)),
        0.42f to Color.White.copy(alpha = edgeFadeMaskAlpha(0.46f, visibilityFraction)),
        0.70f to Color.White.copy(alpha = edgeFadeMaskAlpha(0.78f, visibilityFraction)),
        1f to Color.White,
    )
    if (fadeFromStart) return stops
    return stops
        .map { stop -> (1f - stop.first) to stop.second }
        .asReversed()
        .toTypedArray()
}

internal val HorizontalScrollEdgeDefaultOverlayWidth = 128.dp
private const val HorizontalScrollEdgeAnimationDurationMillis = 220
