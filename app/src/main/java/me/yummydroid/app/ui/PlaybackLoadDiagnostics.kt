package me.yummydroid.app.ui

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import java.io.IOException
import java.util.WeakHashMap

/** One bounded snapshot per live player; no URLs, request data or exception messages are retained. */
internal data class PlaybackLoadDiagnosticSnapshot(
    val errorCount: Long = 0L,
    val httpStatus: Int? = null,
    val errorClass: String? = null,
    val loadStopped: Boolean = false,
    val bufferAtErrorMs: Long = 0L,
    val errorRealtimeMs: Long = 0L,
    val bytesAtError: Long = 0L,
    val audioUnderruns: Long = 0L,
) {
    fun overlayText(receivedBytes: Long, nowRealtimeMs: Long): String {
        val failure = if (errorCount == 0L) "none" else {
            val status = httpStatus?.let { "HTTP$it/" }.orEmpty()
            "$status$errorClass loadStopped=$loadStopped bufAtError=${bufferAtErrorMs}ms " +
                "age=${(nowRealtimeMs - errorRealtimeMs).coerceAtLeast(0L) / 1_000L}s"
        }
        val sinceError = if (errorCount == 0L) "-" else (receivedBytes - bytesAtError).coerceAtLeast(0L).toString()
        return "load: errors=$errorCount last=$failure\n" +
            "network: bytes=$receivedBytes bytesSinceError=$sinceError audioUnderruns=$audioUnderruns"
    }
}

/** No Player field: the registry value must not keep its weak key alive. */
@OptIn(UnstableApi::class)
internal class PlaybackLoadDiagnostics(private val networkProgress: PlaybackNetworkProgress) : AnalyticsListener {
    @Volatile private var state = PlaybackLoadDiagnosticSnapshot()

    @Synchronized
    override fun onLoadError(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData,
        error: IOException,
        wasCanceled: Boolean,
    ) {
        val root = generateSequence<Throwable>(error) { it.cause }.take(16).last()
        val outerClass = error.javaClass.simpleName.take(48)
        val rootClass = root.javaClass.simpleName.take(48)
        state = state.copy(
            errorCount = state.errorCount.incrementBounded(),
            httpStatus = error.playbackHttpDetails()?.statusCode,
            errorClass = if (outerClass == rootClass) outerClass else "$outerClass/$rootClass",
            // Media3's flag describes this load stopping; it does not prove a user cancellation.
            loadStopped = wasCanceled,
            bufferAtErrorMs = eventTime.totalBufferedDurationMs,
            errorRealtimeMs = eventTime.realtimeMs,
            bytesAtError = networkProgress.receivedBytes,
        )
    }

    @Synchronized
    override fun onAudioUnderrun(
        eventTime: AnalyticsListener.EventTime,
        bufferSize: Int,
        bufferSizeMs: Long,
        elapsedSinceLastFeedMs: Long,
    ) {
        state = state.copy(audioUnderruns = state.audioUnderruns.incrementBounded())
    }

    fun overlayText(): String = state.overlayText(networkProgress.receivedBytes, SystemClock.elapsedRealtime())
}

private fun Long.incrementBounded(): Long = if (this == Long.MAX_VALUE) this else this + 1L

private val playbackLoadDiagnostics = WeakHashMap<Player, PlaybackLoadDiagnostics>()

@OptIn(UnstableApi::class)
internal fun ExoPlayer.attachPlaybackLoadDiagnostics(networkProgress: PlaybackNetworkProgress) {
    synchronized(playbackLoadDiagnostics) {
        if (playbackLoadDiagnostics.containsKey(this)) return
        val diagnostics = PlaybackLoadDiagnostics(networkProgress)
        playbackLoadDiagnostics[this] = diagnostics
        addAnalyticsListener(diagnostics)
    }
}

internal fun Player.playbackLoadDiagnosticsText(): String = synchronized(playbackLoadDiagnostics) {
    playbackLoadDiagnostics[this]?.overlayText() ?: "load: diagnostics unavailable"
}
