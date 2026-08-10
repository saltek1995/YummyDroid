package me.yummydroid.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import dev.chrisbanes.haze.HazeState
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
