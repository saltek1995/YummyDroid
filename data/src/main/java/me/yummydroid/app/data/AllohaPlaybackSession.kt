package me.yummydroid.app.data

import java.io.IOException
import java.io.InterruptedIOException
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    private val template = Json.parseToJsonElement(descriptor.playbackStartTemplate).jsonObject
    private val hosts = descriptor.mediaHosts.map { it.lowercase() }.toSet()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "alloha-playback").apply { isDaemon = true }
    }
    private var state = PlaybackRuntimeState()
    private var token = descriptor.initialToken?.takeIf { it.isNotBlank() }
    private var socket: WebSocket? = null
    private var connected = false
    private var generation = 0L
    private var closed = false
    private var finished = false
    private var failure: IOException? = null
    private var attempts = 0
    private var reconnect: ScheduledFuture<*>? = null

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

    override fun update(state: PlaybackRuntimeState) = synchronized(lock) {
        if (!active()) return@synchronized
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
        if (changed) send(if (state.playWhenReady) "resumed" else "paused")
    }

    override fun seek(positionMs: Long) = synchronized(lock) {
        if (!active()) return@synchronized
        finished = false
        state = state.copy(positionMs = positionMs)
        queueConnectionIfNeeded()
        send("seeked")
    }

    override fun ended() = synchronized(lock) {
        if (!active() || finished) return@synchronized
        send("ended")
        finished = true
        state = state.copy(playWhenReady = false)
        reconnect?.cancel(false)
        reconnect = null
    }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
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
        if (socket == null && reconnect == null) {
            reconnect = scheduler.schedule({ connect() }, 0, TimeUnit.MILLISECONDS)
        }
    }

    private fun stop(error: IOException) {
        failure = error
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
                    send("playback_start")
                }

                override fun onMessage(webSocket: WebSocket, text: String) = synchronized(lock) {
                    if (currentGeneration != generation || !active()) return@synchronized
                    val message = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
                        ?: return@synchronized
                    if ((message["type"] as? JsonPrimitive)?.content != "config_update") return@synchronized
                    val next = (message["edge_hash"] as? JsonPrimitive)?.content
                    if (!next.isNullOrBlank() && next != "null") {
                        token = next
                        attempts = 0
                        lock.notifyAll()
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    disconnected(currentGeneration, null)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    disconnected(currentGeneration, response)
                }
            })
        } catch (_: Exception) {
            disconnected(currentGeneration, null)
        }
    }

    private fun disconnected(callbackGeneration: Long, response: Response?): Unit = synchronized(lock) {
        if (callbackGeneration != generation || !active()) return@synchronized
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
            stop(IOException("Playback session reconnection limit reached"))
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

    private fun send(type: String) {
        if (!connected) return
        val fields = template.toMutableMap()
        fields["type"] = JsonPrimitive(type)
        fields["current_time"] = JsonPrimitive(state.positionMs.coerceAtLeast(0) / 1_000)
        fields["resolution"] = JsonPrimitive(state.providerResolution ?: template["resolution"]?.jsonPrimitive?.content.orEmpty())
        fields["track_id"] = JsonPrimitive(state.providerAudioId ?: template["track_id"]?.jsonPrimitive?.content.orEmpty())
        fields["speed"] = JsonPrimitive(state.speed)
        fields["subtitle"] = JsonPrimitive(state.subtitleIndex)
        fields["ts"] = JsonPrimitive(now())
        socket?.send(JsonObject(fields).toString())
    }
}
