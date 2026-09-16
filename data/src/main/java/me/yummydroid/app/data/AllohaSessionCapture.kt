package me.yummydroid.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Inert discovery state. No socket survives the discovery WebView and no resource is cached. */
internal class AllohaSessionCapture {
    private var socketUrl: String? = null
    private var startTemplate: String? = null
    private var token: String? = null
    private var query: String? = null
    private var guard: String? = null
    private var expiresAt: Long? = null
    private var advertisedPnr: String? = null
    private var advertisedPnk: String? = null
    private val acceptedHosts = mutableSetOf<String>()
    private data class Candidate(val host: String, val token: String, val authorization: String?)
    private val pendingRequests = linkedSetOf<Candidate>()

    val hasObservedSocket: Boolean @Synchronized get() = socketUrl != null
    val requiresSession: Boolean @Synchronized get() =
        socketUrl != null || (advertisedPnr != null && advertisedPnk != null)

    /** Inspect original browser headers only, never headers synthesized by the resolver. */
    @Synchronized
    fun observeRequest(url: String, headers: Map<String, String>): Boolean {
        val host = url.toHttpUrlOrNull()?.host ?: return false
        val controls = headers.entries.firstOrNull { it.key.equals("Accepts-Controls", true) }
            ?.value?.takeIf { it.isNotBlank() } ?: return false
        val authorization = headers.entries.firstOrNull { it.key.equals("Authorizations", true) }?.value
        val candidate = Candidate(host, controls, authorization)
        if (matches(candidate)) return acceptedHosts.add(host)
        pendingRequests.add(candidate)
        while (pendingRequests.size > 64) pendingRequests.remove(pendingRequests.first())
        return false
    }

    private fun matches(candidate: Candidate): Boolean =
        (candidate.token == token || candidate.token == query) &&
            (candidate.authorization == null || guard?.let { candidate.authorization == "Bearer $it" } == true)

    private fun validatePendingRequests() {
        val iterator = pendingRequests.iterator()
        while (iterator.hasNext()) {
            val candidate = iterator.next()
            if (matches(candidate)) {
                acceptedHosts.add(candidate.host)
                iterator.remove()
            }
        }
    }

