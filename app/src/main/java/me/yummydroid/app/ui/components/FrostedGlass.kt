package me.yummydroid.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

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
        .background(
            brush = surfaceBrush,
            shape = shape,
        )
        .background(
            brush = glintBrush,
            shape = shape,
        )
        .border(
            border = edgeStroke,
            shape = shape,
        )
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
    this.background(
        brush = fadeBrush,
        shape = shape,
    )
}

fun Modifier.liquidGlassBackdrop(
    shape: Shape,
    intensity: Float = 1f,
    hazeState: HazeState? = null,
    topFadeFraction: Float = 0.24f,
    bottomFadeFraction: Float = 0f,
): Modifier = composed {
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
    val glassBlurRadius = if (startsSolid) 80.dp else 34.dp
    val glassNoiseFactor = if (startsSolid) 0.08f else 0.08f
    val topSoftStop = (resolvedTopFade * 0.45f).coerceAtMost(0.20f)
    val topSolidStop = resolvedTopFade.coerceAtLeast(0.01f)
    val bottomSolidStop = (1f - resolvedBottomFade).coerceIn(topSolidStop, 1f)
    val bottomSoftStop = (1f - resolvedBottomFade * 0.45f).coerceIn(bottomSolidStop, 1f)
    val middleStop = (topSolidStop + (bottomSolidStop - topSolidStop) * 0.56f)
        .coerceIn(topSolidStop, bottomSolidStop)
    val glassMask = remember(
        resolvedBottomFade,
        startsSolid,
        topSoftStop,
        topSolidStop,
        bottomSolidStop,
        bottomSoftStop,
    ) {
        Brush.verticalGradient(
            colorStops = if (resolvedBottomFade > 0f) {
                if (!startsSolid) {
                    arrayOf(
                        0f to Color.Transparent,
                        topSoftStop to Color.Black.copy(alpha = 0.42f),
                        topSolidStop to Color.Black,
                        bottomSolidStop to Color.Black,
                        bottomSoftStop to Color.Black.copy(alpha = 0.26f),
                        1f to Color.Transparent,
                    )
                } else {
                    arrayOf(
                        0f to Color.Black,
                        bottomSolidStop to Color.Black,
                        bottomSoftStop to Color.Black.copy(alpha = 0.26f),
                        1f to Color.Transparent,
                    )
                }
            } else {
                if (!startsSolid) {
                    arrayOf(
                        0f to Color.Transparent,
                        topSoftStop to Color.Black.copy(alpha = 0.42f),
                        topSolidStop to Color.Black,
                        1f to Color.Black,
                    )
                } else {
                    arrayOf(
                        0f to Color.Black,
                        1f to Color.Black,
                    )
                }
            }
        )
    }
    val fallbackTintColor = if (startsSolid) Color(0xF2071221) else Color(0xE6071221)
    val glassFallbackTint = remember(fallbackTintColor) { HazeTint(fallbackTintColor) }
    val hazeTints = remember(tintAlpha) {
        listOf(HazeTint(Color(0xFF071221).copy(alpha = tintAlpha)))
    }
    val glassStyle = remember(baseAlpha, hazeTints, glassBlurRadius, glassNoiseFactor, glassFallbackTint) {
        HazeStyle(
            backgroundColor = Color(0xFF071221).copy(alpha = baseAlpha),
            tints = hazeTints,
            blurRadius = glassBlurRadius,
            noiseFactor = glassNoiseFactor,
            fallbackTint = glassFallbackTint,
        )
    }
    val fallbackBrush = remember(
        resolvedBottomFade,
        startsSolid,
        topSolidStop,
        bottomSolidStop,
    ) {
        Brush.verticalGradient(
            colorStops = if (resolvedBottomFade > 0f) {
                if (!startsSolid) {
                    arrayOf(
                        0f to Color.Transparent,
                        topSolidStop to Color(0xCC071221),
                        bottomSolidStop to Color(0xCC071221),
                        1f to Color.Transparent,
                    )
                } else {
                    arrayOf(
                        0f to Color(0xCC071221),
                        bottomSolidStop to Color(0xCC071221),
                        1f to Color.Transparent,
                    )
                }
            } else {
                if (!startsSolid) {
                    arrayOf(
                        0f to Color.Transparent,
                        topSolidStop to Color(0xCC071221),
                        1f to Color(0xCC071221),
                    )
                } else {
                    arrayOf(
                        0f to Color(0xCC071221),
                        1f to Color(0xCC071221),
                    )
                }
            },
        )
    }
    val glassOverlayBrush = remember(
        resolvedIntensity,
        resolvedBottomFade,
        startsSolid,
        resolvedTopFade,
        middleStop,
        bottomSolidStop,
    ) {
        Brush.verticalGradient(
            colorStops = if (resolvedBottomFade > 0f) {
                if (!startsSolid) {
                    arrayOf(
                        0f to Color.Transparent,
                        (resolvedTopFade * 0.55f).coerceAtMost(0.24f) to Color.White.copy(
                            alpha = (0.025f * resolvedIntensity).coerceIn(0f, 0.04f),
                        ),
                        middleStop to Color(0xFF071221).copy(alpha = (0.10f * resolvedIntensity).coerceIn(0f, 0.16f)),
                        bottomSolidStop to Color(0xFF020812).copy(alpha = (0.24f * resolvedIntensity).coerceIn(0f, 0.34f)),
                        1f to Color.Transparent,
                    )
                } else {
                    arrayOf(
                        0f to Color(0xFF071221).copy(alpha = (0.58f * resolvedIntensity).coerceIn(0.46f, 0.84f)),
                        middleStop to Color(0xFF071221).copy(alpha = (0.46f * resolvedIntensity).coerceIn(0.36f, 0.72f)),
                        bottomSolidStop to Color(0xFF020812).copy(alpha = (0.34f * resolvedIntensity).coerceIn(0.26f, 0.58f)),
                        1f to Color.Transparent,
                    )
                }
            } else {
                if (!startsSolid) {
                    arrayOf(
                        0f to Color.Transparent,
                        (resolvedTopFade * 0.55f).coerceAtMost(0.24f) to Color.White.copy(
                            alpha = (0.025f * resolvedIntensity).coerceIn(0f, 0.04f),
                        ),
                        0.42f to Color(0xFF071221).copy(alpha = (0.10f * resolvedIntensity).coerceIn(0f, 0.16f)),
                        1f to Color(0xFF020812).copy(alpha = (0.28f * resolvedIntensity).coerceIn(0f, 0.38f)),
                    )
                } else {
                    arrayOf(
                        0f to Color(0xFF071221).copy(alpha = (0.58f * resolvedIntensity).coerceIn(0.46f, 0.84f)),
                        0.42f to Color(0xFF071221).copy(alpha = (0.40f * resolvedIntensity).coerceIn(0.32f, 0.66f)),
                        1f to Color(0xFF020812).copy(alpha = (0.34f * resolvedIntensity).coerceIn(0.28f, 0.58f)),
                    )
                }
            },
        )
    }
    val radialBrush = remember(resolvedIntensity) {
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0xFF5E7FA6).copy(alpha = (0.07f * resolvedIntensity).coerceIn(0f, 0.11f)),
                0.62f to Color(0xFF0D1B2D).copy(alpha = (0.07f * resolvedIntensity).coerceIn(0f, 0.11f)),
                1f to Color.Transparent,
            ),
        )
    }
    this
        .then(
            if (hazeState != null) {
                Modifier.hazeEffect(hazeState, style = glassStyle) {
                    alpha = 1f
                    blurRadius = glassBlurRadius
                    backgroundColor = Color(0xFF071221).copy(alpha = baseAlpha)
                    tints = hazeTints
                    noiseFactor = glassNoiseFactor
                    fallbackTint = glassFallbackTint
                    mask = glassMask
                }
            } else {
                Modifier.background(
                    brush = fallbackBrush,
                    shape = shape,
                )
            },
        )
        .drawWithContent {
            drawContent()
            drawRect(brush = glassOverlayBrush)
            drawRect(brush = radialBrush)
        }
}
