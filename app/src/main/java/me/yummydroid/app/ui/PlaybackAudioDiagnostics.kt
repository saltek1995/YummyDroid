package me.yummydroid.app.ui

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import me.yummydroid.app.AppLog

/** Local observations only: never changes audio gain, focus, buffering or renderer selection. */
@OptIn(UnstableApi::class)
internal class PlaybackAudioDiagnostics(private val player: Player) : AnalyticsListener {
    private data class WarningWindow(var atMs: Long, var suppressed: Int = 0)
    private val warningWindows = mutableMapOf<String, WarningWindow>()
    private val lastValues = mutableMapOf<String, String>()
    private var inputFormat = "mime=unknown codec=unknown channels=-1 sampleRate=-1"

    override fun onAudioUnderrun(eventTime: EventTime, bufferSize: Int, bufferSizeMs: Long, elapsedSinceLastFeedMs: Long) {
        warning("underrun", eventTime,
            "sinkBufferBytes=$bufferSize sinkBufferMs=$bufferSizeMs sinceFeedMs=$elapsedSinceLastFeedMs")
    }

    override fun onAudioSinkError(eventTime: EventTime, audioSinkError: Exception) {
        warning("sinkError", eventTime, "type=${audioSinkError.javaClass.simpleName}")
    }

    override fun onAudioCodecError(eventTime: EventTime, audioCodecError: Exception) {
        warning("codecError", eventTime, "type=${audioCodecError.javaClass.simpleName}")
    }

    override fun onAudioDecoderInitialized(
        eventTime: EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        val name = decoderName.takeIf { it.matches(DECODER_NAME) } ?: "other"
        changed("decoder", name, eventTime, "name=$name initializationMs=$initializationDurationMs")
    }

    override fun onAudioInputFormatChanged(eventTime: EventTime, format: Format, decoderReuseEvaluation: DecoderReuseEvaluation?) {
        // Whitelists prevent untrusted manifest identifiers from entering logs.
        val mime = format.sampleMimeType?.takeIf { it in AUDIO_MIME_TYPES } ?: "other"
        val codec = format.codecs?.takeIf { it.matches(AUDIO_CODEC) } ?: "other"
        inputFormat = "mime=$mime codec=$codec channels=${format.channelCount} sampleRate=${format.sampleRate} pcmEncoding=${format.pcmEncoding}"
        changed("inputFormat", inputFormat, eventTime, "changed=true")
    }

    override fun onAudioTrackInitialized(eventTime: EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
        val description = with(audioTrackConfig) {
            "encoding=$encoding sampleRate=$sampleRate channelMask=$channelConfig bufferBytes=$bufferSize offload=$offload tunneling=$tunneling"
        }
        changed("audioTrack", description, eventTime, description)
    }

    override fun onPlaybackSuppressionReasonChanged(eventTime: EventTime, playbackSuppressionReason: Int) {
        changed("suppression", playbackSuppressionReason.toString(), eventTime, "reason=$playbackSuppressionReason")
    }

    private fun warning(kind: String, eventTime: EventTime, details: String) {
        val now = SystemClock.elapsedRealtime()
        val previous = warningWindows[kind]
        if (previous != null && now - previous.atMs < 10_000L) {
            if (previous.suppressed < Int.MAX_VALUE) previous.suppressed++
            return
        }
        AppLog.w(AUDIO_DIAGNOSTICS_TAG,
            "$kind $details suppressedEvents=${previous?.suppressed ?: 0} ${state(eventTime)}")
        warningWindows[kind] = WarningWindow(now)
    }

    private fun changed(kind: String, value: String, eventTime: EventTime, details: String) {
        if (lastValues[kind] == value) return
        lastValues[kind] = value
        AppLog.d(AUDIO_DIAGNOSTICS_TAG, "$kind $details ${state(eventTime)}")
    }

    private fun state(eventTime: EventTime): String =
        "positionMs=${eventTime.currentPlaybackPositionMs} bufferedMs=${eventTime.totalBufferedDurationMs} " +
            "state=${player.playbackState} playWhenReady=${player.playWhenReady} isPlaying=${player.isPlaying} " +
            "suppression=${player.playbackSuppressionReason} playerVolume=${player.volume} " +
            "speed=${player.playbackParameters.speed} $inputFormat"

    private companion object {
        val DECODER_NAME = Regex("(?:c2|OMX)\\.[A-Za-z0-9_.-]{1,96}")
        val AUDIO_CODEC = Regex("(?:mp4a\\.[0-9A-Fa-f]{1,2}(?:\\.[0-9]{1,2})?|ac-3|ec-3|ac-4|opus|flac|vorbis|mp3|dtsc|dtse|dtsh|dtsl|mha1|mhm1)")
        val AUDIO_MIME_TYPES = setOf("audio/mp4a-latm", "audio/ac3", "audio/eac3", "audio/eac3-joc",
            "audio/ac4", "audio/opus", "audio/flac", "audio/vorbis", "audio/mpeg", "audio/raw",
            "audio/vnd.dts", "audio/vnd.dts.hd", "audio/true-hd", "audio/alac", "audio/3gpp", "audio/amr-wb")
    }
}

/** Observes the renderer's effective gain, including focus ducking, without changing it. */
@OptIn(UnstableApi::class)
internal fun AudioSink.withPlaybackGainDiagnostics(): AudioSink = object : ForwardingAudioSink(this) {
    private var previousGainBits: Int? = null

    override fun setVolume(volume: Float) {
        val gainBits = volume.toBits()
        if (previousGainBits != gainBits) {
            previousGainBits = gainBits
            AppLog.d(AUDIO_DIAGNOSTICS_TAG, "rendererGain=$volume")
        }
        super.setVolume(volume)
    }
}

private const val AUDIO_DIAGNOSTICS_TAG = "YummyDroidAudio"
