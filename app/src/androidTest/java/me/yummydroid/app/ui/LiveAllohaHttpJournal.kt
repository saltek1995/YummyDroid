package me.yummydroid.app.ui

import android.content.Context
import android.os.SystemClock
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.json.JSONObject
import java.io.File

/** Explicit diagnostic opt-in only. This private file contains unredacted credentials. */
internal class LiveAllohaHttpJournal(context: Context) {
    private val file = File(context.filesDir, "alloha-native-http.jsonl").apply { writeText("") }
    private val started = SystemClock.elapsedRealtime()

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
