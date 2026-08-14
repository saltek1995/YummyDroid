package me.yummydroid.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// HorizontalScrollEdgeFrame
@Composable
internal fun HorizontalScrollEdgeFrame(
    state: LazyListState,
    modifier: Modifier = Modifier,
    edgeWidth: Dp = HorizontalScrollEdgeDefaultOverlayWidth,
    content: @Composable BoxScope.() -> Unit,
) {
    val edgeVisibility = rememberHorizontalScrollEdgeVisibility(state, edgeWidth)
    Box(
        modifier = modifier
            .horizontalScrollEdgeContentFade(
                visibility = edgeVisibility,
                edgeWidth = edgeWidth,
            ),
        content = content,
    )
}

@Composable
internal fun rememberHorizontalScrollEdgeVisibility(
    state: LazyListState,
    edgeWidth: Dp = HorizontalScrollEdgeDefaultOverlayWidth,
    backwardEdgeInset: Dp = 0.dp,
): HorizontalScrollEdgeVisibility {
    val density = LocalDensity.current
    val edgeWidthPx = remember(density, edgeWidth) { with(density) { edgeWidth.toPx() } }
    val backwardEdgeInsetPx = remember(density, backwardEdgeInset) {
        with(density) { backwardEdgeInset.toPx() }
    }
    val edgeVisibility by remember(state, edgeWidthPx, backwardEdgeInsetPx) {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            resolveHorizontalScrollEdgeVisibility(
                canScrollBackward = state.canScrollBackward,
                canScrollForward = state.canScrollForward,
                totalItemsCount = layoutInfo.totalItemsCount,
                firstVisibleIndex = visibleItems.firstOrNull()?.index,
                firstVisibleOffset = visibleItems.firstOrNull()?.offset,
                lastVisibleIndex = visibleItems.lastOrNull()?.index,
                lastVisibleEndOffset = visibleItems.lastOrNull()?.let { item ->
                    item.offset + item.size
                },
                viewportEndOffset = layoutInfo.viewportSize.width,
                edgeWidthPx = edgeWidthPx,
                backwardEdgeInsetPx = backwardEdgeInsetPx,
            )
        }
    }
    return edgeVisibility
}

internal data class HorizontalScrollEdgeVisibility(
    val backward: Boolean,
    val forward: Boolean,
    val backwardFraction: Float = if (backward) 1f else 0f,
    val forwardFraction: Float = if (forward) 1f else 0f,
)

internal fun resolveHorizontalScrollEdgeVisibility(
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    totalItemsCount: Int,
    firstVisibleIndex: Int?,
    firstVisibleOffset: Int?,
    lastVisibleIndex: Int?,
    lastVisibleEndOffset: Int?,
    viewportEndOffset: Int,
    edgeWidthPx: Float = 0f,
    backwardEdgeInsetPx: Float = 0f,
): HorizontalScrollEdgeVisibility {
    val resolvedFirstOffset = firstVisibleOffset
    val resolvedLastEndOffset = lastVisibleEndOffset
    val hasVisibleItems = totalItemsCount > 0 &&
        firstVisibleIndex != null &&
        resolvedFirstOffset != null &&
        lastVisibleIndex != null &&
        resolvedLastEndOffset != null
    if (!hasVisibleItems) return HorizontalScrollEdgeVisibility(backward = false, forward = false)

    val resolvedEdgeWidth = edgeWidthPx.coerceAtLeast(1f)
    val backwardFraction = if (canScrollBackward) {
        edgeFadeProgress(
            distanceToEdgePx = resolvedFirstOffset.toFloat() - backwardEdgeInsetPx.coerceAtLeast(0f),
            fadeWidthPx = resolvedEdgeWidth,
        )
    } else {
        0f
    }
    val forwardFraction = if (canScrollForward) {
        edgeFadeProgress(
            distanceToEdgePx = viewportEndOffset - resolvedLastEndOffset.toFloat(),
            fadeWidthPx = resolvedEdgeWidth,
        )
    } else {
        0f
    }
    return HorizontalScrollEdgeVisibility(
        backward = backwardFraction > 0.001f,
        forward = forwardFraction > 0.001f,
        backwardFraction = backwardFraction,
        forwardFraction = forwardFraction,
    )
}
