package me.yummydroid.app.ui

import android.content.Context
import android.util.AttributeSet
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar
import kotlin.math.ceil
import me.yummydroid.app.data.VideoSkipSegment
import me.yummydroid.app.data.normalizedSkipSegments

// SkipTimelineMarkerSegment
internal data class SkipTimelineMarkerSegment(
    val startMs: Long,
    val endMs: Long,
)

// SkipTimelineMarkerTimes
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

// SkipTimelineSegments
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

// YummyPlayerTimeBar
@UnstableApi
class YummyPlayerTimeBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : DefaultTimeBar(context, attrs, defStyleAttr) {
    private var playerMarkerTimesMs = LongArray(0)
    private var playerPlayedMarkers = BooleanArray(0)
    private var playerMarkerCount = 0
    private var skipMarkerTimesMs = LongArray(0)

    override fun setAdGroupTimesMs(
        adGroupTimesMs: LongArray?,
        playedAdGroups: BooleanArray?,
        adGroupCount: Int,
    ) {
        playerMarkerCount = adGroupCount
            .coerceAtLeast(0)
            .coerceAtMost(adGroupTimesMs?.size ?: 0)
        playerMarkerTimesMs = adGroupTimesMs?.copyOf(playerMarkerCount) ?: LongArray(0)
        playerPlayedMarkers = playedAdGroups?.copyOf(playerMarkerCount) ?: BooleanArray(playerMarkerCount)
        applyMergedMarkers()
    }

    fun setYummySkipMarkerTimes(markerTimesMs: LongArray) {
        if (skipMarkerTimesMs.contentEquals(markerTimesMs)) return
        skipMarkerTimesMs = markerTimesMs
        applyMergedMarkers()
    }

    private fun applyMergedMarkers() {
        if (skipMarkerTimesMs.isEmpty()) {
            super.setAdGroupTimesMs(playerMarkerTimesMs, playerPlayedMarkers, playerMarkerCount)
            return
        }

        val markers = buildList {
            repeat(playerMarkerCount) { index ->
                add(TimelineMarker(playerMarkerTimesMs[index], playerPlayedMarkers.getOrElse(index) { false }))
            }
            skipMarkerTimesMs.forEach { timeMs ->
                add(TimelineMarker(timeMs, false))
            }
        }
            .filter { marker -> marker.timeMs != C.TIME_UNSET }
            .groupBy { marker -> marker.timeMs }
            .map { (timeMs, sameTimeMarkers) ->
                TimelineMarker(timeMs, sameTimeMarkers.any { marker -> marker.played })
            }
            .sortedBy { marker -> marker.timeMs }

        super.setAdGroupTimesMs(
            markers.map { marker -> marker.timeMs }.toLongArray(),
            markers.map { marker -> marker.played }.toBooleanArray(),
            markers.size,
        )
    }
}

private data class TimelineMarker(
    val timeMs: Long,
    val played: Boolean,
)
