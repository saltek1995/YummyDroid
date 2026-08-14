package me.yummydroid.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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

// HorizontalScrollEdgeFrame
@Composable
internal fun HorizontalScrollEdgeFrame(
    state: LazyListState,
    modifier: Modifier = Modifier,
    edgeWidth: Dp = HorizontalScrollEdgeDefaultOverlayWidth,
    content: @Composable BoxScope.() -> Unit,
) {
    val edgeVisibility = rememberHorizontalScrollEdgeVisibility(state, edgeWidth)
    Box(
        modifier = modifier
            .horizontalScrollEdgeContentFade(
                visibility = edgeVisibility,
                edgeWidth = edgeWidth,
            ),
        content = content,
    )
}

@Composable
internal fun rememberHorizontalScrollEdgeVisibility(
    state: LazyListState,
    edgeWidth: Dp = HorizontalScrollEdgeDefaultOverlayWidth,
    backwardEdgeInset: Dp = 0.dp,
): HorizontalScrollEdgeVisibility {
    val density = LocalDensity.current
    val edgeWidthPx = remember(density, edgeWidth) { with(density) { edgeWidth.toPx() } }
    val backwardEdgeInsetPx = remember(density, backwardEdgeInset) {
        with(density) { backwardEdgeInset.toPx() }
    }
    val edgeVisibility by remember(state, edgeWidthPx, backwardEdgeInsetPx) {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            resolveHorizontalScrollEdgeVisibility(
                canScrollBackward = state.canScrollBackward,
                canScrollForward = state.canScrollForward,
                totalItemsCount = layoutInfo.totalItemsCount,
                firstVisibleIndex = visibleItems.firstOrNull()?.index,
                firstVisibleOffset = visibleItems.firstOrNull()?.offset,
                lastVisibleIndex = visibleItems.lastOrNull()?.index,
                lastVisibleEndOffset = visibleItems.lastOrNull()?.let { item ->
                    item.offset + item.size
                },
                viewportEndOffset = layoutInfo.viewportSize.width,
                edgeWidthPx = edgeWidthPx,
                backwardEdgeInsetPx = backwardEdgeInsetPx,
            )
        }
    }
    return edgeVisibility
}

internal data class HorizontalScrollEdgeVisibility(
    val backward: Boolean,
    val forward: Boolean,
    val backwardFraction: Float = if (backward) 1f else 0f,
    val forwardFraction: Float = if (forward) 1f else 0f,
)

internal fun resolveHorizontalScrollEdgeVisibility(
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    totalItemsCount: Int,
    firstVisibleIndex: Int?,
    firstVisibleOffset: Int?,
    lastVisibleIndex: Int?,
    lastVisibleEndOffset: Int?,
    viewportEndOffset: Int,
    edgeWidthPx: Float = 0f,
    backwardEdgeInsetPx: Float = 0f,
): HorizontalScrollEdgeVisibility {
    val resolvedFirstOffset = firstVisibleOffset
    val resolvedLastEndOffset = lastVisibleEndOffset
    val hasVisibleItems = totalItemsCount > 0 &&
        firstVisibleIndex != null &&
        resolvedFirstOffset != null &&
        lastVisibleIndex != null &&
        resolvedLastEndOffset != null
    if (!hasVisibleItems) return HorizontalScrollEdgeVisibility(backward = false, forward = false)

    val resolvedEdgeWidth = edgeWidthPx.coerceAtLeast(1f)
    val backwardFraction = if (canScrollBackward) {
        edgeFadeProgress(
            distanceToEdgePx = resolvedFirstOffset.toFloat() - backwardEdgeInsetPx.coerceAtLeast(0f),
            fadeWidthPx = resolvedEdgeWidth,
        )
    } else {
        0f
    }
    val forwardFraction = if (canScrollForward) {
        edgeFadeProgress(
            distanceToEdgePx = viewportEndOffset - resolvedLastEndOffset.toFloat(),
            fadeWidthPx = resolvedEdgeWidth,
        )
    } else {
        0f
    }
    return HorizontalScrollEdgeVisibility(
        backward = backwardFraction > 0.001f,
        forward = forwardFraction > 0.001f,
        backwardFraction = backwardFraction,
        forwardFraction = forwardFraction,
    )
}

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

internal fun Modifier.physicalEdgeContentFade(
    offsetPx: Float,
    itemWidthPx: Float,
    viewportEndPx: Float,
    fadeWidthPx: Float,
    fadeBeforeLeftEdge: Boolean = true,
    fadeBeforeRightEdge: Boolean = true,
): Modifier {
    val leftHiddenPx = (-offsetPx).coerceIn(0f, itemWidthPx)
    val rightHiddenPx = (offsetPx + itemWidthPx - viewportEndPx).coerceIn(0f, itemWidthPx)
    val leftFadeFraction = if (fadeBeforeLeftEdge) {
        edgeFadeProgress(
            distanceToEdgePx = offsetPx,
            fadeWidthPx = fadeWidthPx,
        )
    } else {
        (leftHiddenPx / fadeWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    }
    val rightFadeFraction = if (fadeBeforeRightEdge) {
        edgeFadeProgress(
            distanceToEdgePx = viewportEndPx - (offsetPx + itemWidthPx),
            fadeWidthPx = fadeWidthPx,
        )
    } else {
        (rightHiddenPx / fadeWidthPx.coerceAtLeast(1f)).coerceIn(0f, 1f)
    }
    if (leftFadeFraction <= 0.001f && rightFadeFraction <= 0.001f) return this
    return graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }.drawWithContent {
        drawContent()
        val width = size.width
        val resolvedFadeWidth = fadeWidthPx.coerceIn(1f, width)
        val leftHidden = leftHiddenPx.coerceIn(0f, width)
        if (leftHidden > 0f) {
            drawRect(
                color = Color.Transparent,
                topLeft = Offset.Zero,
                size = Size(leftHidden, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (leftHidden < width && leftFadeFraction > 0.001f) {
            val fadeEnd = (leftHidden + resolvedFadeWidth).coerceAtMost(width)
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = edgeFadeColorStops(
                        visibilityFraction = leftFadeFraction,
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
        val rightVisibleEnd = (width - rightHiddenPx).coerceIn(0f, width)
        if (rightFadeFraction > 0.001f && rightVisibleEnd > 0f) {
            val fadeStart = (rightVisibleEnd - resolvedFadeWidth).coerceAtLeast(0f)
            drawRect(
                brush = Brush.horizontalGradient(
                    colorStops = edgeFadeColorStops(
                        visibilityFraction = rightFadeFraction,
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
    }
}

internal val HorizontalScrollEdgeContentPadding = 36.dp
private val HorizontalScrollEdgeDefaultOverlayWidth = 128.dp
private const val HorizontalScrollEdgeAnimationDurationMillis = 220
