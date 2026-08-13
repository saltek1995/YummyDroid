package me.yummydroid.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

// FrostedGlassSurface
fun Modifier.frostedGlassSurface(
    shape: Shape,
    intensity: Float = 1f,
    activeFraction: Float = 0f,
): Modifier = composed {
    val resolvedIntensity = intensity.coerceIn(0f, 1f)
    val resolvedActive = activeFraction.coerceIn(0f, 1f)
    val top = lerp(Color(0xFF24364F), Color(0xFF3D4F68), resolvedActive)
        .copy(alpha = 0.62f * resolvedIntensity)
    val middle = lerp(Color(0xFF0E1A2B), Color(0xFF1A2A42), resolvedActive)
        .copy(alpha = 0.46f * resolvedIntensity)
    val bottom = lerp(Color(0xFF06101C), Color(0xFF101C2C), resolvedActive)
        .copy(alpha = 0.72f * resolvedIntensity)
    val glint = lerp(Color(0xFF62E8FF), Color(0xFFFFB454), resolvedActive)
        .copy(alpha = 0.16f * resolvedIntensity)
    val edge = lerp(Color(0xFF7EA7FF), Color(0xFFFFB454), resolvedActive)
        .copy(alpha = 0.26f + 0.24f * resolvedActive)
    val surfaceBrush = remember(top, middle, bottom) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to top,
                0.42f to middle,
                1f to bottom,
            ),
        )
    }
    val glintBrush = remember(resolvedIntensity, glint) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.10f * resolvedIntensity),
                glint,
                Color.Transparent,
            ),
        )
    }
    val edgeStroke = remember(edge) {
        BorderStroke(1.dp, edge)
    }

    this
        .background(brush = surfaceBrush, shape = shape)
        .background(brush = glintBrush, shape = shape)
        .border(border = edgeStroke, shape = shape)
}

fun Modifier.frostedGlassFade(
    shape: Shape,
    topAlpha: Float = 0.46f,
    bottomAlpha: Float = 0.04f,
): Modifier = composed {
    val fadeBrush = remember(topAlpha, bottomAlpha) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color(0xFF0B1423).copy(alpha = topAlpha.coerceIn(0f, 1f)),
                0.52f to Color(0xFF0B1423).copy(alpha = ((topAlpha + bottomAlpha) / 2f).coerceIn(0f, 1f)),
                1f to Color(0xFF0B1423).copy(alpha = bottomAlpha.coerceIn(0f, 1f)),
            ),
        )
    }
    this.background(brush = fadeBrush, shape = shape)
}

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

// LiquidGlassBackdrop
fun Modifier.liquidGlassBackdrop(
    shape: Shape,
    intensity: Float = 1f,
    hazeState: HazeState? = null,
    topFadeFraction: Float = 0.24f,
    bottomFadeFraction: Float = 0f,
): Modifier = composed {
    val parameters = resolveLiquidGlassBackdropParameters(
        intensity = intensity,
        topFadeFraction = topFadeFraction,
        bottomFadeFraction = bottomFadeFraction,
    )
    val glassMask = rememberLiquidGlassMask(parameters)
    val fallbackBrush = rememberLiquidGlassFallbackBrush(parameters)
    val overlayBrush = rememberLiquidGlassOverlayBrush(parameters)
    val radialBrush = rememberLiquidGlassRadialBrush(parameters.intensity)
    val hazeRendering = rememberLiquidGlassHazeRendering(parameters)

    this
        .then(
            if (hazeState == null) {
                Modifier.background(brush = fallbackBrush, shape = shape)
            } else {
                Modifier.hazeEffect(hazeState, style = hazeRendering.style) {
                    alpha = 1f
                    blurRadius = hazeRendering.blurRadius
                    backgroundColor = hazeRendering.backgroundColor
                    tints = hazeRendering.tints
                    noiseFactor = hazeRendering.noiseFactor
                    fallbackTint = hazeRendering.fallbackTint
                    mask = glassMask
                }
            },
        )
        .drawWithContent {
            drawContent()
            drawRect(brush = overlayBrush)
            drawRect(brush = radialBrush)
        }
}

