package me.yummydroid.app.ui

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the production bootstrap asset against a synthetic page; all WebView traffic is denied locally. */
@RunWith(AndroidJUnit4::class)
class AllohaBootstrapScriptTest {
    @Test
    @SuppressLint("SetJavaScriptEnabled")
    fun bootstrapCapturesMetadataAndReadinessWithoutProviderOrMediaTraffic() {
        val bridge = Bridge()
        val unexpected = CopyOnWriteArrayList<String>()
        val viewRef = AtomicReference<WebView?>()
        val origin = "https://alloha.fixture.test"
        try {
            onMain {
                WebView(InstrumentationRegistry.getInstrumentation().targetContext).also { view ->
                    view.settings.javaScriptEnabled = true
                    view.settings.domStorageEnabled = true
                    view.settings.mediaPlaybackRequiresUserGesture = true
                    view.addJavascriptInterface(bridge, "YummyBootstrapBridge")
                    view.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse {
                            val url = request?.url?.toString().orEmpty()
                            if (url.startsWith("http") && url !in setOf("data:text/html;charset=utf-8;base64,", "$origin/favicon.ico")) unexpected += url
                            return response("")
                        }
                    }
                    view.loadDataWithBaseURL(origin, fixture(bootstrapAsset()), "text/html", "UTF-8", null)
                    viewRef.set(view)
                }
            }

            val metadata = JSONObject(bridge.metadata.pollRequired("metadata"))
            val ready = JSONObject(bridge.ready.pollRequired("ready"))
            val failure = bridge.failed.poll()
            assertNull("bootstrap failed: $failure", failure)
            assertEquals("fixture", metadata.getString("nonce"))
            val supplied = JSONObject(metadata.getString("body"))
            assertEquals("audio-1", supplied.getJSONArray("hlsSource").getJSONObject(0).getString("audioId"))
            assertEquals("wss://control.fixture.test/channel", supplied.getString("pnr"))
            assertEquals("fixture-sid", supplied.getString("pnk"))

            assertEquals("fixture", ready.getString("nonce"))
            assertTrue(ready.getJSONObject("statInfo").getBoolean("webSocket"))
            assertTrue(ready.getBoolean("adBlock"))
            assertTrue(ready.getLong("pageEpochMs") > 0L)
            assertTrue(ready.getJSONObject("environment").has("pixelRatio"))
            assertFalse(ready.has("events"))
            assertEquals(1, bridge.metadataCount.get())
            assertEquals(1, bridge.readyCount.get())
            assertEquals(0, bridge.failedCount.get())

            val state = JSONObject(evaluate(viewRef.get(), "JSON.stringify(window.__fixtureState)"))
            assertEquals(1, state.getInt("fetches"))
            assertEquals(1, state.getInt("webSockets"))
            assertEquals(0, state.getInt("plays"))
            assertEquals(0, state.getInt("mediaWithSrc"))
            assertEquals("POST", state.getString("method"))
            assertEquals("fixture-http-token", state.getJSONObject("form").getString("token"))
            val formKeys = mutableSetOf<String>()
            val formIterator = state.getJSONObject("form").keys()
            while (formIterator.hasNext()) formKeys += formIterator.next()
            assertEquals(setOf("audio", "autoplay", "av1", "subtitle", "token"), formKeys)
            assertEquals("XMLHttpRequest", state.getJSONObject("headers").getString("X-Requested-With"))
            assertTrue(state.getJSONObject("headers").getString("Borth").matches(Regex("[0-9a-f]{64}\\|fgdcheab")))
            assertTrue("Unexpected WebView traffic: $unexpected", unexpected.isEmpty())
        } finally {
            onMain {
                viewRef.getAndSet(null)?.let { view ->
                    view.stopLoading()
                    view.removeJavascriptInterface("YummyBootstrapBridge")
                    view.destroy()
                }
            }
        }
    }

    @Test
    @SuppressLint("SetJavaScriptEnabled")
    fun bootstrapUsesRealFetchFromAnInterceptedCrossOriginIframe() {
        val bridge = Bridge()
        val viewRef = AtomicReference<WebView?>()
        val parentOrigin = "https://parent.fixture.test"
        val childUrl = "https://alloha.fixture.test/embed/bootstrap"
        val posts = CopyOnWriteArrayList<WebResourceRequest>()
        val unexpected = CopyOnWriteArrayList<String>()
        try {
            onMain {
                WebView(InstrumentationRegistry.getInstrumentation().targetContext).also { view ->
                    view.settings.javaScriptEnabled = true
                    view.settings.domStorageEnabled = true
                    view.settings.mediaPlaybackRequiresUserGesture = true
                    view.addJavascriptInterface(bridge, "YummyBootstrapBridge")
                    view.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse {
                            val current = requireNotNull(request)
                            return when {
                                current.url.toString() == childUrl && current.method == "GET" ->
                                    htmlResponse(realFetchFixture(bootstrapAsset()))
                                current.url.toString() == "https://alloha.fixture.test/bnsi/movies/42" && current.method == "POST" -> {
                                    posts += current
                                    jsonResponse(metadataJson())
                                }
                                current.url.toString() in setOf("$parentOrigin/favicon.ico", "https://alloha.fixture.test/favicon.ico") -> response("")
                                else -> {
                                    current.url.toString().takeIf { it.startsWith("http") }?.let(unexpected::add)
                                    response("")
                                }
                            }
                        }
                    }
                    view.loadDataWithBaseURL(parentOrigin, "<!doctype html><iframe src='$childUrl'></iframe>", "text/html", "UTF-8", null)
                    viewRef.set(view)
                }
            }

            val metadata = JSONObject(bridge.metadata.pollRequired("metadata from native fetch"))
            val ready = JSONObject(bridge.ready.pollRequired("ready from native fetch"))
            val form = bridge.forms.pollRequired("native fetch form")
            assertEquals("fixture", metadata.getString("nonce"))
            assertEquals("fixture", ready.getString("nonce"))
            assertTrue(ready.getJSONObject("statInfo").getBoolean("webSocket"))
            assertTrue(ready.getBoolean("adBlock"))
            assertEquals(1, posts.size)
            val headers = posts.single().requestHeaders
            assertEquals("XMLHttpRequest", headers.header("X-Requested-With"))
            assertTrue(headers.header("Borth").orEmpty().matches(Regex("[0-9a-f]{64}\\|fgdcheab")))
            assertTrue(headers.header("Content-Type").orEmpty().startsWith("application/x-www-form-urlencoded"))
            assertEquals(setOf("audio", "autoplay", "av1", "subtitle", "token"), form.split('&').map { it.substringBefore('=') }.toSet())
            assertTrue(form.contains("token=fixture-http-token"))
            assertEquals(0, bridge.mediaPlayCount.get())
            assertEquals(0, bridge.failedCount.get())
            assertTrue("Unexpected WebView traffic: $unexpected", unexpected.isEmpty())
        } finally {
            onMain {
                viewRef.getAndSet(null)?.let { view ->
                    view.stopLoading()
                    view.removeJavascriptInterface("YummyBootstrapBridge")
                    view.destroy()
                }
            }
        }
    }

    @Test
    @SuppressLint("SetJavaScriptEnabled")
    fun metadataHttpFailureIsReportedOnceWithItsPrivateResponseDiagnostic() {
        val bridge = Bridge()
        val viewRef = AtomicReference<WebView?>()
        try {
            onMain {
                WebView(InstrumentationRegistry.getInstrumentation().targetContext).also { view ->
                    view.settings.javaScriptEnabled = true
                    view.settings.domStorageEnabled = true
                    view.addJavascriptInterface(bridge, "YummyBootstrapBridge")
                    view.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?) = response("")
                    }
                    view.loadDataWithBaseURL("https://alloha.fixture.test", errorFixture(bootstrapAsset()), "text/html", "UTF-8", null)
                    viewRef.set(view)
                }
            }

            val failed = JSONObject(bridge.failed.pollRequired("metadata failure"))
            assertEquals("fixture", failed.getString("nonce"))
            assertEquals("metadata", failed.getString("stage"))
            assertEquals(403, failed.getInt("status"))
            assertTrue(failed.getString("error").contains("metadata status"))
            val response = failed.getJSONObject("response")
            assertEquals("{\"detail\":\"private fixture diagnostic\"}", response.getString("body"))
            assertTrue((0 until response.getJSONArray("headers").length()).any { index ->
                val header = response.getJSONArray("headers").getJSONArray(index)
                header.getString(0).equals("X-Fixture-Diagnostic", true) && header.getString(1) == "private-header"
            })
            assertEquals(1, bridge.failedCount.get())
            assertNull(bridge.metadata.poll(500, TimeUnit.MILLISECONDS))
            assertNull(bridge.ready.poll(500, TimeUnit.MILLISECONDS))

            val state = JSONObject(evaluate(viewRef.get(), "JSON.stringify(window.__fixtureState)"))
            assertEquals(1, state.getInt("fetches"))
            assertEquals(0, state.getInt("plays"))
            assertEquals(0, state.getInt("mediaWithSrc"))
        } finally {
            onMain {
                viewRef.getAndSet(null)?.let { view ->
                    view.stopLoading()
                    view.removeJavascriptInterface("YummyBootstrapBridge")
                    view.destroy()
                }
            }
        }
    }

    @Test
    @SuppressLint("SetJavaScriptEnabled")
    fun testOnlyAssetExportsBorthTransformForSyntheticSeed() {
        val bridge = Bridge()
        val viewRef = AtomicReference<WebView?>()
        try {
            onMain {
                WebView(InstrumentationRegistry.getInstrumentation().targetContext).also { view ->
                    view.settings.javaScriptEnabled = true
                    view.addJavascriptInterface(bridge, "YummyBootstrapBridge")
                    view.loadDataWithBaseURL("https://alloha.fixture.test", testOnlyFixture(bootstrapAsset()), "text/html", "UTF-8", null)
                    viewRef.set(view)
                }
            }
            val result = JSONObject(bridge.ready.pollRequired("test-only result"))
            assertEquals("fgdcheab", result.getString("seed"))
        } finally {
            onMain { viewRef.getAndSet(null)?.let { it.stopLoading(); it.destroy() } }
        }
    }

    private fun bootstrapAsset(): String = InstrumentationRegistry.getInstrumentation().targetContext.assets
        .open("alloha-bootstrap.js").bufferedReader().use { it.readText() }

    private fun fixture(asset: String) = """
        <!doctype html><video id="fixture-video"></video><script>
        window.__fixtureState={fetches:0,webSockets:0,plays:0,mediaWithSrc:0};
        window.__yummyBootstrap={userParam:{token:'fixture-http-token',domain:'site.fixture',autoplay:0,audio:'audio-1',subtitle:''},fileList:{active:{id:42},type:'movie'},movie:{type:'movie'},viewportSeed:'abcdefgh',captureNonce:'fixture',probeWebSocketUrl:'wss://probe.fixture.test'};
        window.WebSocket=function(url){window.__fixtureState.webSockets++;this.url=url;};
        HTMLMediaElement.prototype.play=function(){window.__fixtureState.plays++;return Promise.reject(new Error('media playback is forbidden'));};
        window.fetch=function(url,options){
          if(String(url)!=='/bnsi/movies/42') throw new Error('unexpected fetch '+url);
          window.__fixtureState.fetches++;
          window.__fixtureState.method=options.method;
          window.__fixtureState.headers=options.headers;
          window.__fixtureState.form=Object.fromEntries(new URLSearchParams(options.body));
          return Promise.resolve(new Response(JSON.stringify({hlsSource:[{quality:{'1080':'https://media.fixture.test/video.m3u8'},audioId:'audio-1',label:'Fixture'}],pnr:'wss://control.fixture.test/channel',pnk:'fixture-sid',time:123456789}),{status:200}));
        };
        </script><script>$asset</script><script>window.__fixtureState.mediaWithSrc=document.querySelectorAll('video[src],audio[src],source[src]').length;</script>
    """.trimIndent()

    private fun realFetchFixture(asset: String) = """
        <!doctype html><video id="fixture-video"></video><script>
        window.__yummyBootstrap={userParam:{token:'fixture-http-token',domain:'site.fixture',autoplay:0,audio:'audio-1',subtitle:''},fileList:{active:{id:42},type:'movie'},movie:{type:'movie'},viewportSeed:'abcdefgh',captureNonce:'fixture',probeWebSocketUrl:'wss://probe.fixture.test'};
        window.WebSocket=function(){this.send=function(){};};
        HTMLMediaElement.prototype.play=function(){YummyBootstrapBridge.mediaPlay();return Promise.reject(new Error('media playback is forbidden'));};
        const originalToString=URLSearchParams.prototype.toString;
        URLSearchParams.prototype.toString=function(){const body=originalToString.call(this);YummyBootstrapBridge.form(body);return body;};
        </script><script>$asset</script>
    """.trimIndent()

    private fun errorFixture(asset: String) = """
        <!doctype html><video id="fixture-video"></video><script>
        window.__fixtureState={fetches:0,plays:0,mediaWithSrc:0};
        window.__yummyBootstrap={userParam:{token:'fixture-http-token',domain:'site.fixture',autoplay:0,audio:'audio-1',subtitle:''},fileList:{active:{id:42},type:'movie'},movie:{type:'movie'},viewportSeed:'abcdefgh',captureNonce:'fixture',probeWebSocketUrl:'wss://probe.fixture.test'};
        window.WebSocket=function(){};
        HTMLMediaElement.prototype.play=function(){window.__fixtureState.plays++;return Promise.reject(new Error('media playback is forbidden'));};
        window.fetch=function(url,options){
          if(String(url)!=='/bnsi/movies/42') throw new Error('unexpected fetch '+url);
          window.__fixtureState.fetches++;
          return Promise.resolve(new Response('{"detail":"private fixture diagnostic"}',{status:403,headers:{'X-Fixture-Diagnostic':'private-header'}}));
        };
        </script><script>$asset</script><script>window.__fixtureState.mediaWithSrc=document.querySelectorAll('video[src],audio[src],source[src]').length;</script>
    """.trimIndent()

    private fun metadataJson() = """{"hlsSource":[{"quality":{"1080":"https://media.fixture.test/video.m3u8"},"audioId":"audio-1","label":"Fixture"}],"pnr":"wss://control.fixture.test/channel","pnk":"fixture-sid","time":123456789}"""

    private fun testOnlyFixture(asset: String) = """
        <!doctype html><script>window.__yummyBootstrap={testOnly:true};</script><script>$asset</script>
        <script>YummyBootstrapBridge.ready(JSON.stringify({seed:window.__yummyBootstrapAlgorithms.borth('', 'abcdefgh').split('|')[1]}));</script>
    """.trimIndent()

    private fun response(body: String) = WebResourceResponse("text/plain", "UTF-8", 200, "OK", emptyMap(), ByteArrayInputStream(body.toByteArray()))
    private fun htmlResponse(body: String) = WebResourceResponse("text/html", "UTF-8", 200, "OK", emptyMap(), ByteArrayInputStream(body.toByteArray()))
    private fun jsonResponse(body: String) = WebResourceResponse("application/json", "UTF-8", 200, "OK", emptyMap(), ByteArrayInputStream(body.toByteArray()))

    private fun Map<String, String>.header(name: String): String? = entries.firstOrNull { it.key.equals(name, true) }?.value

    private fun evaluate(view: WebView?, script: String): String {
        val result = LinkedBlockingQueue<String>()
        onMain { requireNotNull(view).evaluateJavascript(script, ValueCallback { result.add(it) }) }
        return requireNotNull(result.poll(10, TimeUnit.SECONDS)) { "Timed out evaluating fixture state" }
            .removeSurrounding("\"").replace("\\\"", "\"")
    }

    private fun onMain(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private class Bridge {
        val metadata = LinkedBlockingQueue<String>()
        val ready = LinkedBlockingQueue<String>()
        val failed = LinkedBlockingQueue<String>()
        val forms = LinkedBlockingQueue<String>()
        val metadataCount = AtomicInteger()
        val readyCount = AtomicInteger()
        val failedCount = AtomicInteger()
        val mediaPlayCount = AtomicInteger()

        @JavascriptInterface fun metadata(raw: String) { metadataCount.incrementAndGet(); metadata.add(raw) }
        @JavascriptInterface fun ready(raw: String) { readyCount.incrementAndGet(); ready.add(raw) }
        @JavascriptInterface fun failed(raw: String) { failedCount.incrementAndGet(); failed.add(raw) }
        @JavascriptInterface fun form(raw: String) { forms.add(raw) }
        @JavascriptInterface fun mediaPlay() { mediaPlayCount.incrementAndGet() }
    }

    private fun LinkedBlockingQueue<String>.pollRequired(label: String) =
        requireNotNull(poll(15, TimeUnit.SECONDS)) { "Timed out waiting for $label" }
}
