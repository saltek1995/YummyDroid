package me.yummydroid.app.ui

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.database.Cursor
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import com.google.android.gms.cast.Cast
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
    private val localPlayer: Player,
    private val castPlayer: CastPlayer?,
    private val mediaItemConverter: LocalCastMediaItemConverter?,
    private val connectionObserver: CastConnectionObserver?,
    private val playbackReturn: CastPlaybackReturnCoordinator,
) {
    val playbackPlayer: Player = castPlayer ?: localPlayer
    val available: Boolean = castPlayer != null
    private val remotePlayback = mutableStateOf(playbackPlayer.isRemotePlayback())
    val isRemotePlayback: State<Boolean> = remotePlayback
    val connectionPending: State<Boolean> = connectionObserver?.connectionPending ?: mutableStateOf(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val selectionPlayback = CastPlaybackSelectionCoordinator()
    private var controller: PlayerCastController? = null
    private var controllerBinding: PlayerCastControllerBinding? = null
    private var localPlaybackRestoredHandler: ((Long) -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onDeviceInfoChanged(deviceInfo: androidx.media3.common.DeviceInfo) {
            val isRemote = deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
            if (!isRemote) mainHandler.post(::restoreLocalPlaybackAfterCast)
            updateRemotePlayback(
                isRemote &&
                    (connectionObserver?.isConnected() ?: true),
            )
        }
    }

    init {
        playbackPlayer.addListener(playerListener)
        connectionObserver?.setEpisodeCommandHandler { command ->
            mainHandler.post {
                controllerBinding?.let { binding -> dispatchCastEpisodeCommand(command, binding) }
            }
        }
        connectionObserver?.setSelectionCommandHandler { command ->
            mainHandler.post {
                controllerBinding?.let { binding -> dispatchCastSelectionCommand(command, binding) }
            }
        }
        connectionObserver?.setReceiverStoppingHandler {
            mainHandler.post {
                captureRemotePlaybackReturn()
                connectionObserver.endCurrentSession(stopReceiverApplication = false)
            }
        }
        connectionObserver?.setConnectionStateHandler { connected ->
            if (!connected) captureRemotePlaybackReturn()
            mainHandler.post {
                updateRemotePlayback(
                    resolveRemotePlaybackAfterConnectionChange(
                        currentRemotePlayback = remotePlayback.value,
                        playerIsRemotePlayback = playbackPlayer.isRemotePlayback(),
                        connected = connected,
                    ),
                )
            }
        }
    }

    fun bind(button: MediaRouteButton, binding: PlayerCastControllerBinding) {
        controllerBinding = binding
        connectionObserver?.updateSelectionState(binding.selectionState)
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
        connectionObserver?.endCurrentSession(stopReceiverApplication = true)
    }

    fun updatePayload(payload: YummyCastPlaybackPayload) {
        mediaItemConverter?.updatePayload(payload)
    }

    fun setLocalPlaybackRestoredHandler(handler: (Long) -> Unit) {
        localPlaybackRestoredHandler = handler
    }

    fun performPlaybackSelection(action: () -> Unit) {
        selectionPlayback.perform(action)
    }

    fun consumeSelectionPlayWhenReady(defaultValue: Boolean): Boolean {
        return selectionPlayback.consumePlayWhenReady(defaultValue)
    }

    fun release() {
        controllerBinding = null
        localPlaybackRestoredHandler = null
        mainHandler.removeCallbacksAndMessages(null)
        controller?.dismiss()
        controller = null
        connectionObserver?.setConnectionStateHandler(null)
        connectionObserver?.release()
        playbackPlayer.removeListener(playerListener)
        castPlayer?.release()
    }

    private fun captureRemotePlaybackReturn() {
        if (!playbackPlayer.isRemotePlayback()) return
        playbackReturn.capture(
            positionMs = playbackPlayer.currentPosition,
            playWhenReady = playbackPlayer.playWhenReady,
        )
    }

    private fun restoreLocalPlaybackAfterCast() {
        val state = playbackReturn.consume() ?: return
        if (localPlayer.currentMediaItem == null) return
        val restoredAtMs = SystemClock.elapsedRealtime()
        localPlaybackRestoredHandler?.invoke(restoredAtMs)
        localPlayer.seekTo(state.positionMs)
        if (localPlayer.playbackState == Player.STATE_IDLE) localPlayer.prepare()
        localPlayer.playWhenReady = state.playWhenReady
        AppLog.d(
            CAST_LOG_TAG,
            "Restored local playback after Cast: position=${state.positionMs}, play=${state.playWhenReady}",
        )
    }

    private fun updateRemotePlayback(remote: Boolean) {
        remotePlayback.value = remote
        if (!remote) {
            controller?.dismiss()
            controller = null
        }
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
                    playbackReturn = CastPlaybackReturnCoordinator(),
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
                val playbackReturn = CastPlaybackReturnCoordinator()
                val castPlayer = CastPlayer.Builder(appContext)
                    .setLocalPlayer(localPlayer)
                    .setRemotePlayer(remotePlayer)
                    .setTransferCallback { sourcePlayer, targetPlayer ->
                        transferCastPlaybackState(sourcePlayer, targetPlayer, playbackReturn)
                    }
                    .build()
                PlayerCastSession(
                    localPlayer = localPlayer,
                    castPlayer = castPlayer,
                    mediaItemConverter = mediaItemConverter,
                    connectionObserver = CastConnectionObserver.create(appContext),
                    playbackReturn = playbackReturn,
                )
            } catch (error: RuntimeException) {
                AppLog.w(CAST_LOG_TAG, "Cast sender initialization failed", error)
                PlayerCastSession(
                    localPlayer = localPlayer,
                    castPlayer = null,
                    mediaItemConverter = null,
                    connectionObserver = null,
                    playbackReturn = CastPlaybackReturnCoordinator(),
                )
            }
        }
    }
}

