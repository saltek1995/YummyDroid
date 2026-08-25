package me.yummydroid.app.data

import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

// YummyAnimeApiRequestFactory
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
// YummyAnimeApiResponseReader
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
// YummyAnimeApiRuntime
open class YummyAnimeApiRuntime(
    client: OkHttpClient,
    initialContentLanguage: ContentLanguage = ContentLanguage.Russian,
) {
    private val transport = YummyAnimeApiTransport(client, initialContentLanguage)
    private val catalog = YummyAnimeCatalogApi(transport)
    private val account = YummyAnimeAccountApi(transport)
    private val community = YummyAnimeCommunityApi(transport)
    private val notifications = YummyAnimeNotificationApi(transport)

    fun updateContentLanguage(language: ContentLanguage) = transport.updateContentLanguage(language)

    fun submitCaptchaResponse(response: String) = transport.submitCaptchaResponse(response)

    suspend fun featuredAnime(
        limit: Int,
        offset: Int,
        filters: BrowseFilters,
        authToken: String? = null,
        ids: Set<Long> = emptySet(),
    ): List<Anime> = catalog.featuredAnime(limit, offset, filters, authToken, ids)

    suspend fun search(
        query: String,
        limit: Int,
        offset: Int,
        filters: BrowseFilters,
        authToken: String? = null,
        ids: Set<Long> = emptySet(),
    ): List<Anime> = catalog.search(query, limit, offset, filters, authToken, ids)

    suspend fun getFilterCatalog(): FilterCatalog = catalog.getFilterCatalog()

    suspend fun getAnime(animeId: Long, token: String? = null): AnimeDetails = catalog.getAnime(animeId, token)

    suspend fun getAnimeWithVideos(
        animeId: Long,
        token: String? = null,
    ): Pair<AnimeDetails, List<VideoVariant>> = catalog.getAnimeWithVideos(animeId, token)

    suspend fun getAnimeWithVideos(
        animeAlias: String,
        token: String? = null,
    ): Pair<AnimeDetails, List<VideoVariant>> = catalog.getAnimeWithVideos(animeAlias, token)

    suspend fun getVideos(animeId: Long, token: String? = null): List<VideoVariant> =
        catalog.getVideos(animeId, token)

    suspend fun getUserListAnimeIds(userId: Long, listId: Int, token: String): Set<Long> =
        account.getUserListAnimeIds(userId, listId, token)

    suspend fun getUserFavoriteAnimeIds(userId: Long, token: String): Set<Long> =
        account.getUserFavoriteAnimeIds(userId, token)

    suspend fun login(login: String, password: String, captchaResponse: String? = null): String =
        account.login(login, password, captchaResponse)

    suspend fun refreshToken(token: String): String = account.refreshToken(token)

    suspend fun getProfile(token: String): UserProfile = account.getProfile(token)

    suspend fun getAnimeMark(animeId: Long, token: String): UserAnimeMark = account.getAnimeMark(animeId, token)

    suspend fun setAnimeListMark(animeId: Long, mark: UserAnimeListMark, token: String): UserAnimeMark =
        account.setAnimeListMark(animeId, mark, token)

    suspend fun removeAnimeListMark(animeId: Long, token: String): UserAnimeMark =
        account.removeAnimeListMark(animeId, token)

    suspend fun setFavorite(animeId: Long, isFavorite: Boolean, token: String): UserAnimeMark =
        account.setFavorite(animeId, isFavorite, token)

    suspend fun getWatchHistory(token: String, limit: Int = 100, offset: Int = 0): List<PlaybackProgress> =
        account.getWatchHistory(token, limit, offset)

    suspend fun saveWatchProgress(progress: PlaybackProgress, token: String): Boolean =
        account.saveWatchProgress(progress, token)

    suspend fun deleteWatchProgress(videoIds: List<Long>, token: String): Boolean =
        account.deleteWatchProgress(videoIds, token)

    suspend fun getSchedule(): List<ScheduleAnime> = catalog.getSchedule()

    suspend fun getCollections(offset: Int = 0, limit: Int = 24): List<AnimeCollectionSummary> =
        community.getCollections(offset, limit)

    suspend fun getCollection(id: Long): AnimeCollectionSummary = community.getCollection(id)

    suspend fun getAnimeCollections(
        animeId: Long,
        offset: Int = 0,
        limit: Int = 12,
    ): List<AnimeCollectionSummary> = community.getAnimeCollections(animeId, offset, limit)

    suspend fun getAnimeComments(animeId: Long, offset: Int = 0, limit: Int = 20): List<AnimeComment> =
        community.getAnimeComments(animeId, offset, limit)

    suspend fun addAnimeComment(animeId: Long, text: String, token: String): AnimeComment? =
        community.addAnimeComment(animeId, text, token)

    suspend fun getAnimeRecommendations(animeId: Long, offset: Int = 0, limit: Int = 12): List<Anime> =
        community.getAnimeRecommendations(animeId, offset, limit)

    suspend fun getAnimeRatingSummary(animeId: Long): AnimeRatingSummary =
        community.getAnimeRatingSummary(animeId)

    suspend fun setAnimeRating(animeId: Long, rating: Int, token: String): AnimeRatingSummary =
        community.setAnimeRating(animeId, rating, token)

    suspend fun deleteAnimeRating(animeId: Long, token: String): AnimeRatingSummary =
        community.deleteAnimeRating(animeId, token)

    suspend fun subscribeVideo(videoId: Long, token: String): Boolean =
        notifications.subscribeVideo(videoId, token)

    suspend fun unsubscribeVideo(videoId: Long, token: String): Boolean =
        notifications.unsubscribeVideo(videoId, token)

    suspend fun getVideoSubscriptions(userId: Long, token: String): List<VideoSubscription> =
        notifications.getVideoSubscriptions(userId, token)

    suspend fun getProfileNotifications(
        token: String,
        types: List<String> = emptyList(),
        subTypes: List<String> = emptyList(),
        offset: Int = 0,
        limit: Int = 50,
    ): List<SiteNotification> = notifications.getProfileNotifications(token, types, subTypes, offset, limit)

    suspend fun markProfileNotificationsRead(token: String): Boolean =
        notifications.markProfileNotificationsRead(token)

    suspend fun markProfileNotificationRead(notificationId: Long, token: String): Boolean =
        notifications.markProfileNotificationRead(notificationId, token)

    suspend fun deleteProfileNotification(notificationId: Long, token: String): Boolean =
        notifications.deleteProfileNotification(notificationId, token)
}
// YummyAnimeApiTransport
internal val defaultYummyAnimeApiClient: OkHttpClient = OkHttpClient.Builder()
    .callTimeout(30, TimeUnit.SECONDS)
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

