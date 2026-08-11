package me.yummydroid.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal class BrowseBottomChromeGeometryState {
    private var barTopRootY by mutableFloatStateOf(0f)
    private var barHeightPx by mutableIntStateOf(0)
    private var baseControlsHeightPx by mutableIntStateOf(0)
    private var measuredPointerBlockStartY by mutableStateOf<Float?>(null)

    fun clearPointerBlockStart() {
        measuredPointerBlockStartY = null
    }

    fun pointerBlockHeight(density: Density, fallbackStart: Dp): Dp = with(density) {
        val start = measuredPointerBlockStartY ?: fallbackStart.toPx()
        (barHeightPx - start).coerceAtLeast(0f).toDp()
    }

    fun baseControlsContentHeight(density: Density, contentTopPadding: Dp): Dp {
        val baseControlsHeight = if (baseControlsHeightPx > 0) {
            with(density) { baseControlsHeightPx.toDp() }
        } else {
            contentTopPadding + BrowseBottomBaseControlsFallbackHeight
        }
        return (baseControlsHeight - contentTopPadding).coerceAtLeast(0.dp)
    }

    fun Modifier.trackBar(): Modifier = this
        .onSizeChanged { size -> barHeightPx = size.height }
        .onGloballyPositioned { coordinates ->
            barTopRootY = coordinates.positionInRoot().y
        }

    fun Modifier.trackBaseControls(): Modifier = onSizeChanged { size ->
        baseControlsHeightPx = size.height
    }

    fun Modifier.pointerBlockStartAnchor(): Modifier = onGloballyPositioned { coordinates ->
        measuredPointerBlockStartY = (coordinates.positionInRoot().y - barTopRootY).coerceAtLeast(0f)
    }
}
