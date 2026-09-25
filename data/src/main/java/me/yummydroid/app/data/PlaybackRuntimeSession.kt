package me.yummydroid.app.data

import java.io.Closeable
import java.io.IOException
import okhttp3.OkHttpClient

interface PlaybackSessionDescriptor {
    fun open(client: OkHttpClient): PlaybackRuntimeSession
}

data class PlaybackRuntimeState(
    val positionMs: Long = 0,
    val playWhenReady: Boolean = false,
    val speed: Float = 1f,
    val providerResolution: String? = null,
    val providerAudioId: String? = null,
    val subtitleIndex: Int = -1,
    val durationMs: Long = 0,
    val bufferedDurationMs: Long = 0,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isSeeking: Boolean = false,
    val manifestUrl: String? = null,
    val audioTrackLabel: String? = null,
    val qualityHeight: Int? = null,
    val subtitleLanguage: String? = null,
    val completedMediaBytes: Long = 0,
    val bandwidthEstimate: Long? = null,
    val droppedVideoFrames: Long = 0,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val videoBitrate: Int? = null,
    val videoFrameRate: Float? = null,
    val renderedFirstFrame: Boolean = false,
)

interface PlaybackRuntimeSession : Closeable {
    /** Operational counters only; implementations must never include credentials or URLs. */
    fun diagnostics(): String? = null
    @Throws(IOException::class)
    fun requestHeaders(url: String): Map<String, String>
    fun update(state: PlaybackRuntimeState)
    fun seek(positionMs: Long)
    fun ended()
    fun playbackError(errorCode: String) = Unit
}

class PlaybackSessionExpiredException : IOException("Playback session expired")

class PlaybackSessionRestrictedException(
    val statusCode: Int,
    val retryAtEpochMs: Long?,
) : IOException("Playback session restricted (HTTP $statusCode)")
