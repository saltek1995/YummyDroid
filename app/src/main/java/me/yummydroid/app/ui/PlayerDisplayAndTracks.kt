package me.yummydroid.app.ui

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.type
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import java.util.Locale
import kotlin.math.abs
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.PlayerDecoderMode
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.selectForPreferredQuality

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
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !supportsDisplayModeMatching()) return
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
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
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

@OptIn(UnstableApi::class)
internal fun ExoPlayer.selectQuality(option: QualityOption) {
    val group = option.group ?: return
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        .setMaxVideoBitrate(Int.MAX_VALUE)
        .addOverride(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        .build()
}

@OptIn(UnstableApi::class)
internal fun ExoPlayer.selectSubtitle(option: SubtitleOption) {
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .addOverride(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex))
        .build()
}

@OptIn(UnstableApi::class)
internal fun ExoPlayer.disableSubtitles() {
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        .build()
}

internal fun List<QualityOption>.preferredOption(preferredQuality: PreferredQuality): QualityOption? {
    return takeIf { preferredQuality.height != null }?.selectForPreferredQuality(
        preferredQuality = preferredQuality,
        height = { it.height },
        bitrate = { it.bitrate },
    )
}

@OptIn(UnstableApi::class)
internal fun PlayerDecoderMode.mediaCodecSelector(): MediaCodecSelector {
    return when (this) {
        PlayerDecoderMode.Auto -> MediaCodecSelector.DEFAULT
        PlayerDecoderMode.Hardware -> MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val defaults = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            )
            defaults.filter { it.hardwareAccelerated }.ifEmpty { defaults }
        }
        PlayerDecoderMode.Software -> MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val defaults = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            )
            defaults.filter { it.softwareOnly }.ifEmpty { defaults }
        }
    }
}

@OptIn(UnstableApi::class)
internal fun PlayerBufferPreset.toLoadControl(): DefaultLoadControl {
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(minBufferMs, maxBufferMs, playbackBufferMs, rebufferMs)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
}

@OptIn(UnstableApi::class)
internal fun PlayerBufferPreset.toRecoveryPrebufferLoadControl(): DefaultLoadControl {
    val targetBufferMs = recoveryPrebufferTargetMs().toInt()
    val resolvedMinBufferMs = maxOf(minBufferMs, targetBufferMs)
    val resolvedMaxBufferMs = maxOf(maxBufferMs, resolvedMinBufferMs)
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            resolvedMinBufferMs,
            resolvedMaxBufferMs,
            targetBufferMs,
            maxOf(rebufferMs, targetBufferMs),
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
}

internal fun PlayerBufferPreset.recoveryPrebufferTargetMs(): Long {
    return maxOf(PLAYBACK_RECOVERY_PREBUFFER_MIN_MS, switchFallbackThresholdMs)
}

@OptIn(UnstableApi::class)
internal fun Player.currentQualityKey(): String? {
    (this as? ExoPlayer)?.videoFormat
        ?.takeIf { format -> format.width > 0 || format.height > 0 }
        ?.let { format ->
            return "${format.height}:${format.bitrate}:${format.qualityLabel()}"
        }

    return currentTracks
        .groups
        .asSequence()
        .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
        .flatMap { group ->
            (0 until group.length)
                .asSequence()
                .filter { trackIndex -> group.isTrackSelected(trackIndex) }
                .map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    "${format.height}:${format.bitrate}:${format.qualityLabel()}"
                }
        }
        .firstOrNull()
}

internal data class QualityOption(
    val group: Tracks.Group?,
    val trackIndex: Int,
    val label: String,
    val height: Int,
    val bitrate: Int,
    val key: String,
    val localFile: OfflineVideoFile? = null,
    val preferredQuality: PreferredQuality? = null,
)

internal data class SubtitleOption(
    val group: Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val selectionFlags: Int,
    val key: String,
    val isResolvedTrack: Boolean = false,
)

@OptIn(UnstableApi::class)
internal fun Tracks.videoQualityOptions(): List<QualityOption> {
    return groups
        .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }
        .flatMap { group ->
            (0 until group.length)
                .filter { trackIndex -> group.isTrackSupported(trackIndex) }
                .map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    QualityOption(
                        group = group,
                        trackIndex = trackIndex,
                        label = format.qualityLabel(),
                        height = format.height,
                        bitrate = format.bitrate,
                        key = "${format.height}:${format.bitrate}:${format.qualityLabel()}",
                        preferredQuality = PreferredQuality.fromHeight(format.height),
                    )
                }
        }
        .sortedWith(
            compareByDescending<QualityOption> { it.height.takeIf { height -> height > 0 } ?: 0 }
                .thenByDescending { it.bitrate.takeIf { bitrate -> bitrate > 0 } ?: 0 }
                .thenBy { it.label },
        )
        .distinctBy { it.qualityOptionIdentity() }
}

