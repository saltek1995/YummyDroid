package me.yummydroid.app.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.yummydroid.app.data.BROWSER_USER_AGENT
import me.yummydroid.app.data.DEFAULT_SITE_BASE_URL
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger
import java.io.ByteArrayInputStream

/**
 * Opt-in browser-player comparator. This deliberately uses one real WebView and does not invoke
 * the resolver, click advertisements, or retry a failed request.
 */
@RunWith(AndroidJUnit4::class)
class LiveAllohaWebsiteTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun realAllohaPlayerMakesRequiredProgress() {
        val args = InstrumentationRegistry.getArguments()
        val encoded = args.getString("liveVideo").orEmpty()
        assumeTrue("Set liveWebsite=true and provide one base64 liveVideo JSON argument",
            args.getString("liveWebsite") == "true" && encoded.isNotBlank())
        val sourceUrl = JSONObject(String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8))
            .getString("url")
        val sourceHost = Uri.parse(sourceUrl).host.orEmpty().lowercase()
        assumeTrue("liveVideo URL must have an Alloha frame host", sourceHost.isNotBlank())
        assumeTrue("Document-start WebView scripts are required",
            supportsDocumentStartScript())

        val terminal = AtomicBoolean(false)
        val terminalReason = AtomicReference<String?>(null)
        val suppressedEvents = AtomicInteger()
        val suppressEvents = args.getString("liveSuppressEvents") == "true"
        val captureGate = args.getString("liveCaptureGate")?.also {
            require(it.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        }?.let { java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, it) }
        captureGate?.delete()
        val webViewRef = AtomicReference<WebView?>()
        val snapshot = AtomicReference(PlayerSnapshot())
        val visible = mutableStateOf(true)
        var scriptHandler: Any? = null
        try {
            compose.setContent {
                if (visible.value) AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                    WebView(context).also { webView ->
                        configure(webView)
                        webView.addJavascriptInterface(SnapshotBridge(snapshot), SNAPSHOT_BRIDGE)
                        scriptHandler = addDocumentStartScript(webView, sourceHost)
                        webView.webViewClient = stoppingClient(terminal, terminalReason, sourceHost,
                            suppressEvents, suppressedEvents)
                        webViewRef.set(webView)
                        val startupDelay = args.getString("liveStartupDelayMs")?.toLongOrNull()?.coerceIn(0L, 15_000L) ?: 0L
                        if (captureGate != null) {
                            webView.loadUrl("about:blank")
                            Log.i(LOG_TAG, "captureGateWaiting=true")
                        } else webView.postDelayed({
                            if (webViewRef.get() === webView) loadPlayerFrame(webView, sourceUrl)
                        }, startupDelay)
                    }
                })
            }
            if (captureGate != null) {
                val gateDeadline = SystemClock.elapsedRealtime() + 180_000L
                while (!captureGate.exists() && SystemClock.elapsedRealtime() < gateDeadline) Thread.sleep(100L)
                check(captureGate.exists()) { "Capture was not armed; no provider navigation performed" }
                captureGate.delete()
                Log.i(LOG_TAG, "captureGateReleased=true")
                onMain { loadPlayerFrame(requireNotNull(webViewRef.get()), sourceUrl) }
            }
            waitForPlayback(webViewRef, snapshot, terminal, terminalReason)
            assertTrue("website request failed: ${terminalReason.get()}", !terminal.get())
            val final = snapshot.get()
            val minutes = requestedMinutes()
            assertTrue("insufficient safe player progress: ${final.positionMs}",
                if (startupOnly()) final.durationMs >= 600_000L && final.positionMs >= 15_000L
                else if (fullEpisode()) final.completed else final.completed || final.positionMs >= minutes * 60_000L)
            if (final.completed) Thread.sleep(10_000L) // Capture the site's own end-of-view flush.
            if (suppressEvents) assertTrue("No /events POST was suppressed", suppressedEvents.get() >= 3)
        } finally {
            Log.i(LOG_TAG, "suppressedEvents=${suppressedEvents.get()}")
            onMain {
                scriptHandler?.let(::removeDocumentStartScript)
                webViewRef.getAndSet(null)?.let { view ->
                    view.stopLoading()
                    view.removeJavascriptInterface(SNAPSHOT_BRIDGE)
                    view.loadUrl("about:blank")
                    view.removeAllViews()
                    view.destroy()
                }
            }
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(webView: WebView) {
        WebView.setWebContentsDebuggingEnabled(true)
        webView.keepScreenOn = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            if (InstrumentationRegistry.getArguments().getString("liveAppUserAgent") == "true") {
                userAgentString = BROWSER_USER_AGENT
            }
            loadsImagesAutomatically = true
        }
        Log.i(LOG_TAG, "browserUserAgent=${webView.settings.userAgentString}")
    }

    private fun stoppingClient(terminal: AtomicBoolean, reason: AtomicReference<String?>,
        sourceHost: String, suppressEvents: Boolean, suppressedEvents: AtomicInteger) =
        object : WebViewClient() {
            private val requestCount = AtomicInteger()
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (requestCount.incrementAndGet() > 3000) {
                    reason.compareAndSet(null, "request-cap")
                    terminal.set(true)
                }
                if (terminal.get()) return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                if (suppressEvents && request.url.scheme == "https" && request.url.host == sourceHost &&
                    request.url.path == "/events" && request.method == "POST") {
                    Log.i(LOG_TAG, "suppressedEvents=${suppressedEvents.incrementAndGet()}")
                    return WebResourceResponse("application/json", "UTF-8", ByteArrayInputStream("{}".toByteArray()))
                }
                // The comparison concerns video/session longevity. Do not contact rejected
                // optional caption URLs: a subtitle 403 is not evidence of rejected video.
                if (InstrumentationRegistry.getArguments().getString("liveIgnoreSubtitles") == "true" &&
                    request.url.path.orEmpty().endsWith(".vtt", ignoreCase = true)) {
                    return WebResourceResponse("text/vtt", "UTF-8", ByteArrayInputStream("WEBVTT\n\n".toByteArray()))
                }
                return null
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest,
                response: WebResourceResponse) {
                if (response.statusCode == 403 || response.statusCode == 429) {
                    val extension = request.url.lastPathSegment?.substringAfterLast('.', "")
                        ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,5}")) }
                    Log.i(LOG_TAG, "httpFailure=${response.statusCode} host=${request.url.host} " +
                        "extension=$extension controls=${request.requestHeaders.keys.any { it.equals("Accepts-Controls", true) }} " +
                        "retryAfter=${response.responseHeaders?.get("Retry-After")} " +
                        "denialCode=${response.responseHeaders?.entries?.firstOrNull { it.key.equals("X-VD", true) }?.value?.takeIf { it.matches(Regex("[A-Za-z0-9_.:-]{1,48}")) }}")
                    reason.compareAndSet(null, "HTTP${response.statusCode}")
                    terminal.set(true)
                    view.stopLoading()
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest,
                error: android.webkit.WebResourceError) {
                if (request.isForMainFrame) {
                    reason.compareAndSet(null, "main-frame-error")
                    terminal.set(true)
                    view.stopLoading()
                }
            }
        }

    private fun waitForPlayback(
        webViewRef: AtomicReference<WebView?>,
        snapshot: AtomicReference<PlayerSnapshot>,
        terminal: AtomicBoolean,
        reason: AtomicReference<String?>,
    ) {
        val requiredMs = requestedMinutes() * 60_000L
        val started = SystemClock.elapsedRealtime()
        val startupDeadline = started + STARTUP_TIMEOUT_MS
        val deadline = started + if (fullEpisode()) 40 * 60_000L else requiredMs + 3 * 60_000L
        var nextLog = 0L
        var previousPosition = 0L
        var previousObservationAt = started
        var playedMs = 0L
        while (SystemClock.elapsedRealtime() < deadline && !terminal.get()) {
            if (webViewRef.get() == null) break
            if (SystemClock.elapsedRealtime() >= nextLog) {
                val value = snapshot.get()
                Log.i(LOG_TAG, "position=${value.positionMs} buffer=${value.bufferedMs} ready=${value.readyState} " +
                    "paused=${value.paused} size=${value.width}x${value.height} opens=${value.socketOpens} closes=${value.socketCloses} code=${value.closeCode} " +
                    "configs=${value.configUpdates} versions=${value.tokenVersions} sends=${value.sends} " +
                    "currentTimes=${value.currentTimes} terminal=${reason.get()}")
                nextLog = SystemClock.elapsedRealtime() + 30_000L
            }
            val value = snapshot.get()
            if (startupOnly() && value.durationMs >= 600_000L && value.positionMs >= 15_000L) return
            val observedAt = SystemClock.elapsedRealtime()
            val delta = value.positionMs - previousPosition
            if (delta in 1..(observedAt - previousObservationAt + 1_500L)) playedMs += delta
            previousPosition = value.positionMs
            previousObservationAt = observedAt
            if (value.completed) {
                assertTrue("Playback skipped part of the episode: played=$playedMs duration=${value.durationMs}",
                    !fullEpisode() || playedMs >= value.durationMs - 10_000L)
                Log.i(LOG_TAG, "episodeCompleted=true playedMs=$playedMs durationMs=${value.durationMs}")
                return
            }
            if (!fullEpisode() && value.positionMs >= requiredMs) return
            if (value.positionMs < 1_000L && SystemClock.elapsedRealtime() >= startupDeadline) {
                reason.compareAndSet(null, "startup-no-video")
                terminal.set(true)
                return
            }
            Thread.sleep(1_000L)
        }
    }

    private fun requestedMinutes() = InstrumentationRegistry.getArguments().getString("liveMinutes")
        ?.toLongOrNull()?.coerceIn(18L, 30L) ?: 18L

    private fun fullEpisode() = InstrumentationRegistry.getArguments().getString("liveFullEpisode") == "true"
    private fun startupOnly() = InstrumentationRegistry.getArguments().getString("liveStartupOnly") == "true"

    /** WebKit is an implementation dependency of :data, so call its document-start API reflectively. */
    private fun supportsDocumentStartScript(): Boolean = runCatching {
        val feature = Class.forName("androidx.webkit.WebViewFeature")
        val name = feature.getField("DOCUMENT_START_SCRIPT").get(null) as String
        feature.getMethod("isFeatureSupported", String::class.java).invoke(null, name) as Boolean
    }.getOrDefault(false)

    private fun addDocumentStartScript(webView: WebView, sourceHost: String): Any {
        val compat = Class.forName("androidx.webkit.WebViewCompat")
        return compat.getMethod("addDocumentStartJavaScript", WebView::class.java, String::class.java,
            Set::class.java).invoke(null, webView, observerScript(sourceHost, startupOnly()), setOf("*"))
            ?: error("WebView did not return a document-start handler")
    }

    private fun removeDocumentStartScript(handler: Any) {
        runCatching { handler.javaClass.getMethod("remove").invoke(handler) }
    }

    private fun loadPlayerFrame(webView: WebView, sourceUrl: String) {
        val safeSource = sourceUrl.replace("&", "&amp;").replace("\"", "&quot;")
        val html = """
            <!doctype html><html><head><meta name="referrer" content="no-referrer-when-downgrade"></head>
            <body style="margin:0;background:#000"><iframe src="$safeSource" width="1280" height="720"
            allow="accelerometer *; autoplay *; clipboard-write *; encrypted-media *; gyroscope *; picture-in-picture *; fullscreen *" allowfullscreen></iframe></body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL(DEFAULT_SITE_BASE_URL, html, "text/html", "UTF-8", null)
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }

    private data class PlayerSnapshot(
        val seenVideo: Boolean = false, val positionMs: Long = 0, val bufferedMs: Long = 0, val readyState: Int = 0,
        val width: Int = 0, val height: Int = 0, val durationMs: Long = 0,
        val paused: Boolean = true, val completed: Boolean = false, val socketOpens: Int = 0,
        val socketCloses: Int = 0, val closeCode: Int = 0, val configUpdates: Int = 0, val tokenVersions: Int = 0,
        val sends: Int = 0, val currentTimes: Int = 0,
    )

    private class SnapshotBridge(private val snapshot: AtomicReference<PlayerSnapshot>) {
        @JavascriptInterface fun timing(stage: String?, elapsedMs: Double) {
            if (stage?.matches(Regex("[A-Za-z]{1,40}")) == true && elapsedMs.isFinite())
                Log.i(LOG_TAG, "websiteStage=$stage navigationMs=${elapsedMs.toLong()}")
        }
        @JavascriptInterface fun record(raw: String?) {
            if (raw.isNullOrBlank() || raw.length > 2_048) return
            runCatching { JSONObject(raw).let { value ->
                val duration = value.optLong("duration", -1L)
                val current = PlayerSnapshot(
                    seenVideo = value.optBoolean("seen"),
                    positionMs = (value.optDouble("position", 0.0) * 1_000).toLong(),
                    bufferedMs = (value.optDouble("buffered", 0.0) * 1_000).toLong(),
                    width = value.optInt("width"), height = value.optInt("height"),
                    durationMs = (value.optDouble("duration", 0.0) * 1_000).toLong(),
                    readyState = value.optInt("readyState"), paused = value.optBoolean("paused", true),
                    completed = value.optBoolean("ended") && duration >= 600 &&
                        value.optDouble("position") * 1_000 >= duration * 1_000 - 3_000,
                    socketOpens = value.optInt("opens"), socketCloses = value.optInt("closes"),
                    closeCode = value.optInt("closeCode"), configUpdates = value.optInt("configs"),
                    tokenVersions = value.optInt("versions"), sends = value.optInt("sends"),
                    currentTimes = value.optInt("currentTimes"),
                )
                snapshot.updateAndGet { previous -> if (previous.completed) previous else current }
            } }
        }
    }

    private companion object {
        const val LOG_TAG = "LiveAllohaWebsite"
        const val SNAPSHOT_BRIDGE = "YummyWebsiteBridge"
        const val STARTUP_TIMEOUT_MS = 60_000L
        fun observerScript(sourceHost: String, measureStartup: Boolean = false) = """
            (function(){if(location.hostname!==${JSONObject.quote(sourceHost)}||window.__yummyStats)return;var s=window.__yummyStats={opens:0,closes:0,closeCode:0,configs:0,versions:0,sends:0,currentTimes:0,types:{},lastToken:null,played:false};
            function session(u){try{var x=new URL(u,location.href);return /^wss?:${'$'}/.test(x.protocol)&&x.searchParams.has('sid')&&x.searchParams.get('v')==='2.1'}catch(e){return false}}
            var N=window.WebSocket;function W(u,p){var w=arguments.length>1?new N(u,p):new N(u);if(!session(u))return w;w.addEventListener('open',function(){s.opens++});w.addEventListener('close',function(e){s.closes++;s.closeCode=e.code||0});var send=w.send;w.send=function(b){try{var m=JSON.parse(b);s.sends++;if(m.type){s.types[m.type]=(s.types[m.type]||0)+1;if(m.type==='current_time')s.currentTimes++}}catch(e){}return send.apply(this,arguments)};w.addEventListener('message',function(e){try{var m=JSON.parse(e.data);if(m.type==='config_update'){s.configs++;var t=m.edge_hash;if(t&&t!==s.lastToken){if(s.lastToken)s.versions++;s.lastToken=t}}}catch(x){}});return w}if(N){W.prototype=N.prototype;Object.setPrototypeOf(W,N);window.WebSocket=W}
            function report(){try{var v=document.querySelector('video'),b=0;if(v&&v.buffered.length)b=v.buffered.end(v.buffered.length-1);window.YummyWebsiteBridge.record(JSON.stringify({seen:!!v,position:v?v.currentTime:0,buffered:b,width:v?v.videoWidth:0,height:v?v.videoHeight:0,readyState:v?v.readyState:0,paused:v?v.paused:true,ended:v?v.ended:false,duration:v?v.duration:0,opens:s.opens,closes:s.closes,closeCode:s.closeCode,configs:s.configs,versions:s.versions,sends:s.sends,currentTimes:s.currentTimes}))}catch(e){}}
            document.addEventListener('ended',report,true);
            var measured = {};
            function stamp(name){if(!measured[name]){measured[name]=true;window.YummyWebsiteBridge.timing(name,performance.now());}}
            stamp('documentStart');
            document.addEventListener('playing',function(e){if(e.target instanceof HTMLVideoElement){stamp(e.target.duration>=600?'mainPlaying':'otherMediaPlaying');report()}},true);
            document.addEventListener('loadedmetadata',function(e){if(e.target instanceof HTMLVideoElement&&e.target.duration>=600)stamp('mainMetadata')},true);
            function play(){var v=document.querySelector('video');if(!s.played){
                if($measureStartup){var p=window.player,c=p&&p.elements&&p.elements.container;
                    var button=c&&c.querySelector('.allplay__control--overlaid[data-allplay="play"]');
                    if(p&&p.ready&&p.reloadManifestQuery&&p.reloadManifestQuery.query&&button){s.played=true;stamp('normalPlayClick');button.click();}
                }else if(v){s.played=true;v.playbackRate=1;v.play().catch(function(){})}
            }report()}new MutationObserver(play).observe(document,{childList:true,subtree:true});document.addEventListener('DOMContentLoaded',play);setInterval(play,100);})();
        """.trimIndent()
    }
}
