package me.yummydroid.app.ui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.core.view.isVisible
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.util.Locale
import me.yummydroid.app.R
import me.yummydroid.app.data.ResolvedSubtitleTrack
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.SourceQuality

// PlayerDebugOverlay
internal data class PlayerDebugToggleResult(
    val hits: List<Long>,
    val shouldToggle: Boolean,
)

internal fun playerDebugToggleResult(
    previousHits: List<Long>,
    nowMs: Long,
    windowMs: Long = PLAYER_DEBUG_OVERLAY_TOGGLE_WINDOW_MS,
    requiredHits: Int = PLAYER_DEBUG_OVERLAY_TOGGLE_REQUIRED_HITS,
): PlayerDebugToggleResult {
    val hits = (previousHits + nowMs).filter { hitMs -> nowMs - hitMs <= windowMs }
    return if (hits.size >= requiredHits) {
        PlayerDebugToggleResult(hits = emptyList(), shouldToggle = true)
    } else {
        PlayerDebugToggleResult(hits = hits, shouldToggle = false)
    }
}

internal fun PlayerView.recordPlayerDebugOverlayPlayPauseHit(binding: PlayerControllerBinding) {
    val previousHits = tagValue<List<Long>>(R.id.yummy_player_debug_overlay_hits).orEmpty()
    val result = playerDebugToggleResult(previousHits, SystemClock.uptimeMillis())
    setTag(R.id.yummy_player_debug_overlay_hits, result.hits)
    if (!result.shouldToggle) return

    setPlayerDebugOverlayEnabled(!isPlayerDebugOverlayEnabled(), binding)
}

@OptIn(UnstableApi::class)
internal fun PlayerView.bindPlayerDebugOverlay(binding: PlayerControllerBinding) {
    renderPlayerDebugOverlay(binding)
    schedulePlayerDebugOverlayUpdate(binding)
}

@OptIn(UnstableApi::class)
private fun PlayerView.setPlayerDebugOverlayEnabled(
    enabled: Boolean,
    binding: PlayerControllerBinding,
) {
    setTag(R.id.yummy_player_debug_overlay_enabled, enabled)
    renderPlayerDebugOverlay(binding)
    schedulePlayerDebugOverlayUpdate(binding)
}

private fun PlayerView.isPlayerDebugOverlayEnabled(): Boolean {
    return tagValue<Boolean>(R.id.yummy_player_debug_overlay_enabled) == true
}

@OptIn(UnstableApi::class)
private fun PlayerView.schedulePlayerDebugOverlayUpdate(binding: PlayerControllerBinding) {
    removeTaggedRunnable(R.id.yummy_player_debug_overlay_update_runnable)
    if (!isPlayerDebugOverlayEnabled()) return

    val runnable = object : Runnable {
        override fun run() {
            if (!isPlayerDebugOverlayEnabled()) {
                clearTagValue(R.id.yummy_player_debug_overlay_update_runnable)
                return
            }
            renderPlayerDebugOverlay(binding)
            postDelayed(this, PLAYER_DEBUG_OVERLAY_UPDATE_MS)
        }
    }
    setTag(R.id.yummy_player_debug_overlay_update_runnable, runnable)
    postDelayed(runnable, PLAYER_DEBUG_OVERLAY_UPDATE_MS)
}

@OptIn(UnstableApi::class)
private fun PlayerView.renderPlayerDebugOverlay(binding: PlayerControllerBinding) {
    val overlay = ensurePlayerDebugOverlayView()
    val enabled = isPlayerDebugOverlayEnabled()
    overlay.isVisible = enabled
    if (!enabled) return
    overlay.text = buildPlayerDebugOverlayText(binding)
    overlay.bringToFront()
}

private fun PlayerView.ensurePlayerDebugOverlayView(): TextView {
    findViewById<TextView>(R.id.yummy_player_debug_overlay)?.let { return it }
    val overlay = TextView(context).apply {
        id = R.id.yummy_player_debug_overlay
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
        includeFontPadding = false
        typeface = Typeface.MONOSPACE
        setTextColor(0xFFF3F6FA.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        setLineSpacing(debugDp(4).toFloat(), 1.08f)
        maxLines = 24
        ellipsize = TextUtils.TruncateAt.END
        setPadding(debugDp(14), debugDp(12), debugDp(14), debugDp(12))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = debugDp(8).toFloat()
            setColor(0xD6111827.toInt())
            setStroke(debugDp(1), 0x66FFB454)
        }
        visibility = View.GONE
    }
    addView(
        overlay,
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START,
        ).apply {
            val margin = debugDp(12)
            setMargins(margin, margin, margin, margin)
        },
    )
    return overlay
}