internal fun resolveRemotePlaybackAfterConnectionChange(
    currentRemotePlayback: Boolean,
    playerIsRemotePlayback: Boolean,
    connected: Boolean,
): Boolean {
    if (!connected && playerIsRemotePlayback) return currentRemotePlayback
    return connected && playerIsRemotePlayback
}

internal class CastPlaybackSelectionCoordinator {
    private var autoPlayNextMedia = false

    fun perform(action: () -> Unit) {
        autoPlayNextMedia = true
        action()
    }

    fun consumePlayWhenReady(defaultValue: Boolean): Boolean {
        if (!autoPlayNextMedia) return defaultValue
        autoPlayNextMedia = false
        return true
    }
}

internal data class CastPlaybackReturnState(
    val positionMs: Long,
    val playWhenReady: Boolean,
)

internal class CastPlaybackReturnCoordinator {
    private var pending: CastPlaybackReturnState? = null

    fun capture(positionMs: Long, playWhenReady: Boolean) {
        pending = CastPlaybackReturnState(
            positionMs = positionMs.coerceAtLeast(0L),
            playWhenReady = playWhenReady,
        )
    }

    fun consume(): CastPlaybackReturnState? {
        val state = pending
        pending = null
        return state
    }
}

@OptIn(UnstableApi::class)
private fun transferCastPlaybackState(
    sourcePlayer: Player,
    targetPlayer: Player,
    playbackReturn: CastPlaybackReturnCoordinator,
) {
    AppLog.d(
        CAST_LOG_TAG,
        "Transferring playback ${sourcePlayer.deviceInfo.playbackType} -> " +
            targetPlayer.deviceInfo.playbackType,
    )
    if (sourcePlayer.isRemotePlayback() && !targetPlayer.isRemotePlayback()) {
        playbackReturn.capture(
            positionMs = sourcePlayer.currentPosition,
            playWhenReady = sourcePlayer.playWhenReady,
        )
    }
    PlayerTransferState.fromPlayer(sourcePlayer).setToPlayer(targetPlayer)
}

private const val CAST_LOG_TAG = "YummyDroidCast"
private const val CAST_CONTROL_NAMESPACE = "urn:x-cast:me.yummydroid.control"
private const val CAST_EPISODE_COMMAND_TYPE = "episode-navigation"
private const val CAST_SELECTION_COMMAND_TYPE = "playback-selection"
private const val CAST_SELECTION_STATE_TYPE = "selection-state"
private const val CAST_SELECTION_STATE_REQUEST_TYPE = "selection-state-request"
private const val CAST_RECEIVER_STOPPING_TYPE = "receiver-stopping"
private val CastControlJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
internal data class CastControlMessage(
    val type: String = "",
    val direction: String = "",
    val selectionType: String = "",
    val key: String = "",
)

@Serializable
internal data class CastSelectionOption(
    val key: String,
    val label: String,
)

@Serializable
internal data class CastSelectionGroup(
    val title: String,
    val options: List<CastSelectionOption>,
    val selectedKey: String? = null,
)

@Serializable
internal data class CastSelectionState(
    val type: String = CAST_SELECTION_STATE_TYPE,
    val voice: CastSelectionGroup,
    val source: CastSelectionGroup,
    val quality: CastSelectionGroup,
)

