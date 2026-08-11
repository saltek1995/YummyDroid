package me.yummydroid.app.data

import java.util.Locale
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

internal enum class ApiWriteMethod {
    Post,
    Put,
    Delete,
}

internal class YummyAnimeApiRequestFactory(initialContentLanguage: ContentLanguage) {
    @Volatile
    private var contentLanguage: ContentLanguage = initialContentLanguage

    @Volatile
    @PublishedApi
    internal var pendingCaptchaResponse: String? = null

    val locale: Locale
        get() = contentLanguage.locale

    fun updateContentLanguage(language: ContentLanguage) {
        contentLanguage = language
    }

    fun submitCaptchaResponse(response: String) {
        pendingCaptchaResponse = response.trim().takeIf { it.isNotBlank() }
    }

    @PublishedApi
    internal fun get(
        path: String,
        params: List<Pair<String, String>>,
        authToken: String?,
    ): Request {
        val urlBuilder = "$API_BASE_URL$path".toHttpUrl().newBuilder()
        params.forEach { (key, value) ->
            if (value.isNotBlank()) urlBuilder.addQueryParameter(key, value)
        }
        consumeCaptchaResponse()?.let { urlBuilder.addQueryParameter(CAPTCHA_FIELD, it) }
        return baseRequest(urlBuilder.build().toString(), authToken).get().build()
    }

    @PublishedApi
    internal fun write(
        method: ApiWriteMethod,
        path: String,
        authToken: String?,
        prepareBodyBeforeRequest: Boolean = false,
        body: () -> RequestBody?,
    ): Request {
        val preparedBody = if (prepareBodyBeforeRequest) body() else null
        val request = baseRequest("$API_BASE_URL$path", authToken)
        val requestBody = if (prepareBodyBeforeRequest) preparedBody else body()
        return when (method) {
            ApiWriteMethod.Post -> request.post(requestBody ?: EMPTY_REQUEST_BODY)
            ApiWriteMethod.Put -> request.put(requestBody ?: EMPTY_REQUEST_BODY)
            ApiWriteMethod.Delete -> requestBody?.let(request::delete) ?: request.delete()
        }.build()
    }

    @PublishedApi
    internal inline fun <reified B> withCaptcha(body: B): RequestBody {
        val captcha = consumeCaptchaResponse()
        val element = YUMMY_ANIME_API_JSON.encodeToJsonElement(body)
        val patchedElement = if (!captcha.isNullOrBlank() && element is JsonObject) {
            JsonObject(element + (CAPTCHA_FIELD to JsonPrimitive(captcha)))
        } else {
            element
        }
        return YUMMY_ANIME_API_JSON.encodeToString(JsonElement.serializer(), patchedElement)
            .toRequestBody(JSON_MEDIA_TYPE)
    }

    @PublishedApi
    internal fun captchaBodyOrNull(): RequestBody? {
        val captcha = consumeCaptchaResponse() ?: return null
        val element = JsonObject(mapOf(CAPTCHA_FIELD to JsonPrimitive(captcha)))
        return YUMMY_ANIME_API_JSON.encodeToString(JsonElement.serializer(), element)
            .toRequestBody(JSON_MEDIA_TYPE)
    }

    @PublishedApi
    internal fun consumeCaptchaResponse(): String? {
        val response = pendingCaptchaResponse
        pendingCaptchaResponse = null
        return response
    }

    private fun baseRequest(url: String, authToken: String?): Request.Builder {
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
}

@PublishedApi
internal val YUMMY_ANIME_API_JSON = Json {
    coerceInputValues = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

@PublishedApi
internal const val API_BASE_URL = "https://api.yani.tv"

private const val API_APPLICATION_ID = "wawegr8j13it4rdw"

@PublishedApi
internal const val CAPTCHA_FIELD = "recaptcha_response"

private val EMPTY_REQUEST_BODY = ByteArray(0).toRequestBody(null)

@PublishedApi
internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
