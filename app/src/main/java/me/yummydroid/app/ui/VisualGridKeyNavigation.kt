package me.yummydroid.app.ui

import androidx.compose.ui.input.key.Key

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
