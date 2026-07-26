package me.yummydroid.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import me.yummydroid.app.ui.theme.YummyColors

private val FocusFillColor = YummyColors.focusOverlay

fun Modifier.focusRing(shape: Shape): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val focusProgress by animateFloatAsState(if (focused) 1f else 0f, label = "focus-fill")
    val focusColor = FocusFillColor.copy(alpha = 0.22f * focusProgress)
    val focusScale = 1f + 0.012f * focusProgress

    onFocusChanged { focused = it.isFocused }
        .clip(shape)
        .graphicsLayer {
            scaleX = focusScale
            scaleY = focusScale
            shadowElevation = 14f * focusProgress
            this.shape = shape
            clip = false
        }
        .drawWithContent {
            if (focusProgress > 0f) {
                drawRect(focusColor)
            }
            drawContent()
        }
}

fun Modifier.dpadClickable(
    shape: Shape,
    onClick: () -> Unit,
): Modifier = dpadClickable(shape, enabled = true, onClick = onClick)

fun Modifier.dpadClickable(
    shape: Shape,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = if (enabled) {
    focusRing(shape).clickable(onClick = onClick)
} else {
    clip(shape)
}
