package me.yummydroid.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

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
