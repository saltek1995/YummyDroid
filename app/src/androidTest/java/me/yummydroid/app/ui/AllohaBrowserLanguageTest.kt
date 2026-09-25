package me.yummydroid.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import me.yummydroid.app.data.BROWSER_USER_AGENT
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Compares local Chromium's actual request language with the production Alloha header helper. */
@RunWith(AndroidJUnit4::class)
class AllohaBrowserLanguageTest {
    @Test
    fun computedHeaderMatchesRealWebViewFetchHeader() {
        MockWebServer().use { server ->
            val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
            val certificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
            server.useHttps(certificates.sslSocketFactory(), false)
            server.enqueue(MockResponse().setHeader("Content-Type", "text/html").setBody(page()))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}"))
            val bridge = Bridge()
            val webView = AtomicReference<WebView?>()
            try {
                val origin = server.url("/").toString().removeSuffix("/")
                onMain {
                    WebView(InstrumentationRegistry.getInstrumentation().targetContext).also { view ->
                        view.settings.javaScriptEnabled = true
                        view.settings.userAgentString = BROWSER_USER_AGENT
                        view.webViewClient = loopbackSslClient()
                        view.addJavascriptInterface(bridge, "LanguageBridge")
                        view.loadUrl("$origin/index.html")
                        webView.set(view)
                    }
                }
                server.takeRequestRequired("document request")
                val capture = server.takeRequestRequired("same-origin fetch")
                assertEquals("/capture", capture.path)
                val languages = bridge.languages.pollRequired("navigator.languages")
                val wireHeader = requireNotNull(capture.getHeader("Accept-Language"))

                assertEquals(
                    wireHeader,
                    productionAcceptLanguage(languages, webViewPackageVersion(InstrumentationRegistry.getInstrumentation().targetContext)),
                )
            } finally {
                onMain {
                    webView.getAndSet(null)?.let { view ->
                        view.stopLoading()
                        view.removeJavascriptInterface("LanguageBridge")
                        view.loadUrl("about:blank")
                        view.destroy()
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loopbackSslClient() = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
            if (android.net.Uri.parse(error.url).host in setOf("localhost", "127.0.0.1")) handler.proceed()
            else handler.cancel()
        }
    }

    private fun page() = """
        <!doctype html><script>
        LanguageBridge.languages(JSON.stringify(Array.from(navigator.languages)));
        fetch('/capture').catch(error => LanguageBridge.languages(JSON.stringify(['error:' + error])));
        </script>
    """.trimIndent()

    private fun productionAcceptLanguage(languages: List<String>, webViewVersion: String?): String {
        val helper = Class.forName("me.yummydroid.app.data.AllohaBrowserHeadersKt")
            .getMethod("allohaBrowserAcceptLanguage", java.util.List::class.java, String::class.java)
        return helper.invoke(null, languages, webViewVersion) as String
    }

    private fun webViewPackageVersion(context: Context): String? = runCatching {
        val compat = Class.forName("androidx.webkit.WebViewCompat")
        val info = compat.getMethod("getCurrentWebViewPackage", Context::class.java).invoke(null, context)
        (info as? android.content.pm.PackageInfo)?.versionName
    }.getOrNull()

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return requireNotNull(result).getOrThrow()
    }

    private class Bridge {
        val languages = LinkedBlockingQueue<List<String>>()
        @JavascriptInterface fun languages(raw: String) {
            languages.add(JSONArray(raw).let { array -> List(array.length()) { array.getString(it) } })
        }
    }

    private fun MockWebServer.takeRequestRequired(label: String) =
        requireNotNull(takeRequest(10, TimeUnit.SECONDS)) { "Timed out waiting for $label" }

    private fun <T> LinkedBlockingQueue<T>.pollRequired(label: String) =
        requireNotNull(poll(10, TimeUnit.SECONDS)) { "Timed out waiting for $label" }
}
