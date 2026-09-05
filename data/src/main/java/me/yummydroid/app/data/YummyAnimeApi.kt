package me.yummydroid.app.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import okhttp3.Request

// ApiErrors
fun Throwable.isUnauthorizedApiError(): Boolean {
    return this is ApiHttpException && statusCode in UNAUTHORIZED_STATUS_CODES
}

private val UNAUTHORIZED_STATUS_CODES = setOf(401, 403)
// AppUpdateInfo
data class AppUpdateInfo(
    val version: String,
    val title: String,
    val body: String,
    val pageUrl: String,
    val apkUrl: String,
    val publishedAt: String,
) {
    val normalizedVersion: String
        get() = version.trim().removePrefix("v")
}
// GitHubUpdateChecker
class GitHubUpdateChecker(
    private val owner: String = "saltek1995",
    private val repo: String = "YummyDroid",
    private val client: OkHttpClient = defaultClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    suspend fun latestRelease(): AppUpdateInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "YummyDroid Android")
            .build()

        client.withCancellableResponse(request) { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("GitHub releases are unavailable: HTTP ${response.code}")
            }
            json.decodeFromString<GitHubReleaseDto>(body).toUpdateInfo()
        }
    }

    private companion object {
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}

@Serializable
private data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    val assets: List<GitHubReleaseAssetDto> = emptyList(),
)

@Serializable
private data class GitHubReleaseAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

private fun GitHubReleaseDto.toUpdateInfo(): AppUpdateInfo {
    val apkAsset = assets.firstOrNull { asset ->
        asset.name.endsWith(".apk", ignoreCase = true) &&
            asset.browserDownloadUrl.isNotBlank()
    }
    return AppUpdateInfo(
        version = tagName.ifBlank { name },
        title = name.ifBlank { tagName },
        body = body,
        pageUrl = htmlUrl,
        apkUrl = apkAsset?.browserDownloadUrl.orEmpty(),
        publishedAt = publishedAt,
    )
}
// Versioning
fun AppUpdateInfo.isNewerThanVersion(currentVersion: String): Boolean {
    val latest = normalizedVersion.versionParts()
    val current = currentVersion.versionParts()
    val maxSize = maxOf(latest.size, current.size)
    repeat(maxSize) { index ->
        val left = latest.getOrElse(index) { 0 }
        val right = current.getOrElse(index) { 0 }
        if (left != right) return left > right
    }
    return false
}