    @Synchronized
    fun record(raw: String): Boolean {
        if (raw.length > 262_144) return false
        val value = runCatching { VIDEO_RESOLVER_JSON.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return false
        val previousState = listOf(socketUrl, startTemplate, token, query, guard, expiresAt, advertisedPnr, advertisedPnk, acceptedHosts.toSet())
        fun text(name: String) = (value[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        text("socketUrl")?.let { observed ->
            val parsed = observed.replaceFirst(Regex("^ws"), "http").toHttpUrlOrNull()
            if ((observed.startsWith("wss://") || observed.startsWith("ws://")) &&
                !parsed?.queryParameter("sid").isNullOrBlank() && parsed?.queryParameter("v") == "2.1"
            ) {
                if (socketUrl != observed) {
                    // Never combine an old socket's credentials/template with a new SID.
                    val previous = socketUrl?.replaceFirst(Regex("^ws"), "http")?.toHttpUrlOrNull()
                    if (previous != null && (previous.host != parsed.host ||
                            previous.queryParameter("sid") != parsed.queryParameter("sid"))) {
                        startTemplate = null
                        token = null
                        query = null
                        guard = null
                        acceptedHosts.clear()
                        pendingRequests.clear()
                    }
                    socketUrl = observed
                }
            }
        }
        text("startTemplate")?.let { template ->
            val message = runCatching { VIDEO_RESOLVER_JSON.parseToJsonElement(template) as? JsonObject }.getOrNull()
            if ((message?.get("type") as? JsonPrimitive)?.contentOrNull == "playback_start") startTemplate = template
        }
        text("token")?.let { token = it }
        text("query")?.let { query = it }
        text("guard")?.let { guard = it }
        text("pnr")?.let { advertisedPnr = it }
        text("pnk")?.let { advertisedPnk = it }
        (value["expiresAt"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }?.let { expiresAt = it }
        validatePendingRequests()
        return previousState != listOf(socketUrl, startTemplate, token, query, guard, expiresAt, advertisedPnr, advertisedPnk, acceptedHosts.toSet())
    }

    @Synchronized
    fun descriptor(
        stream: ResolvedVideoStream,
        socketHeaders: (String) -> Map<String, String>,
    ): AllohaSessionDescriptor? {
        val observedUrl = socketUrl ?: return null
        val template = startTemplate ?: return null
        val capturedToken = stream.headers.entries.firstOrNull { it.key.equals("Accepts-Controls", true) }?.value
        val capturedGuard = stream.headers.entries.firstOrNull { it.key.equals("Authorizations", true) }?.value
            ?.removePrefix("Bearer ")
        return AllohaSessionDescriptor(
            observedWebSocketUrl = observedUrl,
            webSocketHeaders = socketHeaders(observedUrl),
            playbackStartTemplate = template,
            initialToken = token ?: capturedToken ?: query,
            guardToken = guard ?: capturedGuard,
            expiresAtEpochMs = expiresAt,
            mediaHosts = (listOf(stream.url) + stream.fallbackUrls + stream.alternatives.map { it.url })
                .mapNotNull { it.toHttpUrlOrNull()?.host }.toSet() + acceptedHosts,
        )
    }
}

// Inject before page scripts capture the browser's WebSocket constructor.
internal val ALLOHA_SESSION_CAPTURE_SCRIPT = """
    (function() {
        if (window.__yummySessionCaptureInstalled) return;
        window.__yummySessionCaptureInstalled = true;
        function report(value) {
            try {
                var bridge = window.YummyResolverBridge;
                if (bridge && bridge.captureSession) bridge.captureSession(JSON.stringify(value));
            } catch (_) {}
        }
        function isSession(url) {
            try { var u = new URL(url, location.href); return /^wss?:${'$'}/.test(u.protocol) &&
                !!u.searchParams.get('sid') && u.searchParams.get('v') === '2.1'; } catch (_) { return false; }
        }
        var NativeWebSocket = window.WebSocket;
        var activeSocket = null;
        function CapturedWebSocket(url, protocols) {
            var socket = arguments.length > 1 ? new NativeWebSocket(url, protocols) : new NativeWebSocket(url);
            if (!isSession(url)) return socket;
            activeSocket = socket;
            report({socketUrl:String(url)});
            var send = socket.send;
            socket.send = function(body) {
                try {
                    var message = JSON.parse(body);
                    if (socket === activeSocket && message.type === 'playback_start') report({socketUrl:String(url), startTemplate:String(body)});
                } catch (_) {}
                return send.apply(this, arguments);
            };
            socket.addEventListener('message', function(event) {
                try {
                    if (socket !== activeSocket) return;
                    var message = JSON.parse(event.data);
                    if (message.type === 'config_update' && message.edge_hash)
                        report({socketUrl:String(url), token:String(message.edge_hash)});
                } catch (_) {}
            });
            return socket;
        }
        if (NativeWebSocket) {
            CapturedWebSocket.prototype = NativeWebSocket.prototype;
            Object.setPrototypeOf(CapturedWebSocket, NativeWebSocket);
            window.WebSocket = CapturedWebSocket;
        }
        function isMetadata(url) {
            try { return /\/bnsi\/(movies|trailers)\//.test(new URL(url, location.href).pathname); }
            catch (_) { return false; }
        }
        function metadata(body) {
            try {
                var data = typeof body === 'string' ? JSON.parse(body) : body;
                if (data) report({expiresAt:Number(data.time) > 0 ? Number(data.time) : null,
                    pnr:data.pnr, pnk:data.pnk});
            } catch (_) {}
        }
        var originalFetch = window.fetch;
        if (originalFetch) window.fetch = function(input) {
            var promise = originalFetch.apply(this, arguments);
            var url = typeof input === 'string' ? input : input && input.url;
            if (isMetadata(url)) promise.then(function(response) {
                response.clone().text().then(metadata).catch(function() {});
            }).catch(function() {});
            return promise;
        };
        var originalSend = XMLHttpRequest.prototype.send;
        XMLHttpRequest.prototype.send = function() {
            this.addEventListener('load', function() {
                try { if (isMetadata(this.responseURL)) metadata(this.responseType === 'json' ? this.response : this.responseText); }
                catch (_) {}
            });
            return originalSend.apply(this, arguments);
        };
        var attempts = 120;
        var poll = setInterval(function() {
            try {
                var player = window.player || window.allplay || window.videoPlayer;
                var q = player && player.reloadManifestQuery;
                if (q) report({query:q.query, guard:q.guard,
                    token:typeof q.getStreamToken === 'function' ? q.getStreamToken() : null});
            } catch (_) {}
            if (--attempts <= 0) clearInterval(poll);
        }, 250);
    })();
""".trimIndent()
