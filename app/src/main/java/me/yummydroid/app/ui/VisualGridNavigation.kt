package me.yummydroid.app.ui

import androidx.compose.ui.input.key.Key

internal enum class VisualGridDirection {
    Left,
    Right,
    Up,
    Down,
}

internal fun visualGridMoveTarget(
    index: Int,
    total: Int,
    columns: Int,
    direction: VisualGridDirection,
): Int? {
    if (total <= 0 || columns <= 0 || index !in 0 until total) return null
    val column = index % columns
    return when (direction) {
        VisualGridDirection.Left -> (index - 1).takeIf { column > 0 && it >= 0 }
        VisualGridDirection.Right -> (index + 1).takeIf {
            column < columns - 1 && it < total
        }
        VisualGridDirection.Up -> (index - columns).takeIf { it >= 0 }
        VisualGridDirection.Down -> (index + columns).takeIf { it < total }
    }
}

internal fun Key.toVisualGridDirectionOrNull(): VisualGridDirection? {
    return when (this) {
        Key.DirectionLeft -> VisualGridDirection.Left
        Key.DirectionRight -> VisualGridDirection.Right
        Key.DirectionUp -> VisualGridDirection.Up
        Key.DirectionDown -> VisualGridDirection.Down
        else -> null
    }
}

internal fun handleVisualGridNavigationKey(
    key: Key,
    itemCount: Int,
    columns: Int,
    currentFocusedIndex: Int,
    fallbackIndex: Int,
    moveFocusTo: (Int) -> Boolean,
    onEdgeExit: (VisualGridDirection) -> Boolean,
): Boolean {
    val direction = key.toVisualGridDirectionOrNull() ?: return false
    if (columns <= 0 || itemCount <= 0 || fallbackIndex !in 0 until itemCount) return false
    val sourceIndex = currentFocusedIndex.takeIf { it in 0 until itemCount } ?: fallbackIndex
    val target = visualGridMoveTarget(sourceIndex, itemCount, columns, direction)
    return target?.let(moveFocusTo) ?: onEdgeExit(direction)
}
