package me.yummydroid.app.ui

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow

internal class VisualFocusTargetRegistry(
    size: Int,
    private val allowLoosePerpendicularMatch: Boolean,
) {
    private val requesters = List(size) { FocusRequester() }
    private val storedBounds = LinkedHashMap<Int, VisualFocusBounds>()
    private val coordinates = LinkedHashMap<Int, LayoutCoordinates>()

    val size: Int get() = requesters.size

    fun contains(index: Int): Boolean = index in requesters.indices

    fun requester(index: Int): FocusRequester? = requesters.getOrNull(index)

    fun bounds(index: Int): VisualFocusBounds? = storedBounds[index]

    fun hasBounds(index: Int): Boolean = storedBounds[index] != null

    fun updateBounds(
        index: Int,
        bounds: VisualFocusBounds,
        coordinates: LayoutCoordinates,
    ): Any? {
        if (!contains(index)) return null
        this.coordinates[index] = coordinates
        if (storedBounds[index] == bounds) return null
        storedBounds[index] = bounds
        return bounds.focusKey
    }

    fun clearBounds(index: Int) {
        storedBounds.remove(index)
        coordinates.remove(index)
    }

    fun focusTarget(
        index: Int,
        direction: VisualGridDirection,
        exit: FocusRequester?,
    ): FocusRequester? {
        val target = focusTargetIndex(index, direction)
        return when {
            target != null -> requester(target)
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
            target != null -> requester(target)?.requestFocusSafely() == true
            exit != null -> exit.requestFocusSafely()
            hasBounds(index) -> true
            else -> false
        }
    }

    fun requestFirstAvailableFocus(): Boolean {
        return availableFocusIndexes().any { index ->
            requester(index)?.requestFocusSafely() == true
        }
    }

    fun requestFocusByKey(focusKey: Any?): Boolean? {
        if (focusKey == null) return null
        val target = currentBounds().firstOrNull { bounds -> bounds.focusKey == focusKey }
            ?: return false
        return requestFocusAt(target.index)
    }

    fun requestFocusAt(index: Int): Boolean {
        if (!contains(index) || !hasBounds(index)) return false
        return requester(index)?.requestFocusSafely() == true
    }

    private fun availableFocusIndexes(): List<Int> {
        return currentBounds()
            .sortedWith(compareBy<VisualFocusBounds> { it.top }.thenBy { it.left })
            .map { it.index }
            .ifEmpty { storedBounds.keys.sorted() }
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
        return storedBounds.mapNotNull { (index, bounds) ->
            currentBounds(index, bounds).takeIf { it.hasUsableSize() }
        }
    }

    private fun currentBounds(index: Int, bounds: VisualFocusBounds): VisualFocusBounds {
        val itemCoordinates = coordinates[index] ?: return bounds
        return runCatching {
            val rect = itemCoordinates.boundsInWindow(clipBounds = false)
            bounds.copy(
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,
            )
        }.getOrDefault(bounds)
    }

    private fun fallbackTargetBeforeLayout(index: Int, direction: VisualGridDirection): Int? {
        if (hasBounds(index)) return null
        return when (direction) {
            VisualGridDirection.Left -> (index - 1).takeIf { it >= 0 }
            VisualGridDirection.Right -> (index + 1).takeIf { it < size }
            VisualGridDirection.Up,
            VisualGridDirection.Down -> null
        }
    }
}
