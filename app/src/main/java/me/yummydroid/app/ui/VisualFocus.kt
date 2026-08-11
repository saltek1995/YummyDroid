package me.yummydroid.app.ui

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent

// DpadFocusPolicy
internal fun InputActionEvent.shouldInitializeFocusBeforePlatformDispatch(
    layerHadPointerInput: Boolean,
    touchInputMode: Boolean,
): Boolean {
    if (action !in DpadFocusActions) return false
    return followsPointerInput || layerHadPointerInput || touchInputMode
}

private val DpadFocusActions = setOf(
    InputAction.Up,
    InputAction.Down,
    InputAction.Left,
    InputAction.Right,
    InputAction.Confirm,
)

// FocusRequesterExtensions
internal fun FocusRequester.requestFocusSafely(): Boolean {
    return runCatching { requestFocus() }.getOrDefault(false)
}

// VisualFocusBounds
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

// VisualFocusGeometry
internal fun VisualFocusBounds.hasUsableSize(): Boolean {
    return left.isFinite() &&
        top.isFinite() &&
        right.isFinite() &&
        bottom.isFinite() &&
        width > 0f &&
        height > 0f
}

internal fun VisualFocusBounds.canNavigate(direction: VisualGridDirection): Boolean {
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

internal fun visualFocusCandidates(
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

internal fun visualFocusComparator(
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

internal fun Collection<VisualFocusBounds>.entryIndexForTargetBlock(
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

// VisualFocusModifiers
internal fun Modifier.visualFocusGridNavigation(
    state: VisualFocusGridState,
): Modifier {
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            return@onPreviewKeyEvent false
        }
        val direction = event.key.toVisualGridDirectionOrNull()
            ?: return@onPreviewKeyEvent false
        state.requestDirectionalFocusFromCurrent(direction)
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
    val configuration = VisualFocusItemConfiguration(
        horizontal = horizontal,
        vertical = vertical,
        leftExit = leftExit,
        rightExit = rightExit,
        upExit = upExit,
        downExit = downExit,
        blockKey = blockKey,
        blockEntryIndex = blockEntryIndex,
        consumeDisabledAxis = consumeDisabledAxis,
        focusKey = focusKey,
    )
    return then(Modifier.visualFocusGridItemModifier(state, index, configuration))
}

internal fun Modifier.visualFocusGridItemIfPresent(
    state: VisualFocusGridState?,
    index: Int,
    blockKey: Any? = null,
    blockEntryIndex: Int = index,
): Modifier {
    if (state == null) return this
    return visualFocusGridItem(
        state = state,
        index = index,
        horizontal = true,
        vertical = true,
        blockKey = blockKey,
        blockEntryIndex = blockEntryIndex,
    )
}

private fun Modifier.visualFocusGridItemModifier(
    state: VisualFocusGridState,
    index: Int,
    configuration: VisualFocusItemConfiguration,
): Modifier {
    return composed {
        val requester = state.requester(index) ?: return@composed Modifier
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
                    index = index,
                    bounds = configuration.toBounds(index, rect),
                    coordinates = coordinates,
                )
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                val direction = event.key.toVisualGridDirectionOrNull()
                    ?: return@onPreviewKeyEvent false
                state.handleItemNavigation(index, direction, configuration)
            }
            .focusProperties {
                applyVisualFocusTargets(state, index, configuration)
            }
    }
}

internal fun Modifier.focusEntryGroup(entry: FocusRequester?): Modifier {
    if (entry == null) return focusGroup()
    return focusProperties {
        onEnter = { entry.requestFocusSafely() }
    }.focusGroup()
}

private fun VisualFocusGridState.handleItemNavigation(
    index: Int,
    direction: VisualGridDirection,
    configuration: VisualFocusItemConfiguration,
): Boolean {
    if (!configuration.canNavigate(direction)) return configuration.consumeDisabledAxis
    return requestFocusTarget(
        index = index,
        direction = direction,
        exit = configuration.exit(direction),
    )
}

private fun FocusProperties.applyVisualFocusTargets(
    state: VisualFocusGridState,
    index: Int,
    configuration: VisualFocusItemConfiguration,
) {
    VisualGridDirection.entries.forEach { direction ->
        if (configuration.canNavigate(direction)) {
            state.focusTarget(index, direction, configuration.exit(direction))
                ?.let { target -> setVisualFocusTarget(direction, target) }
        }
    }
}

private fun FocusProperties.setVisualFocusTarget(
    direction: VisualGridDirection,
    target: FocusRequester,
) {
    when (direction) {
        VisualGridDirection.Left -> left = target
        VisualGridDirection.Right -> right = target
        VisualGridDirection.Up -> up = target
        VisualGridDirection.Down -> down = target
    }
}

private data class VisualFocusItemConfiguration(
    val horizontal: Boolean,
    val vertical: Boolean,
    val leftExit: FocusRequester?,
    val rightExit: FocusRequester?,
    val upExit: FocusRequester?,
    val downExit: FocusRequester?,
    val blockKey: Any?,
    val blockEntryIndex: Int,
    val consumeDisabledAxis: Boolean,
    val focusKey: Any?,
) {
    fun canNavigate(direction: VisualGridDirection): Boolean {
        return when (direction) {
            VisualGridDirection.Left,
            VisualGridDirection.Right -> horizontal
            VisualGridDirection.Up,
            VisualGridDirection.Down -> vertical
        }
    }

    fun exit(direction: VisualGridDirection): FocusRequester? {
        return when (direction) {
            VisualGridDirection.Left -> leftExit
            VisualGridDirection.Right -> rightExit
            VisualGridDirection.Up -> upExit
            VisualGridDirection.Down -> downExit
        }
    }

    fun toBounds(index: Int, rect: Rect): VisualFocusBounds {
        return VisualFocusBounds(
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
            focusKey = focusKey ?: blockKey?.let {
                VisualFocusRestoreKey(it, blockEntryIndex)
            },
        )
    }
}

private data class VisualFocusRestoreKey(
    val blockKey: Any,
    val blockEntryIndex: Int,
)

// VisualFocusRetentionState
internal class VisualFocusRetentionState(private val size: Int) {
    private val focusedIndexState = mutableIntStateOf(-1)
    private val lastFocusedIndexState = mutableIntStateOf(-1)
    private val lastFocusedKeyState = mutableStateOf<Any?>(null)

    val focusedIndex: Int? get() = focusedIndexState.intValue.takeIf(::contains)
    val lastFocusedIndex: Int? get() = lastFocusedIndexState.intValue.takeIf(::contains)
    val lastFocusedKey: Any? get() = lastFocusedKeyState.value

    fun focus(index: Int, focusKey: Any?) {
        focusedIndexState.intValue = index
        lastFocusedIndexState.intValue = index
        lastFocusedKeyState.value = focusKey
    }

    fun clearFocusedIndex(index: Int) {
        if (focusedIndexState.intValue == index) {
            focusedIndexState.intValue = -1
        }
    }

    fun updateLastFocusedKey(focusKey: Any) {
        lastFocusedKeyState.value = focusKey
    }

    private fun contains(index: Int): Boolean = index in 0 until size
}

// VisualFocusTargetRegistry
internal class VisualFocusTargetRegistry(
    size: Int,
    private val allowLoosePerpendicularMatch: Boolean,
) {
    private val requesters = List(size) { FocusRequester() }
    private val storedBounds = LinkedHashMap<Int, VisualFocusBounds>()
    private val coordinates = LinkedHashMap<Int, LayoutCoordinates>()

    val size: Int get() = requesters.size

    fun contains(index: Int): Boolean = index in requesters.indices

    fun requester(index: Int): FocusRequester? = requesters.getOrNull(index)

    fun bounds(index: Int): VisualFocusBounds? = storedBounds[index]

    fun hasBounds(index: Int): Boolean = storedBounds[index] != null

    fun updateBounds(
        index: Int,
        bounds: VisualFocusBounds,
        coordinates: LayoutCoordinates,
    ): Any? {
        if (!contains(index)) return null
        this.coordinates[index] = coordinates
        if (storedBounds[index] == bounds) return null
        storedBounds[index] = bounds
        return bounds.focusKey
    }

    fun clearBounds(index: Int) {
        storedBounds.remove(index)
        coordinates.remove(index)
    }

    fun focusTarget(
        index: Int,
        direction: VisualGridDirection,
        exit: FocusRequester?,
    ): FocusRequester? {
        val target = focusTargetIndex(index, direction)
        return when {
            target != null -> requester(target)
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
            target != null -> requester(target)?.requestFocusSafely() == true
            exit != null -> exit.requestFocusSafely()
            hasBounds(index) -> true
            else -> false
        }
    }

    fun requestFirstAvailableFocus(): Boolean {
        return availableFocusIndexes().any { index ->
            requester(index)?.requestFocusSafely() == true
        }
    }

    fun requestFocusByKey(focusKey: Any?): Boolean? {
        if (focusKey == null) return null
        val target = currentBounds().firstOrNull { bounds -> bounds.focusKey == focusKey }
            ?: return false
        return requestFocusAt(target.index)
    }

    fun requestFocusAt(index: Int): Boolean {
        if (!contains(index) || !hasBounds(index)) return false
        return requester(index)?.requestFocusSafely() == true
    }

    private fun availableFocusIndexes(): List<Int> {
        return currentBounds()
            .sortedWith(compareBy<VisualFocusBounds> { it.top }.thenBy { it.left })
            .map { it.index }
            .ifEmpty { storedBounds.keys.sorted() }
            .ifEmpty { requesters.indices.toList() }
    }

    private fun focusTargetIndex(index: Int, direction: VisualGridDirection): Int? {
        return visualFocusDirectionalTarget(
            bounds = currentBounds(),
            sourceIndex = index,
            direction = direction,
            allowLoosePerpendicularMatch = allowLoosePerpendicularMatch,
        ) ?: fallbackTargetBeforeLayout(index, direction)
    }

    private fun currentBounds(): Collection<VisualFocusBounds> {
        return storedBounds.mapNotNull { (index, bounds) ->
            currentBounds(index, bounds).takeIf { it.hasUsableSize() }
        }
    }

    private fun currentBounds(index: Int, bounds: VisualFocusBounds): VisualFocusBounds {
        val itemCoordinates = coordinates[index] ?: return bounds
        return runCatching {
            val rect = itemCoordinates.boundsInWindow(clipBounds = false)
            bounds.copy(
                left = rect.left,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom,
            )
        }.getOrDefault(bounds)
    }

    private fun fallbackTargetBeforeLayout(index: Int, direction: VisualGridDirection): Int? {
        if (hasBounds(index)) return null
        return when (direction) {
            VisualGridDirection.Left -> (index - 1).takeIf { it >= 0 }
            VisualGridDirection.Right -> (index + 1).takeIf { it < size }
            VisualGridDirection.Up,
            VisualGridDirection.Down -> null
        }
    }
}
