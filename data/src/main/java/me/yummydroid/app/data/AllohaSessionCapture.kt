package me.yummydroid.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Inert discovery state. No socket survives the discovery WebView and no resource is cached. */
internal class AllohaSessionCapture(private val providerPageUrl: String? = null, private val captureNonce: String? = null) {
    private var telemetry: JsonObject? = null
    private var providerReadiness: JsonObject? = null
    private val providerHeaders = mutableMapOf<String, Map<String, String>>()

    /** Readiness flags only: never include provider URLs, credentials or identifiers. */
    @Synchronized
    fun readinessSummary(): String = listOf(
        "socket=${socketUrl != null}", "template=${startTemplate != null}", "token=${token != null || query != null}",
        "http=${telemetry != null}", "envelope=${telemetry?.get("envelope") is JsonObject}",
        "statInfo=${telemetry?.get("statInfo") is JsonObject}",
        "file=${telemetry?.get("fileId") != null}",
        "providerReady=${providerReadiness?.get("ready") == JsonPrimitive(true)}",
        "providerMedia=${providerReadiness?.get("video") == JsonPrimitive(true)}",
        "providerSource=${providerReadiness?.get("source") == JsonPrimitive(true)}",
        "providerControl=${providerReadiness?.get("control") == JsonPrimitive(true)}",
        "eventsReady=${providerReadiness?.get("eventsReady") == JsonPrimitive(true)}",
        "statReady=${providerReadiness?.get("statReady") == JsonPrimitive(true)}",
        "startAttempted=${providerReadiness?.get("attempted") == JsonPrimitive(true)}",
    ).joinToString(",")

    @Synchronized
    fun observeProviderRequest(url: String, headers: Map<String, String>) {
        val page = providerPageUrl?.toHttpUrlOrNull() ?: return
        val request = url.toHttpUrlOrNull() ?: return
        if (request.scheme == page.scheme && request.host == page.host && request.port == page.port &&
            request.encodedPath in setOf("/events", "/stat")) providerHeaders[request.encodedPath] = headers.toMap()
    }

