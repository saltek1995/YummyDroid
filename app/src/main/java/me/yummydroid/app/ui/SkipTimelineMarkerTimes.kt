package me.yummydroid.app.ui

import kotlin.math.ceil

private const val MarkerStrideDp = 3f

internal fun List<SkipTimelineMarkerSegment>.timelineAdMarkerTimes(
    durationMs: Long?,
    timelineWidthPx: Int,
    density: Float,
): LongArray {
    val duration = durationMs?.takeIf { it > 0L } ?: return LongArray(0)
    if (isEmpty()) return LongArray(0)
    val effectiveWidthPx = maxOf(1, timelineWidthPx)
    val stridePx = maxOf(1f, MarkerStrideDp * density)
    val strideMs = maxOf(1L, ceil(duration.toDouble() * stridePx / effectiveWidthPx.toDouble()).toLong())
    return flatMap { segment ->
        buildList {
            var timeMs = segment.startMs
            add(timeMs)
            while (timeMs + strideMs < segment.endMs) {
                timeMs += strideMs
                add(timeMs)
            }
            add(segment.endMs)
        }
    }
        .distinct()
        .sorted()
        .toLongArray()
}
