package me.yummydroid.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Explicit diagnostic sink only; payloads contain credentials and must never enter normal logs. */
class AllohaBootstrapDiagnostics(val record: (String, String) -> Unit) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AllohaBootstrapDiagnostics>
}

/** Runs only provider bootstrap/probes. No website player, browser media, or advertising code. */
internal class AllohaBootstrapResolver(
    context: Context?,
    private val client: OkHttpClient,
    private val inspector: PlayerMetadataInspector,
) {
    private val appContext = context?.applicationContext

    suspend fun resolve(sourceUrl: String, siteBaseUrl: String, quality: PreferredQuality): ResolvedVideoStream =
        withContext(Dispatchers.Main) {
            val context = appContext ?: throw UnsupportedAllohaBootstrap()
            suspendCancellableCoroutine { continuation ->
                Bootstrap(context, client, inspector, sourceUrl, siteBaseUrl, quality, continuation).start()
            }
        }
}

private class Bootstrap(
    private val context: Context,
    private val client: OkHttpClient,
    private val inspector: PlayerMetadataInspector,
    private val sourceUrl: String,
    private val siteBaseUrl: String,
    private val quality: PreferredQuality,
    private val continuation: CancellableContinuation<ResolvedVideoStream>,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val view = WebView(context)
    private val startedAt = SystemClock.uptimeMillis()
    private val termination = WebViewSessionTermination(continuation.context)
    private val nonce = UUID.randomUUID().toString()
    private val origin = requireNotNull(sourceUrl.urlOrigin())
    @Volatile private var page: AllohaBootstrapPage? = null
    @Volatile private var guard: String? = null
    @Volatile private var browserHeaders: Map<String, String> = emptyMap()
    @Volatile private var browserLanguage: String? = null
    private var metadataCapture: PlayerMetadataCapture? = null
    private var cleaned = false
    private val stages = linkedMapOf<String, Long>()

    @SuppressLint("SetJavaScriptEnabled")
    fun start() {
        continuation.invokeOnCancellation { if (termination.tryTerminate()) handler.post(::cleanup) }
        if (!continuation.isActive) return cleanup()
        view.settings.javaScriptEnabled = true
        view.settings.domStorageEnabled = true
        view.settings.mediaPlaybackRequiresUserGesture = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.addJavascriptInterface(Bridge(), "YummyBootstrapBridge")
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (termination.isTerminated || request == null) return emptyResponse()
                val url = request.url.toString()
                continuation.context[AllohaBootstrapDiagnostics]?.record?.invoke("browserRequest", buildJsonObject {
                    put("url", url); put("method", request.method)
                    put("headers", JsonObject(request.requestHeaders.mapValues { JsonPrimitive(it.value) }))
                }.toString())
                return try {
                    if (url == sourceUrl && request.method == "GET") {
                        // Do not mix an unknown browser identity with native bootstrap requests.
                        // Compatibility discovery can handle older WebViews before any metadata POST.
                        if (browserLanguage == null) throw UnsupportedAllohaBootstrap()
                        browserHeaders = request.requestHeaders.toMap() +
                            (browserLanguage?.let { mapOf("Accept-Language" to it) } ?: emptyMap())
                        val html = termination.runRequest {
                            val source = sourceUrl.toHttpUrl()
                            val parent = siteBaseUrl.toHttpUrl()
                            val site = when {
                                sourceUrl.urlOrigin() == siteBaseUrl.urlOrigin() -> "same-origin"
                                source.scheme == parent.scheme && source.topPrivateDomain() != null &&
                                    source.topPrivateDomain() == parent.topPrivateDomain() -> "same-site"
                                else -> "cross-site"
                            }
                            val providerHtml = fetchText(sourceUrl, browserHeaders + mapOf(
                                "Sec-Fetch-Dest" to "iframe", "Sec-Fetch-Mode" to "navigate", "Sec-Fetch-Site" to site,
                                "Sec-Fetch-Storage-Access" to "active", "X-Requested-With" to context.packageName,
                                "Priority" to "u=0, i",
                            ))
                            val parsed = parseAllohaBootstrapPage(providerHtml, sourceUrl)
                            val program = fetchText(parsed.bundleUrl, browserHeaders + mapOf(
                                "Referer" to sourceUrl, "Sec-Fetch-Dest" to "script", "Sec-Fetch-Mode" to "no-cors",
                                "Sec-Fetch-Site" to "same-origin", "Accept" to "*/*",
                            ))
                            val suppliedGuard = extractAllohaBootstrapGuard(program)
                            page = parsed
                            guard = suppliedGuard
                            document(parsed)
                        }
                        response("text/html", html)
                    } else if (url.startsWith("$origin/bnsi/") && request.method == "POST") {
                        continuation.context[HttpRequestPolicy]?.beforeRequest()
                        null
                    } else emptyResponse()
                } catch (failure: Exception) {
                    handler.post { finish(Result.failure(failure)) }
                    emptyResponse()
                }
            }

            override fun onReceivedHttpError(v: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                continuation.context[AllohaBootstrapDiagnostics]?.record?.invoke("browserHttpError", buildJsonObject {
                    put("url", request?.url?.toString()); put("status", errorResponse?.statusCode)
                }.toString())
                if (request?.url?.toString()?.startsWith("$origin/bnsi/") != true) return
                val status = errorResponse?.statusCode ?: return
                try {
                    continuation.context[HttpRequestPolicy]?.onResponse(status)
                } catch (failure: Exception) { finish(Result.failure(failure)) }
            }

            override fun onReceivedError(v: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                continuation.context[AllohaBootstrapDiagnostics]?.record?.invoke("browserNetworkError", buildJsonObject {
                    put("url", request?.url?.toString()); put("code", error?.errorCode)
                    put("description", error?.description?.toString())
                }.toString())
            }

            override fun onReceivedSslError(v: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                continuation.context[AllohaBootstrapDiagnostics]?.record?.invoke("browserTlsError", buildJsonObject {
                    put("url", error?.url); put("code", error?.primaryError)
                }.toString())
                handler?.cancel()
            }
        }
        handler.postDelayed({ finish(Result.failure(IOException("Alloha bootstrap timed out"))) }, 25_000)
        // Preserve iframe origin/referrer and viewport without running the site's parent UI.
        val sourceLiteral = JsonPrimitive(sourceUrl).toString().replace("<", "\\u003c")
        val nonceLiteral = JsonPrimitive(nonce).toString()
        view.loadDataWithBaseURL(siteBaseUrl,
            "<!doctype html><meta name=referrer content=no-referrer-when-downgrade>" +
                "<body><script>YummyBootstrapBridge.environment(JSON.stringify({nonce:$nonceLiteral," +
                "languages:Array.from(navigator.languages||[navigator.language])}));" +
                "var frame=document.createElement('iframe');frame.width=1280;frame.height=720;" +
                "frame.src=$sourceLiteral;document.body.appendChild(frame);</script>", "text/html", "UTF-8", null)
    }

    private suspend fun fetchText(url: String, headers: Map<String, String>): String {
        val request = Request.Builder().url(url).apply {
            headers.filterKeys { it.lowercase() !in setOf("host", "content-length", "accept-encoding", "connection") }
                .forEach { (name, value) -> header(name, value) }
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
        }.build()
        return client.withCancellableResponse(request) { response ->
            if (!response.isSuccessful) throw PlaybackHttpException(response.code)
            for (cookie in response.headers.values("Set-Cookie")) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { c ->
                        CookieManager.getInstance().setCookie(url, cookie) { if (c.isActive) c.resume(Unit) }
                    }
                }
            }
            val body = response.body ?: throw IOException("Alloha bootstrap is empty")
            if (body.contentLength() > 2_097_152) throw UnsupportedAllohaBootstrap()
            val source = body.source()
            source.request(2_097_153)
            if (source.buffer.size > 2_097_152) throw UnsupportedAllohaBootstrap()
            source.buffer.readString(body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8).also {
                continuation.context[AllohaBootstrapDiagnostics]?.record?.invoke(
                    if (url == sourceUrl) "providerHtml" else "providerProgram", it)
            }
        }
    }

    private fun document(parsed: AllohaBootstrapPage): String {
        val config = buildJsonObject {
            put("userParam", parsed.userParam); put("fileList", parsed.fileList); put("movie", parsed.movie)
            put("viewportSeed", parsed.viewportSeed); put("captureNonce", nonce)
            put("probeWebSocketUrl", "wss://echo.websocket.org")
        }.toString().replace("<", "\\u003c")
        val script = context.assets.open("alloha-bootstrap.js").bufferedReader().use { it.readText() }
        return "<!doctype html><html><head><meta charset=utf-8>" +
            "<meta name=referrer content=no-referrer-when-downgrade></head><body>" +
            "<script>window.__yummyBootstrap=$config;</script><script>$script</script></body></html>"
    }

    private fun identityHeaders(): Map<String, String> = browserHeaders.filterKeys {
        it.lowercase() in setOf("user-agent", "accept-language", "sec-ch-ua", "sec-ch-ua-mobile", "sec-ch-ua-platform")
    }

    private fun capture(body: String): PlayerMetadataCapture = inspector.inspect(
        url = sourceUrl, body = VIDEO_RESOLVER_JSON.parseToJsonElement(body).jsonObject.withAllohaBootstrapSelection().toString(),
        requestHeaders = identityHeaders(), sourceUrl = sourceUrl,
        siteBaseUrl = siteBaseUrl, preferredQuality = quality,
    )

    private inner class Bridge {
        @JavascriptInterface fun environment(raw: String) {
            val data = decode(raw) ?: return
            val languages = (data["languages"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
            browserLanguage = allohaBrowserAcceptLanguage(languages, WebViewCompat.getCurrentWebViewPackage(context)?.versionName)
        }

        private fun decode(raw: String): JsonObject? {
            if (termination.isTerminated || raw.length > 2_097_152) return null
            val data = runCatching { VIDEO_RESOLVER_JSON.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
            return data.takeIf { it.string("nonce") == nonce }
        }

        @JavascriptInterface fun metadata(raw: String) {
            val data = decode(raw) ?: return
            val body = data.string("body") ?: return
            continuation.context[AllohaBootstrapDiagnostics]?.record?.invoke("metadataResponse", body)
            handler.post {
                if (termination.isTerminated) return@post
                runCatching { capture(body) }.onSuccess {
                    metadataCapture = it
                    stages["metadata"] = SystemClock.uptimeMillis() - startedAt
                    continuation.context[SubtitlePreparation]?.prefetch(it.subtitles)
                }.onFailure { finish(Result.failure(it)) }
            }
        }

        @JavascriptInterface fun ready(raw: String) {
            val data = decode(raw) ?: return
            continuation.context[AllohaBootstrapDiagnostics]?.record?.invoke("bootstrapReady", raw)
            handler.post { if (!termination.isTerminated) finish(runCatching { stream(data) }) }
        }

        @JavascriptInterface fun trace(raw: String) {
            if (decode(raw) == null) return
            continuation.context[AllohaBootstrapDiagnostics]?.record?.invoke("metadataRequest", raw)
        }

        @JavascriptInterface fun failed(raw: String) {
            val data = decode(raw) ?: return
            continuation.context[AllohaBootstrapDiagnostics]?.record?.invoke("bootstrapFailure", raw)
            val status = data["status"]?.jsonPrimitive?.intOrNull
            handler.post {
                try {
                    status?.let { continuation.context[HttpRequestPolicy]?.onResponse(it) }
                    finish(Result.failure(status?.let(::PlaybackHttpException)
                        ?: IOException("Alloha bootstrap failed at ${data.string("stage")?.take(40)}")))
                } catch (failure: Exception) { finish(Result.failure(failure)) }
            }
        }
    }

    private fun stream(data: JsonObject): ResolvedVideoStream {
        val parsed = requireNotNull(page)
        val metadata = data["metadata"] as? JsonObject ?: error("Missing Alloha metadata")
        val captured = metadataCapture ?: capture(metadata.toString())
        val playback = captured.playback ?: throw IOException("Alloha bootstrap returned no stream")
        val stream = playback.toStream(captured.subtitles, captured.embeddedSubtitles, captured.hasEmbeddedSubtitles)
        val languages = (data["languages"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
        val language = allohaBrowserAcceptLanguage(languages, WebViewCompat.getCurrentWebViewPackage(context)?.versionName)
        val identity = identityHeaders().filterKeys { !it.equals("User-Agent", true) && !it.equals("Accept-Language", true) } +
            mapOf("User-Agent" to view.settings.userAgentString) + (language?.let { mapOf("Accept-Language" to it) } ?: emptyMap())
        fun headers(url: String, socket: Boolean = false) = buildMap {
            putAll(if (socket) identity.filterKeys { !it.startsWith("sec-ch-", true) } else identity)
            put("Origin", origin)
            if (!socket) {
                put("Referer", sourceUrl); put("Accept", "*/*")
                put("Sec-Fetch-Mode", "cors"); put("Sec-Fetch-Dest", "empty"); put("Sec-Fetch-Site", "same-origin")
                browserHeaders.entries.firstOrNull { it.key.equals("X-Requested-With", true) }
                    ?.let { put("X-Requested-With", it.value) }
            }
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
        }
        val endpoint = metadata.string("pnr") ?: error("Missing Alloha control endpoint")
        val sid = metadata.string("pnk") ?: error("Missing Alloha control session")
        require(endpoint.startsWith("wss://")) { "Invalid Alloha control endpoint" }
        val socketHttp = endpoint.replaceFirst("wss://", "https://").toHttpUrl().newBuilder()
            .setQueryParameter("sid", sid).setQueryParameter("v", "2.1").build().toString()
        val now = System.currentTimeMillis()
        val template = buildJsonObject {
            put("type", "playback_start"); put("current_time", 0)
            put("resolution", stream.selectedVideoHeight?.toString() ?: "auto")
            put("track_id", stream.providerAudioId ?: "0"); put("speed", 1); put("subtitle", -1); put("ts", now)
        }
        val environment = data["environment"] as? JsonObject ?: error("Missing browser environment")
        val active = parsed.fileList["active"]!!.jsonObject
        val fileId = active.string("id_file")?.takeIf { it.toLongOrNull()?.let { id -> id > 0 } == true } ?: active.string("id")!!
        val isTrailer = parsed.movie.string("type") == "trailer"
        val envelope = buildJsonObject {
            put("clientSessionId", UUID.randomUUID().toString().replace("-", ""))
            put("clientRequestId", UUID.randomUUID().toString().replace("-", ""))
            put("playerInitAt", now); put("token", parsed.userParam["token"]!!); put("domain", parsed.userParam["domain"]!!)
            put("env", environment); put("events", JsonArray(emptyList())); put("isTrailer", isTrailer); put("dropped", 0)
        }
        val telemetry = AllohaHttpTelemetryDescriptor(
            providerPageUrl = sourceUrl, headers = headers(sourceUrl), token = parsed.userParam.string("token")!!,
            domain = parsed.userParam.string("domain")!!, fileId = fileId,
            statType = data.string("statType") ?: error("Missing capability results"), environment = environment,
            statInfo = data["statInfo"] as? JsonObject ?: error("Missing capability results"), isTrailer = isTrailer,
            initialEnvelope = envelope, pageEpochMs = data["pageEpochMs"]!!.jsonPrimitive.long,
            adBlock = data["adBlock"]?.jsonPrimitive?.booleanOrNull,
            endpointHeaders = mapOf(
                "/events" to headers("$origin/events"),
                "/stat" to (headers("$origin/stat") + ("X-Requested-With" to "XMLHttpRequest")),
            ),
        )
        val descriptor = AllohaSessionDescriptor(
            observedWebSocketUrl = socketHttp.replaceFirst("https://", "wss://"), webSocketHeaders = headers(socketHttp, true),
            playbackStartTemplate = template.toString(), initialToken = null, guardToken = requireNotNull(guard),
            expiresAtEpochMs = metadata["time"]?.jsonPrimitive?.longOrNull,
            mediaHosts = (listOf(stream.url) + stream.fallbackUrls).map { it.toHttpUrl().host }.toSet(),
            telemetry = telemetry,
            providerAudioLabel = (metadata["hlsSource"] as? JsonArray)?.mapNotNull { it as? JsonObject }
                ?.firstOrNull { it.string("audioId") == stream.providerAudioId }?.string("label"),
        )
        return stream.copy(sessionDescriptor = descriptor, provider = PlaybackProvider.Alloha,
            runtimeMetadataResolved = true, skipPlaybackProbe = true,
            headers = stream.headers.filterKeys { name -> identity.keys.none { it.equals(name, true) } } + identity)
    }

    private fun finish(result: Result<ResolvedVideoStream>) {
        if (!termination.tryTerminate()) return
        stages["finished"] = SystemClock.uptimeMillis() - startedAt
        android.util.Log.i("AllohaBootstrap", "success=${result.isSuccess} timingMs=$stages")
        cleanup()
        if (continuation.isActive) result.onSuccess { continuation.resume(it) }.onFailure { continuation.resumeWithException(it) }
    }

    private fun cleanup() {
        if (cleaned) return
        cleaned = true
        handler.removeCallbacksAndMessages(null)
        view.stopLoading(); view.removeJavascriptInterface("YummyBootstrapBridge"); view.destroy()
    }

    private fun response(type: String, body: String) = WebResourceResponse(type, "UTF-8", ByteArrayInputStream(body.toByteArray()))
    private fun emptyResponse() = response("text/plain", "")
}

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
