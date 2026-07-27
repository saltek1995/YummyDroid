package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import me.yummydroid.app.data.VideoSkipSegment
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.normalizedSkipSegments
import kotlin.math.ceil

private const val SKIP_MARKER_COLOR = 0xD83F8E49.toInt()
private const val MARKER_STRIDE_DP = 3f

@OptIn(UnstableApi::class)
internal fun PlayerView.bindSkipTimelineMarkers(
    player: ExoPlayer,
    currentVideo: VideoVariant,
) {
    val timeBar = findViewById<YummyPlayerTimeBar>(Media3R.id.exo_progress) ?: return
    val durationMs = resolvedPlaybackDurationMs(
        playerDurationMs = player.duration,
        contentDurationMs = player.contentDuration,
        metadataDurationSeconds = currentVideo.durationSeconds,
    )
    val segments = currentVideo.skipSegments.timelineMarkerSegments(durationMs)
    if (segments.isNotEmpty() && timeBar.width <= 0) {
        timeBar.post { bindSkipTimelineMarkers(player, currentVideo) }
    }
    timeBar.setYummySkipMarkerTimes(
        markerTimesMs = segments.timelineAdMarkerTimes(
            durationMs = durationMs,
            timelineWidthPx = timeBar.width,
            density = resources.displayMetrics.density,
        ),
    )
}

internal data class SkipTimelineMarkerSegment(
    val startMs: Long,
    val endMs: Long,
)

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

internal fun List<SkipTimelineMarkerSegment>.timelineAdMarkerTimes(
    durationMs: Long?,
    timelineWidthPx: Int,
    density: Float,
): LongArray {
    val duration = durationMs?.takeIf { it > 0L } ?: return LongArray(0)
    if (isEmpty()) return LongArray(0)
    val effectiveWidthPx = maxOf(1, timelineWidthPx)
    val stridePx = maxOf(1f, MARKER_STRIDE_DP * density)
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
