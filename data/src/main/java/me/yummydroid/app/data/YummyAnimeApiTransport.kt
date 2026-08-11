package me.yummydroid.app.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

internal val defaultYummyAnimeApiClient: OkHttpClient = OkHttpClient.Builder()
    .callTimeout(30, TimeUnit.SECONDS)
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

internal class YummyAnimeApiTransport(
    @PublishedApi internal val client: OkHttpClient,
    initialContentLanguage: ContentLanguage,
) {
    @Volatile
    private var contentLanguage: ContentLanguage = initialContentLanguage

    @Volatile
    @PublishedApi
    internal var pendingCaptchaResponse: String? = null

    @PublishedApi
    internal val json = Json {
        coerceInputValues = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    val locale: Locale
        get() = contentLanguage.locale

    fun updateContentLanguage(language: ContentLanguage) {
        contentLanguage = language
    }

    fun submitCaptchaResponse(response: String) {
        pendingCaptchaResponse = response.trim().takeIf { it.isNotBlank() }
    }

    suspend inline fun <reified T> get(
        path: String,
        params: List<Pair<String, String>> = emptyList(),
        authToken: String? = null,
    ): T = withContext(Dispatchers.IO) {
        val urlBuilder = "$API_BASE_URL$path".toHttpUrl().newBuilder()
        params.forEach { (key, value) ->
            if (value.isNotBlank()) urlBuilder.addQueryParameter(key, value)
        }
        consumeCaptchaResponse()?.let { captcha ->
            urlBuilder.addQueryParameter(CAPTCHA_FIELD, captcha)
        }
        val request = baseRequest(urlBuilder.build().toString(), authToken).get().build()
        execute(request)
    }

    suspend inline fun <reified T, reified B> post(
        path: String,
        body: B,
        authToken: String? = null,
    ): T = withContext(Dispatchers.IO) {
        val request = baseRequest("$API_BASE_URL$path", authToken)
            .post(requestBodyWithCaptcha(body))
            .build()
        execute(request)
    }

    suspend fun postEmptySuccess(path: String, authToken: String? = null): Boolean = withContext(Dispatchers.IO) {
        val request = baseRequest("$API_BASE_URL$path", authToken)
            .post(captchaBodyOrNull() ?: ByteArray(0).toRequestBody(null))
            .build()
        executeSuccess(request)
    }

    suspend inline fun <reified T, reified B> put(
        path: String,
        body: B,
        authToken: String? = null,
    ): T = withContext(Dispatchers.IO) {
        val request = baseRequest("$API_BASE_URL$path", authToken)
            .put(requestBodyWithCaptcha(body))
            .build()
        execute(request)
    }

    suspend fun putEmptySuccess(path: String, authToken: String? = null): Boolean = withContext(Dispatchers.IO) {
        val request = baseRequest("$API_BASE_URL$path", authToken)
            .put(captchaBodyOrNull() ?: ByteArray(0).toRequestBody(null))
            .build()
        executeSuccess(request)
    }

    suspend inline fun <reified T> delete(path: String, authToken: String? = null): T =
        withContext(Dispatchers.IO) {
            val requestBuilder = baseRequest("$API_BASE_URL$path", authToken)
            val request = captchaBodyOrNull()
                ?.let { requestBuilder.delete(it).build() }
                ?: requestBuilder.delete().build()
            execute(request)
        }

    suspend fun deleteSuccess(path: String, authToken: String? = null): Boolean = withContext(Dispatchers.IO) {
        val requestBuilder = baseRequest("$API_BASE_URL$path", authToken)
        val request = captchaBodyOrNull()
            ?.let { requestBuilder.delete(it).build() }
            ?: requestBuilder.delete().build()
        executeSuccess(request)
    }

    suspend inline fun <reified B> deleteSuccess(
        path: String,
        body: B,
        authToken: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val request = baseRequest("$API_BASE_URL$path", authToken)
            .delete(requestBodyWithCaptcha(body))
            .build()
        executeSuccess(request)
    }

    @PublishedApi
    internal inline fun <reified B> requestBodyWithCaptcha(body: B): RequestBody {
        val captcha = consumeCaptchaResponse()
        val element = json.encodeToJsonElement(body)
        val patchedElement = if (!captcha.isNullOrBlank() && element is JsonObject) {
            JsonObject(element + (CAPTCHA_FIELD to JsonPrimitive(captcha)))
        } else {
            element
        }
        return json.encodeToString(JsonElement.serializer(), patchedElement).toRequestBody(JSON_MEDIA_TYPE)
    }

    @PublishedApi
    internal fun captchaBodyOrNull(): RequestBody? {
        val captcha = consumeCaptchaResponse() ?: return null
        val element = JsonObject(mapOf(CAPTCHA_FIELD to JsonPrimitive(captcha)))
        return json.encodeToString(JsonElement.serializer(), element).toRequestBody(JSON_MEDIA_TYPE)
    }

    @PublishedApi
    internal fun consumeCaptchaResponse(): String? {
        val response = pendingCaptchaResponse
        pendingCaptchaResponse = null
        return response
    }

    @PublishedApi
    internal fun baseRequest(url: String, authToken: String?): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json, image/avif, image/webp")
            .header("Lang", contentLanguage.apiCode)
            .header("Vary", "json")
            .header("X-Application", API_APPLICATION_ID)
            .header("User-Agent", APP_USER_AGENT)
            .apply {
                if (!authToken.isNullOrBlank()) header("Authorization", "Bearer $authToken")
            }
    }

    @PublishedApi
    internal inline fun <reified T> execute(request: Request): T {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throwApiError(response.code, body)
            return json.decodeFromString<ApiEnvelope<T>>(body).response
        }
    }

    @PublishedApi
    internal fun executeSuccess(request: Request): Boolean {
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
            val root = json.parseToJsonElement(this).jsonObject
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

@PublishedApi
internal const val API_BASE_URL = "https://api.yani.tv"

@PublishedApi
internal const val API_APPLICATION_ID = "wawegr8j13it4rdw"

@PublishedApi
internal const val CAPTCHA_FIELD = "recaptcha_response"

@PublishedApi
internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
