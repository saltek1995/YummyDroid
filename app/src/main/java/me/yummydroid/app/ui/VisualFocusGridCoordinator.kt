package me.yummydroid.app.ui

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.LayoutCoordinates

internal class VisualFocusGridState internal constructor(
    size: Int,
    allowLoosePerpendicularMatch: Boolean = false,
) {
    private val targets = VisualFocusTargetRegistry(size, allowLoosePerpendicularMatch)
    private val retention = VisualFocusRetentionState(size)

    val size: Int get() = targets.size
    val focusedIndex: Int? get() = retention.focusedIndex
    val lastFocusedIndex: Int? get() = retention.lastFocusedIndex
    val lastFocusedKey: Any? get() = retention.lastFocusedKey

    fun requester(index: Int): FocusRequester? = targets.requester(index)

    fun updateBounds(index: Int, bounds: VisualFocusBounds, coordinates: LayoutCoordinates) {
        val updatedFocusKey = targets.updateBounds(index, bounds, coordinates)
        if (retention.focusedIndex == index && updatedFocusKey != null) {
            retention.updateLastFocusedKey(updatedFocusKey)
        }
    }

    fun clearBounds(index: Int) {
        targets.clearBounds(index)
        retention.clearFocusedIndex(index)
    }

    fun updateFocusedIndex(index: Int, focused: Boolean) {
        if (!targets.contains(index)) return
        if (focused) {
            retention.focus(index, targets.bounds(index)?.focusKey)
        } else {
            retention.clearFocusedIndex(index)
        }
    }

    fun focusTarget(
        index: Int,
        direction: VisualGridDirection,
        exit: FocusRequester?,
    ): FocusRequester? = targets.focusTarget(index, direction, exit)

    fun requestFocusTarget(
        index: Int,
        direction: VisualGridDirection,
        exit: FocusRequester?,
    ): Boolean = targets.requestFocusTarget(index, direction, exit)

    fun requestDirectionalFocusFromCurrent(direction: VisualGridDirection): Boolean {
        val index = focusedIndex ?: return false
        targets.bounds(index)?.takeUnless { it.canNavigate(direction) }?.let { focusedBounds ->
            return focusedBounds.consumeDisabledAxis
        }
        return requestFocusTarget(
            index = index,
            direction = direction,
            exit = null,
        )
    }

    fun requestFirstAvailableFocus(): Boolean = targets.requestFirstAvailableFocus()

    fun requestRetainedOrFirstAvailableFocus(): Boolean {
        if (requestLastFocusedFocus()) return true
        val retainedIndex = focusedIndex?.takeIf(targets::hasBounds)
        if (retainedIndex != null && requestFocusAt(retainedIndex)) return true
        return requestFirstAvailableFocus()
    }

    fun requestLastFocusedFocus(): Boolean {
        requestFocusByKey(lastFocusedKey)?.let { restored ->
            if (restored) return true
        }
        val index = lastFocusedIndex?.takeIf(targets::hasBounds)
            ?: return false
        return requestFocusAt(index)
    }

    fun requestFocusByKey(focusKey: Any?): Boolean? = targets.requestFocusByKey(focusKey)

    fun requestFocusAt(index: Int): Boolean = targets.requestFocusAt(index)
}
