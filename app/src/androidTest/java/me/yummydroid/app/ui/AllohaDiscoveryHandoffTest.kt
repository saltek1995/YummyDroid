package me.yummydroid.app.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real WebView, synthetic provider, and intercepted responses only: never opens a network source. */
@RunWith(AndroidJUnit4::class)
class AllohaDiscoveryHandoffTest {
    @Test
    @SuppressLint("SetJavaScriptEnabled")
    fun productionCaptureWaitsForSubmittedFormsBeforeTransferringView() {
        val bridge = Bridge()
        val releaseEvents = CountDownLatch(1)
        val eventsStarted = CountDownLatch(1)
        val requestCount = AtomicInteger()
        val blockedAdvertising = AtomicInteger()
        val unexpected = java.util.concurrent.CopyOnWriteArrayList<String>()
        val viewRef = AtomicReference<WebView>()
        val provider = "https://alloha.fixture.test/player?fixture=synthetic"
        val origin = "https://alloha.fixture.test"
        val parent = "https://parent.fixture.test"
        try {
            onMain {
                val view = WebView(InstrumentationRegistry.getInstrumentation().targetContext)
                viewRef.set(view)
                view.settings.javaScriptEnabled = true
                view.settings.mediaPlaybackRequiresUserGesture = false
                view.addJavascriptInterface(bridge, "YummyResolverBridge")
                view.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(v: WebView?, request: WebResourceRequest?): WebResourceResponse {
                        val url = request?.url.toString()
                        if (Class.forName("me.yummydroid.app.data.AllohaDiscoveryAdsKt")
                            .getMethod("isAllohaAdvertisingRequest", String::class.java, String::class.java)
                            .invoke(null, url, provider) == true) {
                            blockedAdvertising.incrementAndGet()
                            return WebResourceResponse("text/plain", "UTF-8", 410, "Gone", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                        }
                        return when (url) {
                            provider -> response("text/html", fixture())
                            // WebView's empty auxiliary document and this fixture parent's icon only.
                            "data:text/html;charset=utf-8;base64," -> response("text/html", "")
                            "$parent/favicon.ico" -> response("image/x-icon", "")
                            "$origin/stat" -> { requestCount.incrementAndGet(); response("application/json", "{}") }
                            "$origin/events" -> {
                                requestCount.incrementAndGet()
                                eventsStarted.countDown()
                                check(releaseEvents.await(10, TimeUnit.SECONDS))
                                response("application/json", "{}")
                            }
                            else -> { unexpected.add(url); response("text/plain", "") }
                        }
                    }
                }
                val preamble = """
                    window.__yummyExpectedProviderPage = ${JSONObject.quote(provider)};
                    window.__yummyExpectedParentOrigin = ${JSONObject.quote(parent)};
                    window.__yummyCaptureNonce = 'synthetic-proof';
                    window.WebSocket = class {
                        constructor() { this.listeners = {}; }
                        send() {}
                        addEventListener(type, fn) { this.listeners[type] = fn; }
                    };
                """.trimIndent()
                installProductionScript(view, preamble + productionScript(), origin)
                view.loadDataWithBaseURL(parent, "<iframe src='$provider'></iframe>", "text/html", "UTF-8", null)
            }
            assertTrue("provider submitted events", eventsStarted.await(10, TimeUnit.SECONDS))
            onMain { viewRef.get().evaluateJavascript(
                "document.querySelector('iframe').contentWindow.postMessage('__yummySessionHandoff', '$origin')", null,
            ) }
            assertNull("active submission must not transfer", bridge.handoffs.poll(250, TimeUnit.MILLISECONDS))
            releaseEvents.countDown()
            val handoff = JSONObject(requireNotNull(bridge.handoffs.poll(10, TimeUnit.SECONDS)))
            assertEquals(provider, handoff.getString("pageUrl"))
            assertEquals("synthetic-proof", handoff.getString("captureNonce"))
            val state = handoff.getJSONObject("telemetry")
            assertEquals("http-view", state.getJSONObject("envelope").getString("clientSessionId"))
            assertEquals("request", state.getJSONObject("envelope").getString("clientRequestId"))
            assertEquals("file", state.getString("fileId"))
            assertTrue(state.getBoolean("initialStatSent"))
            assertEquals(1280, state.getJSONObject("statInfo").getJSONObject("resolution").getInt("screenWidth"))
            assertEquals(1, state.getJSONArray("events").length())
            assertEquals(2, requestCount.get())
            assertEquals("only the static SDK is attempted and locally denied", 1, blockedAdvertising.get())
            assertTrue("Unexpected intercepted fixture URLs: $unexpected", unexpected.isEmpty())
            assertTrue(bridge.reports.any { JSONObject(it).optString("startupEvent") == "playback_start" })
        } finally {
            releaseEvents.countDown()
            onMain { viewRef.getAndSet(null)?.let { it.stopLoading(); it.destroy() } }
        }
    }

