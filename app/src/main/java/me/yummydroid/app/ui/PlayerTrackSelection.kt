package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.PlayerDecoderMode
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.selectForPreferredQuality

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
internal fun androidx.media3.common.Format.qualityLabel(): String {
    return when {
        height > 0 -> "${height}p"
        width > 0 -> "${width}px"
        else -> "Video"
    }
}