internal fun encodeCastSelectionState(state: CastSelectionState): String {
    return CastControlJson.encodeToString(CastSelectionState.serializer(), state)
}

internal enum class CastEpisodeCommand {
    Previous,
    Next,
}

internal enum class CastSelectionType {
    Voice,
    Source,
    Quality,
}

internal data class CastSelectionCommand(
    val type: CastSelectionType,
    val key: String,
)

private fun parseCastControlMessage(message: String): CastControlMessage? {
    return runCatching {
        CastControlJson.decodeFromString<CastControlMessage>(message)
    }.getOrNull()
}

internal fun parseCastEpisodeCommand(message: String): CastEpisodeCommand? {
    val payload = parseCastControlMessage(message) ?: return null
    if (payload.type != CAST_EPISODE_COMMAND_TYPE) return null
    return when (payload.direction) {
        "previous" -> CastEpisodeCommand.Previous
        "next" -> CastEpisodeCommand.Next
        else -> null
    }
}

internal fun parseCastSelectionCommand(message: String): CastSelectionCommand? {
    val payload = parseCastControlMessage(message) ?: return null
    if (payload.type != CAST_SELECTION_COMMAND_TYPE || payload.key.isBlank()) return null
    val type = when (payload.selectionType) {
        "voice" -> CastSelectionType.Voice
        "source" -> CastSelectionType.Source
        "quality" -> CastSelectionType.Quality
        else -> return null
    }
    return CastSelectionCommand(type, payload.key)
}

internal fun dispatchCastEpisodeCommand(
    command: CastEpisodeCommand,
    binding: PlayerCastControllerBinding,
): Boolean {
    return when (command) {
        CastEpisodeCommand.Previous -> binding.hasPrevious.also { available ->
            if (available) binding.onPrevious()
        }
        CastEpisodeCommand.Next -> binding.hasNext.also { available ->
            if (available) binding.onNext()
        }
    }
}

internal fun dispatchCastSelectionCommand(
    command: CastSelectionCommand,
    binding: PlayerCastControllerBinding,
): Boolean {
    val group = when (command.type) {
        CastSelectionType.Voice -> binding.selectionState.voice
        CastSelectionType.Source -> binding.selectionState.source
        CastSelectionType.Quality -> binding.selectionState.quality
    }
    if (group.options.none { option -> option.key == command.key }) return false
    if (group.selectedKey == command.key) return true
    when (command.type) {
        CastSelectionType.Voice -> binding.onSelectVoice(command.key)
        CastSelectionType.Source -> binding.onSelectSource(command.key)
        CastSelectionType.Quality -> binding.onSelectQuality(command.key)
    }
    return true
}