// LiquidGlassBackdropBrushes
@Composable
internal fun rememberLiquidGlassMask(parameters: LiquidGlassBackdropParameters): Brush {
    return remember(
        parameters.bottomFadeFraction,
        parameters.startsSolid,
        parameters.topSoftStop,
        parameters.topSolidStop,
        parameters.bottomSolidStop,
        parameters.bottomSoftStop,
    ) {
        Brush.verticalGradient(
            colorStops = when {
                parameters.bottomFadeFraction > 0f && !parameters.startsSolid -> arrayOf(
                    0f to Color.Transparent,
                    parameters.topSoftStop to Color.Black.copy(alpha = 0.42f),
                    parameters.topSolidStop to Color.Black,
                    parameters.bottomSolidStop to Color.Black,
                    parameters.bottomSoftStop to Color.Black.copy(alpha = 0.26f),
                    1f to Color.Transparent,
                )
                parameters.bottomFadeFraction > 0f -> arrayOf(
                    0f to Color.Black,
                    parameters.bottomSolidStop to Color.Black,
                    parameters.bottomSoftStop to Color.Black.copy(alpha = 0.26f),
                    1f to Color.Transparent,
                )
                !parameters.startsSolid -> arrayOf(
                    0f to Color.Transparent,
                    parameters.topSoftStop to Color.Black.copy(alpha = 0.42f),
                    parameters.topSolidStop to Color.Black,
                    1f to Color.Black,
                )
                else -> arrayOf(
                    0f to Color.Black,
                    1f to Color.Black,
                )
            },
        )
    }
}

@Composable
internal fun rememberLiquidGlassFallbackBrush(parameters: LiquidGlassBackdropParameters): Brush {
    return remember(
        parameters.bottomFadeFraction,
        parameters.startsSolid,
        parameters.topSolidStop,
        parameters.bottomSolidStop,
    ) {
        Brush.verticalGradient(
            colorStops = when {
                parameters.bottomFadeFraction > 0f && !parameters.startsSolid -> arrayOf(
                    0f to Color.Transparent,
                    parameters.topSolidStop to Color(0xCC071221),
                    parameters.bottomSolidStop to Color(0xCC071221),
                    1f to Color.Transparent,
                )
                parameters.bottomFadeFraction > 0f -> arrayOf(
                    0f to Color(0xCC071221),
                    parameters.bottomSolidStop to Color(0xCC071221),
                    1f to Color.Transparent,
                )
                !parameters.startsSolid -> arrayOf(
                    0f to Color.Transparent,
                    parameters.topSolidStop to Color(0xCC071221),
                    1f to Color(0xCC071221),
                )
                else -> arrayOf(
                    0f to Color(0xCC071221),
                    1f to Color(0xCC071221),
                )
            },
        )
    }
}

@Composable
internal fun rememberLiquidGlassOverlayBrush(parameters: LiquidGlassBackdropParameters): Brush {
    return remember(
        parameters.intensity,
        parameters.bottomFadeFraction,
        parameters.startsSolid,
        parameters.topFadeFraction,
        parameters.middleStop,
        parameters.bottomSolidStop,
    ) {
        Brush.verticalGradient(colorStops = liquidGlassOverlayColorStops(parameters))
    }
}

private fun liquidGlassOverlayColorStops(
    parameters: LiquidGlassBackdropParameters,
): Array<Pair<Float, Color>> = when {
    parameters.bottomFadeFraction > 0f && !parameters.startsSolid -> arrayOf(
        0f to Color.Transparent,
        (parameters.topFadeFraction * 0.55f).coerceAtMost(0.24f) to Color.White.copy(
            alpha = (0.025f * parameters.intensity).coerceIn(0f, 0.04f),
        ),
        parameters.middleStop to Color(0xFF071221).copy(
            alpha = (0.10f * parameters.intensity).coerceIn(0f, 0.16f),
        ),
        parameters.bottomSolidStop to Color(0xFF020812).copy(
            alpha = (0.24f * parameters.intensity).coerceIn(0f, 0.34f),
        ),
        1f to Color.Transparent,
    )
    parameters.bottomFadeFraction > 0f -> arrayOf(
        0f to Color(0xFF071221).copy(
            alpha = (0.58f * parameters.intensity).coerceIn(0.46f, 0.84f),
        ),
        parameters.middleStop to Color(0xFF071221).copy(
            alpha = (0.46f * parameters.intensity).coerceIn(0.36f, 0.72f),
        ),
        parameters.bottomSolidStop to Color(0xFF020812).copy(
            alpha = (0.34f * parameters.intensity).coerceIn(0.26f, 0.58f),
        ),
        1f to Color.Transparent,
    )
    !parameters.startsSolid -> arrayOf(
        0f to Color.Transparent,
        (parameters.topFadeFraction * 0.55f).coerceAtMost(0.24f) to Color.White.copy(
            alpha = (0.025f * parameters.intensity).coerceIn(0f, 0.04f),
        ),
        0.42f to Color(0xFF071221).copy(
            alpha = (0.10f * parameters.intensity).coerceIn(0f, 0.16f),
        ),
        1f to Color(0xFF020812).copy(
            alpha = (0.28f * parameters.intensity).coerceIn(0f, 0.38f),
        ),
    )
    else -> arrayOf(
        0f to Color(0xFF071221).copy(
            alpha = (0.58f * parameters.intensity).coerceIn(0.46f, 0.84f),
        ),
        0.42f to Color(0xFF071221).copy(
            alpha = (0.40f * parameters.intensity).coerceIn(0.32f, 0.66f),
        ),
        1f to Color(0xFF020812).copy(
            alpha = (0.34f * parameters.intensity).coerceIn(0.28f, 0.58f),
        ),
    )
}

