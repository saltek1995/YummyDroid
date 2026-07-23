package me.yummydroid.app.ui

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
    if (index !in 0 until total || total <= 0 || columns <= 0) return null
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
    return (columns.coerceAtLeast(1) * rows.coerceAtLeast(1)).coerceAtLeast(1)
}

internal fun visualGridPageCount(total: Int, pageSize: Int): Int {
    if (total <= 0) return 1
    val safePageSize = pageSize.coerceAtLeast(1)
    return ((total + safePageSize - 1) / safePageSize).coerceAtLeast(1)
}

internal fun visualGridPageStart(page: Int, pageSize: Int, total: Int): Int {
    if (total <= 0) return 0
    val safePageSize = pageSize.coerceAtLeast(1)
    val lastPage = visualGridPageCount(total, safePageSize) - 1
    return page.coerceIn(0, lastPage) * safePageSize
}
