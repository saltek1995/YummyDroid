package me.yummydroid.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

@Composable
internal fun Modifier.browseTopBarVisibility(visibility: BrowseTopChromeVisibility): Modifier {
    val animatedProgress = if (visibility.progress == null && visibility.progressProvider == null) {
        val progress by animateFloatAsState(
            targetValue = if (visibility.visible) 1f else 0f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "browseTopBarVisibility",
        )
        progress
    } else {
        null
    }
    fun progress(): Float = (
        visibility.progressProvider?.invoke() ?: visibility.progress ?: animatedProgress ?: 0f
    ).coerceIn(0f, 1f)

    return this
        .then(if (visibility.visible) Modifier else Modifier.focusProperties { canFocus = false })
        .layout { measurable, constraints ->
            val resolvedProgress = progress()
            val placeable = measurable.measure(constraints)
            val height = if (visibility.collapseWhenHidden) {
                (placeable.height * resolvedProgress).roundToInt()
            } else {
                placeable.height
            }
            val offsetY = if (visibility.collapseWhenHidden) {
                height - placeable.height
            } else {
                ((resolvedProgress - 1f) * placeable.height).roundToInt()
            }
            layout(width = placeable.width, height = height) {
                placeable.placeRelative(x = 0, y = offsetY)
            }
        }
        .clipToBounds()
        .graphicsLayer { alpha = if (visibility.collapseWhenHidden) progress() else 1f }
}

internal fun Modifier.browseTopBarExitDown(onExitDown: (() -> Unit)?): Modifier {
    if (onExitDown == null) return this
    return onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
            onExitDown()
            true
        } else {
            false
        }
    }
}
