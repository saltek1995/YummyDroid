package me.yummydroid.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

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
