package me.yummydroid.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

internal data class BrowseBottomProtectedContentState(
    val content: (@Composable (Modifier) -> Unit)?,
    val progress: Float,
) {
    val active: Boolean
        get() = content != null && progress > 0.001f
}

@Composable
internal fun rememberBrowseBottomProtectedContentState(
    content: (@Composable (Modifier) -> Unit)?,
    visibilityProgress: Float?,
): BrowseBottomProtectedContentState {
    var retainedContent by remember {
        mutableStateOf<(@Composable (Modifier) -> Unit)?>(null)
    }
    val animatedProgress = if (visibilityProgress == null) {
        val progress by animateFloatAsState(
            targetValue = if (content != null) 1f else 0f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "browseBottomTopProtectedProgress",
            finishedListener = { value ->
                if (value <= 0.001f) retainedContent = null
            },
        )
        progress
    } else {
        0f
    }
    val resolvedProgress = visibilityProgress?.coerceIn(0f, 1f) ?: animatedProgress

    SideEffect {
        if (content != null) retainedContent = content
    }
    LaunchedEffect(content, resolvedProgress) {
        if (content == null && resolvedProgress <= 0.001f) retainedContent = null
    }
    return BrowseBottomProtectedContentState(
        content = content ?: retainedContent,
        progress = resolvedProgress,
    )
}

internal fun Modifier.browseBottomTopProtectedVisibility(progress: Float): Modifier {
    val resolvedProgress = progress.coerceIn(0f, 1f)
    return this
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val height = (placeable.height * resolvedProgress).roundToInt()
            layout(width = placeable.width, height = height) {
                placeable.placeRelative(x = 0, y = height - placeable.height)
            }
        }
        .clipToBounds()
        .graphicsLayer { alpha = resolvedProgress }
}