    private fun productionScript(): String = Class.forName("me.yummydroid.app.data.AllohaSessionCaptureKt")
        .getMethod("getALLOHA_SESSION_CAPTURE_SCRIPT").invoke(null) as String

    private fun installProductionScript(view: WebView, script: String, origin: String) {
        Class.forName("androidx.webkit.WebViewCompat").getMethod(
            "addDocumentStartJavaScript", WebView::class.java, String::class.java, Set::class.java,
        ).invoke(null, view, script, setOf(origin))
    }

    private fun fixture() = """
        <!doctype html><script src="/js/rmp-vast.min.js?v=2.6"></script><div id="controls"><video id="media"></video>
        <button class="allplay__control--overlaid" data-allplay="play">Play</button>
        <button class="allplay__control--overlaid" data-allplay="play-large-ads">Advertisement</button></div><script>
        var firstClick = false;
        // Cold visit: ads are enabled in the site's input, with no stored frequency cap.
        const config = JSON.parse(JSON.stringify({debug:false,mediaMetadata:{title:'fixture'},poster:'',
            controls:['play-large','play','progress'],settings:['quality','audio','captions'],
            ads:{enabled:true,replace:{preserve:'identity'},preroll:'fixture-preroll',midroll:[],postroll:''}}));
        if (config.ads.enabled) throw Error('advertising manager must never be constructed');
        if (config.ads.replace.preserve !== 'identity') throw Error('unrelated config was changed');
        var advertisingProbeBlocked = false;
        fetch('https://imasdk.googleapis.com/cekh8i', {method:'HEAD',mode:'no-cors'})
            .catch(function() { advertisingProbeBlocked = true; });
        document.addEventListener('click', function() { firstClick = true; }, true);
        window.storageAvailable = function() { return true; };
        function submitInitialStat() {
            return fetch('/stat', {method:'POST',body:'id=file&token=http-token&domain=site.test&type=mgpo&ab=false&info[wasm]=true&info[resolution][screenWidth]=1280'});
        }
        window.player = {config:config,ready:true,media:document.querySelector('video'),
            elements:{container:document.querySelector('#controls')},
            reloadManifestQuery:{query:'synthetic-query'},play:function() {
            if (!firstClick) throw Error('normal document click handler did not run');
            if (!advertisingProbeBlocked) throw Error('ad probe must be denied without successful fake response');
            // Mirrors the real provider gain() on mobile/TV and its volume setter.
            player.volume = 1; player.media.muted = false;
            player.media.play();
            var socket = new WebSocket('wss://socket.fixture.test/channel?sid=synthetic&v=2.1');
            socket.send(JSON.stringify({type:'playback_start',track_id:'1'}));
            submitInitialStat().then(function() {
                var envelope = {token:'http-token',domain:'site.test',clientSessionId:'http-view',clientRequestId:'request',
                    env:{pixelRatio:1},playerInitAt:1234,events:[{event_type:'view_start',at:5678}]};
                return fetch('/events', {method:'POST',body:new URLSearchParams({token:'http-token',payload:JSON.stringify(envelope)})});
            });
        }};
        player.media.play = function() {
            if (player.volume !== 0 || !this.muted) throw Error('provider gain must not make discovery audible');
            return Promise.resolve();
        };
        player.eventListeners = [{type:'play',element:player.elements.container,callback:submitInitialStat}];
        document.querySelector('[data-allplay="play"]').addEventListener('click',function() { player.play(); });
        document.querySelector('[data-allplay="play-large-ads"]').addEventListener('click',function() {
            throw Error('advertisement control must never be selected');
        });
        </script>
    """.trimIndent()

    private fun response(type: String, body: String) = WebResourceResponse(
        type, "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(body.toByteArray()),
    )

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync { block() }

    private class Bridge {
        val handoffs = LinkedBlockingQueue<String>()
        val reports = java.util.concurrent.CopyOnWriteArrayList<String>()
        @JavascriptInterface fun captureSession(raw: String) { reports.add(raw) }
        @JavascriptInterface fun handoffSession(raw: String) { handoffs.add(raw) }
    }
}
