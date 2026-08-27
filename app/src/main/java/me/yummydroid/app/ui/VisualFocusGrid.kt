package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.LayoutCoordinates

// VisualFocusGridCoordinator
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
            if (pendingMaterializedFocusIndex != index) pendingMaterializedFocusIndex = null
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
    ): Boolean {
        return when (val result = targets.requestFocusTarget(index, direction, exit)) {
            VisualFocusRequestResult.Focused,
            VisualFocusRequestResult.Consumed -> {
                pendingMaterializedFocusIndex = null
                true
            }
            VisualFocusRequestResult.Failed -> {
                pendingMaterializedFocusIndex = null
                false
            }
            is VisualFocusRequestResult.Materializing -> {
                pendingMaterializedFocusIndex = result.targetIndex
                true
            }
        }
    }

    fun registerVirtualBlockEntry(
        blockKey: Any,
        entryIndex: Int,
        materialize: () -> Unit,
    ): Long = targets.registerBlockEntryMaterializer(blockKey, entryIndex, materialize)

    fun unregisterVirtualBlockEntry(blockKey: Any, entryIndex: Int, registrationId: Long) {
        val removed = targets.unregisterBlockEntryMaterializer(blockKey, registrationId)
        if (removed && pendingMaterializedFocusIndex == entryIndex) pendingMaterializedFocusIndex = null
    }

    fun requestVirtualBlockEntry(blockKey: Any, entryIndex: Int): Boolean {
        if (requestFocusAt(entryIndex)) {
            pendingMaterializedFocusIndex = null
            return true
        }
        return if (targets.materializeBlockEntry(blockKey, entryIndex)) {
            pendingMaterializedFocusIndex = entryIndex
            true
        } else {
            pendingMaterializedFocusIndex = null
            false
        }
    }

    fun completePendingMaterializedFocus(): Boolean {
        val index = pendingMaterializedFocusIndex ?: return false
        if (!targets.hasBounds(index)) return false
        if (!targets.requestFocusAt(index)) return false
        pendingMaterializedFocusIndex = null
        return true
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

    private var pendingMaterializedFocusIndex: Int? = null
}

// VisualFocusGridRemember
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

// VisualGridDirection
internal enum class VisualGridDirection {
    Left,
    Right,
    Up,
    Down,
}

// VisualGridKeyNavigation
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
    sourceIndex: Int,
    moveFocusTo: (Int) -> Unit,
    onEdgeExit: (VisualGridDirection) -> Unit,
): Boolean {
    if (columns <= 0 || itemCount <= 0 || sourceIndex !in 0 until itemCount) return false
    return handleManagedDpadNavigationKey(key) { direction ->
        val target = visualGridMoveTarget(sourceIndex, itemCount, columns, direction)
        if (target != null) {
            moveFocusTo(target)
        } else {
            onEdgeExit(direction)
        }
    }
}

internal fun handleManagedDpadNavigationKey(
    key: Key,
    ownsDirection: (VisualGridDirection) -> Boolean = { true },
    onDirection: (VisualGridDirection) -> Unit,
): Boolean {
    val direction = key.toVisualGridDirectionOrNull() ?: return false
    if (!ownsDirection(direction)) return false
    onDirection(direction)
    return true
}

// VisualGridMovePolicy
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

// VisualGridPaging
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
