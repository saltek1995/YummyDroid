package me.yummydroid.app.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.database.Cursor
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.OpenableColumns
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import me.yummydroid.app.AppLog
import me.yummydroid.app.R

internal fun isCastSenderSupported(uiModeType: Int, playServicesStatus: Int): Boolean {
    return uiModeType != Configuration.UI_MODE_TYPE_TELEVISION &&
        uiModeType != Configuration.UI_MODE_TYPE_WATCH &&
        uiModeType != Configuration.UI_MODE_TYPE_CAR &&
        playServicesStatus == ConnectionResult.SUCCESS
}

@OptIn(UnstableApi::class)
internal class PlayerCastSession private constructor(
    private val localPlayer: Player,
    private val castPlayer: CastPlayer?,
    private val localMediaServer: LocalCastMediaServer?,
) {
    val playbackPlayer: Player = castPlayer ?: localPlayer
    val available: Boolean = castPlayer != null
    private val remotePlayback = mutableStateOf(playbackPlayer.isRemotePlayback())
    val isRemotePlayback: State<Boolean> = remotePlayback

    private val playerListener = object : Player.Listener {
        override fun onDeviceInfoChanged(deviceInfo: androidx.media3.common.DeviceInfo) {
            remotePlayback.value = deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
        }
    }

    init {
        playbackPlayer.addListener(playerListener)
    }

    fun bind(button: MediaRouteButton) {
        button.visibility = if (available) View.VISIBLE else View.GONE
        if (!available) return
        button.contentDescription = button.context.getString(R.string.player_cast)
        button.setBackgroundResource(R.drawable.player_center_control_background)
        if (button.getTag(R.id.yummy_player_cast) != true) {
            CastButtonFactory.setUpMediaRouteButton(button.context.applicationContext, button)
            button.setTag(R.id.yummy_player_cast, true)
        }
    }

    fun release() {
        playbackPlayer.removeListener(playerListener)
        playbackPlayer.release()
        localMediaServer?.stop()
    }

    companion object {
        fun create(context: Context, localPlayer: Player): PlayerCastSession {
            val appContext = context.applicationContext
            if (!appContext.supportsCastSender()) {
                return PlayerCastSession(localPlayer, castPlayer = null, localMediaServer = null)
            }
            val localMediaServer = LocalCastMediaServer(appContext)
            return try {
                val remotePlayer = RemoteCastPlayer.Builder(appContext)
                    .setMediaItemConverter(LocalCastMediaItemConverter(localMediaServer))
                    .build()
                val castPlayer = CastPlayer.Builder(appContext)
                    .setLocalPlayer(localPlayer)
                    .setRemotePlayer(remotePlayer)
                    .build()
                PlayerCastSession(localPlayer, castPlayer, localMediaServer)
            } catch (error: RuntimeException) {
                localMediaServer.stop()
                AppLog.w("YummyDroidCast", "Cast sender initialization failed", error)
                PlayerCastSession(localPlayer, castPlayer = null, localMediaServer = null)
            }
        }
    }
}

@Composable
internal fun rememberPlayerCastSession(
    context: Context,
    localPlayer: Player,
): PlayerCastSession {
    return remember(context.applicationContext, localPlayer) {
        PlayerCastSession.create(context, localPlayer)
    }
}

private fun Context.supportsCastSender(): Boolean {
    val uiModeType = (getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType
    val playServicesStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
    return isCastSenderSupported(uiModeType, playServicesStatus)
}

private fun Player.isRemotePlayback(): Boolean {
    return deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
}

@OptIn(UnstableApi::class)
private class LocalCastMediaItemConverter(
    private val server: LocalCastMediaServer,
) : MediaItemConverter {
    private val delegate = DefaultMediaItemConverter()
    private val originalItems = ConcurrentHashMap<String, MediaItem>()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val uri = mediaItem.localConfiguration?.uri
        if (uri == null || !uri.isLocalMediaUri()) return delegate.toMediaQueueItem(mediaItem)
        val castUrl = server.castUrl(uri, mediaItem.localConfiguration?.mimeType)
        val castItem = mediaItem.buildUpon().setUri(castUrl).build()
        originalItems[castUrl] = mediaItem
        return delegate.toMediaQueueItem(castItem)
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val converted = delegate.toMediaItem(mediaQueueItem)
        val castUrl = converted.localConfiguration?.uri?.toString()
        val original = castUrl?.let(originalItems::get) ?: return converted
        return original.buildUpon().setMediaMetadata(converted.mediaMetadata).build()
    }
}

private fun Uri.isLocalMediaUri(): Boolean {
    return scheme.equals("file", ignoreCase = true) || scheme.equals("content", ignoreCase = true)
}

internal data class LocalCastByteRange(
    val start: Long,
    val endInclusive: Long,
) {
    val length: Long = endInclusive - start + 1L
}

internal fun parseLocalCastByteRange(header: String, contentLength: Long): LocalCastByteRange? {
    if (contentLength <= 0L || !header.startsWith("bytes=", ignoreCase = true)) return null
    val value = header.substringAfter('=').trim()
    if (value.isBlank() || ',' in value) return null
    val separator = value.indexOf('-')
    if (separator < 0) return null
    val startText = value.substring(0, separator).trim()
    val endText = value.substring(separator + 1).trim()
    if (startText.isBlank()) {
        val suffixLength = endText.toLongOrNull()?.takeIf { it > 0L } ?: return null
        val start = (contentLength - suffixLength).coerceAtLeast(0L)
        return LocalCastByteRange(start, contentLength - 1L)
    }
    val start = startText.toLongOrNull()?.takeIf { it in 0 until contentLength } ?: return null
    val end = endText.toLongOrNull()?.coerceAtMost(contentLength - 1L) ?: (contentLength - 1L)
    if (end < start) return null
    return LocalCastByteRange(start, end)
}

