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
)

interface PlaybackRuntimeSession : Closeable {
    @Throws(IOException::class)
    fun requestHeaders(url: String): Map<String, String>
    fun update(state: PlaybackRuntimeState)
    fun seek(positionMs: Long)
    fun ended()
}

class PlaybackSessionExpiredException : IOException("Playback session expired")

class PlaybackSessionRestrictedException(
    val statusCode: Int,
    val retryAtEpochMs: Long?,
) : IOException("Playback session restricted (HTTP $statusCode)")
