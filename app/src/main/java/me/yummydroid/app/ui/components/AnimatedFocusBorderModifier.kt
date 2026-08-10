package me.yummydroid.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val FocusBorderFadeDurationMillis = 80
private const val FocusBorderRotationFrameMillis = 50L
private val DefaultFocusBorderWidth = 2.dp
private val DefaultFocusHighlightWidth = 2.5.dp
private val DefaultFocusBorderCorner = 8.dp

fun Modifier.animatedFocusBorder(
    active: Boolean,
    cornerRadius: Dp = DefaultFocusBorderCorner,
    borderWidth: Dp = DefaultFocusBorderWidth,
    highlightWidth: Dp = DefaultFocusHighlightWidth,
): Modifier = composed {
    val visible = active && LocalInputModeManager.current.inputMode != InputMode.Touch
    if (!visible) return@composed this

    val borderProgress = remember { Animatable(0f) }
    var borderFrameIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(visible) {
        borderProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = FocusBorderFadeDurationMillis,
                easing = FastOutSlowInEasing,
            ),
        )
        while (true) {
            delay(FocusBorderRotationFrameMillis)
            borderFrameIndex = (borderFrameIndex + 1) % FocusBorderFrameCount
        }
    }

    drawAnimatedFocusBorder(
        cornerRadius = cornerRadius,
        borderWidth = borderWidth,
        highlightWidth = highlightWidth,
        alpha = { borderProgress.value },
        frameIndex = { borderFrameIndex },
    )
}
