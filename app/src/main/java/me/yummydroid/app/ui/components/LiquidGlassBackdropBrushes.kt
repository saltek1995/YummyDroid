package me.yummydroid.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

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
        Brush.verticalGradient(
            colorStops = when {
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
            },
        )
    }
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
