package me.yummyani.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

fun Modifier.focusRing(shape: Shape): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.025f else 1f, label = "focus-scale")
    val borderAlpha by animateIntAsState(if (focused) 255 else 0, label = "focus-border")
    val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = borderAlpha / 255f)

    onFocusChanged { focused = it.isFocused || it.hasFocus }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .border(2.dp, borderColor.takeUnless { borderAlpha == 0 } ?: Color.Transparent, shape)
        .clip(shape)
}

fun Modifier.dpadClickable(
    shape: Shape,
    onClick: () -> Unit,
): Modifier = focusRing(shape)
    .clickable(onClick = onClick)
