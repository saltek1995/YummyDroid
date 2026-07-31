package me.yummydroid.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

fun Modifier.frostedGlassSurface(
    shape: Shape,
    intensity: Float = 1f,
    activeFraction: Float = 0f,
): Modifier {
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

    return this
        .background(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to top,
                    0.42f to middle,
                    1f to bottom,
                ),
            ),
            shape = shape,
        )
        .background(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.10f * resolvedIntensity),
                    glint,
                    Color.Transparent,
                ),
            ),
            shape = shape,
        )
        .border(
            border = BorderStroke(1.dp, edge),
            shape = shape,
        )
}

fun Modifier.frostedGlassFade(
    shape: Shape,
    topAlpha: Float = 0.46f,
    bottomAlpha: Float = 0.04f,
): Modifier {
    return background(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color(0xFF0B1423).copy(alpha = topAlpha.coerceIn(0f, 1f)),
                0.52f to Color(0xFF0B1423).copy(alpha = ((topAlpha + bottomAlpha) / 2f).coerceIn(0f, 1f)),
                1f to Color(0xFF0B1423).copy(alpha = bottomAlpha.coerceIn(0f, 1f)),
            ),
        ),
        shape = shape,
    )
}

fun Modifier.liquidGlassBackdrop(
    shape: Shape,
    intensity: Float = 1f,
): Modifier {
    val resolvedIntensity = intensity.coerceIn(0f, 1f)
    return this
        .background(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.14f to Color(0xFF203652).copy(alpha = 0.26f * resolvedIntensity),
                    0.28f to Color(0xFF172943).copy(alpha = 0.74f * resolvedIntensity),
                    0.48f to Color(0xFF0E1B2E).copy(alpha = 0.96f * resolvedIntensity),
                    0.70f to Color(0xFF0A1728).copy(alpha = 0.70f * resolvedIntensity),
                    1f to Color.Transparent,
                ),
            ),
            shape = shape,
        )
        .background(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color(0xFF091321).copy(alpha = 0.48f * resolvedIntensity),
                    0.52f to Color(0xFF0D1B2D).copy(alpha = 0.34f * resolvedIntensity),
                    1f to Color.Transparent,
                ),
            ),
            shape = shape,
        )
}
