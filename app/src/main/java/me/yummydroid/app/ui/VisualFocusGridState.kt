package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow

internal fun FocusRequester.requestFocusSafely(): Boolean {
    return runCatching { requestFocus() }.getOrDefault(false)
}

@Composable
internal fun rememberVisualFocusGridState(
    size: Int,
    key: Any? = Unit,
    allowLoosePerpendicularMatch: Boolean = false,
): VisualFocusGridState {
    return remember(size, key, allowLoosePerpendicularMatch) {
        VisualFocusGridState(
            size = size.coerceAtLeast(0),
            allowLoosePerpendicularMatch = allowLoosePerpendicularMatch,
        )
    }
}

internal class VisualFocusGridState internal constructor(
    size: Int,
    private val allowLoosePerpendicularMatch: Boolean = false,
) {
    private val requesters = List(size) { FocusRequester() }
    private val bounds = LinkedHashMap<Int, VisualFocusBounds>()
    private val coordinates = LinkedHashMap<Int, LayoutCoordinates>()
    private val focusedIndexState = mutableIntStateOf(-1)
    private val lastFocusedIndexState = mutableIntStateOf(-1)
    private val lastFocusedKeyState = mutableStateOf<Any?>(null)

    val size: Int get() = requesters.size
    val focusedIndex: Int? get() = focusedIndexState.intValue.takeIf { it in requesters.indices }
    val lastFocusedIndex: Int? get() = lastFocusedIndexState.intValue.takeIf { it in requesters.indices }
    val lastFocusedKey: Any? get() = lastFocusedKeyState.value

    fun requester(index: Int): FocusRequester? = requesters.getOrNull(index)

    fun updateBounds(index: Int, bounds: VisualFocusBounds, coordinates: LayoutCoordinates) {
        if (index !in requesters.indices) return
        this.coordinates[index] = coordinates
        if (this.bounds[index] == bounds) return
        this.bounds[index] = bounds
        if (focusedIndexState.intValue == index && bounds.focusKey != null) {
            lastFocusedKeyState.value = bounds.focusKey
        }
    }

    fun clearBounds(index: Int) {
        bounds.remove(index)
        coordinates.remove(index)
        if (focusedIndexState.intValue == index) {
            focusedIndexState.intValue = -1
        }
    }

    fun updateFocusedIndex(index: Int, focused: Boolean) {
        if (index !in requesters.indices) return
        if (focused) {
            focusedIndexState.intValue = index
            lastFocusedIndexState.intValue = index
            lastFocusedKeyState.value = bounds[index]?.focusKey
        } else if (focusedIndexState.intValue == index) {
            focusedIndexState.intValue = -1
        }
    }

    fun focusTarget(
        index: Int,
        direction: VisualGridDirection,
        exit: FocusRequester?,
    ): FocusRequester? {
        val target = focusTargetIndex(index, direction)
        return when {
            target != null -> requesters.getOrNull(target)
            exit != null -> exit
            else -> null
        }
    }

    fun requestFocusTarget(
        index: Int,
        direction: VisualGridDirection,
        exit: FocusRequester?,
    ): Boolean {
        val target = focusTargetIndex(index, direction)
        return when {
            target != null -> requesters.getOrNull(target)?.requestFocusSafely() == true
            exit != null -> exit.requestFocusSafely()
            bounds[index] != null -> true
            else -> false
        }
    }

    fun requestDirectionalFocusFromCurrent(direction: VisualGridDirection): Boolean {
        val index = focusedIndex ?: return false
        bounds[index]?.takeUnless { it.canNavigate(direction) }?.let { focusedBounds ->
            return focusedBounds.consumeDisabledAxis
        }
        return requestFocusTarget(
            index = index,
            direction = direction,
            exit = null,
        )
    }

    fun requestFirstAvailableFocus(): Boolean {
        return availableFocusIndexes().any { targetIndex ->
            requesters.getOrNull(targetIndex)?.requestFocusSafely() == true
        }
    }

    fun requestRetainedOrFirstAvailableFocus(): Boolean {
        if (requestLastFocusedFocus()) return true
        val retainedIndex = focusedIndex?.takeIf { index -> bounds[index] != null }
        if (retainedIndex != null && requestFocusAt(retainedIndex)) return true
        return requestFirstAvailableFocus()
    }

    fun requestLastFocusedFocus(): Boolean {
        requestFocusByKey(lastFocusedKey)?.let { restored ->
            if (restored) return true
        }
        val index = lastFocusedIndex?.takeIf { retainedIndex -> bounds[retainedIndex] != null }
            ?: return false
        return requestFocusAt(index)
    }

    fun requestFocusByKey(focusKey: Any?): Boolean? {
        if (focusKey == null) return null
        val target = currentBounds().firstOrNull { itemBounds -> itemBounds.focusKey == focusKey }
            ?: return false
        return requestFocusAt(target.index)
    }

    fun requestFocusAt(index: Int): Boolean {
        if (index !in requesters.indices || bounds[index] == null) return false
        return requesters.getOrNull(index)?.requestFocusSafely() == true
    }

    private fun availableFocusIndexes(): List<Int> {
        return currentBounds()
            .sortedWith(compareBy<VisualFocusBounds> { it.top }.thenBy { it.left })
            .map { it.index }
            .ifEmpty { bounds.keys.sorted() }
            .ifEmpty { requesters.indices.toList() }
    }

    private fun focusTargetIndex(index: Int, direction: VisualGridDirection): Int? {
        return visualFocusDirectionalTarget(
            bounds = currentBounds(),
            sourceIndex = index,
            direction = direction,
            allowLoosePerpendicularMatch = allowLoosePerpendicularMatch,
        ) ?: fallbackTargetBeforeLayout(index, direction)
    }

    private fun currentBounds(): Collection<VisualFocusBounds> {
        return bounds.mapNotNull { (index, storedBounds) ->
            currentBounds(index, storedBounds).takeIf { it.hasUsableSize() }
        }
    }

    private fun currentBounds(index: Int, storedBounds: VisualFocusBounds): VisualFocusBounds {
        val itemCoordinates = coordinates[index] ?: return storedBounds
        return runCatching {
            val rect = itemCoordinates.boundsInWindow(clipBounds = false)
            storedBounds.copy(
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,
            )
        }.getOrDefault(storedBounds)
    }

    private fun fallbackTargetBeforeLayout(index: Int, direction: VisualGridDirection): Int? {
        if (bounds[index] != null) return null
        return when (direction) {
            VisualGridDirection.Left -> (index - 1).takeIf { it >= 0 }
            VisualGridDirection.Right -> (index + 1).takeIf { it < size }
            VisualGridDirection.Up,
            VisualGridDirection.Down -> null
        }
    }
}
