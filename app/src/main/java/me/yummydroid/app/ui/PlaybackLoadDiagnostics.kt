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

internal enum class PlaybackTriggerReason { PlayerError, StartupTimeout, BufferingTimeout }
internal data class PlaybackPoint(val positionMs: Long, val bufferMs: Long, val loading: Boolean,
    val state: Int, val playWhenReady: Boolean, val realtimeMs: Long) {
    fun text() = "pos=$positionMs buf=$bufferMs loading=$loading state=$state play=$playWhenReady t=$realtimeMs"
}
internal data class PlaybackTrigger(val reason: PlaybackTriggerReason, val point: PlaybackPoint,
    val errorCode: Int? = null, val httpStatus: Int? = null, val timeoutMs: Long? = null, val inactivityMs: Long? = null)
internal data class PlaybackLoadRequest(val point: PlaybackPoint, val oldGeneration: Long?,
    val newGeneration: Long, val sameUrl: Boolean?)
internal data class PlaybackAttemptDiagnostics(val trigger: PlaybackTrigger? = null, val load: PlaybackLoadRequest? = null,
    val loadCount: Long = 0, val loadsAfterTrigger: Long = 0) {
    fun triggered(value: PlaybackTrigger) = copy(trigger = value, loadsAfterTrigger = 0)
    fun loaded(value: PlaybackLoadRequest) = copy(load = value, loadCount = loadCount.incrementBounded(),
        loadsAfterTrigger = if (trigger == null) 0 else loadsAfterTrigger.incrementBounded())
    fun overlayText(): String {
        val triggerText = trigger?.let { "${it.reason} code=${it.errorCode} http=${it.httpStatus} " +
            "timeout=${it.timeoutMs} idle=${it.inactivityMs} ${it.point.text()}" } ?: "none"
        val loadText = load?.let { "gen=${it.oldGeneration}->${it.newGeneration} sameUrl=${it.sameUrl} ${it.point.text()}" } ?: "none"
        return "trigger=$triggerText\nloadRequests=$loadCount afterTrigger=$loadsAfterTrigger last=$loadText"
    }
}

private fun Player.diagnosticPoint() = PlaybackPoint(currentPosition, totalBufferedDuration, isLoading,
    playbackState, playWhenReady, SystemClock.elapsedRealtime())

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
    @Volatile private var attempts = PlaybackAttemptDiagnostics()

    @Synchronized fun recordTrigger(value: PlaybackTrigger) { attempts = attempts.triggered(value) }
    @Synchronized fun recordLoad(value: PlaybackLoadRequest) { attempts = attempts.loaded(value) }

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

    fun overlayText(): String = state.overlayText(networkProgress.receivedBytes, SystemClock.elapsedRealtime()) + "\n" + attempts.overlayText()
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

internal fun Player.recordPlaybackTrigger(reason: PlaybackTriggerReason, error: androidx.media3.common.PlaybackException? = null,
    timeoutMs: Long? = null, inactivityMs: Long? = null) {
    synchronized(playbackLoadDiagnostics) { playbackLoadDiagnostics[this] }?.recordTrigger(
        PlaybackTrigger(reason, diagnosticPoint(), error?.errorCode, error?.playbackHttpDetails()?.statusCode, timeoutMs, inactivityMs))
}

internal fun Player.recordPlaybackLoad(previous: me.yummydroid.app.data.ResolvedVideoStream?, next: me.yummydroid.app.data.ResolvedVideoStream) {
    synchronized(playbackLoadDiagnostics) { playbackLoadDiagnostics[this] }?.recordLoad(
        PlaybackLoadRequest(diagnosticPoint(), previous?.playbackGeneration, next.playbackGeneration, previous?.let { it.url == next.url }))
}
