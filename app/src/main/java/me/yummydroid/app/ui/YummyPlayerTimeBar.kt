package me.yummydroid.app.ui

import android.content.Context
import android.util.AttributeSet
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.DefaultTimeBar

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
