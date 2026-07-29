package me.yummydroid.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

internal const val YUMMY_FADE_IN_MS = 260
internal const val YUMMY_FADE_OUT_MS = 220

@Composable
internal fun Modifier.yummyAppearMotion(
    visible: Boolean = true,
    scaleFrom: Float = 0.99f,
): Modifier {
    val progress = remember(visible) { Animatable(if (visible) 0f else 1f) }
    LaunchedEffect(visible) {
        progress.animateTo(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (visible) YUMMY_FADE_IN_MS else YUMMY_FADE_OUT_MS,
                easing = FastOutSlowInEasing,
            ),
        )
    }
    return graphicsLayer {
        val value = progress.value
        alpha = value
        val scale = scaleFrom + ((1f - scaleFrom) * value)
        scaleX = scale
        scaleY = scale
    }
}

@Composable
internal fun Modifier.yummyDialogMotion(): Modifier = yummyAppearMotion(scaleFrom = 0.975f)
