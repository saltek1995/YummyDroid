package me.yummydroid.app.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal val DetailsBringIntoViewSpec = object : BringIntoViewSpec {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val targetEnd = offset + size
        val edgeGuard = (containerSize * 0.06f).coerceAtMost(56f)
        val visibleStart = edgeGuard
        val visibleEnd = containerSize - edgeGuard
        return when {
            offset < visibleStart -> offset - visibleStart
            targetEnd > visibleEnd -> targetEnd - visibleEnd
            else -> 0f
        }
    }
}
