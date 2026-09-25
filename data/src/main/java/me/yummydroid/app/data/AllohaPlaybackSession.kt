package me.yummydroid.app.data

import java.io.IOException
import java.io.InterruptedIOException
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** Captured provider values only; constructing a descriptor opens no resources. */
data class AllohaSessionDescriptor(
    val observedWebSocketUrl: String,
    val webSocketHeaders: Map<String, String>,
    val playbackStartTemplate: String,
    val initialToken: String?,
    val guardToken: String?,
    val expiresAtEpochMs: Long?,
    val mediaHosts: Set<String>,
    val observedStartupEvents: Set<String> = emptySet(),
    val telemetry: AllohaHttpTelemetryDescriptor? = null,
) : PlaybackSessionDescriptor {
    override fun open(client: OkHttpClient): PlaybackRuntimeSession =
        AllohaPlaybackSession(this, client)
}

internal class AllohaPlaybackSession(
    private val descriptor: AllohaSessionDescriptor,
    private val client: OkHttpClient,
    private val now: () -> Long = System::currentTimeMillis,
    heartbeatIntervalMs: Long = 30_000,
    private val initialTokenWaitMs: Long = 5_000,
    private val reconnectBaseMs: Long = 1_000,
) : PlaybackRuntimeSession {
    private val lock = Object()
    private val httpTelemetry = descriptor.telemetry?.let { captured ->
        // Optional reporting must never prevent media playback if capture was incomplete.
        runCatching { AllohaHttpTelemetry(captured, client, now) }.getOrNull()
    }
    private val template = Json.parseToJsonElement(descriptor.playbackStartTemplate).jsonObject
    private val capturedAudioId = (template["track_id"] as? JsonPrimitive)?.contentOrNull
        ?.takeIf { it.isNotBlank() }
    private val capturedAudioLabel = run {
        val captured = descriptor.telemetry
        val observations = captured?.observedEvents?.takeIf { it.isNotEmpty() }
            ?: (captured?.initialEnvelope?.get("events") as? JsonArray)
                ?.filterIsInstance<JsonObject>().orEmpty()
        observations.asReversed().firstNotNullOfOrNull { event ->
            (event["audioTrack"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }
    }
    private val hosts = descriptor.mediaHosts.map { it.lowercase() }.toSet()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "alloha-playback").apply { isDaemon = true }
    }
    private var state = PlaybackRuntimeState()
    private var token = descriptor.initialToken?.takeIf { it.isNotBlank() }
    private var socket: WebSocket? = null
    private var connected = false
    private var initialized = "init" in descriptor.observedStartupEvents
    private var pendingPlaybackIntent: Boolean? = null
    private var generation = 0L
    private var closed = false
    private var finished = false
    private var failure: IOException? = null
    private var attempts = 0
    private var reconnect: ScheduledFuture<*>? = null
    private var reconnectExhausted = false
    private var connectionCount = 0
    private var successfulConnections = 0
    private var tokenUpdates = 0
    private var tokenChanges = 0
    private var lastTokenUpdateAt: Long? = null
    private var receivedMessages = 0
    private var sentMessages = 0
    private val sentEvents = mutableMapOf<String, Int>()
    private var lastHandshakeStatus: Int? = null
    private var lastFailureClass: String? = null
    private var lastCloseCode: Int? = null

    override fun diagnostics(): String = synchronized(lock) {
        "Alloha connected=$connected connections=$successfulConnections/$connectionCount " +
            "received=$receivedMessages tokenUpdates=$tokenUpdates sent=$sentMessages retries=$attempts " +
            "exhausted=$reconnectExhausted http=$lastHandshakeStatus " +
            "failure=$lastFailureClass close=$lastCloseCode tokenChanges=$tokenChanges " +
            "tokenAgeMs=${lastTokenUpdateAt?.let { (now() - it).coerceAtLeast(0) }} " +
            "positionMs=${state.positionMs} playWhenReady=${state.playWhenReady} " +
            "queuedBytes=${socket?.queueSize() ?: 0L} events=$sentEvents " +
            (httpTelemetry?.diagnostics() ?: "httpTelemetry=unavailable")
    }

    init {
        scheduler.scheduleAtFixedRate({ synchronized(lock) {
            if (active() && state.playWhenReady && !finished) send("playing")
        } }, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS)
        synchronized(lock) { queueConnectionIfNeeded() }
    }

    override fun requestHeaders(url: String): Map<String, String> {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull()
        if (host !in hosts) return emptyMap()
        synchronized(lock) {
            val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(initialTokenWaitMs)
            while (true) {
                checkUsable()
                token?.let { value ->
                    return buildMap {
                        put("Accepts-Controls", value)
                        descriptor.guardToken?.takeIf { it.isNotBlank() }?.let {
                            put("Authorizations", "Bearer $it")
                        }
                    }
                }
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) throw IOException("Playback session token was not received")
                try {
                    TimeUnit.NANOSECONDS.timedWait(lock, remaining)
                } catch (exception: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException("Interrupted waiting for playback session").apply {
                        initCause(exception)
                    }
                }
            }
        }
    }

    override fun update(state: PlaybackRuntimeState): Unit = synchronized(lock) {
        if (!active()) return@synchronized
        // Media3 often exposes only a language such as "rus". The provider's HTTP
        // audio label names the selected translation, and remains authoritative only
        // while the provider track ID still identifies the captured translation.
        val effectiveAudioId = state.providerAudioId ?: capturedAudioId
        val telemetryState = if (capturedAudioId != null && effectiveAudioId == capturedAudioId &&
            capturedAudioLabel != null) state.copy(audioTrackLabel = capturedAudioLabel) else state
        httpTelemetry?.update(telemetryState)
        val restarting = finished && state.playWhenReady
        if (finished && !restarting) {
            this.state = state
            return@synchronized
        }
        val changed = this.state.playWhenReady != state.playWhenReady
        val providerChanged = state.providerResolution != this.state.providerResolution ||
            state.providerAudioId != this.state.providerAudioId
        this.state = state
        if (restarting) {
            finished = false
            queueConnectionIfNeeded()
        }
        if (providerChanged || restarting) send("playback_start")
        if (changed || !connected) {
            pendingPlaybackIntent = if (send(if (state.playWhenReady) "resumed" else "paused")) null
                else state.playWhenReady
        }
    }

    override fun seek(positionMs: Long): Unit = synchronized(lock) {
        if (!active()) return@synchronized
        httpTelemetry?.seek(positionMs)
        finished = false
        state = state.copy(positionMs = positionMs)
        queueConnectionIfNeeded()
        send("seeked")
    }

    override fun ended() = synchronized(lock) {
        if (!active() || finished) return@synchronized
        httpTelemetry?.ended()
        send("ended")
        finished = true
        pendingPlaybackIntent = null
        state = state.copy(playWhenReady = false)
        reconnect?.cancel(false)
        reconnect = null
        if (!connected) {
            // A pending upgrade must not start a session after the episode ended.
            generation++
            socket?.cancel()
            socket = null
        }
    }

    override fun playbackError(errorCode: String) {
        httpTelemetry?.playbackError(errorCode)
    }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        httpTelemetry?.close()
        generation++
        connected = false
        reconnect?.cancel(false)
        socket?.cancel()
        socket = null
        scheduler.shutdownNow()
        lock.notifyAll()
    }

    private fun active(): Boolean {
        if (closed || failure != null) return false
        // Provider metadata is a renewal hint, not an observed media/socket expiry.
        // A healthy connection and its current token remain usable past this hint.
        return true
    }

    private fun checkUsable() {
        if (closed) throw IOException("Playback session closed")
        active()
        failure?.let { throw it }
    }

    private fun queueConnectionIfNeeded() {
        if (socket == null && reconnect == null && !reconnectExhausted) {
            reconnect = scheduler.schedule({ connect() }, 0, TimeUnit.MILLISECONDS)
        }
    }

    private fun stop(error: IOException) {
        failure = error
        httpTelemetry?.close()
        generation++
        connected = false
        reconnect?.cancel(false)
        socket?.cancel()
        socket = null
        scheduler.shutdownNow()
        lock.notifyAll()
    }

    private fun connect(): Unit = synchronized(lock) {
        reconnect = null
        if (!active() || finished) return@synchronized
        val currentGeneration = ++generation
        connectionCount++
        try {
            val url = descriptor.observedWebSocketUrl.replaceFirst("wss://", "https://")
                    .replaceFirst("ws://", "http://").toHttpUrl().newBuilder()
                    .setQueryParameter("t", now().toString()).build().toString()
            val request = Request.Builder().url(url).apply {
                descriptor.webSocketHeaders.forEach { (name, value) -> header(name, value) }
            }.build()
            socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) = synchronized(lock) {
                    if (currentGeneration != generation || !active()) {
                        webSocket.cancel()
                        return@synchronized
                    }
                    socket = webSocket
                    connected = true
                    successfulConnections++
                    lastHandshakeStatus = response.code
                    send("playback_start")
                    if (!initialized) initialized = send("init")
                    // Playback intent can arrive while the handshake is still pending.
                    // Synchronize it on the opened socket instead of losing that event.
                    val intent = pendingPlaybackIntent ?: true.takeIf { state.playWhenReady }
                    if (intent != null && !finished && send(if (intent) "resumed" else "paused")) {
                        pendingPlaybackIntent = null
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) = synchronized(lock) {
                    if (currentGeneration != generation || !active()) return@synchronized
                    receivedMessages++
                    val message = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
                        ?: return@synchronized
                    if ((message["type"] as? JsonPrimitive)?.content != "config_update") return@synchronized
                    val next = (message["edge_hash"] as? JsonPrimitive)?.content
                    if (!next.isNullOrBlank() && next != "null") {
                        if (token != next) tokenChanges++
                        token = next
                        tokenUpdates++
                        lastTokenUpdateAt = now()
                        attempts = 0
                        lock.notifyAll()
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    disconnected(currentGeneration, null, closeCode = code)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    disconnected(currentGeneration, response, error = t)
                }
            })
        } catch (error: Exception) {
            disconnected(currentGeneration, null, error = error)
        }
    }

    private fun disconnected(callbackGeneration: Long, response: Response?, error: Throwable? = null,
        closeCode: Int? = null): Unit = synchronized(lock) {
        if (callbackGeneration != generation || !active()) return@synchronized
        lastHandshakeStatus = response?.code ?: lastHandshakeStatus
        lastFailureClass = error?.javaClass?.simpleName
        lastCloseCode = closeCode
        generation++ // Any subsequent callbacks from this socket are stale.
        connected = false
        socket?.cancel()
        socket = null
        val disconnectedAt = now()
        val retryAt = response?.let { httpRetryAfterEpochMs(it.headers.toMultimap(), disconnectedAt) }
        // A rejected handshake alone does not invalidate the current media token. Keep it
        // usable while the same bounded reconnect path handles transient socket failures.
        if (response?.code == 429 || (response?.code == 403 && retryAt != null && retryAt > disconnectedAt)) {
            stop(PlaybackSessionRestrictedException(response.code, retryAt))
            return@synchronized
        }
        if (finished) return@synchronized
        if (attempts >= 30) {
            // Like the website, stop reconnecting without invalidating an existing media
            // token. A failed control socket is not evidence of rejected media access.
            reconnectExhausted = true
            if (token == null) stop(IOException("Playback session reconnection limit reached"))
            return@synchronized
        }
        val backoff = (reconnectBaseMs * (1L shl attempts.coerceAtMost(20))).coerceAtMost(15_000)
        val retryDelay = if (response?.code == 503 && retryAt != null && retryAt > disconnectedAt) {
            // Saturate untrusted deadlines rather than overflowing into an immediate retry.
            if (disconnectedAt < 0 && retryAt > Long.MAX_VALUE + disconnectedAt) Long.MAX_VALUE
            else retryAt - disconnectedAt
        } else 0L
        val delay = maxOf(backoff, retryDelay)
        attempts++
        reconnect = scheduler.schedule({ connect() }, delay, TimeUnit.MILLISECONDS)
    }

    private fun send(type: String): Boolean {
        if (!connected) return false
        val fields = template.toMutableMap()
        fields["type"] = JsonPrimitive(type)
        fields["current_time"] = JsonPrimitive(state.positionMs.coerceAtLeast(0) / 1_000)
        fields["resolution"] = JsonPrimitive(state.providerResolution ?: template["resolution"]?.jsonPrimitive?.content.orEmpty())
        fields["track_id"] = JsonPrimitive(state.providerAudioId ?: template["track_id"]?.jsonPrimitive?.content.orEmpty())
        fields["speed"] = JsonPrimitive(state.speed)
        fields["subtitle"] = JsonPrimitive(state.subtitleIndex)
        fields["ts"] = JsonPrimitive(now())
        if (socket?.send(JsonObject(fields).toString()) == true) {
            sentMessages++
            sentEvents[type] = (sentEvents[type] ?: 0) + 1
            return true
        }
        return false
    }
}