@Composable
internal fun rememberLiquidGlassRadialBrush(intensity: Float): Brush {
    return remember(intensity) {
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0xFF5E7FA6).copy(alpha = (0.07f * intensity).coerceIn(0f, 0.11f)),
                0.62f to Color(0xFF0D1B2D).copy(alpha = (0.07f * intensity).coerceIn(0f, 0.11f)),
                1f to Color.Transparent,
            ),
        )
    }
}

// LiquidGlassBackdropParameters
internal data class LiquidGlassBackdropParameters(
    val intensity: Float,
    val topFadeFraction: Float,
    val bottomFadeFraction: Float,
    val startsSolid: Boolean,
    val baseAlpha: Float,
    val tintAlpha: Float,
    val blurRadiusDp: Float,
    val topSoftStop: Float,
    val topSolidStop: Float,
    val bottomSolidStop: Float,
    val bottomSoftStop: Float,
    val middleStop: Float,
)

internal fun resolveLiquidGlassBackdropParameters(
    intensity: Float,
    topFadeFraction: Float,
    bottomFadeFraction: Float,
): LiquidGlassBackdropParameters {
    val resolvedIntensity = intensity.coerceIn(0f, 1.85f)
    val resolvedTopFade = topFadeFraction.coerceIn(0f, 0.60f)
    val resolvedBottomFade = bottomFadeFraction.coerceIn(0f, 0.75f)
    val startsSolid = resolvedTopFade <= 0f
    val baseAlpha = if (startsSolid) {
        (0.22f * resolvedIntensity).coerceIn(0.16f, 0.42f)
    } else {
        (0.14f * resolvedIntensity).coerceIn(0.06f, 0.24f)
    }
    val tintAlpha = if (startsSolid) {
        (0.78f * resolvedIntensity).coerceIn(0.58f, 0.96f)
    } else {
        (0.58f * resolvedIntensity).coerceIn(0.36f, 0.72f)
    }
    val topSoftStop = (resolvedTopFade * 0.45f).coerceAtMost(0.20f)
    val topSolidStop = resolvedTopFade.coerceAtLeast(0.01f)
    val bottomSolidStop = (1f - resolvedBottomFade).coerceIn(topSolidStop, 1f)
    val bottomSoftStop = (1f - resolvedBottomFade * 0.45f).coerceIn(bottomSolidStop, 1f)
    val middleStop = (topSolidStop + (bottomSolidStop - topSolidStop) * 0.56f)
        .coerceIn(topSolidStop, bottomSolidStop)
    return LiquidGlassBackdropParameters(
        intensity = resolvedIntensity,
        topFadeFraction = resolvedTopFade,
        bottomFadeFraction = resolvedBottomFade,
        startsSolid = startsSolid,
        baseAlpha = baseAlpha,
        tintAlpha = tintAlpha,
        blurRadiusDp = if (startsSolid) 80f else 34f,
        topSoftStop = topSoftStop,
        topSolidStop = topSolidStop,
        bottomSolidStop = bottomSolidStop,
        bottomSoftStop = bottomSoftStop,
        middleStop = middleStop,
    )
}

// LiquidGlassBackdropStyle
internal data class LiquidGlassHazeRendering(
    val style: HazeStyle,
    val backgroundColor: Color,
    val tints: List<HazeTint>,
    val blurRadius: Dp,
    val noiseFactor: Float,
    val fallbackTint: HazeTint,
)

@Composable
internal fun rememberLiquidGlassHazeRendering(
    parameters: LiquidGlassBackdropParameters,
): LiquidGlassHazeRendering {
    val backgroundColor = Color(0xFF071221).copy(alpha = parameters.baseAlpha)
    val blurRadius = parameters.blurRadiusDp.dp
    val noiseFactor = 0.08f
    val fallbackTintColor = if (parameters.startsSolid) Color(0xF2071221) else Color(0xE6071221)
    val fallbackTint = remember(fallbackTintColor) { HazeTint(fallbackTintColor) }
    val tints = remember(parameters.tintAlpha) {
        listOf(HazeTint(Color(0xFF071221).copy(alpha = parameters.tintAlpha)))
    }
    val style = remember(backgroundColor, tints, blurRadius, noiseFactor, fallbackTint) {
        HazeStyle(
            backgroundColor = backgroundColor,
            tints = tints,
            blurRadius = blurRadius,
            noiseFactor = noiseFactor,
            fallbackTint = fallbackTint,
        )
    }
    return LiquidGlassHazeRendering(
        style = style,
        backgroundColor = backgroundColor,
        tints = tints,
        blurRadius = blurRadius,
        noiseFactor = noiseFactor,
        fallbackTint = fallbackTint,
    )
}
