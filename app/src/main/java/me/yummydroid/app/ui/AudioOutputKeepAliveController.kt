package me.yummydroid.app.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.yummydroid.app.AppLog

internal class AudioOutputKeepAliveController(
    context: Context,
) {
    private val appContext = context.applicationContext

    suspend fun holdLease(onReady: () -> Unit) {
        if (!appContext.shouldWarmUpExternalAudioOutput()) {
            onReady()
            return
        }
        withContext(Dispatchers.IO) {
            var readyReported = false
            fun reportReady() {
                if (readyReported) return
                readyReported = true
                onReady()
            }
            try {
                holdSilentPcmLease(::reportReady)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                reportReady()
                AppLog.w("YummyDroidPlayer", "Audio output keep-alive failed", error)
            }
        }
    }
}

private fun Context.shouldWarmUpExternalAudioOutput(): Boolean {
    val uiModeManager = getSystemService(UiModeManager::class.java)
    val isTelevision = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    val hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    return isTelevision || hasLeanback
}

private suspend fun holdSilentPcmLease(onReady: () -> Unit) {
    val format = AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(AUDIO_OUTPUT_KEEP_ALIVE_SAMPLE_RATE)
        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
        .build()
    val minBufferSize = AudioTrack.getMinBufferSize(
        AUDIO_OUTPUT_KEEP_ALIVE_SAMPLE_RATE,
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT,
    )
    val bufferSize = maxOf(minBufferSize, AUDIO_OUTPUT_KEEP_ALIVE_CHUNK_BYTES * 2)
    val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build(),
        )
        .setAudioFormat(format)
        .setBufferSizeInBytes(bufferSize)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setSessionId(AudioManager.AUDIO_SESSION_ID_GENERATE)
        .build()

    try {
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            onReady()
            return
        }
        val silence = ByteArray(AUDIO_OUTPUT_KEEP_ALIVE_CHUNK_BYTES)
        val deadlineMs = SystemClock.elapsedRealtime() + AUDIO_OUTPUT_READY_DELAY_MS
        track.play()
        while (true) {
            currentCoroutineContext().ensureActive()
            val written = track.write(silence, 0, silence.size, AudioTrack.WRITE_BLOCKING)
            if (written < 0) break
            if (SystemClock.elapsedRealtime() >= deadlineMs) {
                onReady()
            }
        }
        onReady()
    } finally {
        runCatching { track.pause() }
        runCatching { track.flush() }
        runCatching { track.release() }
    }
}

private const val AUDIO_OUTPUT_KEEP_ALIVE_SAMPLE_RATE = 48_000
private const val AUDIO_OUTPUT_KEEP_ALIVE_CHUNK_BYTES = 16_384
private const val AUDIO_OUTPUT_READY_DELAY_MS = 2_500L
