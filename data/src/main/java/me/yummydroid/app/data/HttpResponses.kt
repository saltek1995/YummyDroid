package me.yummydroid.app.data

import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

internal fun OkHttpClient.readRequiredResponseBody(
    url: String,
    headers: Map<String, String>,
    errorMessage: (Int) -> String,
): String {
    val request = Request.Builder()
        .url(url)
        .headers(headers.toOkHttpHeaders())
        .build()

    newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful || body.isBlank()) {
            throw IOException(errorMessage(response.code))
        }
        return body
    }
}
