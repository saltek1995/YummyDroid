package me.yummydroid.app.ui

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.abs

internal data class VideoDisplayInfo(
    val width: Int,
    val height: Int,
    val frameRate: Float,
)

internal tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

internal fun Context.supportsDisplayModeMatching(): Boolean {
    val uiModeManager = getSystemService(UiModeManager::class.java)
    val isTelevision = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    if (isTelevision) return true

    val displayManager = getSystemService(DisplayManager::class.java)
    return displayManager
        ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        ?.isNotEmpty() == true
}

internal fun Activity.applyVideoDisplayMode(enabled: Boolean, video: VideoDisplayInfo?) {
    if (!supportsDisplayModeMatching()) return
    if (!enabled || video == null || video.width <= 0 || video.height <= 0) {
        clearPreferredDisplayMode()
        return
    }

    @Suppress("DEPRECATION")
    val display = windowManager.defaultDisplay ?: return
    val targetMode = display.supportedModes
        .filter { mode -> mode.physicalWidth > 0 && mode.physicalHeight > 0 }
        .minByOrNull { mode -> mode.displayModeScore(video) }

    val targetModeId = targetMode?.modeId ?: 0
    if (window.attributes.preferredDisplayModeId == targetModeId) return
    window.attributes = window.attributes.apply {
        preferredDisplayModeId = targetModeId
    }
}

internal fun Activity.clearPreferredDisplayMode() {
    if (window.attributes.preferredDisplayModeId == 0) return
    window.attributes = window.attributes.apply {
        preferredDisplayModeId = 0
    }
}

internal fun android.view.Display.Mode.displayModeScore(video: VideoDisplayInfo): Float {
    val modeLongSide = maxOf(physicalWidth, physicalHeight)
    val modeShortSide = minOf(physicalWidth, physicalHeight)
    val videoLongSide = maxOf(video.width, video.height)
    val videoShortSide = minOf(video.width, video.height)
    val resolutionPenalty = when {
        modeLongSide >= videoLongSide && modeShortSide >= videoShortSide ->
            (modeLongSide - videoLongSide) + (modeShortSide - videoShortSide)
        else ->
            100_000 + abs(modeLongSide - videoLongSide) + abs(modeShortSide - videoShortSide)
    }
    return resolutionPenalty + refreshRatePenalty(refreshRate, video.frameRate)
}

internal fun refreshRatePenalty(refreshRate: Float, frameRate: Float): Float {
    if (refreshRate <= 0f || frameRate <= 0f) return 0f
    val candidates = listOf(frameRate, frameRate * 2f, frameRate * 3f, frameRate / 2f)
    return candidates.minOf { abs(refreshRate - it) } * 100f
}

@OptIn(UnstableApi::class)
internal fun Player.currentVideoDisplayInfo(): VideoDisplayInfo? {
    (this as? ExoPlayer)?.videoFormat
        ?.takeIf { format -> format.width > 0 || format.height > 0 }
        ?.let { format ->
            return VideoDisplayInfo(
                width = format.width,
                height = format.height,
                frameRate = format.frameRate,
            )
        }

    return currentTracks.groups
        .asSequence()
        .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
        .flatMap { group ->
            (0 until group.length)
                .asSequence()
                .filter { trackIndex -> group.isTrackSelected(trackIndex) }
                .map { trackIndex -> group.getTrackFormat(trackIndex) }
        }
        .firstOrNull { format -> format.width > 0 || format.height > 0 }
        ?.let { format ->
            VideoDisplayInfo(
                width = format.width,
                height = format.height,
                frameRate = format.frameRate,
            )
        }
}

internal fun VideoSize.toVideoDisplayInfo(): VideoDisplayInfo? {
    if (width <= 0 || height <= 0) return null
    return VideoDisplayInfo(width = width, height = height, frameRate = 0f)
}