internal class YummyAnimeApiTransport(
    client: OkHttpClient,
    initialContentLanguage: ContentLanguage,
) {
    @PublishedApi
    internal val requests = YummyAnimeApiRequestFactory(initialContentLanguage)

    @PublishedApi
    internal val responses = YummyAnimeApiResponseReader(client)

    val locale
        get() = requests.locale

    fun updateContentLanguage(language: ContentLanguage) = requests.updateContentLanguage(language)

    fun submitCaptchaResponse(response: String) = requests.submitCaptchaResponse(response)

    suspend inline fun <reified T> get(
        path: String,
        params: List<Pair<String, String>> = emptyList(),
        authToken: String? = null,
    ): T = read { requests.get(path, params, authToken) }

    suspend inline fun <reified T, reified B> post(
        path: String,
        body: B,
        authToken: String? = null,
    ): T = read {
        requests.write(ApiWriteMethod.Post, path, authToken, prepareBodyBeforeRequest = true) {
            requests.withCaptcha(body)
        }
    }

    suspend fun postEmptySuccess(path: String, authToken: String? = null): Boolean = success {
        requests.write(ApiWriteMethod.Post, path, authToken) { requests.captchaBodyOrNull() }
    }

    suspend inline fun <reified T, reified B> put(
        path: String,
        body: B,
        authToken: String? = null,
    ): T = read {
        requests.write(ApiWriteMethod.Put, path, authToken, prepareBodyBeforeRequest = true) {
            requests.withCaptcha(body)
        }
    }

    suspend inline fun <reified T> putEmpty(
        path: String,
        authToken: String? = null,
    ): T = read {
        requests.write(ApiWriteMethod.Put, path, authToken) { requests.captchaBodyOrNull() }
    }

    suspend fun putEmptySuccess(path: String, authToken: String? = null): Boolean = success {
        requests.write(ApiWriteMethod.Put, path, authToken) { requests.captchaBodyOrNull() }
    }

    suspend inline fun <reified T> delete(path: String, authToken: String? = null): T = read {
        requests.write(ApiWriteMethod.Delete, path, authToken) { requests.captchaBodyOrNull() }
    }

    suspend fun deleteSuccess(path: String, authToken: String? = null): Boolean = success {
        requests.write(ApiWriteMethod.Delete, path, authToken) { requests.captchaBodyOrNull() }
    }

    suspend inline fun <reified B> deleteSuccess(
        path: String,
        body: B,
        authToken: String? = null,
    ): Boolean = success {
        requests.write(ApiWriteMethod.Delete, path, authToken) { requests.withCaptcha(body) }
    }

    @PublishedApi
    internal suspend inline fun <reified T> read(crossinline request: () -> Request): T {
        return withContext(Dispatchers.IO) { responses.read(request()) }
    }

    @PublishedApi
    internal suspend fun success(request: () -> Request): Boolean {
        return withContext(Dispatchers.IO) { responses.isSuccessful(request()) }
    }
}
