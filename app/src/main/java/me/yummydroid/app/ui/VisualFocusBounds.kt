package me.yummydroid.app.ui

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
