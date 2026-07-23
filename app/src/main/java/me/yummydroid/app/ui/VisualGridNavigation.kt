package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Modifier
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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

internal data class VisualFocusBounds(
    val index: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

internal fun visualFocusDirectionalTarget(
    bounds: Collection<VisualFocusBounds>,
    sourceIndex: Int,
    direction: VisualGridDirection,
): Int? {
    val source = bounds.firstOrNull { it.index == sourceIndex } ?: return null
    val candidates = bounds
        .asSequence()
        .filter { it.index != sourceIndex }
        .filter { candidate -> candidate.isStrictlyInDirectionOf(source, direction) }
        .filter { candidate -> candidate.perpendicularOverlapWith(source, direction) > 0f }
        .toList()
    return candidates.minWithOrNull(
        compareBy<VisualFocusBounds>(
            { it.majorDistanceFrom(source, direction) },
            { it.perpendicularCenterDistanceFrom(source, direction) },
            { it.index },
        ),
    )?.index
}

@Composable
internal fun rememberVisualFocusGridState(
    size: Int,
    key: Any? = Unit,
): VisualFocusGridState {
    return remember(size, key) { VisualFocusGridState(size.coerceAtLeast(0)) }
}

internal class VisualFocusGridState internal constructor(size: Int) {
    private val requesters = List(size) { FocusRequester() }
    private val bounds = mutableStateMapOf<Int, VisualFocusBounds>()
    private val layoutVersionState = mutableIntStateOf(0)

    val size: Int get() = requesters.size
    val layoutVersion: Int get() = layoutVersionState.intValue

    fun requester(index: Int): FocusRequester? = requesters.getOrNull(index)

    fun updateBounds(index: Int, bounds: VisualFocusBounds) {
        if (index in requesters.indices) {
            if (this.bounds[index] == bounds) return
            this.bounds[index] = bounds
            layoutVersionState.intValue++
        }
    }

    fun focusTarget(
        index: Int,
        direction: VisualGridDirection,
        exit: FocusRequester?,
        cancelWhenMissing: Boolean,
    ): FocusRequester? {
        val target = visualFocusDirectionalTarget(bounds.values, index, direction)
            ?: fallbackTargetBeforeLayout(index, direction)
        return when {
            target != null -> requesters.getOrNull(target)
            exit != null -> exit
            cancelWhenMissing -> FocusRequester.Cancel
            else -> null
        }
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

internal fun Modifier.visualFocusGridItem(
    state: VisualFocusGridState,
    index: Int,
    horizontal: Boolean = true,
    vertical: Boolean = false,
    leftExit: FocusRequester? = null,
    rightExit: FocusRequester? = null,
    upExit: FocusRequester? = null,
    downExit: FocusRequester? = null,
    cancelMissingHorizontal: Boolean = true,
    cancelMissingVertical: Boolean = false,
    cancelUp: Boolean = false,
    cancelDown: Boolean = false,
): Modifier {
    val requester = state.requester(index) ?: return this
    val layoutVersion = state.layoutVersion
    return this.focusRequester(requester)
        .onGloballyPositioned { coordinates ->
            val rect = coordinates.boundsInWindow()
            state.updateBounds(
                index,
                VisualFocusBounds(
                    index = index,
                    left = rect.left,
                    top = rect.top,
                    right = rect.right,
                    bottom = rect.bottom,
                ),
            )
        }
        .focusProperties {
            layoutVersion
            if (horizontal) {
                state.focusTarget(index, VisualGridDirection.Left, leftExit, cancelMissingHorizontal)?.let { left = it }
                state.focusTarget(index, VisualGridDirection.Right, rightExit, cancelMissingHorizontal)?.let { right = it }
            }
            if (vertical) {
                state.focusTarget(index, VisualGridDirection.Up, upExit, cancelMissingVertical)?.let { up = it }
                state.focusTarget(index, VisualGridDirection.Down, downExit, cancelMissingVertical)?.let { down = it }
            }
            if (cancelUp) up = FocusRequester.Cancel
            if (cancelDown) down = FocusRequester.Cancel
        }
}

private fun VisualFocusBounds.isStrictlyInDirectionOf(
    source: VisualFocusBounds,
    direction: VisualGridDirection,
): Boolean {
    return when (direction) {
        VisualGridDirection.Left -> centerX < source.centerX
        VisualGridDirection.Right -> centerX > source.centerX
        VisualGridDirection.Up -> centerY < source.centerY
        VisualGridDirection.Down -> centerY > source.centerY
    }
}

private fun VisualFocusBounds.perpendicularOverlapWith(
    source: VisualFocusBounds,
    direction: VisualGridDirection,
): Float {
    return when (direction) {
        VisualGridDirection.Left,
        VisualGridDirection.Right -> overlap(top, bottom, source.top, source.bottom)
        VisualGridDirection.Up,
        VisualGridDirection.Down -> overlap(left, right, source.left, source.right)
    }
}

private fun VisualFocusBounds.majorDistanceFrom(
    source: VisualFocusBounds,
    direction: VisualGridDirection,
): Float {
    return when (direction) {
        VisualGridDirection.Left -> max(0f, source.left - right)
        VisualGridDirection.Right -> max(0f, left - source.right)
        VisualGridDirection.Up -> max(0f, source.top - bottom)
        VisualGridDirection.Down -> max(0f, top - source.bottom)
    }
}

private fun VisualFocusBounds.perpendicularCenterDistanceFrom(
    source: VisualFocusBounds,
    direction: VisualGridDirection,
): Float {
    return when (direction) {
        VisualGridDirection.Left,
        VisualGridDirection.Right -> abs(centerY - source.centerY)
        VisualGridDirection.Up,
        VisualGridDirection.Down -> abs(centerX - source.centerX)
    }
}

private fun overlap(
    firstStart: Float,
    firstEnd: Float,
    secondStart: Float,
    secondEnd: Float,
): Float {
    return min(firstEnd, secondEnd) - max(firstStart, secondStart)
}
