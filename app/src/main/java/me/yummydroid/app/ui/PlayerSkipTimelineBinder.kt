package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import me.yummydroid.app.data.VideoVariant

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
