package me.yummydroid.app.ui

internal fun visualGridHorizontalPageTarget(
    sourceLocalIndex: Int,
    sourceTotal: Int,
    targetTotal: Int,
    columns: Int,
    direction: VisualGridDirection,
): Int? {
    if (sourceLocalIndex !in 0 until sourceTotal || targetTotal <= 0 || columns <= 0) return null
    val sourceColumn = sourceLocalIndex % columns
    val targetColumn = direction.horizontalPageTargetColumn(
        sourceColumn = sourceColumn,
        sourceLocalIndex = sourceLocalIndex,
        sourceTotal = sourceTotal,
        columns = columns,
    ) ?: return null
    val sourceRow = sourceLocalIndex / columns
    return (sourceRow * columns + targetColumn).coerceAtMost(targetTotal - 1)
}

private fun VisualGridDirection.horizontalPageTargetColumn(
    sourceColumn: Int,
    sourceLocalIndex: Int,
    sourceTotal: Int,
    columns: Int,
): Int? {
    return when {
        this == VisualGridDirection.Left && sourceColumn == 0 -> columns - 1
        this == VisualGridDirection.Right &&
            (sourceColumn == columns - 1 || sourceLocalIndex == sourceTotal - 1) -> 0
        else -> null
    }
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
