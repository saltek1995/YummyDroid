package me.yummydroid.app.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