private class LocalCastMediaServer(
    private val context: Context,
) : NanoHTTPD(0) {
    private val sources = ConcurrentHashMap<String, LocalCastMediaSource>()
    private val urlsByUri = ConcurrentHashMap<String, String>()

    @Synchronized
    fun castUrl(uri: Uri, requestedMimeType: String?): String {
        urlsByUri[uri.toString()]?.let { return it }
        val host = context.localCastHostAddress()
            ?: throw IllegalStateException("No local network address available for Chromecast")
        val source = LocalCastMediaSource.create(context, uri, requestedMimeType)
        if (!isAlive) start(SOCKET_READ_TIMEOUT, true)
        val token = UUID.randomUUID().toString().replace("-", "")
        val path = "/media/$token"
        sources[path] = source
        return "http://$host:$listeningPort$path".also { urlsByUri[uri.toString()] = it }
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method != Method.GET && session.method != Method.HEAD) {
            return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "")
        }
        val source = sources[session.uri]
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "")
        val rangeHeader = session.headers["range"]
        val range = if (rangeHeader == null) {
            LocalCastByteRange(0L, source.length - 1L)
        } else {
            parseLocalCastByteRange(rangeHeader, source.length) ?: return rangeNotSatisfiable(source.length)
        }
        return try {
            val status = if (rangeHeader == null) Response.Status.OK else Response.Status.PARTIAL_CONTENT
            newFixedLengthResponse(status, source.mimeType, source.open(range.start), range.length).apply {
                addHeader("Accept-Ranges", "bytes")
                if (rangeHeader != null) {
                    addHeader("Content-Range", "bytes ${range.start}-${range.endInclusive}/${source.length}")
                }
            }
        } catch (error: IOException) {
            AppLog.w("YummyDroidCast", "Local Cast media read failed", error)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "")
        }
    }

    private fun rangeNotSatisfiable(contentLength: Long): Response {
        return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "").apply {
            addHeader("Content-Range", "bytes */$contentLength")
        }
    }
}

private class LocalCastMediaSource private constructor(
    val length: Long,
    val mimeType: String,
    private val openAt: (Long) -> InputStream,
) {
    fun open(position: Long): InputStream = openAt(position)

    companion object {
        fun create(context: Context, uri: Uri, requestedMimeType: String?): LocalCastMediaSource {
            return if (uri.scheme.equals("file", ignoreCase = true)) {
                fromFile(uri, requestedMimeType)
            } else {
                fromContentUri(context, uri, requestedMimeType)
            }
        }

        private fun fromFile(uri: Uri, requestedMimeType: String?): LocalCastMediaSource {
            val file = uri.path?.let(::File)?.takeIf(File::isFile)
                ?: throw IllegalArgumentException("Local Cast file is unavailable")
            val length = file.length().takeIf { it > 0L }
                ?: throw IllegalArgumentException("Local Cast file is empty")
            return LocalCastMediaSource(length, resolvedMimeType(uri, requestedMimeType)) { position ->
                FileInputStream(file).apply { channel.position(position) }
            }
        }

        private fun fromContentUri(
            context: Context,
            uri: Uri,
            requestedMimeType: String?,
        ): LocalCastMediaSource {
            val resolver = context.contentResolver
            val length = resolver.queryContentLength(uri).takeIf { it > 0L }
                ?: resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                    descriptor.length.takeIf { it > 0L }
                }
                ?: throw IllegalArgumentException("Local Cast content length is unavailable")
            val mimeType = requestedMimeType ?: resolver.getType(uri)
            return LocalCastMediaSource(length, resolvedMimeType(uri, mimeType)) { position ->
                val descriptor = resolver.openAssetFileDescriptor(uri, "r")
                    ?: throw IOException("Local Cast content is unavailable")
                val stream = descriptor.createInputStream()
                try {
                    stream.skipFully(position)
                } catch (error: IOException) {
                    stream.close()
                    descriptor.close()
                    throw error
                }
                object : FilterInputStream(stream) {
                    override fun close() {
                        try {
                            super.close()
                        } finally {
                            descriptor.close()
                        }
                    }
                }
            }
        }
    }
}

private fun android.content.ContentResolver.queryContentLength(uri: Uri): Long {
    var cursor: Cursor? = null
    return try {
        cursor = query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
        if (cursor?.moveToFirst() == true) cursor.getLong(0) else -1L
    } catch (_: RuntimeException) {
        -1L
    } finally {
        cursor?.close()
    }
}

private fun InputStream.skipFully(byteCount: Long) {
    var remaining = byteCount
    while (remaining > 0L) {
        val skipped = skip(remaining)
        if (skipped > 0L) {
            remaining -= skipped
        } else if (read() >= 0) {
            remaining--
        } else {
            throw IOException("Unexpected end of local Cast media")
        }
    }
}

private fun resolvedMimeType(uri: Uri, requestedMimeType: String?): String {
    return requestedMimeType?.substringBefore(';')?.trim()?.takeIf(String::isNotBlank)
        ?: NanoHTTPD.getMimeTypeForFile(uri.lastPathSegment.orEmpty())
            .takeUnless { it == "application/octet-stream" }
        ?: "video/mp4"
}

private fun Context.localCastHostAddress(): String? {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeAddress = connectivityManager.activeNetwork
        ?.let(connectivityManager::getLinkProperties)
        ?.linkAddresses
        ?.asSequence()
        ?.map { it.address }
        ?.filterIsInstance<Inet4Address>()
        ?.firstOrNull { !it.isLoopbackAddress }
        ?.hostAddress
    if (!activeAddress.isNullOrBlank()) return activeAddress
    return NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        .asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList().asSequence() }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
        ?.hostAddress
}
