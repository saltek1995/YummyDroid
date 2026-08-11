package me.yummydroid.app.ui

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import me.yummydroid.app.data.APP_USER_AGENT
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.SourceQuality
import okhttp3.OkHttpClient

private const val DEFAULT_INITIAL_VIDEO_BITRATE = 12_000_000L
private const val MAX_INITIAL_VIDEO_BITRATE = 50_000_000L
private const val INITIAL_VIDEO_BITRATE_HEADROOM = 2L

@OptIn(UnstableApi::class)
internal fun createVideoPlayer(
    context: Context,
    stream: ResolvedVideoStream,
    startPositionMs: Long,
    httpClient: OkHttpClient,
    renderersFactory: DefaultRenderersFactory,
    loadControl: DefaultLoadControl,
): ExoPlayer {
    val userAgent = stream.headers.entries
        .firstOrNull { (name, _) -> name.equals("User-Agent", ignoreCase = true) }
        ?.value
        ?.takeIf(String::isNotBlank)
        ?: APP_USER_AGENT
    val defaultRequestHeaders = stream.headers.filterKeys { name ->
        !name.isMedia3ManagedRequestHeader()
    }
    val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
        .setInitialBitrateEstimate(initialVideoBitrateEstimate(stream.availableQualities))
        .build()
    val trackSelector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .setMaxVideoBitrate(Int.MAX_VALUE)
            .build()
    }
    val httpDataSourceFactory = OkHttpDataSource.Factory(httpClient)
        .setUserAgent(userAgent)
        .setDefaultRequestProperties(defaultRequestHeaders)
    val dataSourceFactory = if (stream.url.startsWith("file:", ignoreCase = true)) {
        DefaultDataSource.Factory(context)
    } else {
        DefaultDataSource.Factory(context, httpDataSourceFactory)
    }.setTransferListener(bandwidthMeter)

    return ExoPlayer.Builder(context, renderersFactory)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setBandwidthMeter(bandwidthMeter)
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .apply {
            setForegroundMode(true)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            setMediaItem(stream.toMediaItem(), startPositionMs.coerceAtLeast(0L))
            playWhenReady = false
            prepare()
        }
}

private fun String.isMedia3ManagedRequestHeader(): Boolean {
    return equals("User-Agent", ignoreCase = true) || equals("Accept-Encoding", ignoreCase = true)
}

internal fun initialVideoBitrateEstimate(qualities: List<SourceQuality>): Long {
    val highestDeclaredBitrate = qualities.maxOfOrNull { it.bitrate.coerceAtLeast(0).toLong() } ?: 0L
    return (highestDeclaredBitrate * INITIAL_VIDEO_BITRATE_HEADROOM)
        .coerceIn(DEFAULT_INITIAL_VIDEO_BITRATE, MAX_INITIAL_VIDEO_BITRATE)
}

internal fun ResolvedVideoStream.toMediaItem(): MediaItem {
    val mediaItemBuilder = MediaItem.Builder().setUri(url)
    mimeType?.let { mediaItemBuilder.setMimeType(it) }
    val subtitleConfigurations = subtitles.mapNotNull { it.toMedia3SubtitleConfiguration() }
    if (subtitleConfigurations.isNotEmpty()) {
        mediaItemBuilder.setSubtitleConfigurations(subtitleConfigurations)
    }
    return mediaItemBuilder.build()
}
