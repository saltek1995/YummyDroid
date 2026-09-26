package me.yummydroid.app.ui

import android.content.Context
import android.os.SystemClock
import okhttp3.Request
import okhttp3.Response
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.io.IOException
import okio.Buffer
import org.json.JSONObject
import java.io.File

/** Explicit diagnostic opt-in only. This private file contains unredacted credentials. */
internal class LiveAllohaHttpJournal(context: Context) {
    @Volatile var nativeBootstrapReady = false
        private set
    private val file = File(context.filesDir, "alloha-native-http.jsonl").apply { writeText("") }
    private val started = SystemClock.elapsedRealtime()

    val eventListenerFactory = EventListener.Factory { call ->
        object : EventListener() {
            fun stage(name: String, detail: String? = null) = write(JSONObject()
                .put("event", "transport").put("stage", name).put("url", call.request().url.toString())
                .put("detail", detail))
            override fun callStart(call: Call) = stage("callStart")
            override fun dnsStart(call: Call, domainName: String) = stage("dnsStart")
            override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) =
                stage("dnsEnd", inetAddressList.joinToString { it.hostAddress.orEmpty() })
            override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) =
                stage("connectStart", inetSocketAddress.toString())
            override fun secureConnectStart(call: Call) = stage("tlsStart")
            override fun secureConnectEnd(call: Call, handshake: Handshake?) = stage("tlsEnd")
            override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) =
                stage("connectEnd", protocol?.toString())
            override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy,
                protocol: Protocol?, ioe: IOException) = stage("connectFailed", ioe.javaClass.simpleName)
            override fun requestHeadersStart(call: Call) = stage("requestHeadersStart")
            override fun responseBodyEnd(call: Call, byteCount: Long) = stage("responseBodyEnd", byteCount.toString())
            override fun callEnd(call: Call) = stage("callEnd")
            override fun callFailed(call: Call, ioe: IOException) = stage("callFailed", ioe.javaClass.simpleName)
        }
    }

    @Synchronized private fun write(value: JSONObject) {
        value.put("elapsedMs", SystemClock.elapsedRealtime() - started)
        file.appendText(value.toString() + "\n")
    }

    fun request(request: Request) {
        val value = JSONObject().put("event", "request").put("url", request.url.toString())
            .put("method", request.method).put("headers", JSONObject(request.headers.toMultimap()))
        val body = request.body
        if (body != null && !body.isOneShot() && !body.isDuplex() && body.contentLength() in 0..1_000_000) {
            val buffer = Buffer()
            body.writeTo(buffer)
            value.put("body", buffer.readUtf8())
        }
        write(value)
    }

    fun bootstrap(stage: String, body: String) {
        if (stage == "bootstrapReady") nativeBootstrapReady = true
        write(JSONObject().put("event", "bootstrap").put("stage", stage).put("body", body))
    }

    fun response(response: Response) {
        val value = JSONObject().put("event", "response").put("url", response.request.url.toString())
            .put("status", response.code).put("protocol", response.protocol.toString())
            .put("headers", JSONObject(response.headers.toMultimap()))
        if (response.code >= 400 || response.request.url.encodedPath in setOf("/events", "/stat", "/stats", "/errors")) {
            value.put("body", response.peekBody(65_536).string())
        }
        write(value)
    }

    fun failure(request: Request, error: Exception) = write(JSONObject().put("event", "failure")
        .put("url", request.url.toString()).put("errorClass", error.javaClass.simpleName)
        .put("message", error.message).put("stack", error.stackTraceToString()))

    fun resolutionFailure(error: Exception) = write(JSONObject().put("event", "resolutionFailure")
        .put("errorClass", error.javaClass.simpleName).put("message", error.message)
        .put("stack", error.stackTraceToString()))
}