@OptIn(UnstableApi::class)
private fun PlayerView.buildPlayerDebugOverlayText(binding: PlayerControllerBinding): String {
    val player = binding.player
    val selectedVideoFormats = player.currentTracks.selectedFormats(C.TRACK_TYPE_VIDEO)
    val selectedAudioFormats = player.currentTracks.selectedFormats(C.TRACK_TYPE_AUDIO)
    val media3SubtitleFormats = player.currentTracks.supportedFormats(C.TRACK_TYPE_TEXT)
    val selectedSubtitleFormats = player.currentTracks.selectedFormats(C.TRACK_TYPE_TEXT)
    val videoFormat = player.videoFormat ?: selectedVideoFormats.firstOrNull()
    val audioFormat = selectedAudioFormats.firstOrNull()
    return buildString {
        appendLine("YummyDroid player debug")
        appendLine("state: ${player.playbackState.debugPlaybackState()} playing=${player.isPlaying} ready=${player.playWhenReady}")
        appendLine(
            "time: pos=${player.currentPosition.safeMs()} buf=${player.bufferedPosition.safeMs()} " +
                "dur=${player.duration.safeDurationMs()} buffered=${player.totalBufferedDuration.safeMs()}",
        )
        appendLine()
        appendLine(
            "video: ${videoFormat.debugVideoFormat()} size=${player.videoSize.width}x${player.videoSize.height} " +
                "quality=${binding.selectedQualityKey.orEmpty().ifBlank { "auto" }}",
        )
        appendLine("audio: ${audioFormat.debugAudioFormat()}")
        appendLine()
        appendLine(
            "stream: mime=${binding.stream.mimeType.orEmpty().ifBlank { "unknown" }} " +
                "selected=${binding.stream.selectedVideoHeight.debugHeight()} max=${binding.stream.maxVideoHeight.debugHeight()}",
        )
        appendLine("url: ${binding.stream.url.debugMiddleEllipsized()}")
        appendLine("headers: ${binding.stream.headers.keys.sorted().joinToString().ifBlank { "none" }}")
        appendLine("qualities: ${binding.stream.availableQualities.debugQualities()}")
        appendLine("fallback urls: ${binding.stream.fallbackUrls.size}")
        appendLine(
            "source: ${binding.selectedSourceKey.orEmpty().ifBlank { "unknown" }} " +
                binding.sourceOptions.firstOrNull { it.key == binding.selectedSourceKey }
                    ?.label
                    .orEmpty(),
        )
        appendLine()
        appendLine(
            "subtitles: stream=${binding.stream.subtitles.size} embedded=${binding.stream.embeddedSubtitles.size} " +
                "hasEmbedded=${binding.stream.hasEmbeddedSubtitles} media3=${media3SubtitleFormats.size} " +
                "options=${binding.subtitleOptions.size} selected=${binding.selectedSubtitleKey}",
        )
        appendLine(
            "subtitle options: " + binding.subtitleOptions
                .joinToString(limit = PLAYER_DEBUG_OVERLAY_LIST_LIMIT) { option ->
                    "${option.label}:${option.language.orEmpty().ifBlank { "-" }}:${option.isResolvedTrack}"
                }
                .ifBlank { "none" },
        )
        appendLine(
            "selected text tracks: " + selectedSubtitleFormats
                .joinToString(limit = PLAYER_DEBUG_OVERLAY_LIST_LIMIT, transform = Format::debugTextFormat)
                .ifBlank { "none" },
        )
        appendLine(
            "stream subtitles: " + binding.stream.subtitles
                .joinToString(limit = PLAYER_DEBUG_OVERLAY_LIST_LIMIT, transform = ResolvedSubtitleTrack::debugSubtitleTrack)
                .ifBlank { "none" },
        )
        appendLine(
            "embedded subtitles: " + binding.stream.embeddedSubtitles
                .joinToString(limit = PLAYER_DEBUG_OVERLAY_LIST_LIMIT) { subtitle ->
                    "${subtitle.id.ifBlank { "-" }}:${subtitle.label.ifBlank { "-" }}:" +
                        subtitle.language.orEmpty().ifBlank { "-" }
                }
                .ifBlank { "none" },
        )
    }.trimEnd()
}

@OptIn(UnstableApi::class)
private fun Tracks.selectedFormats(trackType: Int): List<Format> {
    return groups
        .filter { group -> group.type == trackType && group.isSelected }
        .flatMap { group ->
            (0 until group.length)
                .filter { trackIndex -> group.isTrackSelected(trackIndex) }
                .map { trackIndex -> group.getTrackFormat(trackIndex) }
        }
}