private fun String.versionParts(): List<Int> {
    return trim()
        .removePrefix("v")
        .split('.', '-', '_')
        .mapNotNull { part -> part.takeWhile(Char::isDigit).toIntOrNull() }
        .ifEmpty { listOf(0) }
}
// YummyAnimeAccountApi
internal class YummyAnimeAccountApi(
    private val transport: YummyAnimeApiTransport,
) {
    suspend fun getUserListAnimeIds(userId: Long, listId: Int, token: String): Set<Long> {
        return transport.get<List<UserListAnimeDto>>(
            path = "/users/$userId/lists/$listId",
            authToken = token,
        ).positiveAnimeIds()
    }

    suspend fun getUserFavoriteAnimeIds(userId: Long, token: String): Set<Long> {
        return transport.get<List<UserListAnimeDto>>(
            path = "/users/$userId/lists",
            authToken = token,
        ).filter { it.user?.list?.isFavorite == true }
            .positiveAnimeIds()
    }

    suspend fun login(login: String, password: String, captchaResponse: String?): String {
        val response = transport.post<LoginResponseDto, LoginRequestDto>(
            path = "/profile/login",
            body = LoginRequestDto(
                login = login,
                password = password,
                needJson = true,
                recaptchaResponse = captchaResponse,
            ),
        )
        if (!response.success || response.token.isBlank()) throw IOException("Could not sign in")
        return response.token
    }

    suspend fun refreshToken(token: String): String {
        return transport.get<TokenResponseDto>(
            path = "/profile/token",
            authToken = token,
        ).token.takeIf { it.isNotBlank() }
            ?: throw IOException("Could not refresh token")
    }

    suspend fun getProfile(token: String): UserProfile {
        return transport.get<ProfileDto>(path = "/profile", authToken = token).toUserProfile()
    }

    suspend fun getAnimeMark(animeId: Long, token: String): UserAnimeMark {
        return transport.get<UserAnimeMarkDto>(
            path = "/anime/$animeId/list",
            authToken = token,
        ).toUserAnimeMark()
    }

    suspend fun setAnimeListMark(animeId: Long, mark: UserAnimeListMark, token: String): UserAnimeMark {
        transport.put<JsonElement, SetAnimeListRequestDto>(
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
        transport.delete<JsonElement>(path = "/anime/$animeId/list", authToken = token)
        return getAnimeMark(animeId, token)
    }

    suspend fun setFavorite(animeId: Long, isFavorite: Boolean, token: String): UserAnimeMark {
        if (isFavorite) {
            transport.put<JsonElement, FavoriteRequestDto>(
                path = "/anime/$animeId/list/fav",
                body = FavoriteRequestDto(date = System.currentTimeMillis() / 1000L),
                authToken = token,
            )
        } else {
            transport.delete<JsonElement>(path = "/anime/$animeId/list/fav", authToken = token)
        }
        return getAnimeMark(animeId, token)
    }

    suspend fun getWatchHistory(token: String, limit: Int, offset: Int): List<PlaybackProgress> {
        return transport.get<List<WatchHistoryDto>>(
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
        val positionSeconds = progress.positionMs.toWholeSeconds()
        return transport.put(
            path = "/video/${progress.videoId}",
            body = SetVideoWatchRequestDto(
                time = positionSeconds,
                duration = progress.durationMs.toWholeSeconds(),
                date = (progress.updatedAtMs / 1000L).coerceAtLeast(0L),
                times = listOf(positionSeconds).filter { it > 0 },
            ),
            authToken = token,
        )
    }

    suspend fun deleteWatchProgress(videoIds: List<Long>, token: String): Boolean {
        val normalizedIds = videoIds.filter { it > 0L }.distinct()
        if (normalizedIds.isEmpty()) return true
        return transport.deleteSuccess(
            path = "/video",
            body = DeleteVideoWatchRequestDto(videoIds = normalizedIds),
            authToken = token,
        )
    }

    private fun List<UserListAnimeDto>.positiveAnimeIds(): Set<Long> {
        return mapNotNull { it.animeId.takeIf { animeId -> animeId > 0 } }.toSet()
    }
}
// YummyAnimeApi
class YummyAnimeApi(
    client: OkHttpClient = defaultYummyAnimeApiClient,
    initialContentLanguage: ContentLanguage = ContentLanguage.Russian,
) : YummyAnimeApiRuntime(client, initialContentLanguage)

// YummyAnimeCatalogApi
internal class YummyAnimeCatalogApi(
    private val transport: YummyAnimeApiTransport,
) {
    suspend fun featuredAnime(
        limit: Int,
        offset: Int,
        filters: BrowseFilters,
        authToken: String?,
        ids: Set<Long>,
    ): List<Anime> {
        return loadAnime(
            filters.toAnimeQueryParams(
                query = null,
                limit = limit,
                offset = offset,
                ids = ids,
            ),
            authToken,
        )
    }

    suspend fun search(
        query: String,
        limit: Int,
        offset: Int,
        filters: BrowseFilters,
        authToken: String?,
        ids: Set<Long>,
    ): List<Anime> {
        return loadAnime(
            filters.toAnimeQueryParams(
                query = query,
                limit = limit,
                offset = offset,
                ids = ids,
            ),
            authToken,
        )
    }

    suspend fun getFilterCatalog(): FilterCatalog {
        return transport.get<CatalogDto>(path = "/anime/catalog")
            .toFilterCatalog(transport.locale)
    }

    suspend fun getAnime(animeId: Long, token: String?): AnimeDetails {
        return transport.get<AnimeDto>(path = "/anime/$animeId", authToken = token)
            .toDetails(transport.locale)
    }

    suspend fun getAnimeWithVideos(
        animeId: Long,
        token: String?,
    ): Pair<AnimeDetails, List<VideoVariant>> {
        return loadAnimeWithVideos(animeId.toString(), token).toDetailsWithVideos(transport.locale)
    }

    suspend fun getAnimeWithVideos(
        animeAlias: String,
        token: String?,
    ): Pair<AnimeDetails, List<VideoVariant>> {
        return loadAnimeWithVideos(animeAlias, token).toDetailsWithVideos(transport.locale)
    }

    suspend fun getVideos(animeId: Long, token: String?): List<VideoVariant> {
        return loadAnimeWithVideos(animeId.toString(), token)
            .toVideoVariants()
    }

    suspend fun getSchedule(): List<ScheduleAnime> {
        return transport.get<List<ScheduleAnimeDto>>(path = "/anime/schedule")
            .mapNotNull { it.toScheduleAnime() }
    }

    private suspend fun loadAnime(params: List<Pair<String, String>>, authToken: String?): List<Anime> {
        return transport.get<List<AnimeDto>>(
            path = "/anime",
            params = params,
            authToken = authToken,
        ).map { it.toAnime() }
    }

    private suspend fun loadAnimeWithVideos(animeReference: String, token: String?): AnimeDto {
        return transport.get(
            path = "/anime/$animeReference",
            params = listOf("need_videos" to "true"),
            authToken = token,
        )
    }
}
// YummyAnimeCommunityApi
internal class YummyAnimeCommunityApi(
    private val transport: YummyAnimeApiTransport,
) {
    suspend fun getCollections(offset: Int, limit: Int): List<AnimeCollectionSummary> {
        return loadCollections(
            path = "/collection",
            offset = offset,
            limit = limit,
        )
    }

    suspend fun getCollection(id: Long): AnimeCollectionSummary {
        return transport.get<CollectionDto>(path = "/collection/$id").toAnimeCollectionSummary()
    }

    suspend fun getAnimeCollections(animeId: Long, offset: Int, limit: Int): List<AnimeCollectionSummary> {
        return loadCollections(
            path = "/anime/$animeId/collections",
            offset = offset,
            limit = limit,
        )
    }

    suspend fun getAnimeComments(animeId: Long, offset: Int, limit: Int): List<AnimeComment> {
        return transport.get<CommentsResponseDto>(
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
        return transport.post<JsonElement, CommentRequestDto>(
            path = "/comments/anime/$animeId",
            body = CommentRequestDto(text = trimmedText),
            authToken = token,
        ).toAnimeCommentOrNull()
    }

    suspend fun getAnimeRecommendations(animeId: Long, offset: Int, limit: Int): List<Anime> {
        return transport.get<List<AnimeDto>>(
            path = "/anime/$animeId/recommendations",
            params = listOf(
                "limit" to limit.coerceIn(1, 100).toString(),
                "offset" to offset.coerceAtLeast(0).toString(),
                "from_ai" to "true",
            ),
        ).map { it.toAnime() }
    }

    suspend fun getAnimeRatingSummary(animeId: Long): AnimeRatingSummary {
        val buckets = transport.get<List<RatingBucketDto>>(path = "/anime/$animeId/rates")
            .mapNotNull { it.toAnimeRatingBucket() }
        return AnimeRatingSummary(buckets = buckets)
    }

    suspend fun setAnimeRating(animeId: Long, rating: Int, token: String): AnimeRatingSummary {
        transport.put<JsonElement, RateRequestDto>(
            path = "/anime/$animeId/rate",
            body = RateRequestDto(rate = rating.coerceIn(1, 10)),
            authToken = token,
        )
        return getAnimeRatingSummary(animeId)
    }

    suspend fun deleteAnimeRating(animeId: Long, token: String): AnimeRatingSummary {
        transport.delete<JsonElement>(path = "/anime/$animeId/rate", authToken = token)
        return getAnimeRatingSummary(animeId)
    }

    private suspend fun loadCollections(
        path: String,
        offset: Int,
        limit: Int,
    ): List<AnimeCollectionSummary> {
        return transport.get<List<CollectionDto>>(
            path = path,
            params = listOf(
                "limit" to limit.coerceIn(1, 100).toString(),
                "offset" to offset.coerceAtLeast(0).toString(),
            ),
        ).map { it.toAnimeCollectionSummary() }
    }
}
// YummyAnimeNotificationApi
internal class YummyAnimeNotificationApi(
    private val transport: YummyAnimeApiTransport,
) {
    suspend fun subscribeVideo(videoId: Long, token: String): Boolean {
        return transport.putEmpty<Boolean>(
            path = "/video/$videoId/subscribe",
            authToken = token,
        )
    }

    suspend fun unsubscribeVideo(videoId: Long, token: String): Boolean {
        return transport.delete<Boolean>(
            path = "/video/$videoId/subscribe",
            authToken = token,
        )
    }

    suspend fun getVideoSubscriptions(userId: Long, token: String): List<VideoSubscription> {
        return transport.get<List<SubscriptionDto>>(
            path = "/users/$userId/lists/subs",
            authToken = token,
        ).mapNotNull(SubscriptionDto::toVideoSubscription)
    }

    suspend fun getProfileNotifications(
        token: String,
        types: List<String>,
        subTypes: List<String>,
        offset: Int,
        limit: Int,
    ): List<SiteNotification> {
        return transport.get<List<NotificationDto>>(
            path = "/profile/notifications",
            params = notificationParams(types, subTypes, offset, limit),
            authToken = token,
        ).mapNotNull { it.toSiteNotification() }
    }

    suspend fun markProfileNotificationsRead(token: String): Boolean {
        return transport.postEmptySuccess(
            path = "/profile/notifications/read",
            authToken = token,
        )
    }

    suspend fun markProfileNotificationRead(notificationId: Long, token: String): Boolean {
        if (notificationId <= 0L) return false
        return transport.postEmptySuccess(
            path = "/profile/notifications/$notificationId/read",
            authToken = token,
        )
    }

    suspend fun deleteProfileNotification(notificationId: Long, token: String): Boolean {
        if (notificationId <= 0L) return false
        return transport.deleteSuccess(
            path = "/profile/notifications/$notificationId",
            authToken = token,
        )
    }

    private fun notificationParams(
        types: List<String>,
        subTypes: List<String>,
        offset: Int,
        limit: Int,
    ): List<Pair<String, String>> = buildList {
        types.forEach { add("type" to it) }
        subTypes.forEach { add("sub_type" to it) }
        add("offset" to offset.coerceAtLeast(0).toString())
        add("limit" to limit.coerceIn(1, 100).toString())
    }
}
