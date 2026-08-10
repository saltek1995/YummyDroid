package me.yummydroid.app.ui

import me.yummydroid.app.data.VideoSkipSegment
import me.yummydroid.app.data.normalizedSkipSegments

internal fun List<VideoSkipSegment>.timelineMarkerSegments(durationMs: Long?): List<SkipTimelineMarkerSegment> {
    val duration = durationMs?.takeIf { it > 0L } ?: return emptyList()
    return normalizedSkipSegments()
        .mapNotNull { segment ->
            val startMs = segment.startMs.coerceIn(0L, duration)
            val endMs = segment.endMs.coerceIn(0L, duration)
            if (endMs <= startMs) return@mapNotNull null
            SkipTimelineMarkerSegment(
                startMs = startMs,
                endMs = endMs,
            )
        }
}