@OptIn(UnstableApi::class)
internal fun Tracks.subtitleOptions(
    texts: PlayerControlTexts,
    resolvedSubtitleLabels: Set<String>? = null,
): List<SubtitleOption> {
    val normalizedResolvedLabels = resolvedSubtitleLabels.orEmpty()
        .map { it.normalizedSubtitleIdentityToken() }
        .filter { it.isNotBlank() }
        .toSet()
    val options = groups
        .filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
        .flatMap { group ->
            (0 until group.length)
                .filter { trackIndex -> group.isTrackSupported(trackIndex) }
                .map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    val label = format.subtitleLabel(texts, trackIndex)
                    SubtitleOption(
                        group = group,
                        trackIndex = trackIndex,
                        label = label,
                        language = format.language,
                        selectionFlags = format.selectionFlags,
                        key = "${format.id.orEmpty()}:${format.language.orEmpty()}:${format.label.orEmpty()}:$trackIndex",
                        isResolvedTrack = format.matchesResolvedSubtitleLabels(
                            label = label,
                            resolvedSubtitleLabels = normalizedResolvedLabels,
                        ),
                    )
                }
        }
        .distinctBy { it.subtitleOptionIdentity() }
    val visibleOptions = if (resolvedSubtitleLabels == null) {
        options
    } else {
        options.filter { option -> option.isResolvedTrack }
    }
    return visibleOptions
        .sortedWith(compareByDescending<SubtitleOption> { it.isResolvedTrack }.thenBy { it.label })
}

internal fun List<SubtitleOption>.defaultSubtitleOption(): SubtitleOption? {
    return firstOrNull { option -> (option.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0 }
        ?: firstOrNull()
}

@OptIn(UnstableApi::class)
internal fun Tracks.currentSubtitleKey(): String? {
    return groups
        .asSequence()
        .filter { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
        .flatMap { group ->
            (0 until group.length)
                .asSequence()
                .filter { trackIndex -> group.isTrackSelected(trackIndex) }
                .map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    "${format.id.orEmpty()}:${format.language.orEmpty()}:${format.label.orEmpty()}:$trackIndex"
                }
        }
        .firstOrNull()
}

@OptIn(UnstableApi::class)
internal fun androidx.media3.common.Format.subtitleLabel(
    texts: PlayerControlTexts,
    trackIndex: Int,
): String {
    val explicitLabel = label?.subtitleUserVisibleLabel()
    val idLabel = id
        ?.takeIf { it.isNotBlank() }
        ?.subtitleIdentifierLabel()
        ?.takeIf { it.isNotBlank() }
    val languageLabel = language
        ?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
        ?.let { languageTag ->
            runCatching { Locale.forLanguageTag(languageTag).getDisplayLanguage(Locale.getDefault()) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }
    return explicitLabel
        ?: idLabel
        ?: languageLabel
        ?: "${texts.subtitles} ${trackIndex + 1}"
}

private fun androidx.media3.common.Format.matchesResolvedSubtitleLabels(
    label: String,
    resolvedSubtitleLabels: Set<String>,
): Boolean {
    if (resolvedSubtitleLabels.isEmpty()) return false
    return subtitleIdentityTokens(label)
        .any { token -> token.normalizedSubtitleIdentityToken() in resolvedSubtitleLabels }
}

private fun androidx.media3.common.Format.subtitleIdentityTokens(label: String): List<String> {
    return listOfNotNull(
        id,
        this.label,
        label,
        id?.subtitleIdentifierLabel(),
        this.label?.subtitleIdentifierLabel(),
    )
}

internal fun String.subtitleIdentifierLabel(): String {
    val fileName = substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .takeIf { it.isNotBlank() }
        ?: return ""
    val label = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
    return label
        .subtitleUserVisibleLabel()
        ?.replace('_', ' ')
        ?.replace('-', ' ')
        ?.takeIf {
            (
                contains("file:", ignoreCase = true) ||
                    '/' in this ||
                    '\\' in this
                )
        }
        .orEmpty()
}

private fun String.normalizedSubtitleIdentityToken(): String {
    return trim().lowercase(Locale.ROOT).replace(Regex("""\s+"""), "")
}

@OptIn(UnstableApi::class)
internal fun androidx.media3.common.Format.qualityLabel(): String {
    return when {
        height > 0 -> "${height}p"
        width > 0 -> "${width}px"
        else -> "Видео"
    }
}
