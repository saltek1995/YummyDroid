package me.yummydroid.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as Media3R
import me.yummydroid.app.R
import me.yummydroid.app.data.VideoSkipKind
import me.yummydroid.app.data.VideoSkipSegment
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.normalizedSkipSegments

private const val OPENING_MARKER_COLOR = 0xD8FFB454.toInt()
private const val ENDING_MARKER_COLOR = 0xD85DE1E6.toInt()

@OptIn(UnstableApi::class)
internal fun PlayerView.bindSkipTimelineMarkers(
    player: ExoPlayer,
    currentVideo: VideoVariant,
) {
    val timeBar = findViewById<DefaultTimeBar>(Media3R.id.exo_progress) ?: return
    val markerView = timeBar.ensureSkipTimelineMarkerView() ?: return
    val durationMs = resolvedPlaybackDurationMs(
        playerDurationMs = player.duration,
        contentDurationMs = player.contentDuration,
        metadataDurationSeconds = currentVideo.durationSeconds,
    )
    markerView.setTimeline(
        segments = currentVideo.skipSegments.timelineMarkerSegments(durationMs),
        durationMs = durationMs,
    )
}

@OptIn(UnstableApi::class)
private fun DefaultTimeBar.ensureSkipTimelineMarkerView(): SkipTimelineMarkerView? {
    val parentView = parent as? ViewGroup ?: return null
    parentView.findViewById<SkipTimelineMarkerView>(R.id.yummy_player_skip_timeline_markers)
        ?.let { return it }

    if (parentView is FrameLayout) {
        return SkipTimelineMarkerView(context).also { markerView ->
            parentView.addView(
                markerView,
                parentView.indexOfChild(this) + 1,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    val originalLayoutParams = layoutParams
    val originalIndex = parentView.indexOfChild(this)
    parentView.removeView(this)

    val wrapper = FrameLayout(context).apply {
        layoutParams = originalLayoutParams
        clipChildren = false
        clipToPadding = false
        isFocusable = false
        isClickable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    parentView.addView(wrapper, originalIndex)

    wrapper.addView(
        this,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ),
    )
    return SkipTimelineMarkerView(context).also { markerView ->
        wrapper.addView(
            markerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }
}

internal data class SkipTimelineMarkerSegment(
    val kind: VideoSkipKind,
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
                kind = segment.kind,
                startMs = startMs,
                endMs = endMs,
            )
        }
}

internal class SkipTimelineMarkerView(
    context: Context,
) : View(context) {
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val markerRect = RectF()
    private var durationMs: Long = C.TIME_UNSET
    private var segments: List<SkipTimelineMarkerSegment> = emptyList()

    init {
        id = R.id.yummy_player_skip_timeline_markers
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    fun setTimeline(
        segments: List<SkipTimelineMarkerSegment>,
        durationMs: Long?,
    ) {
        val nextDurationMs = durationMs?.takeIf { it > 0L } ?: C.TIME_UNSET
        if (this.durationMs == nextDurationMs && this.segments == segments) return
        this.durationMs = nextDurationMs
        this.segments = segments
        visibility = if (nextDurationMs > 0L && segments.isNotEmpty()) VISIBLE else GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val duration = durationMs.takeIf { it > 0L } ?: return
        if (segments.isEmpty() || width <= 0 || height <= 0) return

        val trackBounds = timelineTrackBounds(width = width, height = height)
        if (trackBounds.width() <= 0f || trackBounds.height() <= 0f) return
        val radius = trackBounds.height() / 2f
        segments.forEach { segment ->
            val left = trackBounds.left + trackBounds.width() * (segment.startMs.toFloat() / duration.toFloat())
            val right = trackBounds.left + trackBounds.width() * (segment.endMs.toFloat() / duration.toFloat())
            markerRect.set(
                left,
                trackBounds.top,
                maxOf(right, left + minimumMarkerWidthPx()),
                trackBounds.bottom,
            )
            markerPaint.color = segment.markerColor()
            canvas.drawRoundRect(markerRect, radius, radius, markerPaint)
        }
    }

    private fun timelineTrackBounds(width: Int, height: Int): RectF {
        val verticalInset = maxOf(0f, (height - markerHeightPx(height)) / 2f)
        val horizontalInset = maxOf(dp(4f), height * 0.45f)
        return RectF(
            horizontalInset,
            verticalInset,
            width - horizontalInset,
            height - verticalInset,
        )
    }

    private fun markerHeightPx(height: Int): Float {
        return minOf(dp(7f), maxOf(dp(4f), height * 0.42f))
    }

    private fun minimumMarkerWidthPx(): Float = dp(2f)

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }
}

private fun SkipTimelineMarkerSegment.markerColor(): Int {
    return when (kind) {
        VideoSkipKind.Opening -> OPENING_MARKER_COLOR
        VideoSkipKind.Ending -> ENDING_MARKER_COLOR
    }
}
