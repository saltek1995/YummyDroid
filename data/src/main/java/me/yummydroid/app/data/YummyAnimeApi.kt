package me.yummydroid.app.data

import java.io.IOException
import java.util.concurrent.TimeUnit
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

class YummyAnimeApi(
    private val client: OkHttpClient = defaultClient,
    initialContentLanguage: ContentLanguage = ContentLanguage.Russian,
) {
    @Volatile
    private var contentLanguage: ContentLanguage = initialContentLanguage

    @Volatile
    private var pendingCaptchaResponse: String? = null

    private val json = Json {
        coerceInputValues = true
        explicitNulls = false
        ignoreUnknownKeys = true
    }

    fun updateContentLanguage(language: ContentLanguage) {
        contentLanguage = language
    }

    fun submitCaptchaResponse(response: String) {
        pendingCaptchaResponse = response.trim().takeIf { it.isNotBlank() }
    }

    suspend fun featuredAnime(
        limit: Int,
        offset: Int,
        filters: BrowseFilters,
        authToken: String? = null,
        ids: Set<Long> = emptySet(),
    ): List<Anime> {
        return get<List<AnimeDto>>(
            path = "/anime",
            params = filters.toApiParams() + listOf(
                "limit" to limit.toString(),
                "offset" to offset.coerceAtLeast(0).toString(),
            ) + ids.map { "ids" to it.toString() },
            authToken = authToken,
        ).map { it.toAnime() }
    }

    suspend fun search(
        query: String,
        limit: Int,
        offset: Int,
        filters: BrowseFilters,
        authToken: String? = null,
        ids: Set<Long> = emptySet(),
    ): List<Anime> {
        return get<List<AnimeDto>>(
            path = "/anime",
            params = filters.toApiParams() + listOf(
                "q" to query,
                "limit" to limit.toString(),
                "offset" to offset.coerceAtLeast(0).toString(),
            ) + ids.map { "ids" to it.toString() },
            authToken = authToken,
        ).map { it.toAnime() }
    }

    suspend fun getFilterCatalog(): FilterCatalog {
        return get<CatalogDto>(path = "/anime/catalog").toFilterCatalog(contentLanguage.locale)
    }

    suspend fun getAnime(animeId: Long, token: String? = null): AnimeDetails {
        return get<AnimeDto>(path = "/anime/$animeId", authToken = token).toDetails(contentLanguage.locale)
    }

    suspend fun getAnimeWithVideos(animeId: Long, token: String? = null): Pair<AnimeDetails, List<VideoVariant>> {
        val anime = get<AnimeDto>(
            path = "/anime/$animeId",
            params = listOf("need_videos" to "true"),
            authToken = token,
        )

        return anime.toDetailsWithVideos(contentLanguage.locale)
    }

    suspend fun getVideos(animeId: Long, token: String? = null): List<VideoVariant> {
        return get<AnimeDto>(
            path = "/anime/$animeId",
            params = listOf("need_videos" to "true"),
            authToken = token,
        ).toDetailsWithVideos(contentLanguage.locale).second
    }

    suspend fun getUserListAnimeIds(userId: Long, listId: Int, token: String): Set<Long> {
        return get<List<UserListAnimeDto>>(
            path = "/users/$userId/lists/$listId",
            authToken = token,
        ).mapNotNull { it.animeId.takeIf { animeId -> animeId > 0 } }
            .toSet()
    }

    suspend fun getUserFavoriteAnimeIds(userId: Long, token: String): Set<Long> {
        return get<List<UserListAnimeDto>>(
            path = "/users/$userId/lists",
            authToken = token,
        ).filter { it.user?.list?.isFavorite == true }
            .mapNotNull { it.animeId.takeIf { animeId -> animeId > 0 } }
            .toSet()
    }

    suspend fun login(login: String, password: String, captchaResponse: String? = null): String {
        val response: LoginResponseDto = post(
            path = "/profile/login",
            body = LoginRequestDto(
                login = login,
                password = password,
                needJson = true,
                recaptchaResponse = captchaResponse,
            ),
        )
        if (!response.success || response.token.isBlank()) {
            throw IOException("Could not sign in")
        }
        return response.token
    }

    suspend fun refreshToken(token: String): String {
        val response: TokenResponseDto = get(
            path = "/profile/token",
            authToken = token,
        )
        return response.token.takeIf { it.isNotBlank() }
            ?: throw IOException("Could not refresh token")
    }

    suspend fun getProfile(token: String): UserProfile {
        return get<ProfileDto>(
            path = "/profile",
            authToken = token,
        ).toUserProfile()
    }

    suspend fun getAnimeMark(animeId: Long, token: String): UserAnimeMark {
        return get<UserAnimeMarkDto>(
            path = "/anime/$animeId/list",
            authToken = token,
        ).toUserAnimeMark()
    }

    suspend fun setAnimeListMark(animeId: Long, mark: UserAnimeListMark, token: String): UserAnimeMark {
        put<JsonElement, SetAnimeListRequestDto>(
            path = "/anime/$animeId/list",
            body = SetAnimeListRequestDto(
                list = mark.id,
                date = System.currentTimeMillis() / 1000L,
            ),
            authToken = token,
        )
        return getAnimeMark(animeId, token)
    }

    suspend fun removeAnimeListMark(animeId: Long, token: String): UserAnimeMark {
        delete<JsonElement>(
            path = "/anime/$animeId/list",
            authToken = token,
        )
        return getAnimeMark(animeId, token)
    }

    suspend fun setFavorite(animeId: Long, isFavorite: Boolean, token: String): UserAnimeMark {
        if (isFavorite) {
            put<JsonElement, FavoriteRequestDto>(
                path = "/anime/$animeId/list/fav",
                body = FavoriteRequestDto(date = System.currentTimeMillis() / 1000L),
                authToken = token,
            )
        } else {
            delete<JsonElement>(
                path = "/anime/$animeId/list/fav",
                authToken = token,
            )
        }
        return getAnimeMark(animeId, token)
    }

    suspend fun getWatchHistory(token: String, limit: Int = 100, offset: Int = 0): List<PlaybackProgress> {
        return get<List<WatchHistoryDto>>(
            path = "/video/watch-history",
            params = listOf(
                "limit" to limit.coerceIn(0, 100).toString(),
                "offset" to offset.coerceIn(0, 100_000).toString(),
            ),
            authToken = token,
        ).mapNotNull { it.toPlaybackProgress() }
    }

    suspend fun saveWatchProgress(progress: PlaybackProgress, token: String): Boolean {
        if (progress.videoId <= 0L) return false
        return put<Boolean, SetVideoWatchRequestDto>(
            path = "/video/${progress.videoId}",
            body = SetVideoWatchRequestDto(
                time = progress.positionMs.toWholeSeconds(),
                duration = progress.durationMs.toWholeSeconds(),
                date = (progress.updatedAtMs / 1000L).coerceAtLeast(0L),
                times = listOf(progress.positionMs.toWholeSeconds()).filter { it > 0 },
            ),
            authToken = token,
        )
    }

    suspend fun deleteWatchProgress(videoIds: List<Long>, token: String): Boolean {
        val normalizedIds = videoIds.filter { it > 0L }.distinct()
        if (normalizedIds.isEmpty()) return true
        return deleteSuccess(
            path = "/video",
            body = DeleteVideoWatchRequestDto(videoIds = normalizedIds),
            authToken = token,
        )
    }

    suspend fun getSchedule(): List<ScheduleAnime> {
        return get<List<ScheduleAnimeDto>>(path = "/anime/schedule")
            .mapNotNull { it.toScheduleAnime() }
    }

    suspend fun getCollections(offset: Int = 0, limit: Int = 24): List<AnimeCollectionSummary> {
        return get<List<CollectionDto>>(
            path = "/collection",
            params = listOf(
                "limit" to limit.coerceIn(1, 100).toString(),
                "offset" to offset.coerceAtLeast(0).toString(),
            ),
        ).map { it.toAnimeCollectionSummary() }
    }

    suspend fun getCollection(id: Long): AnimeCollectionSummary {
        return get<CollectionDto>(path = "/collection/$id").toAnimeCollectionSummary()
    }

    suspend fun getAnimeCollections(animeId: Long, offset: Int = 0, limit: Int = 12): List<AnimeCollectionSummary> {
        return get<List<CollectionDto>>(
            path = "/anime/$animeId/collections",
            params = listOf(
                "limit" to limit.coerceIn(1, 100).toString(),
                "offset" to offset.coerceAtLeast(0).toString(),
            ),
        ).map { it.toAnimeCollectionSummary() }
    }

    suspend fun getAnimeComments(animeId: Long, offset: Int = 0, limit: Int = 20): List<AnimeComment> {
        return get<CommentsResponseDto>(
            path = "/comments/anime/$animeId",
            params = listOf(
                "limit" to limit.coerceIn(1, 50).toString(),
                "skip" to offset.coerceAtLeast(0).toString(),
                "sort" to "new",
            ),
        ).comments.map { it.toAnimeComment() }
    }

    suspend fun addAnimeComment(animeId: Long, text: String, token: String): AnimeComment? {
        val trimmedText = text.trim()
        if (trimmedText.isBlank()) return null
        val response: JsonElement = post(
            path = "/comments/anime/$animeId",
            body = CommentRequestDto(text = trimmedText),
            authToken = token,
        )
        return response.toAnimeCommentOrNull()
    }

    suspend fun getAnimeRecommendations(animeId: Long, offset: Int = 0, limit: Int = 12): List<Anime> {
        return get<List<AnimeDto>>(
            path = "/anime/$animeId/recommendations",
            params = listOf(
                "limit" to limit.coerceIn(1, 100).toString(),
                "offset" to offset.coerceAtLeast(0).toString(),
                "from_ai" to "true",
            ),
        ).map { it.toAnime() }
    }

    suspend fun getAnimeRatingSummary(animeId: Long): AnimeRatingSummary {
        val buckets = get<List<RatingBucketDto>>(path = "/anime/$animeId/rates")
            .mapNotNull { it.toAnimeRatingBucket() }
        return AnimeRatingSummary(buckets = buckets)
    }

    suspend fun setAnimeRating(animeId: Long, rating: Int, token: String): AnimeRatingSummary {
        put<JsonElement, RateRequestDto>(
            path = "/anime/$animeId/rate",
            body = RateRequestDto(rate = rating.coerceIn(1, 10)),
            authToken = token,
        )
        return getAnimeRatingSummary(animeId)
    }

    suspend fun deleteAnimeRating(animeId: Long, token: String): AnimeRatingSummary {
        delete<JsonElement>(
            path = "/anime/$animeId/rate",
            authToken = token,
        )
        return getAnimeRatingSummary(animeId)
    }

    suspend fun subscribeVideo(videoId: Long, token: String): Boolean {
        return putEmptySuccess(
            path = "/video/$videoId/subscribe",
            authToken = token,
        )
    }

    suspend fun unsubscribeVideo(videoId: Long, token: String): Boolean {
        return deleteSuccess(
            path = "/video/$videoId/subscribe",
            authToken = token,
        )
    }

    suspend fun getVideoSubscriptions(userId: Long, token: String): List<VideoSubscription> {
        return get<List<SubscriptionDto>>(
            path = "/users/$userId/lists/subs",
            authToken = token,
        ).mapNotNull { it.toVideoSubscription() }
    }

    suspend fun getProfileNotifications(
        token: String,
        types: List<String> = emptyList(),
        subTypes: List<String> = emptyList(),
        offset: Int = 0,
        limit: Int = 50,
    ): List<SiteNotification> {
        val params = buildList {
            types.forEach { add("type" to it) }
            subTypes.forEach { add("sub_type" to it) }
            add("offset" to offset.coerceAtLeast(0).toString())
            add("limit" to limit.coerceIn(1, 100).toString())
        }
        return get<List<NotificationDto>>(
            path = "/profile/notifications",
            params = params,
            authToken = token,
        ).mapNotNull { it.toSiteNotification() }
    }

    suspend fun markProfileNotificationsRead(token: String): Boolean {
        return postEmptySuccess(
            path = "/profile/notifications/read",
            authToken = token,
        )
    }

    suspend fun markProfileNotificationRead(notificationId: Long, token: String): Boolean {
        if (notificationId <= 0L) return false
        return postEmptySuccess(
            path = "/profile/notifications/$notificationId/read",
            authToken = token,
        )
    }

    suspend fun deleteProfileNotification(notificationId: Long, token: String): Boolean {
        if (notificationId <= 0L) return false
        return deleteSuccess(
            path = "/profile/notifications/$notificationId",
            authToken = token,
        )
    }

    private suspend inline fun <reified T> get(
        path: String,
        params: List<Pair<String, String>> = emptyList(),
        authToken: String? = null,
    ): T = withContext(Dispatchers.IO) {
        val urlBuilder = "$BASE_URL$path".toHttpUrl().newBuilder()
        params.forEach { (key, value) ->
            if (value.isNotBlank()) urlBuilder.addQueryParameter(key, value)
        }
        consumeCaptchaResponse()?.let { captcha ->
            urlBuilder.addQueryParameter(CAPTCHA_FIELD, captcha)
        }

        val request = baseRequest(urlBuilder.build().toString(), authToken)
            .get()
            .build()

        execute(request)
    }

    private suspend inline fun <reified T, reified B> post(
        path: String,
        body: B,
        authToken: String? = null,
    ): T = withContext(Dispatchers.IO) {
        val requestBody = requestBodyWithCaptcha(body)
        val request = baseRequest("$BASE_URL$path", authToken)
            .post(requestBody)
            .build()

        execute(request)
    }

    private suspend fun postEmptySuccess(
        path: String,
        authToken: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val request = baseRequest("$BASE_URL$path", authToken)
            .post(captchaBodyOrNull() ?: ByteArray(0).toRequestBody(null))
            .build()

        executeSuccess(request)
    }

    private suspend inline fun <reified T, reified B> put(
        path: String,
        body: B,
        authToken: String? = null,
    ): T = withContext(Dispatchers.IO) {
        val requestBody = requestBodyWithCaptcha(body)
        val request = baseRequest("$BASE_URL$path", authToken)
            .put(requestBody)
            .build()

        execute(request)
    }

    private suspend fun putEmptySuccess(
        path: String,
        authToken: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val request = baseRequest("$BASE_URL$path", authToken)
            .put(captchaBodyOrNull() ?: ByteArray(0).toRequestBody(null))
            .build()

        executeSuccess(request)
    }

    private suspend inline fun <reified T> delete(
        path: String,
        authToken: String? = null,
    ): T = withContext(Dispatchers.IO) {
        val requestBuilder = baseRequest("$BASE_URL$path", authToken)
        val request = captchaBodyOrNull()
            ?.let { requestBuilder.delete(it).build() }
            ?: requestBuilder.delete().build()

        execute(request)
    }

    private suspend fun deleteSuccess(
        path: String,
        authToken: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val requestBuilder = baseRequest("$BASE_URL$path", authToken)
        val request = captchaBodyOrNull()
            ?.let { requestBuilder.delete(it).build() }
            ?: requestBuilder.delete().build()

        executeSuccess(request)
    }

    private suspend inline fun <reified B> deleteSuccess(
        path: String,
        body: B,
        authToken: String? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val request = baseRequest("$BASE_URL$path", authToken)
            .delete(requestBodyWithCaptcha(body))
            .build()

        executeSuccess(request)
    }

    private inline fun <reified B> requestBodyWithCaptcha(body: B): RequestBody {
        val captcha = consumeCaptchaResponse()
        val element = json.encodeToJsonElement(body)
        val patchedElement = if (!captcha.isNullOrBlank() && element is JsonObject) {
            JsonObject(element + (CAPTCHA_FIELD to JsonPrimitive(captcha)))
        } else {
            element
        }
        return json.encodeToString(JsonElement.serializer(), patchedElement).toRequestBody(JSON_MEDIA_TYPE)
    }

    private fun captchaBodyOrNull(): RequestBody? {
        val captcha = consumeCaptchaResponse() ?: return null
        val element = JsonObject(mapOf(CAPTCHA_FIELD to JsonPrimitive(captcha)))
        return json.encodeToString(JsonElement.serializer(), element).toRequestBody(JSON_MEDIA_TYPE)
    }

    private fun consumeCaptchaResponse(): String? {
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
            .header("X-Application", APPLICATION_ID)
            .header("User-Agent", USER_AGENT)
            .apply {
                if (!authToken.isNullOrBlank()) {
                    header("Authorization", "Bearer $authToken")
                }
            }
    }

    private inline fun <reified T> execute(request: Request): T {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = body.apiErrorMessage() ?: "YummyAnime API returned HTTP ${response.code}"
                if (response.code == 420) {
                    throw CaptchaRequiredException(message)
                }
                throw ApiHttpException(response.code, message)
            }

            return json.decodeFromString<ApiEnvelope<T>>(body).response
        }
    }

    private fun executeSuccess(request: Request): Boolean {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = body.apiErrorMessage() ?: "YummyAnime API returned HTTP ${response.code}"
                if (response.code == 420) {
                    throw CaptchaRequiredException(message)
                }
                throw ApiHttpException(response.code, message)
            }

            return true
        }
    }

    private fun String.apiErrorMessage(): String? {
        return runCatching {
            val root = json.parseToJsonElement(this).jsonObject
            root["error_title"]?.jsonPrimitive?.contentOrNull
                ?: root["error"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    private companion object {
        const val BASE_URL = "https://api.yani.tv"
        const val APPLICATION_ID = "wawegr8j13it4rdw"
        const val USER_AGENT = APP_USER_AGENT
        const val CAPTCHA_FIELD = "recaptcha_response"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

class CaptchaRequiredException(message: String) : IOException(message)

class ApiHttpException(
    val statusCode: Int,
    message: String,
) : IOException(message)