private class CastConnectionObserver private constructor(
    private val context: Context,
    private val sessionManager: SessionManager,
) : SessionManagerListener<CastSession> {
    val connectionPending = mutableStateOf(false)
    private var episodeCommandHandler: ((CastEpisodeCommand) -> Unit)? = null
    private var selectionCommandHandler: ((CastSelectionCommand) -> Unit)? = null
    private var receiverStoppingHandler: (() -> Unit)? = null
    private var connectionStateHandler: ((Boolean) -> Unit)? = null
    private var connected = sessionManager.currentCastSession?.isConnected == true
    private var latestSelectionStateMessage: String? = null
    private val messageCallback = Cast.MessageReceivedCallback { _, namespace, message ->
        if (namespace != CAST_CONTROL_NAMESPACE) return@MessageReceivedCallback
        val payload = parseCastControlMessage(message) ?: return@MessageReceivedCallback
        when (payload.type) {
            CAST_EPISODE_COMMAND_TYPE -> parseCastEpisodeCommand(message)
                ?.let { command -> episodeCommandHandler?.invoke(command) }
            CAST_SELECTION_COMMAND_TYPE -> parseCastSelectionCommand(message)
                ?.let { command -> selectionCommandHandler?.invoke(command) }
            CAST_SELECTION_STATE_REQUEST_TYPE -> sendSelectionState()
            CAST_RECEIVER_STOPPING_TYPE -> receiverStoppingHandler?.invoke()
        }
    }

    init {
        sessionManager.addSessionManagerListener(this, CastSession::class.java)
        sessionManager.currentCastSession?.let(::registerMessageCallback)
    }

    fun release() {
        sessionManager.currentCastSession?.let(::removeMessageCallback)
        episodeCommandHandler = null
        selectionCommandHandler = null
        receiverStoppingHandler = null
        connectionStateHandler = null
        latestSelectionStateMessage = null
        sessionManager.removeSessionManagerListener(this, CastSession::class.java)
    }

    fun setEpisodeCommandHandler(handler: (CastEpisodeCommand) -> Unit) {
        episodeCommandHandler = handler
    }

    fun setSelectionCommandHandler(handler: (CastSelectionCommand) -> Unit) {
        selectionCommandHandler = handler
    }

    fun setReceiverStoppingHandler(handler: () -> Unit) {
        receiverStoppingHandler = handler
    }

    fun setConnectionStateHandler(handler: ((Boolean) -> Unit)?) {
        connectionStateHandler = handler
        handler?.invoke(connected)
    }

    fun isConnected(): Boolean = connected

    fun updateSelectionState(state: CastSelectionState) {
        val message = encodeCastSelectionState(state)
        if (message == latestSelectionStateMessage) return
        latestSelectionStateMessage = message
        sendSelectionState()
    }

    fun currentDeviceName(): String? {
        return sessionManager.currentCastSession?.castDevice?.friendlyName
    }

    fun endCurrentSession(stopReceiverApplication: Boolean) {
        sessionManager.endCurrentSession(stopReceiverApplication)
    }

    override fun onSessionStarting(session: CastSession) {
        connectionPending.value = true
        updateConnected(false)
        AppLog.d(CAST_LOG_TAG, "Cast session starting")
    }

    override fun onSessionStarted(session: CastSession, sessionId: String) {
        connectionPending.value = false
        updateConnected(true)
        registerMessageCallback(session)
        sendSelectionState(session)
        AppLog.d(CAST_LOG_TAG, "Cast session started")
    }

    override fun onSessionStartFailed(session: CastSession, error: Int) {
        connectionPending.value = false
        updateConnected(false)
        reportFailure("start", error)
    }

    override fun onSessionResuming(session: CastSession, sessionId: String) {
        connectionPending.value = true
    }

    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
        connectionPending.value = false
        updateConnected(true)
        registerMessageCallback(session)
        sendSelectionState(session)
    }

    override fun onSessionResumeFailed(session: CastSession, error: Int) {
        connectionPending.value = false
        updateConnected(false)
        reportFailure("resume", error)
    }

    override fun onSessionEnding(session: CastSession) {
        connectionPending.value = false
        updateConnected(false)
        removeMessageCallback(session)
    }

    override fun onSessionEnded(session: CastSession, error: Int) {
        connectionPending.value = false
        updateConnected(false)
        removeMessageCallback(session)
    }

    override fun onSessionSuspended(session: CastSession, reason: Int) {
        connectionPending.value = false
        updateConnected(false)
        removeMessageCallback(session)
    }

    private fun updateConnected(value: Boolean) {
        if (connected == value) return
        connected = value
        connectionStateHandler?.invoke(value)
    }

    private fun registerMessageCallback(session: CastSession) {
        try {
            session.setMessageReceivedCallbacks(CAST_CONTROL_NAMESPACE, messageCallback)
        } catch (error: IOException) {
            AppLog.w(CAST_LOG_TAG, "Cast control channel registration failed", error)
        } catch (error: RuntimeException) {
            AppLog.w(CAST_LOG_TAG, "Cast control channel registration failed", error)
        }
    }

    private fun removeMessageCallback(session: CastSession) {
        try {
            session.removeMessageReceivedCallbacks(CAST_CONTROL_NAMESPACE)
        } catch (error: IOException) {
            AppLog.w(CAST_LOG_TAG, "Cast control channel removal failed", error)
        } catch (error: RuntimeException) {
            AppLog.w(CAST_LOG_TAG, "Cast control channel removal failed", error)
        }
    }

    private fun sendSelectionState(session: CastSession? = sessionManager.currentCastSession) {
        val message = latestSelectionStateMessage ?: return
        if (session == null || !session.isConnected) return
        try {
            session.sendMessage(CAST_CONTROL_NAMESPACE, message)
        } catch (error: RuntimeException) {
            AppLog.w(CAST_LOG_TAG, "Cast selection state delivery failed", error)
        }
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
    onLocalPlaybackRestored: (Long) -> Unit,
): PlayerCastSession {
    val session = remember(context.applicationContext, localPlayer) {
        PlayerCastSession.create(context, localPlayer, payload)
    }
    SideEffect {
        session.updatePayload(payload)
        session.setLocalPlaybackRestoredHandler(onLocalPlaybackRestored)
    }
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
            uri?.toString()?.let { playbackUrl -> originalItems[playbackUrl] = mediaItem }
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
