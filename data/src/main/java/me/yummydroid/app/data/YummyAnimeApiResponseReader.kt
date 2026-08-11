package me.yummydroid.app.data

import java.io.IOException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

internal class YummyAnimeApiResponseReader(
    @PublishedApi internal val client: OkHttpClient,
) {
    @PublishedApi
    internal inline fun <reified T> read(request: Request): T {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throwApiError(response.code, body)
            return YUMMY_ANIME_API_JSON.decodeFromString<ApiEnvelope<T>>(body).response
        }
    }

    fun isSuccessful(request: Request): Boolean {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throwApiError(response.code, body)
            return true
        }
    }

    @PublishedApi
    internal fun throwApiError(statusCode: Int, body: String): Nothing {
        val message = body.apiErrorMessage() ?: "YummyAnime API returned HTTP $statusCode"
        if (statusCode == 420) throw CaptchaRequiredException(message)
        throw ApiHttpException(statusCode, message)
    }

    private fun String.apiErrorMessage(): String? {
        return runCatching {
            val root = YUMMY_ANIME_API_JSON.parseToJsonElement(this).jsonObject
            root["error_title"]?.jsonPrimitive?.contentOrNull
                ?: root["error"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }
}

class CaptchaRequiredException(message: String) : IOException(message)

class ApiHttpException(
    val statusCode: Int,
    message: String,
) : IOException(message)