    @Synchronized
    fun telemetryDescriptor(headers: (String, Map<String, String>) -> Map<String, String>): AllohaHttpTelemetryDescriptor? {
        val page = providerPageUrl ?: return null
        val state = telemetry ?: return null
        fun text(key: String) = (state[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val envelope = state["envelope"] as? JsonObject ?: return null
        val info = state["statInfo"] as? JsonObject ?: return null
        if ((envelope["token"] as? JsonPrimitive)?.contentOrNull != text("token") ||
            (envelope["domain"] as? JsonPrimitive)?.contentOrNull != text("domain")) return null
        if ((envelope["clientSessionId"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank() ||
            (envelope["clientRequestId"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank()) return null
        val endpointHeaders = listOf("/events", "/stat").associateWith { path ->
            headers(page.toHttpUrlOrNull()!!.newBuilder().encodedPath(path).query(null).fragment(null).build().toString(),
                providerHeaders[path].orEmpty())
        }
        return AllohaHttpTelemetryDescriptor(
            providerPageUrl = page, headers = emptyMap(), endpointHeaders = endpointHeaders,
            token = text("token") ?: return null, domain = text("domain") ?: return null,
            fileId = text("fileId") ?: return null, statType = text("statType") ?: "mgpo",
            environment = envelope["env"] as? JsonObject ?: return null, statInfo = info,
            isTrailer = (envelope["isTrailer"] as? JsonPrimitive)?.contentOrNull == "true",
            initialEnvelope = envelope,
            adBlock = (state["statAb"] as? JsonPrimitive)?.contentOrNull?.let {
                when (it) { "true" -> true; "false" -> false; else -> null }
            },
            observedEvents = (state["events"] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty(),
            observedStatPercents = (state["percents"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }?.toSet().orEmpty(),
            initialStatSent = (state["initialStatSent"] as? JsonPrimitive)?.contentOrNull == "true",
            pageEpochMs = (state["pageEpochMs"] as? JsonPrimitive)?.longOrNull ?: return null,
        )
    }
    private var socketUrl: String? = null
    private var startTemplate: String? = null
    private var token: String? = null
    private var query: String? = null
    private var guard: String? = null
    private var expiresAt: Long? = null
    private var advertisedPnr: String? = null
    private var advertisedPnk: String? = null
    private val acceptedHosts = mutableSetOf<String>()
    private val startupEvents = mutableSetOf<String>()
    private var browserLanguages: List<String> = emptyList()
    private var observedAcceptLanguage: String? = null
    private data class Candidate(val host: String, val token: String, val authorization: String?)
    private val pendingRequests = linkedSetOf<Candidate>()

    val hasObservedSocket: Boolean @Synchronized get() = socketUrl != null
    val requiresSession: Boolean @Synchronized get() =
        socketUrl != null || (advertisedPnr != null && advertisedPnk != null)

    @Synchronized
    fun browserAcceptLanguage(webViewVersion: String?): String? =
        observedAcceptLanguage ?: allohaBrowserAcceptLanguage(browserLanguages, webViewVersion)

    /** Inspect original browser headers only, never headers synthesized by the resolver. */
    @Synchronized
    fun observeRequest(url: String, headers: Map<String, String>): Boolean {
        val host = url.toHttpUrlOrNull()?.host ?: return false
        val controls = headers.entries.firstOrNull { it.key.equals("Accepts-Controls", true) }
            ?.value?.takeIf { it.isNotBlank() } ?: return false
        headers.entries.firstOrNull { it.key.equals("Accept-Language", true) }?.value
            ?.takeIf { it.isNotBlank() }?.let { observedAcceptLanguage = it }
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
        if (captureNonce != null && (value["captureNonce"] as? JsonPrimitive)?.contentOrNull != captureNonce) return false
        if (providerPageUrl != null) {
            val reportedPage = (value["pageUrl"] as? JsonPrimitive)?.contentOrNull
            if (reportedPage != providerPageUrl) return false
        }
        val previousReadiness = providerReadiness
        (value["providerReadiness"] as? JsonObject)?.let { providerReadiness = it }
        val previousTelemetry = telemetry
        (value["telemetry"] as? JsonObject)?.let { telemetry = it }
        val previousState = listOf(socketUrl, startTemplate, token, query, guard, expiresAt, advertisedPnr, advertisedPnk, acceptedHosts.toSet(), startupEvents.toSet(), browserLanguages)
        fun text(name: String) = (value[name] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        (value["languages"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.takeIf { it.isNotEmpty() }?.let { browserLanguages = it.take(20) }
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
                        telemetry = null
                        startTemplate = null
                        token = null
                        query = null
                        guard = null
                        acceptedHosts.clear()
                        pendingRequests.clear()
                        startupEvents.clear()
                    }
                    socketUrl = observed
                }
            }
        }
        text("startTemplate")?.let { template ->
            val message = runCatching { VIDEO_RESOLVER_JSON.parseToJsonElement(template) as? JsonObject }.getOrNull()
            if ((message?.get("type") as? JsonPrimitive)?.contentOrNull == "playback_start") startTemplate = template
        }
        text("startupEvent")?.takeIf { it in setOf("playback_start", "init", "resumed", "paused") }
            ?.let(startupEvents::add)
        text("token")?.let { token = it }
        text("query")?.let { query = it }
        text("guard")?.let { guard = it }
        text("pnr")?.let { advertisedPnr = it }
        text("pnk")?.let { advertisedPnk = it }
        (value["expiresAt"] as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }?.let { expiresAt = it }
        validatePendingRequests()
        return previousReadiness != providerReadiness || previousTelemetry != telemetry || previousState != listOf(socketUrl, startTemplate, token, query, guard, expiresAt, advertisedPnr, advertisedPnk, acceptedHosts.toSet(), startupEvents.toSet(), browserLanguages)
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
            observedStartupEvents = startupEvents.toSet(),
        )
    }
}

// Inject before page scripts capture the browser's WebSocket constructor.
internal val ALLOHA_SESSION_CAPTURE_SCRIPT = """
    (function() {
        if (window.__yummySessionCaptureInstalled) return;
        window.__yummySessionCaptureInstalled = true;
        var expectedPage = window.__yummyExpectedProviderPage;
        if (expectedPage && location.href !== expectedPage) return;
        function report(value) {
            value.pageUrl = location.href;
            value.captureNonce = window.__yummyCaptureNonce;
            try {
                var bridge = window.YummyResolverBridge;
                if (bridge && bridge.captureSession) bridge.captureSession(JSON.stringify(value));
            } catch (_) {}
        }
        function isSession(url) {
            try { var u = new URL(url, location.href); return /^wss?:${'$'}/.test(u.protocol) &&
                !!u.searchParams.get('sid') && u.searchParams.get('v') === '2.1'; } catch (_) { return false; }
        }
        report({languages:Array.prototype.slice.call(navigator.languages || [navigator.language], 0, 20)});
        var NativeWebSocket = window.WebSocket;
        var activeSocket = null;
        function CapturedWebSocket(url, protocols) {
            var socket = arguments.length > 1 ? new NativeWebSocket(url, protocols) : new NativeWebSocket(url);
            if (!isSession(url)) return socket;
            if (activeSocket && http) http = {events:[], percents:[], initialStatSent:false, pageEpochMs:http.pageEpochMs};
            activeSocket = socket;
            report({socketUrl:String(url)});
            var send = socket.send;
            socket.send = function(body) {
                var result = send.apply(this, arguments);
                try {
                    var message = JSON.parse(body);
                    if (socket === activeSocket) {
                        if (message.type === 'playback_start') report({socketUrl:String(url), startTemplate:String(body), startupEvent:message.type});
                        else if (message.type === 'init' || message.type === 'resumed' || message.type === 'paused')
                            report({socketUrl:String(url), startupEvent:message.type});
                    }
                } catch (_) {}
                return result;
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
            try { var u = new URL(url, location.href); return u.origin === location.origin && /\/bnsi\/(movies|trailers)\//.test(u.pathname); }
            catch (_) { return false; }
        }
        function metadata(body) {
            try {
                if (typeof body === 'string' && body.length > 2097152) return;
                var data = typeof body === 'string' ? JSON.parse(body) : body;
                if (data && http && (data.fileID || data.id_file)) {
                    http.fileId = String(data.fileID || data.id_file); publishHttp();
                }
                if (data) report({expiresAt:Number(data.time) > 0 ? Number(data.time) : null,
                    pnr:data.pnr, pnk:data.pnk});
            } catch (_) {}
        }

        var transferred = false;
        var handoffRequested = false;
        var pendingHttp = 0;
        var http = {events:[], percents:[], initialStatSent:false,
            pageEpochMs: Math.round(performance.timeOrigin || (Date.now() - performance.now()))};
        function publishHttp() { report({telemetry:http}); }
        function finishHandoff() {
            if (!handoffRequested || pendingHttp !== 0 || transferred) return;
            try {
                transferred = true;
                window.YummyResolverBridge.handoffSession(JSON.stringify({pageUrl:location.href, captureNonce:window.__yummyCaptureNonce, telemetry:http}));
            } catch (_) {}
        }
        function beginHttp() {
            pendingHttp++;
            var settled = false;
            return function(success) {
                if (settled) return;
                settled = true;
                pendingHttp--;
                if (!success) { http.deliveryFailed = true; publishHttp(); }
                finishHandoff();
            };
        }
        window.addEventListener('message', function(event) {
            if (event.source !== window.parent || event.origin !== window.__yummyExpectedParentOrigin ||
                event.data !== '__yummySessionHandoff') return;
            handoffRequested = true;
            finishHandoff();
        });
        function requestPath(url) {
            try { var u = new URL(url, location.href);
                return u.origin === location.origin && (u.pathname === '/events' || u.pathname === '/stat') ? u.pathname : null;
            } catch (_) { return null; }
        }
        function formBody(body) {
            var result = {};
            if (typeof body === 'string') {
                if (body.length > 65536) return null;
                new URLSearchParams(body).forEach(function(v,k) { result[k] = v; });
            } else if (body instanceof URLSearchParams || (typeof FormData !== 'undefined' && body instanceof FormData)) {
                var size = 0;
                body.forEach(function(v,k) { if (typeof v !== 'string') throw Error('binary body');
                    size += k.length + v.length; if (size > 65536) throw Error('large body'); result[k] = v; });
            } else return null;
            return result;
        }
        function captureHttp(url, body) {
            try {
                var path = requestPath(url); if (!path) return null;
                var form = formBody(body); if (!form) return null;
                var candidate = JSON.parse(JSON.stringify(http));
                if (path === '/events') {
                    var envelope = JSON.parse(form.payload);
                    if (!envelope || !envelope.clientSessionId || !envelope.clientRequestId ||
                        !envelope.token || envelope.token !== form.token || !envelope.domain || !envelope.env) return null;
                    if (candidate.token && candidate.token !== form.token) return null;
                    if (candidate.envelope && candidate.envelope.clientSessionId !== envelope.clientSessionId) return null;
                    candidate.token = form.token; candidate.domain = envelope.domain;
                    candidate.envelope = envelope;
                    candidate.events = candidate.events.concat(envelope.events || []).slice(-256);
                } else {
                    if (!form.token || !form.id || !form.domain) return null;
                    if (candidate.token && candidate.token !== form.token) return null;
                    if (candidate.domain && candidate.domain !== form.domain) return null;
                    if (candidate.fileId && candidate.fileId !== form.id) return null;
                    candidate.token = form.token; candidate.domain = form.domain; candidate.fileId = form.id;
                    candidate.statType = form.type;
                    if (form.ab === 'true' || form.ab === 'false') candidate.statAb = form.ab === 'true';
                    var info = {};
                    Object.keys(form).forEach(function(key) {
                        var parts = /^info\[([^\]]+)\](?:\[([^\]]+)\])?${'$'}/.exec(key);
                        if (parts && parts[1] !== '__proto__' && parts[1] !== 'constructor' && parts[2] !== '__proto__') {
                            var value = form[key];
                            if (value === 'true' || value === 'false') value = value === 'true';
                            else if (/^-?\d+(?:\.\d+)?${'$'}/.test(value)) value = Number(value);
                            if (parts[2]) { if (!info[parts[1]]) info[parts[1]] = {}; info[parts[1]][parts[2]] = value; }
                            else info[parts[1]] = value;
                        }
                    });
                    if (Object.keys(info).length) candidate.statInfo = info;
                    if (form.percent === undefined) candidate.initialStatSent = true;
                    else if (/^\d+${'$'}/.test(form.percent) && candidate.percents.indexOf(Number(form.percent)) < 0)
                        candidate.percents.push(Number(form.percent));
                }
                while (candidate.events.length > 1 && JSON.stringify(candidate).length > 196608) candidate.events.shift();
                if (JSON.stringify(candidate).length > 196608) return null;
                return candidate;
            } catch (_) { return null; }
        }
        function commitHttp(candidate) { if (candidate) { http = candidate; publishHttp(); } }
        var originalBeacon = navigator.sendBeacon;
        if (originalBeacon) navigator.sendBeacon = function(url, body) {
            if (transferred && requestPath(url)) return true;
            var candidate = captureHttp(url, body);
            var result = originalBeacon.apply(this, arguments);
            if (result) commitHttp(candidate);
            return result;
        };
        var originalOpen = XMLHttpRequest.prototype.open;
        if (originalOpen) XMLHttpRequest.prototype.open = function(method, url) {
            var result = originalOpen.apply(this, arguments);
            this.__yummyPostUrl = String(method).toUpperCase() === 'POST' ? String(url) : null;
            return result;
        };
        var originalFetch = window.fetch;
        if (originalFetch) window.fetch = function(input) {
            var url = typeof input === 'string' ? input : input && input.url;
            if (transferred && requestPath(url)) return Promise.resolve(new Response('', {status:200}));
            var init = arguments[1];
            var isPost = String((init && init.method) || (input && input.method) || 'GET').toUpperCase() === 'POST';
            var candidate = init && isPost ? captureHttp(url, init.body) : null;
            var promise = originalFetch.apply(this, arguments);
            commitHttp(candidate);
            if (isPost && requestPath(url)) {
                var settled = beginHttp();
                // Observe settlement without replacing the caller's original promise.
                promise.then(function(response) { settled(response.status >= 200 && response.status < 400); },
                    function() { settled(false); });
            }
            if (isMetadata(url)) promise.then(function(response) {
                response.clone().text().then(metadata).catch(function() {});
            }).catch(function() {});
            return promise;
        };
        var originalSend = XMLHttpRequest.prototype.send;
        XMLHttpRequest.prototype.send = function() {
            if (transferred && requestPath(this.__yummyPostUrl)) return;
            var candidate = captureHttp(this.__yummyPostUrl, arguments[0]);
            this.addEventListener('load', function() {
                try { if (isMetadata(this.responseURL)) metadata(this.responseType === 'json' ? this.response : this.responseText); }
                catch (_) {}
            });
            var settled = requestPath(this.__yummyPostUrl) ? beginHttp() : null;
            var xhr = this;
            if (settled) this.addEventListener('loadend', function() {
                // Also handles synchronous XHR: snapshot commit must precede settlement.
                var accepted = xhr.status >= 200 && xhr.status < 400;
                Promise.resolve().then(function() { settled(accepted); });
            }, {once:true});
            var result;
            try { result = originalSend.apply(this, arguments); }
            catch (error) { if (settled) settled(true); throw error; }
            commitHttp(candidate);
            return result;
        };
        var startAttempted = false;
        var lastReadiness = '';
        function startProviderOnce(player) {
            if (transferred || handoffRequested) return;
            var media = player && player.media;
            var query = player && player.reloadManifestQuery;
            var hasSource = !!(query && query.query);
            var container = player && player.elements && player.elements.container;
            var control = container && container.querySelector
                ? container.querySelector('.allplay__control--overlaid[data-allplay="play"]') : null;
            // These read-only markers follow the provider's HTTP listener installation.
            // The scoped /stat listener is registered only after its asynchronous capability probes.
            var statReady = Array.isArray(player && player.eventListeners) && player.eventListeners.some(function(entry) {
                if (!entry || entry.type !== 'play' || entry.element !== container || typeof entry.callback !== 'function') return false;
                try { return /["']\/stat["']/.test(Function.prototype.toString.call(entry.callback)); }
                catch (_) { return false; }
            });
            var state = {ready:!!(player && player.ready === true), video:media instanceof HTMLVideoElement && !!(container && container.contains(media)),
                source:hasSource, control:!!control, eventsReady:typeof window.storageAvailable === 'function',
                statReady:statReady, attempted:startAttempted, playing:!!(media && !media.paused)};
            if (!startAttempted && state.ready && state.video && state.source && state.eventsReady && state.statReady && control && media.paused) {
                // The exact normal player control runs its first-click and HLS initialization handlers.
                // Mark before dispatch: discovery never retries clicks or touches advertisement controls.
                startAttempted = true;
                state.attempted = true;
                control.click();
            }
            var signature = JSON.stringify(state);
            if (signature !== lastReadiness) { lastReadiness = signature; report({providerReadiness:state}); }
        }
        var attempts = 120;
        var poll = setInterval(function() {
            try {
                var params = window.userParam;
                if (params && http && params.token && params.domain && (!http.token || http.token === String(params.token))) {
                    var changed = !http.token || !http.domain || (!http.fileId && params.id_file);
                    http.token = String(params.token); http.domain = String(params.domain);
                    if (!http.fileId && params.id_file) http.fileId = String(params.id_file);
                    if (changed) publishHttp();
                }
                var player = window.player || window.allplay || window.videoPlayer;
                startProviderOnce(player);
                var q = player && player.reloadManifestQuery;
                if (q) report({query:q.query, guard:q.guard,
                    token:typeof q.getStreamToken === 'function' ? q.getStreamToken() : null});
            } catch (_) {}
            if (--attempts <= 0) clearInterval(poll);
        }, 250);
    })();
""".trimIndent()
