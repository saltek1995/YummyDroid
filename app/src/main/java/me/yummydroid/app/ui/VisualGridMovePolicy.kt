package me.yummydroid.app.ui

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
