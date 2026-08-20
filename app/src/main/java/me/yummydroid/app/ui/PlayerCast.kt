package me.yummydroid.app.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.database.Cursor
import android.net.ConnectivityManager
import android.net.Uri
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.cast.MediaRouteButtonFactory
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlayerTransferState
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.CastStatusCodes
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
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
import me.yummydroid.app.YummyCastPlaybackPayload
import me.yummydroid.app.withDirectPlayback
import me.yummydroid.app.withYummyCastPayload

internal fun isCastSenderSupported(uiModeType: Int, playServicesStatus: Int): Boolean {
    return uiModeType != Configuration.UI_MODE_TYPE_TELEVISION &&
        uiModeType != Configuration.UI_MODE_TYPE_WATCH &&
        uiModeType != Configuration.UI_MODE_TYPE_CAR &&
        playServicesStatus == ConnectionResult.SUCCESS
}

@OptIn(UnstableApi::class)
internal class PlayerCastSession private constructor(
    localPlayer: Player,
    private val castPlayer: CastPlayer?,
    private val mediaItemConverter: LocalCastMediaItemConverter?,
    private val connectionObserver: CastConnectionObserver?,
) {
    val playbackPlayer: Player = castPlayer ?: localPlayer
    val available: Boolean = castPlayer != null
    private val remotePlayback = mutableStateOf(playbackPlayer.isRemotePlayback())
    val isRemotePlayback: State<Boolean> = remotePlayback
    val connectionPending: State<Boolean> = connectionObserver?.connectionPending ?: mutableStateOf(false)
    private var controller: PlayerCastController? = null

    private val playerListener = object : Player.Listener {
        override fun onDeviceInfoChanged(deviceInfo: androidx.media3.common.DeviceInfo) {
            remotePlayback.value = deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
            if (!remotePlayback.value) {
                controller?.dismiss()
                controller = null
            }
        }
    }

    init {
        playbackPlayer.addListener(playerListener)
    }

    fun bind(button: MediaRouteButton, binding: PlayerCastControllerBinding) {
        button.visibility = if (available) View.VISIBLE else View.GONE
        if (!available) return
        button.contentDescription = button.context.getString(R.string.player_cast)
        button.setBackgroundResource(R.drawable.player_center_control_background)
        if (button.getTag(R.id.yummy_player_cast) != true) {
            MediaRouteButtonFactory.setUpMediaRouteButton(button.context, button)
            button.setTag(R.id.yummy_player_cast, true)
        }
        (button as? YummyCastRouteButton)?.onConnectedClick = {
            if (!remotePlayback.value) {
                false
            } else {
                controller?.dismiss()
                controller = PlayerCastController(
                    context = button.context,
                    player = playbackPlayer,
                    deviceName = connectionObserver?.currentDeviceName().orEmpty(),
                    binding = binding,
                    onStopCasting = ::stopCasting,
                    onDismissed = { controller = null },
                ).also(PlayerCastController::show)
                true
            }
        }
    }

    fun stopCasting() {
        val observer = connectionObserver ?: return
        stopCastPlayback(
            pausePlayback = playbackPlayer::pause,
            stopRemotePlayback = observer::stopRemotePlayback,
            endSession = observer::endCurrentSession,
        )
    }

    fun updatePayload(payload: YummyCastPlaybackPayload) {
        mediaItemConverter?.updatePayload(payload)
    }

    fun release() {
        controller?.dismiss()
        controller = null
        connectionObserver?.release()
        playbackPlayer.removeListener(playerListener)
        playbackPlayer.release()
    }

    companion object {
        fun create(
            context: Context,
            localPlayer: Player,
            payload: YummyCastPlaybackPayload,
        ): PlayerCastSession {
            val appContext = context.applicationContext
            if (!appContext.supportsCastSender()) {
                return PlayerCastSession(
                    localPlayer = localPlayer,
                    castPlayer = null,
                    mediaItemConverter = null,
                    connectionObserver = null,
                )
            }
            return try {
                val mediaItemConverter = LocalCastMediaItemConverter(
                    server = LocalCastMediaServer.get(appContext),
                    payload = payload,
                )
                val remotePlayer = RemoteCastPlayer.Builder(appContext)
                    .setMediaItemConverter(mediaItemConverter)
                    .build()
                val castPlayer = CastPlayer.Builder(appContext)
                    .setLocalPlayer(localPlayer)
                    .setRemotePlayer(remotePlayer)
                    .setTransferCallback(::transferCastPlaybackState)
                    .build()
                PlayerCastSession(
                    localPlayer = localPlayer,
                    castPlayer = castPlayer,
                    mediaItemConverter = mediaItemConverter,
                    connectionObserver = CastConnectionObserver.create(appContext),
                )
            } catch (error: RuntimeException) {
                AppLog.w(CAST_LOG_TAG, "Cast sender initialization failed", error)
                PlayerCastSession(
                    localPlayer = localPlayer,
                    castPlayer = null,
                    mediaItemConverter = null,
                    connectionObserver = null,
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun transferCastPlaybackState(sourcePlayer: Player, targetPlayer: Player) {
    AppLog.d(
        CAST_LOG_TAG,
        "Transferring playback ${sourcePlayer.deviceInfo.playbackType} -> " +
            targetPlayer.deviceInfo.playbackType,
    )
    PlayerTransferState.fromPlayer(sourcePlayer).setToPlayer(targetPlayer)
}

private const val CAST_LOG_TAG = "YummyDroidCast"

internal fun stopCastPlayback(
    pausePlayback: () -> Unit,
    stopRemotePlayback: () -> Unit,
    endSession: (Boolean) -> Unit,
) {
    pausePlayback()
    stopRemotePlayback()
    endSession(true)
}

private class CastConnectionObserver private constructor(
    private val context: Context,
    private val sessionManager: SessionManager,
) : SessionManagerListener<CastSession> {
    val connectionPending = mutableStateOf(false)

    init {
        sessionManager.addSessionManagerListener(this, CastSession::class.java)
    }

    fun release() {
        sessionManager.removeSessionManagerListener(this, CastSession::class.java)
    }

    fun currentDeviceName(): String? {
        return sessionManager.currentCastSession?.castDevice?.friendlyName
    }

    fun stopRemotePlayback() {
        sessionManager.currentCastSession?.remoteMediaClient?.stop()
    }

    fun endCurrentSession(stopReceiverApplication: Boolean) {
        sessionManager.endCurrentSession(stopReceiverApplication)
    }

    override fun onSessionStarting(session: CastSession) {
        connectionPending.value = true
        AppLog.d(CAST_LOG_TAG, "Cast session starting")
    }

    override fun onSessionStarted(session: CastSession, sessionId: String) {
        connectionPending.value = false
        AppLog.d(CAST_LOG_TAG, "Cast session started")
    }

    override fun onSessionStartFailed(session: CastSession, error: Int) {
        connectionPending.value = false
        reportFailure("start", error)
    }

    override fun onSessionResuming(session: CastSession, sessionId: String) {
        connectionPending.value = true
    }

    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
        connectionPending.value = false
    }

    override fun onSessionResumeFailed(session: CastSession, error: Int) {
        connectionPending.value = false
        reportFailure("resume", error)
    }

    override fun onSessionEnding(session: CastSession) {
        connectionPending.value = false
    }

    override fun onSessionEnded(session: CastSession, error: Int) {
        connectionPending.value = false
    }

    override fun onSessionSuspended(session: CastSession, reason: Int) {
        connectionPending.value = false
    }

    private fun reportFailure(operation: String, error: Int) {
        AppLog.w(
            CAST_LOG_TAG,
            "Cast session $operation failed: $error (${CastStatusCodes.getStatusCodeString(error)})",
        )
        Toast.makeText(context, R.string.player_cast_connection_failed, Toast.LENGTH_LONG).show()
    }

    companion object {
        fun create(context: Context): CastConnectionObserver? {
            return try {
                val manager = CastContext.getSharedInstance(context).sessionManager
                CastConnectionObserver(context.applicationContext, manager)
            } catch (error: RuntimeException) {
                AppLog.w(CAST_LOG_TAG, "Cast session observer initialization failed", error)
                null
            }
        }
    }
}

@Composable
internal fun rememberPlayerCastSession(
    context: Context,
    localPlayer: Player,
    payload: YummyCastPlaybackPayload,
): PlayerCastSession {
    val session = remember(context.applicationContext, localPlayer) {
        PlayerCastSession.create(context, localPlayer, payload)
    }
    SideEffect { session.updatePayload(payload) }
    return session
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
    payload: YummyCastPlaybackPayload,
) : MediaItemConverter {
    private val delegate = DefaultMediaItemConverter()
    private val originalItems = ConcurrentHashMap<String, MediaItem>()
    @Volatile
    private var payload = payload

    fun updatePayload(payload: YummyCastPlaybackPayload) {
        this.payload = payload
    }

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val uri = mediaItem.localConfiguration?.uri
        if (uri == null || !uri.isLocalMediaUri()) {
            return delegate.toMediaQueueItem(mediaItem).withYummyCastPayload(payload)
        }
        val mimeType = mediaItem.localConfiguration?.mimeType
        val castUrl = server.castUrl(uri, mimeType)
        val castItem = mediaItem.buildUpon().setUri(castUrl).build()
        originalItems[castUrl] = mediaItem
        return delegate.toMediaQueueItem(castItem).withYummyCastPayload(
            payload.withDirectPlayback(castUrl, mimeType),
        )
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val converted = delegate.toMediaItem(mediaQueueItem)
        val castUrl = converted.localConfiguration?.uri?.toString()
        val original = castUrl?.let(originalItems::get) ?: return converted
        return original.buildUpon().setMediaMetadata(converted.mediaMetadata).build()
    }
}

private fun MediaQueueItem.withYummyCastPayload(payload: YummyCastPlaybackPayload): MediaQueueItem {
    val sourceMedia = media ?: return this
    val customData = sourceMedia.customData
        ?.withYummyCastPayload(payload)
        ?: org.json.JSONObject().withYummyCastPayload(payload)
    val enrichedMedia = MediaInfo.Builder(sourceMedia.contentId)
        .apply {
            sourceMedia.contentType?.let(::setContentType)
            sourceMedia.contentUrl?.let(::setContentUrl)
            sourceMedia.metadata?.let(::setMetadata)
            sourceMedia.mediaTracks?.let(::setMediaTracks)
            sourceMedia.textTrackStyle?.let(::setTextTrackStyle)
            sourceMedia.hlsSegmentFormat?.let(::setHlsSegmentFormat)
            sourceMedia.hlsVideoSegmentFormat?.let(::setHlsVideoSegmentFormat)
            if (sourceMedia.streamDuration >= 0L) setStreamDuration(sourceMedia.streamDuration)
            if (sourceMedia.streamType != MediaInfo.STREAM_TYPE_INVALID) {
                setStreamType(sourceMedia.streamType)
            }
            setCustomData(customData)
        }
        .build()
    return MediaQueueItem.Builder(enrichedMedia)
        .setAutoplay(autoplay)
        .setStartTime(startTime)
        .setPlaybackDuration(playbackDuration)
        .setPreloadTime(preloadTime)
        .apply {
            activeTrackIds?.let(::setActiveTrackIds)
            this@withYummyCastPayload.customData?.let(::setCustomData)
            if (itemId != MediaQueueItem.INVALID_ITEM_ID) setItemId(itemId)
        }
        .build()
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

    companion object {
        @Volatile
        private var instance: LocalCastMediaServer? = null

        fun get(context: Context): LocalCastMediaServer {
            return instance ?: synchronized(this) {
                instance ?: LocalCastMediaServer(context.applicationContext).also { instance = it }
            }
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
