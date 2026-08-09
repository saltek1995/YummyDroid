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
    val target = visualGridMoveTarget(
        index = sourceIndex,
        total = itemCount,
        columns = columns,
        direction = direction,
    )
    return if (target != null) {
        moveFocusTo(target)
    } else {
        onEdgeExit(direction)
    }
}

internal fun visualGridHorizontalPageTarget(
    sourceLocalIndex: Int,
    sourceTotal: Int,
    targetTotal: Int,
    columns: Int,
    direction: VisualGridDirection,
): Int? {
    if (
        sourceLocalIndex !in 0 until sourceTotal ||
        targetTotal <= 0 ||
        columns <= 0
    ) {
        return null
    }
    val sourceColumn = sourceLocalIndex % columns
    val sourceRow = sourceLocalIndex / columns
    val targetColumn = when (direction) {
        VisualGridDirection.Left -> if (sourceColumn == 0) columns - 1 else return null
        VisualGridDirection.Right -> if (
            sourceColumn == columns - 1 ||
            sourceLocalIndex == sourceTotal - 1
        ) {
            0
        } else {
            return null
        }
        VisualGridDirection.Up,
        VisualGridDirection.Down -> return null
    }
    return (sourceRow * columns + targetColumn).coerceAtMost(targetTotal - 1)
}

internal fun visualGridPageSize(columns: Int, rows: Int): Int {
    val safeColumns = columns.coerceAtLeast(1).toLong()
    val safeRows = rows.coerceAtLeast(1).toLong()
    return (safeColumns * safeRows)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}

internal fun visualGridPageCount(total: Int, pageSize: Int): Int {
    if (total <= 0) return 1
    val safePageSize = pageSize.coerceAtLeast(1)
    return ((total - 1) / safePageSize + 1).coerceAtLeast(1)
}

internal fun visualGridPageStart(page: Int, pageSize: Int, total: Int): Int {
    if (total <= 0) return 0
    val safePageSize = pageSize.coerceAtLeast(1)
    val lastPage = visualGridPageCount(total, safePageSize) - 1
    return page.coerceIn(0, lastPage) * safePageSize
}

internal fun visualGridActivePageLocalIndex(
    activePage: Boolean,
    localIndex: Int,
    activeTotal: Int,
): Boolean {
    return activePage && localIndex in 0 until activeTotal
}