@OptIn(UnstableApi::class)
private fun Tracks.supportedFormats(trackType: Int): List<Format> {
    return groups
        .filter { group -> group.type == trackType && group.isSupported }
        .flatMap { group ->
            (0 until group.length)
                .filter { trackIndex -> group.isTrackSupported(trackIndex) }
                .map { trackIndex -> group.getTrackFormat(trackIndex) }
        }
}

private fun Int.debugPlaybackState(): String {
    return when (this) {
        Player.STATE_IDLE -> "idle"
        Player.STATE_BUFFERING -> "buffering"
        Player.STATE_READY -> "ready"
        Player.STATE_ENDED -> "ended"
        else -> toString()
    }
}

private fun Format?.debugVideoFormat(): String {
    val format = this ?: return "unknown"
    val resolution = if (format.width > 0 && format.height > 0) {
        "${format.width}x${format.height}"
    } else {
        "unknown"
    }
    val fps = format.frameRate.takeIf { it > 0f }?.let { "%.2ffps".format(Locale.US, it) }.orEmpty()
    return listOf(
        resolution,
        format.bitrate.debugBitrate(),
        fps,
        format.codecs.orEmpty(),
        format.sampleMimeType.orEmpty(),
    ).filter(String::isNotBlank).joinToString(" ")
}

private fun Format?.debugAudioFormat(): String {
    val format = this ?: return "unknown"
    return listOf(
        format.language.orEmpty(),
        format.channelCount.takeIf { it > 0 }?.let { "${it}ch" }.orEmpty(),
        format.sampleRate.takeIf { it > 0 }?.let { "${it}Hz" }.orEmpty(),
        format.bitrate.debugBitrate(),
        format.codecs.orEmpty(),
        format.sampleMimeType.orEmpty(),
    ).filter(String::isNotBlank).joinToString(" ").ifBlank { "unknown" }
}

private fun Format.debugTextFormat(): String {
    return listOf(
        id.orEmpty().ifBlank { "-" },
        label.orEmpty().ifBlank { "-" },
        language.orEmpty().ifBlank { "-" },
        sampleMimeType.orEmpty().ifBlank { "-" },
    ).joinToString(":")
}

private fun ResolvedSubtitleTrack.debugSubtitleTrack(): String {
    val status = if (isMaterializedSubtitleTrack()) "file" else "remote"
    return listOf(
        label.ifBlank { "-" },
        language.orEmpty().ifBlank { "-" },
        mimeType.orEmpty().ifBlank { "-" },
        status,
        uri.debugMiddleEllipsized(maxChars = 96),
    ).joinToString(":")
}

private fun List<SourceQuality>.debugQualities(): String {
    if (isEmpty()) return "none"
    return joinToString { quality ->
        listOf(
            quality.height.debugHeight(),
            quality.bitrate.debugBitrate(),
        ).filter(String::isNotBlank).joinToString("/")
    }
}

private fun Int?.debugHeight(): String {
    val height = this?.takeIf { it > 0 } ?: return "unknown"
    return "${height}p"
}

private fun Int.debugBitrate(): String {
    if (this <= 0) return ""
    return toLong().debugBitrate()
}

private fun Long.debugBitrate(): String {
    if (this <= 0L) return ""
    return if (this >= 1_000_000L) {
        "%.2fMbps".format(Locale.US, this / 1_000_000.0)
    } else {
        "${(this / 1_000L).coerceAtLeast(1L)}kbps"
    }
}

private fun Long.safeMs(): String {
    return takeIf { it != C.TIME_UNSET && it >= 0L }?.let { "${it}ms" } ?: "unknown"
}

private fun Long.safeDurationMs(): String {
    return takeIf { it != C.TIME_UNSET && it > 0L }?.let { "${it}ms" } ?: "unknown"
}

private fun View.debugDp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
}

private fun String.debugMiddleEllipsized(maxChars: Int = 132): String {
    val value = trim()
    if (value.length <= maxChars) return value
    val edgeLength = ((maxChars - DEBUG_URL_ELLIPSIS.length) / 2).coerceAtLeast(16)
    val head = value.take(edgeLength)
    val tail = value.takeLast(edgeLength)
    return "$head$DEBUG_URL_ELLIPSIS$tail len=${value.length}"
}

private const val DEBUG_URL_ELLIPSIS = "... "
private const val PLAYER_DEBUG_OVERLAY_TOGGLE_REQUIRED_HITS = 5
private const val PLAYER_DEBUG_OVERLAY_TOGGLE_WINDOW_MS = 5_000L
private const val PLAYER_DEBUG_OVERLAY_UPDATE_MS = 1_000L
private const val PLAYER_DEBUG_OVERLAY_LIST_LIMIT = 4
