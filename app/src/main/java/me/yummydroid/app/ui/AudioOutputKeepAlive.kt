package me.yummydroid.app.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean
import me.yummydroid.app.AppLog

internal class AudioOutputKeepAlive {
    private val running = AtomicBoolean(false)
    @Volatile
    private var worker: Thread? = null

    fun start(audioSessionId: Int) {
        if (!running.compareAndSet(false, true)) return
        worker = Thread {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            runAudioLoop(audioSessionId)
        }.apply {
            name = "YummyAudioKeepAlive"
            isDaemon = true
            start()
        }
    }

    fun stop() {
        if (!running.compareAndSet(true, false)) return
        worker?.interrupt()
        worker = null
    }

    fun release() {
        stop()
    }

    private fun runAudioLoop(audioSessionId: Int) {
        val minBufferSize = AudioTrack.getMinBufferSize(
            KEEP_ALIVE_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) {
            running.set(false)
            return
        }

        val audioTrack = runCatching {
            createAudioTrack(minBufferSize, audioSessionId)
        }.recoverCatching { throwable ->
            if (audioSessionId <= 0) throw throwable
            AppLog.w("YummyAudioKeepAlive", "Failed to create silent AudioTrack in player session, using own session", throwable)
            createAudioTrack(minBufferSize, audioSessionId = 0)
        }.getOrElse { throwable ->
            AppLog.w("YummyAudioKeepAlive", "Failed to create silent AudioTrack", throwable)
            running.set(false)
            return
        }

        try {
            if (audioTrack.state != AudioTrack.STATE_INITIALIZED) return
            val silence = ByteArray(KEEP_ALIVE_WRITE_BYTES)
            audioTrack.play()
            while (running.get()) {
                val written = audioTrack.write(silence, 0, silence.size, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) {
                    Thread.sleep(KEEP_ALIVE_RETRY_DELAY_MS)
                }
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (throwable: Throwable) {
            AppLog.w("YummyAudioKeepAlive", "Silent AudioTrack failed", throwable)
        } finally {
            running.set(false)
            runCatching { audioTrack.pause() }
            runCatching { audioTrack.flush() }
            runCatching { audioTrack.release() }
        }
    }

    private fun createAudioTrack(minBufferSize: Int, audioSessionId: Int): AudioTrack {
        val format = AudioFormat.Builder()
            .setSampleRate(KEEP_ALIVE_SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()
        val builder = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes((minBufferSize * 2).coerceAtLeast(KEEP_ALIVE_WRITE_BYTES))
        if (audioSessionId > 0) {
            builder.setSessionId(audioSessionId)
        }
        return builder.build()
    }

    private companion object {
        const val KEEP_ALIVE_SAMPLE_RATE = 48_000
        const val KEEP_ALIVE_WRITE_BYTES = 3_840
        const val KEEP_ALIVE_RETRY_DELAY_MS = 20L
    }
}
