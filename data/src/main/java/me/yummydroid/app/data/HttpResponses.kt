package me.yummydroid.app.data

import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class HttpResponseSnapshot(
    val code: Int,
    val message: String,
    val headers: Map<String, String>,
    val mimeType: String?,
    val encoding: String,
    val body: ByteArray,
) {
    val isSuccessful: Boolean
        get() = code in 200..299

    fun bodyString(): String = body.toString(Charset.forName(encoding))

    fun requiredBody(errorMessage: (Int) -> String): String {
        if (!isSuccessful || body.isEmpty()) {
            throw IOException(errorMessage(code))
        }
        return bodyString()
    }
}

internal fun OkHttpClient.readResponseSnapshot(
    url: String,
    headers: Map<String, String>,
): HttpResponseSnapshot {
    val request = Request.Builder()
        .url(url)
        .headers(headers.toOkHttpHeaders())
        .build()

    return newCall(request).execute().use { response ->
        val responseBody = response.body
        val contentType = responseBody?.contentType()
        HttpResponseSnapshot(
            code = response.code,
            message = response.message.ifBlank { "HTTP ${response.code}" },
            headers = response.headers.names().associateWith { name ->
                response.headers.values(name).joinToString(", ")
            },
            mimeType = contentType?.let { type -> "${type.type}/${type.subtype}" },
            encoding = contentType?.charset(StandardCharsets.UTF_8)?.name() ?: StandardCharsets.UTF_8.name(),
            body = responseBody?.bytes() ?: ByteArray(0),
        )
    }
}

internal fun OkHttpClient.readRequiredResponseBody(
    url: String,
    headers: Map<String, String>,
    errorMessage: (Int) -> String,
): String {
    return readResponseSnapshot(url, headers).requiredBody(errorMessage)
}

internal suspend fun OkHttpClient.awaitRequiredResponseBody(
    url: String,
    headers: Map<String, String>,
    errorMessage: (Int) -> String,
): String {
    val request = Request.Builder()
        .url(url)
        .headers(headers.toOkHttpHeaders())
        .build()
    return awaitRequiredResponseBody(request, errorMessage)
}

internal suspend fun OkHttpClient.awaitRequiredResponseBody(
    request: Request,
    errorMessage: (Int) -> String,
): String {
    return newCall(request).awaitResponse().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful || body.isBlank()) {
            throw IOException(errorMessage(response.code))
        }
        body
    }
}

private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(
        object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, cancelledResponse, _ -> cancelledResponse.close() }
            }
        },
    )
}
