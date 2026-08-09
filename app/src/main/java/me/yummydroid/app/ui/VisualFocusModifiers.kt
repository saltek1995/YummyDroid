package me.yummydroid.app.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

internal fun Modifier.visualFocusGridNavigation(
    state: VisualFocusGridState,
): Modifier {
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            return@onPreviewKeyEvent false
        }
        val direction = event.key.toVisualGridDirectionOrNull()
            ?: return@onPreviewKeyEvent false
        state.requestDirectionalFocusFromCurrent(direction)
    }
}

internal fun Modifier.visualFocusGridItem(
    state: VisualFocusGridState,
    index: Int,
    horizontal: Boolean = true,
    vertical: Boolean = false,
    leftExit: FocusRequester? = null,
    rightExit: FocusRequester? = null,
    upExit: FocusRequester? = null,
    downExit: FocusRequester? = null,
    blockKey: Any? = null,
    blockEntryIndex: Int = index,
    consumeDisabledAxis: Boolean = false,
    focusKey: Any? = null,
): Modifier {
    val configuration = VisualFocusItemConfiguration(
        horizontal = horizontal,
        vertical = vertical,
        leftExit = leftExit,
        rightExit = rightExit,
        upExit = upExit,
        downExit = downExit,
        blockKey = blockKey,
        blockEntryIndex = blockEntryIndex,
        consumeDisabledAxis = consumeDisabledAxis,
        focusKey = focusKey,
    )
    return then(Modifier.visualFocusGridItemModifier(state, index, configuration))
}

internal fun Modifier.visualFocusGridItemIfPresent(
    state: VisualFocusGridState?,
    index: Int,
    blockKey: Any? = null,
    blockEntryIndex: Int = index,
): Modifier {
    if (state == null) return this
    return visualFocusGridItem(
        state = state,
        index = index,
        horizontal = true,
        vertical = true,
        blockKey = blockKey,
        blockEntryIndex = blockEntryIndex,
    )
}

private fun Modifier.visualFocusGridItemModifier(
    state: VisualFocusGridState,
    index: Int,
    configuration: VisualFocusItemConfiguration,
): Modifier {
    return composed {
        val requester = state.requester(index) ?: return@composed Modifier
        DisposableEffect(state, index) {
            onDispose { state.clearBounds(index) }
        }
        Modifier
            .focusRequester(requester)
            .onFocusChanged { focusState ->
                state.updateFocusedIndex(
                    index = index,
                    focused = focusState.isFocused || focusState.hasFocus,
                )
            }
            .onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInWindow(clipBounds = false)
                state.updateBounds(
                    index = index,
                    bounds = configuration.toBounds(index, rect),
                    coordinates = coordinates,
                )
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                val direction = event.key.toVisualGridDirectionOrNull()
                    ?: return@onPreviewKeyEvent false
                state.handleItemNavigation(index, direction, configuration)
            }
            .focusProperties {
                applyVisualFocusTargets(state, index, configuration)
            }
    }
}

internal fun Modifier.focusEntryGroup(entry: FocusRequester?): Modifier {
    if (entry == null) return focusGroup()
    return focusProperties {
        onEnter = { entry.requestFocusSafely() }
    }.focusGroup()
}

private fun VisualFocusGridState.handleItemNavigation(
    index: Int,
    direction: VisualGridDirection,
    configuration: VisualFocusItemConfiguration,
): Boolean {
    if (!configuration.canNavigate(direction)) return configuration.consumeDisabledAxis
    return requestFocusTarget(
        index = index,
        direction = direction,
        exit = configuration.exit(direction),
    )
}

private fun FocusProperties.applyVisualFocusTargets(
    state: VisualFocusGridState,
    index: Int,
    configuration: VisualFocusItemConfiguration,
) {
    VisualGridDirection.entries.forEach { direction ->
        if (configuration.canNavigate(direction)) {
            state.focusTarget(index, direction, configuration.exit(direction))
                ?.let { target -> setVisualFocusTarget(direction, target) }
        }
    }
}

private fun FocusProperties.setVisualFocusTarget(
    direction: VisualGridDirection,
    target: FocusRequester,
) {
    when (direction) {
        VisualGridDirection.Left -> left = target
        VisualGridDirection.Right -> right = target
        VisualGridDirection.Up -> up = target
        VisualGridDirection.Down -> down = target
    }
}

private data class VisualFocusItemConfiguration(
    val horizontal: Boolean,
    val vertical: Boolean,
    val leftExit: FocusRequester?,
    val rightExit: FocusRequester?,
    val upExit: FocusRequester?,
    val downExit: FocusRequester?,
    val blockKey: Any?,
    val blockEntryIndex: Int,
    val consumeDisabledAxis: Boolean,
    val focusKey: Any?,
) {
    fun canNavigate(direction: VisualGridDirection): Boolean {
        return when (direction) {
            VisualGridDirection.Left,
            VisualGridDirection.Right -> horizontal
            VisualGridDirection.Up,
            VisualGridDirection.Down -> vertical
        }
    }

    fun exit(direction: VisualGridDirection): FocusRequester? {
        return when (direction) {
            VisualGridDirection.Left -> leftExit
            VisualGridDirection.Right -> rightExit
            VisualGridDirection.Up -> upExit
            VisualGridDirection.Down -> downExit
        }
    }

    fun toBounds(index: Int, rect: Rect): VisualFocusBounds {
        return VisualFocusBounds(
            index = index,
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
            blockKey = blockKey,
            blockEntryIndex = blockEntryIndex,
            horizontal = horizontal,
            vertical = vertical,
            consumeDisabledAxis = consumeDisabledAxis,
            focusKey = focusKey ?: blockKey?.let {
                VisualFocusRestoreKey(it, blockEntryIndex)
            },
        )
    }
}

private data class VisualFocusRestoreKey(
    val blockKey: Any,
    val blockEntryIndex: Int,
)
