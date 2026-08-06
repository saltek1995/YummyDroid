package me.yummydroid.app.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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

internal fun FocusRequester.requestFocusSafely(): Boolean {
    return runCatching { requestFocus() }.getOrDefault(false)
}

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
    val focusKey: Any? = null,
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
    private val bounds = LinkedHashMap<Int, VisualFocusBounds>()
    private val coordinates = LinkedHashMap<Int, LayoutCoordinates>()
    private val focusedIndexState = mutableIntStateOf(-1)
    private val lastFocusedIndexState = mutableIntStateOf(-1)
    private val lastFocusedKeyState = mutableStateOf<Any?>(null)

    val size: Int get() = requesters.size
    val focusedIndex: Int? get() = focusedIndexState.intValue.takeIf { it in requesters.indices }
    val lastFocusedIndex: Int? get() = lastFocusedIndexState.intValue.takeIf { it in requesters.indices }
    val lastFocusedKey: Any? get() = lastFocusedKeyState.value

    fun requester(index: Int): FocusRequester? = requesters.getOrNull(index)

    fun updateBounds(index: Int, bounds: VisualFocusBounds, coordinates: LayoutCoordinates) {
        if (index in requesters.indices) {
            this.coordinates[index] = coordinates
            if (this.bounds[index] == bounds) return
            this.bounds[index] = bounds
            if (focusedIndexState.intValue == index && bounds.focusKey != null) {
                lastFocusedKeyState.value = bounds.focusKey
            }
        }
    }

    fun clearBounds(index: Int) {
        bounds.remove(index)
        coordinates.remove(index)
        if (focusedIndexState.intValue == index) {
            focusedIndexState.intValue = -1
        }
    }

    fun updateFocusedIndex(index: Int, focused: Boolean) {
        if (index !in requesters.indices) return
        if (focused) {
            focusedIndexState.intValue = index
            lastFocusedIndexState.intValue = index
            lastFocusedKeyState.value = bounds[index]?.focusKey
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
                requester.requestFocusSafely()
            } == true
            exit != null -> exit.requestFocusSafely()
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

    fun requestFirstAvailableFocus(): Boolean {
        val targetIndexes = currentBounds()
            .sortedWith(compareBy<VisualFocusBounds> { it.top }.thenBy { it.left })
            .map { it.index }
            .ifEmpty { bounds.keys.sorted() }
            .ifEmpty { requesters.indices.toList() }
        return targetIndexes.any { targetIndex ->
            requesters.getOrNull(targetIndex)?.let { requester ->
                requester.requestFocusSafely()
            } == true
        }
    }

    fun requestRetainedOrFirstAvailableFocus(): Boolean {
        if (requestLastFocusedFocus()) {
            return true
        }
        listOfNotNull(focusedIndex)
            .distinct()
            .firstOrNull { index -> bounds[index] != null }
            ?.let { index ->
                if (requesters.getOrNull(index)?.requestFocusSafely() == true) {
                    return true
                }
            }
        return requestFirstAvailableFocus()
    }

    fun requestLastFocusedFocus(): Boolean {
        requestFocusByKey(lastFocusedKey)?.let { restored ->
            if (restored) return true
        }
        val index = lastFocusedIndex?.takeIf { retainedIndex -> bounds[retainedIndex] != null }
            ?: return false
        return requestFocusAt(index)
    }

    fun requestFocusByKey(focusKey: Any?): Boolean? {
        if (focusKey == null) return null
        val target = currentBounds().firstOrNull { itemBounds -> itemBounds.focusKey == focusKey }
            ?: return false
        return requestFocusAt(target.index)
    }

    fun requestFocusAt(index: Int): Boolean {
        if (index !in requesters.indices || bounds[index] == null) return false
        return requesters.getOrNull(index)?.requestFocusSafely() == true
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
    focusKey: Any? = null,
): Modifier {
    return then(
        Modifier.composed {
            val requester = state.requester(index) ?: return@composed Modifier
            val resolvedFocusKey = focusKey ?: blockKey?.let { VisualFocusRestoreKey(it, blockEntryIndex) }
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
                            focusKey = resolvedFocusKey,
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
        onEnter = { entry.requestFocusSafely() }
    }.focusGroup()
}

private data class VisualFocusRestoreKey(
    val blockKey: Any,
    val blockEntryIndex: Int,
)
