package me.yummydroid.app.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
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

internal fun visualGridActivePageLocalIndex(
    activePage: Boolean,
    localIndex: Int,
    activeTotal: Int,
): Boolean {
    return activePage && localIndex in 0 until activeTotal
}

internal data class VisualFocusBounds(
    val index: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val blockKey: Any? = null,
    val blockEntryIndex: Int = index,
    val horizontal: Boolean = true,
    val vertical: Boolean = true,
    val consumeDisabledAxis: Boolean = false,
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

internal fun visualFocusDirectionalTarget(
    bounds: Collection<VisualFocusBounds>,
    sourceIndex: Int,
    direction: VisualGridDirection,
    allowLoosePerpendicularMatch: Boolean = false,
): Int? {
    val usableBounds = bounds.filter { it.hasUsableSize() }
    val source = usableBounds.firstOrNull { it.index == sourceIndex } ?: return null
    val candidates = visualFocusCandidates(
        bounds = usableBounds,
        source = source,
        direction = direction,
        allowLoosePerpendicularMatch = allowLoosePerpendicularMatch,
    )
    val target = candidates.minWithOrNull(
        visualFocusComparator(
            bounds = usableBounds,
            source = source,
            direction = direction,
        ),
    ) ?: return null
    return usableBounds.entryIndexForTargetBlock(source, target, direction) ?: target.index
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
    private val bounds = mutableStateMapOf<Int, VisualFocusBounds>()
    private val coordinates = mutableStateMapOf<Int, LayoutCoordinates>()
    private val layoutVersionState = mutableIntStateOf(0)
    private val focusedIndexState = mutableIntStateOf(-1)

    val size: Int get() = requesters.size
    val layoutVersion: Int get() = layoutVersionState.intValue
    val focusedIndex: Int? get() = focusedIndexState.intValue.takeIf { it in requesters.indices }

    fun requester(index: Int): FocusRequester? = requesters.getOrNull(index)

    fun updateBounds(index: Int, bounds: VisualFocusBounds, coordinates: LayoutCoordinates) {
        if (index in requesters.indices) {
            this.coordinates[index] = coordinates
            if (this.bounds[index] == bounds) return
            this.bounds[index] = bounds
            layoutVersionState.intValue++
        }
    }

    fun clearBounds(index: Int) {
        val removedBounds = bounds.remove(index) != null
        val removedCoordinates = coordinates.remove(index) != null
        if (focusedIndexState.intValue == index) {
            focusedIndexState.intValue = -1
        }
        if (removedBounds || removedCoordinates) {
            layoutVersionState.intValue++
        }
    }

    fun updateFocusedIndex(index: Int, focused: Boolean) {
        if (index !in requesters.indices) return
        if (focused) {
            focusedIndexState.intValue = index
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
            target != null -> requesters.getOrNull(target)?.let { requester ->
                runCatching { requester.requestFocus() }.getOrDefault(false)
            } ?: false
            exit != null -> runCatching { exit.requestFocus() }.getOrDefault(false)
            bounds[index] != null -> true
            else -> false
        }
    }

    fun requestDirectionalFocusFromCurrent(
        direction: VisualGridDirection,
    ): Boolean {
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

    private fun focusTargetIndex(index: Int, direction: VisualGridDirection): Int? {
        return visualFocusDirectionalTarget(
            bounds = currentBounds(),
            sourceIndex = index,
            direction = direction,
            allowLoosePerpendicularMatch = allowLoosePerpendicularMatch,
        )
            ?: fallbackTargetBeforeLayout(index, direction)
    }

    private fun currentBounds(): Collection<VisualFocusBounds> {
        return bounds.mapNotNull { (index, storedBounds) ->
            val itemCoordinates = coordinates[index]
            if (itemCoordinates == null) {
                storedBounds
            } else {
                runCatching {
                    val rect = itemCoordinates.boundsInWindow(clipBounds = false)
                    storedBounds.copy(
                        left = rect.left,
                        top = rect.top,
                        right = rect.right,
                        bottom = rect.bottom,
                    )
                }.getOrDefault(storedBounds)
            }
                .takeIf { it.hasUsableSize() }
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

internal fun Modifier.visualFocusGridNavigation(
    state: VisualFocusGridState,
): Modifier {
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            return@onPreviewKeyEvent false
        }
        when (event.key) {
            Key.DirectionLeft -> state.requestDirectionalFocusFromCurrent(
                direction = VisualGridDirection.Left,
            )
            Key.DirectionRight -> state.requestDirectionalFocusFromCurrent(
                direction = VisualGridDirection.Right,
            )
            Key.DirectionUp -> state.requestDirectionalFocusFromCurrent(
                direction = VisualGridDirection.Up,
            )
            Key.DirectionDown -> state.requestDirectionalFocusFromCurrent(
                direction = VisualGridDirection.Down,
            )
            else -> false
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
    blockKey: Any? = null,
    blockEntryIndex: Int = index,
    consumeDisabledAxis: Boolean = false,
): Modifier {
    return then(
        Modifier.composed {
            val requester = state.requester(index) ?: return@composed Modifier
            val layoutVersion = state.layoutVersion
            DisposableEffect(state, index) {
                onDispose { state.clearBounds(index) }
            }
            Modifier
                .focusRequester(requester)
                .onFocusChanged { focusState ->
                    state.updateFocusedIndex(
                        index = index,
                        focused = focusState.isFocused || focusState.hasFocus,
                    )
                }
                .onGloballyPositioned { coordinates ->
                    val rect = coordinates.boundsInWindow(clipBounds = false)
                    state.updateBounds(
                        index,
                        VisualFocusBounds(
                            index = index,
                            left = rect.left,
                            top = rect.top,
                            right = rect.right,
                            bottom = rect.bottom,
                            blockKey = blockKey,
                            blockEntryIndex = blockEntryIndex,
                            horizontal = horizontal,
                            vertical = vertical,
                            consumeDisabledAxis = consumeDisabledAxis,
                        ),
                        coordinates,
                    )
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    when (event.key) {
                        Key.DirectionLeft -> if (horizontal) {
                            state.requestFocusTarget(
                                index = index,
                                direction = VisualGridDirection.Left,
                                exit = leftExit,
                            )
                        } else {
                            consumeDisabledAxis
                        }
                        Key.DirectionRight -> if (horizontal) {
                            state.requestFocusTarget(
                                index = index,
                                direction = VisualGridDirection.Right,
                                exit = rightExit,
                            )
                        } else {
                            consumeDisabledAxis
                        }
                        Key.DirectionUp -> if (vertical) {
                            state.requestFocusTarget(
                                index = index,
                                direction = VisualGridDirection.Up,
                                exit = upExit,
                            )
                        } else {
                            consumeDisabledAxis
                        }
                        Key.DirectionDown -> if (vertical) {
                            state.requestFocusTarget(
                                index = index,
                                direction = VisualGridDirection.Down,
                                exit = downExit,
                            )
                        } else {
                            consumeDisabledAxis
                        }
                        else -> false
                    }
                }
                .focusProperties {
                    layoutVersion
                    if (horizontal) {
                        state.focusTarget(index, VisualGridDirection.Left, leftExit)?.let { left = it }
                        state.focusTarget(index, VisualGridDirection.Right, rightExit)?.let { right = it }
                    }
                    if (vertical) {
                        state.focusTarget(index, VisualGridDirection.Up, upExit)?.let { up = it }
                        state.focusTarget(index, VisualGridDirection.Down, downExit)?.let { down = it }
                    }
                }
        },
    )
}

internal fun Modifier.focusEntryGroup(entry: FocusRequester?): Modifier {
    if (entry == null) return focusGroup()
    return focusProperties {
        onEnter = { entry.requestFocus() }
    }.focusGroup()
}

private fun VisualFocusBounds.hasUsableSize(): Boolean {
    return left.isFinite() &&
        top.isFinite() &&
        right.isFinite() &&
        bottom.isFinite() &&
        width > 0f &&
        height > 0f
}

private fun VisualFocusBounds.canNavigate(direction: VisualGridDirection): Boolean {
    return when (direction) {
        VisualGridDirection.Left,
        VisualGridDirection.Right -> horizontal
        VisualGridDirection.Up,
        VisualGridDirection.Down -> vertical
    }
}

private fun VisualFocusBounds.isDirectionallyReachableFrom(
    source: VisualFocusBounds,
    direction: VisualGridDirection,
): Boolean {
    return when (direction) {
        VisualGridDirection.Left -> right <= source.left
        VisualGridDirection.Right -> left >= source.right
        VisualGridDirection.Up -> top < source.top
        VisualGridDirection.Down -> bottom > source.bottom
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
        VisualGridDirection.Up -> if (bottom <= source.top) {
            source.top - bottom
        } else {
            source.top - top
        }
        VisualGridDirection.Down -> if (top >= source.bottom) {
            top - source.bottom
        } else {
            bottom - source.bottom
        }
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

private fun VisualFocusBounds.perpendicularGapFrom(
    source: VisualFocusBounds,
    direction: VisualGridDirection,
): Float {
    return when (direction) {
        VisualGridDirection.Left,
        VisualGridDirection.Right -> gap(top, bottom, source.top, source.bottom)
        VisualGridDirection.Up,
        VisualGridDirection.Down -> gap(left, right, source.left, source.right)
    }
}

private fun visualFocusCandidates(
    bounds: Collection<VisualFocusBounds>,
    source: VisualFocusBounds,
    direction: VisualGridDirection,
    allowLoosePerpendicularMatch: Boolean,
): List<VisualFocusBounds> {
    val directionalCandidates = bounds
        .asSequence()
        .filter { it.index != source.index }
        .filter { candidate -> candidate.isDirectionallyReachableFrom(source, direction) }
        .toList()
    if (direction == VisualGridDirection.Up || direction == VisualGridDirection.Down) {
        return directionalCandidates.nearestVerticalLayer(source, direction)
    }
    val overlappingCandidates = directionalCandidates
        .filter { candidate -> candidate.perpendicularOverlapWith(source, direction) > 0f }
    if (!allowLoosePerpendicularMatch) return overlappingCandidates
    return (overlappingCandidates.ifEmpty { directionalCandidates })
        .nearestHorizontalLayer(source, direction)
}

private fun List<VisualFocusBounds>.nearestVerticalLayer(
    source: VisualFocusBounds,
    direction: VisualGridDirection,
): List<VisualFocusBounds> {
    val usesStructuredBlocks = source.blockKey != null || any { candidate -> candidate.blockKey != null }
    val seed = if (usesStructuredBlocks) {
        minWithOrNull(
            compareBy<VisualFocusBounds>(
                { it.majorDistanceFrom(source, direction) + it.perpendicularGapFrom(source, direction) },
                { it.majorDistanceFrom(source, direction) },
                { it.perpendicularCenterDistanceFrom(source, direction) },
                { it.index },
            ),
        )
    } else {
        minWithOrNull(
            compareBy<VisualFocusBounds>(
                { it.majorDistanceFrom(source, direction) },
                { it.perpendicularGapFrom(source, direction) },
                { it.perpendicularCenterDistanceFrom(source, direction) },
                { it.index },
            ),
        )
    }
        ?: return emptyList()
    return filter { candidate -> candidate.isSameVerticalLayerAs(seed) }
}

private fun VisualFocusBounds.isSameVerticalLayerAs(other: VisualFocusBounds): Boolean {
    if (overlap(top, bottom, other.top, other.bottom) > 0f) return true
    val layerTolerance = max(height, other.height) * 0.35f
    return abs(centerY - other.centerY) <= layerTolerance
}

private fun List<VisualFocusBounds>.nearestHorizontalLayer(
    source: VisualFocusBounds,
    direction: VisualGridDirection,
): List<VisualFocusBounds> {
    val seed = minWithOrNull(
        compareBy<VisualFocusBounds>(
            { it.perpendicularGapFrom(source, direction) },
            { it.perpendicularCenterDistanceFrom(source, direction) },
            { it.majorDistanceFrom(source, direction) },
            { it.index },
        ),
    ) ?: return emptyList()
    return filter { candidate -> candidate.isSameHorizontalLayerAs(seed) }
}

private fun VisualFocusBounds.isSameHorizontalLayerAs(other: VisualFocusBounds): Boolean {
    if (overlap(top, bottom, other.top, other.bottom) > 0f) return true
    val layerTolerance = max(height, other.height) * 0.35f
    return abs(centerY - other.centerY) <= layerTolerance
}

private fun visualFocusComparator(
    bounds: Collection<VisualFocusBounds>,
    source: VisualFocusBounds,
    direction: VisualGridDirection,
): Comparator<VisualFocusBounds> {
    val usesStructuredBlocks = source.blockKey != null || bounds.any { candidate -> candidate.blockKey != null }
    if (
        usesStructuredBlocks &&
        (direction == VisualGridDirection.Up || direction == VisualGridDirection.Down)
    ) {
        return compareBy<VisualFocusBounds>(
            { it.majorDistanceFrom(source, direction) + it.perpendicularGapFrom(source, direction) },
            { it.majorDistanceFrom(source, direction) },
            { candidate ->
                if (candidate.isReciprocalVisualTargetOf(source, bounds, direction)) 0 else 1
            },
            { it.perpendicularCenterDistanceFrom(source, direction) },
            { it.index },
        )
    }
    return compareBy<VisualFocusBounds>(
        { it.majorDistanceFrom(source, direction) },
        { it.perpendicularGapFrom(source, direction) },
        { candidate ->
            if (candidate.isReciprocalVisualTargetOf(source, bounds, direction)) 0 else 1
        },
        { it.perpendicularCenterDistanceFrom(source, direction) },
        { it.index },
    )
}

private fun VisualFocusBounds.isReciprocalVisualTargetOf(
    source: VisualFocusBounds,
    bounds: Collection<VisualFocusBounds>,
    direction: VisualGridDirection,
): Boolean {
    val reverseDirection = direction.opposite()
    val reverseCandidates = visualFocusCandidates(
        bounds = bounds,
        source = this,
        direction = reverseDirection,
        allowLoosePerpendicularMatch = true,
    )
    val reverseTarget = reverseCandidates.minWithOrNull(
        compareBy<VisualFocusBounds>(
            { it.majorDistanceFrom(this, reverseDirection) },
            { it.perpendicularGapFrom(this, reverseDirection) },
            { it.perpendicularCenterDistanceFrom(this, reverseDirection) },
            { it.index },
        ),
    )
    return reverseTarget?.index == source.index
}

private fun Collection<VisualFocusBounds>.entryIndexForTargetBlock(
    source: VisualFocusBounds,
    target: VisualFocusBounds,
    direction: VisualGridDirection,
): Int? {
    if (direction == VisualGridDirection.Left || direction == VisualGridDirection.Right) return null
    val targetBlockKey = target.blockKey ?: return null
    if (source.blockKey == targetBlockKey) return null
    val entryIndex = target.blockEntryIndex
    firstOrNull { candidate ->
        candidate.index == entryIndex && candidate.blockKey == targetBlockKey
    }?.let { return it.index }
    return filter { candidate -> candidate.blockKey == targetBlockKey }
        .minWithOrNull(
            compareBy<VisualFocusBounds>(
                { it.blockEntryIndex },
                { it.top },
                { it.left },
                { it.index },
            ),
        )
        ?.index
}

private fun VisualGridDirection.opposite(): VisualGridDirection = when (this) {
    VisualGridDirection.Left -> VisualGridDirection.Right
    VisualGridDirection.Right -> VisualGridDirection.Left
    VisualGridDirection.Up -> VisualGridDirection.Down
    VisualGridDirection.Down -> VisualGridDirection.Up
}

private fun overlap(
    firstStart: Float,
    firstEnd: Float,
    secondStart: Float,
    secondEnd: Float,
): Float {
    return min(firstEnd, secondEnd) - max(firstStart, secondStart)
}

private fun gap(
    firstStart: Float,
    firstEnd: Float,
    secondStart: Float,
    secondEnd: Float,
): Float {
    return when {
        firstEnd < secondStart -> secondStart - firstEnd
        secondEnd < firstStart -> firstStart - secondEnd
        else -> 0f
    }
}
