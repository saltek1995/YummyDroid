package me.yummydroid.app.data

import java.io.IOException
import java.text.Collator
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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

        client.newCall(request).execute().use { response ->
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

// YummyAnimeApiDtos
@Serializable
internal data class ApiEnvelope<T>(
    val response: T,
)

@Serializable
internal data class AnimeDto(
    @SerialName("anime_id") val animeId: Long = 0,
    @SerialName("anime_url") val animeUrl: String = "",
    val title: String = "",
    val description: String = "",
    val poster: PosterDto? = null,
    val rating: JsonElement? = null,
    val genres: List<GenreDto> = emptyList(),
    val creators: List<CatalogLinkDto> = emptyList(),
    val studios: List<CatalogLinkDto> = emptyList(),
    val original: String = "",
    val duration: Int = 0,
    @SerialName("comments_count") val commentsCount: Long = 0,
    @SerialName("lists_count") val listsCount: Long = 0,
    val year: Int = 0,
    val views: Long = 0,
    @SerialName("anime_status") val animeStatus: NamedDto? = null,
    val type: NamedDto? = null,
    @SerialName("min_age") val minAge: AgeDto? = null,
    @SerialName("blocked_in") val blockedIn: List<String> = emptyList(),
    @SerialName("other_titles") val otherTitles: List<String> = emptyList(),
    @SerialName("viewing_order") val viewingOrder: List<ViewingOrderDto> = emptyList(),
    val translates: List<TranslateDto> = emptyList(),
    val episodes: EpisodesDto? = null,
    val videos: List<VideoDto> = emptyList(),
    @SerialName("random_screenshots") val randomScreenshots: List<ScreenshotDto> = emptyList(),
    val user: AnimeUserDto? = null,
)

@Serializable
internal data class ScheduleAnimeDto(
    @SerialName("anime_id") val animeId: Long = 0,
    @SerialName("anime_url") val animeUrl: String = "",
    val title: String = "",
    val description: String = "",
    val poster: PosterDto? = null,
    val episodes: EpisodesDto? = null,
)

@Serializable
internal data class UserListAnimeDto(
    @SerialName("anime_id") val animeId: Long = 0,
    val user: AnimeUserDto? = null,
)

@Serializable
internal data class PosterDto(
    val fullsize: String = "",
    val mega: String = "",
    val huge: String = "",
    val big: String = "",
    val medium: String = "",
    val small: String = "",
)

@Serializable
internal data class GenreDto(
    val title: String = "",
    val id: Long = 0,
    val alias: String = "",
    val url: String = "",
)

@Serializable
internal data class CatalogLinkDto(
    val title: String = "",
    val id: Long = 0,
    val url: String = "",
)

@Serializable
internal data class NamedDto(
    val title: String? = null,
    val name: String? = null,
    val shortname: String? = null,
)

@Serializable
internal data class AgeDto(
    val title: String = "",
    @SerialName("title_long") val titleLong: String = "",
)

@Serializable
internal data class TranslateDto(
    val title: String = "",
)

@Serializable
internal data class EpisodesDto(
    val count: Int = 0,
    val aired: Int = 0,
    @SerialName("next_date") val nextDate: Long = 0,
    @SerialName("prev_date") val previousDate: Long = 0,
)

@Serializable
internal data class ScreenshotDto(
    val sizes: ScreenshotSizesDto? = null,
)

@Serializable
internal data class ScreenshotSizesDto(
    val full: String = "",
    val small: String = "",
)

@Serializable
internal data class VideoDto(
    @SerialName("video_id") val videoId: Long = 0,
    val data: VideoDataDto = VideoDataDto(),
    val number: String = "",
    @SerialName("iframe_url") val iframeUrl: String = "",
    val index: Int = 0,
    val views: Long = 0,
    val duration: Int? = null,
    val skips: VideoSkipsDto? = null,
    val preview: String = "",
    val poster: String = "",
    val image: String = "",
    val screenshot: String = "",
    val subscribed: Boolean = false,
)

@Serializable
internal data class VideoDataDto(
    val player: String = "",
    @SerialName("player_id") val playerId: Long = 0,
    val dubbing: String = "",
)

@Serializable
internal data class VideoSkipsDto(
    val opening: JsonElement? = null,
    val ending: JsonElement? = null,
)

@Serializable
internal data class ViewingOrderDto(
    @SerialName("anime_id") val animeId: Long = 0,
    val title: String = "",
    val poster: PosterDto? = null,
    val year: Int = 0,
    val rating: JsonElement? = null,
    val type: NamedDto? = null,
    @SerialName("anime_status") val animeStatus: NamedDto? = null,
    val data: ViewingOrderDataDto? = null,
)

@Serializable
internal data class ViewingOrderDataDto(
    val index: Int = 0,
    val text: String = "",
)

@Serializable
internal data class AnimeUserDto(
    val list: AnimeUserListWrapperDto? = null,
    val rating: Int? = null,
)

@Serializable
internal data class AnimeUserListWrapperDto(
    @SerialName("is_fav") val isFavorite: Boolean = false,
    val list: AnimeUserListDto? = null,
)

@Serializable
internal data class AnimeUserListDto(
    val id: Int? = null,
)

@Serializable
internal data class CatalogDto(
    val genres: CatalogGenresDto = CatalogGenresDto(),
    val types: List<CatalogTypeEntryDto> = emptyList(),
    val studios: List<CatalogLinkDto> = emptyList(),
    val creators: List<CatalogLinkDto> = emptyList(),
    val directors: List<CatalogLinkDto> = emptyList(),
    val data: List<AnimeDto> = emptyList(),
)

@Serializable
internal data class CatalogGenresDto(
    val genres: List<CatalogGenreDto> = emptyList(),
)

@Serializable
internal data class CatalogGenreDto(
    val title: String = "",
    val href: String = "",
    val value: Long = 0,
)

@Serializable
internal data class CatalogTypeEntryDto(
    val type: CatalogTypeDto = CatalogTypeDto(),
)

@Serializable
internal data class CatalogTypeDto(
    val name: String = "",
    val shortname: String = "",
    val alias: String = "",
)

@Serializable
internal data class LoginRequestDto(
    val login: String,
    val password: String,
    @SerialName("need_json") val needJson: Boolean,
    @SerialName("recaptcha_response") val recaptchaResponse: String? = null,
)

@Serializable
internal data class LoginResponseDto(
    val success: Boolean = false,
    val token: String = "",
)

@Serializable
internal data class TokenResponseDto(
    val token: String = "",
)

@Serializable
internal data class ProfileDto(
    val id: Long = 0,
    val nickname: String = "",
    val about: String = "",
    val banned: Boolean = false,
    val roles: List<String> = emptyList(),
    val avatars: AvatarDto? = null,
    val notifications: ProfileNotificationsDto? = null,
    val messages: ProfileMessagesDto? = null,
)

@Serializable
internal data class AvatarDto(
    val full: String = "",
    val big: String = "",
    val small: String = "",
)

@Serializable
internal data class ProfileNotificationsDto(
    val count: Int = 0,
)

@Serializable
internal data class ProfileMessagesDto(
    @SerialName("unread_count") val unreadCount: Int = 0,
)

@Serializable
internal data class UserAnimeMarkDto(
    val list: Int? = null,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
)

@Serializable
internal data class SetAnimeListRequestDto(
    val date: Long,
    val list: Int,
)

@Serializable
internal data class FavoriteRequestDto(
    val date: Long,
)

@Serializable
internal data class SetVideoWatchRequestDto(
    val time: Int,
    val duration: Int,
    val date: Long,
    val times: List<Int> = emptyList(),
)

@Serializable
internal data class DeleteVideoWatchRequestDto(
    @SerialName("video_ids") val videoIds: List<Long>,
)

@Serializable
internal data class WatchHistoryDto(
    @SerialName("anime_id") val animeId: Long = 0,
    @SerialName("anime_url") val animeUrl: String = "",
    @SerialName("video_id") val videoId: Long = 0,
    @SerialName("ep_title") val episodeTitle: String = "",
    val title: String = "",
    @SerialName("end_time") val endTime: Long = 0,
    val duration: Long = 0,
    val date: Long = 0,
    val poster: PosterDto? = null,
)

@Serializable
internal data class CollectionDto(
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val owner: CollectionOwnerDto? = null,
    val poster: PosterDto? = null,
    val likes: CollectionLikesDto? = null,
    val animes: List<AnimeDto> = emptyList(),
    val views: Long = 0,
    val count: Int = 0,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("date_create") val dateCreate: Long = 0,
)

@Serializable
internal data class CollectionOwnerDto(
    val id: Long = 0,
    val nickname: String = "",
    val login: String = "",
)

@Serializable
internal data class CollectionLikesDto(
    val likes: Long = 0,
    val dislikes: Long = 0,
)

@Serializable
internal data class CommentsResponseDto(
    val comments: List<CommentDto> = emptyList(),
)

@Serializable
internal data class CommentDto(
    val id: Long = 0,
    @SerialName("user_id") val userId: Long = 0,
    val user: CommentUserDto? = null,
    val avatars: AvatarDto? = null,
    val name: String = "",
    val nickname: String = "",
    val login: String = "",
    val username: String = "",
    @SerialName("user_name") val userName: String = "",
    @SerialName("user_nickname") val userNickname: String = "",
    @SerialName("user_login") val userLogin: String = "",
    @SerialName("author") val author: String = "",
    @SerialName("author_name") val authorName: String = "",
    val text: String = "",
    val comment: String = "",
    val body: String = "",
    val time: Long = 0,
    val date: Long = 0,
    @SerialName("created_at") val createdAt: Long = 0,
    val likes: Long = 0,
    val dislikes: Long = 0,
    @SerialName("children_count") val childrenCount: Int = 0,
)

@Serializable
internal data class CommentUserDto(
    val id: Long = 0,
    val name: String = "",
    val nickname: String = "",
    val login: String = "",
    val username: String = "",
    @SerialName("user_name") val userName: String = "",
    val avatars: AvatarDto? = null,
)

@Serializable
internal data class CommentRequestDto(
    val text: String,
    @SerialName("parent_comment") val parentComment: Long? = null,
    @SerialName("reply_to_comment") val replyToComment: Long? = null,
)

@Serializable
internal data class RatingBucketDto(
    val rating: Int = 0,
    val count: Long = 0,
)

@Serializable
internal data class RateRequestDto(
    val rate: Int,
)

@Serializable
internal data class SubscriptionDto(
    @SerialName("anime_id") val animeId: Long = 0,
    val title: String = "",
    val poster: PosterDto? = null,
    val sub: JsonElement? = null,
)

@Serializable
internal data class NotificationDto(
    val id: Long = 0,
    val date: Long = 0,
    @SerialName("title_html") val titleHtml: String = "",
    @SerialName("text_html") val textHtml: String = "",
    @SerialName("click_uri") val clickUri: String = "",
    val type: String = "",
    @SerialName("sub_type") val subType: String = "",
    @SerialName("object_id") val objectId: Long = 0,
    val viewed: Boolean = false,
)

// YummyAnimeApiMapping
internal fun AnimeDto.toAnime(): Anime {
    return Anime(
        id = animeId,
        title = title,
        description = description,
        posterUrl = poster.bestPosterUrl(),
        animeUrl = animeUrl,
        year = year.takeIf { it > 0 },
        rating = rating.ratingValue(),
        userRating = user?.rating?.takeIf { it in 1..10 },
        views = views,
        status = animeStatus?.title.orEmpty(),
        type = type?.name ?: type?.title ?: type?.shortname.orEmpty(),
        genres = genres.mapNotNull { it.title.takeIf(String::isNotBlank) },
        blockedIn = blockedIn.filter { it.isNotBlank() },
        episodeAired = episodes?.aired ?: 0,
        episodeCount = episodes?.count ?: 0,
    )
}

internal fun AnimeDto.toDetails(locale: Locale): AnimeDetails {
    val screenshots = (
        randomScreenshots.mapNotNull { screenshot ->
            screenshot.sizes?.let { sizes ->
                sizes.small.ifBlank { sizes.full }
                    .normalizeUrl()
                    .takeIf(String::isNotBlank)
            }
        } +
            videos.mapNotNull { video ->
                listOf(video.preview, video.poster, video.image, video.screenshot)
                    .firstOrNull { it.isNotBlank() }
                    ?.normalizeUrl()
                    ?.takeIf(String::isNotBlank)
            }
        ).distinct()
    val genreTags = genres.mapNotNull { it.toFilterOption() }

    return AnimeDetails(
        id = animeId,
        title = title,
        otherTitles = otherTitles,
        description = description,
        posterUrl = poster.bestPosterUrl(),
        backdropUrl = null,
        year = year.takeIf { it > 0 },
        rating = rating.ratingValue(),
        userRating = user?.rating?.takeIf { it in 1..10 },
        views = views,
        status = animeStatus?.title.orEmpty(),
        type = type?.name ?: type?.title ?: type?.shortname.orEmpty(),
        minAge = minAge?.title.orEmpty(),
        genreTags = genreTags,
        genres = genreTags.map { it.title },
        episodeSummary = "",
        episodeAired = episodes?.aired ?: 0,
        episodeCount = episodes?.count ?: 0,
        nextEpisodeText = episodes.nextEpisodeText(),
        durationSeconds = duration,
        ratingDetails = rating.ratingDetails(),
        studios = studios.mapNotNull { it.toFilterOption() }.sortedByFilterTitle(locale),
        creators = creators.mapNotNull { it.toFilterOption() }.sortedByFilterTitle(locale),
        original = original,
        commentsCount = commentsCount,
        listsCount = listsCount,
        translations = translates.mapNotNull { it.title.takeIf(String::isNotBlank) },
        relatedAnime = viewingOrder.toRelatedAnime(animeId),
        screenshots = screenshots,
        blockedIn = blockedIn.filter { it.isNotBlank() },
    )
}

internal fun AnimeDto.toDetailsWithVideos(locale: Locale): Pair<AnimeDetails, List<VideoVariant>> {
    return toDetails(locale) to toVideoVariants()
}

internal fun AnimeDto.toVideoVariants(): List<VideoVariant> {
    return videos
        .map { it.toVideoVariant(animeId) }
        .sortedForUi()
}

private fun GenreDto.toFilterOption(): FilterOption? {
    val title = title.takeIf { it.isNotBlank() } ?: return null
    val value = alias.takeIf { it.isNotBlank() }
        ?: id.takeIf { it > 0 }?.toString()
        ?: url.substringAfterLast('/').takeIf { it.isNotBlank() }
        ?: return null
    return FilterOption(title = title, value = value)
}

private fun CatalogLinkDto.toFilterOption(): FilterOption? {
    val title = title.takeIf { it.isNotBlank() } ?: return null
    val value = id.takeIf { it > 0 }?.toString()
        ?: url.substringAfterLast('/').takeIf { it.isNotBlank() }
        ?: return null
    return FilterOption(title = title, value = value)
}

private fun List<ViewingOrderDto>.toRelatedAnime(currentAnimeId: Long): List<RelatedAnime> {
    return sortedWith(
        compareBy<ViewingOrderDto> { it.data?.index ?: Int.MAX_VALUE }
            .thenBy { it.year.takeIf { year -> year > 0 } ?: Int.MAX_VALUE }
            .thenBy { it.animeId },
    )
        .filter { it.animeId > 0 && it.title.isNotBlank() }
        .distinctBy { it.animeId }
        .map { item ->
            RelatedAnime(
                id = item.animeId,
                title = item.title,
                posterUrl = item.poster.bestPosterUrl(),
                year = item.year.takeIf { it > 0 },
                rating = item.rating.ratingValue(),
                type = item.type?.name ?: item.type?.title ?: item.type?.shortname.orEmpty(),
                status = item.animeStatus?.title.orEmpty(),
                relation = item.data?.text.orEmpty(),
                isCurrent = item.animeId == currentAnimeId,
            )
        }
}

internal fun JsonElement?.ratingValue(): Double? {
    if (this == null) return null
    val average = runCatching {
        jsonObject["average"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
    }.getOrNull()
    val direct = runCatching {
        jsonPrimitive.contentOrNull?.toDoubleOrNull()
    }.getOrNull()
    return (average ?: direct)?.takeIf { it > 0.0 }
}

private fun JsonElement?.ratingDetails(): RatingDetails {
    if (this == null) return RatingDetails()
    val root = runCatching { jsonObject }.getOrNull() ?: return RatingDetails(average = ratingValue())
    fun doubleValue(name: String): Double? =
        root[name]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.takeIf { it > 0.0 }
    fun longValue(name: String): Long =
        root[name]?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.takeIf { it > 0L } ?: 0L

    return RatingDetails(
        average = doubleValue("average"),
        counters = longValue("counters"),
        kinopoisk = doubleValue("kp_rating"),
        shikimori = doubleValue("shikimori_rating"),
        myAnimeList = doubleValue("myanimelist_rating"),
        worldArt = doubleValue("worldart_rating"),
        aniDub = doubleValue("anidub_rating"),
    )
}

private fun EpisodesDto?.nextEpisodeText(): String {
    val nextDate = this?.nextDate?.takeIf { it > 0L } ?: return ""
    val deltaSeconds = nextDate - System.currentTimeMillis() / 1000L
    if (deltaSeconds <= 0L) return ""
    val days = deltaSeconds / 86_400L
    val hours = (deltaSeconds % 86_400L) / 3_600L
    return when {
        days > 0 && hours > 0 -> "${days}d ${hours}h"
        days > 0 -> "${days}d"
        hours > 0 -> "${hours}h"
        else -> "less than 1h"
    }
}

private fun VideoDto.toVideoVariant(animeId: Long): VideoVariant {
    return VideoVariant(
        id = videoId,
        animeId = animeId,
        player = data.player.ifBlank { "Player" },
        dubbing = data.dubbing.ifBlank { "Voice" },
        playerId = data.playerId,
        episode = number,
        url = iframeUrl.normalizeUrl(),
        index = index,
        durationSeconds = duration,
        views = views,
        skipSegments = skips.toVideoSkipSegments(),
        previewUrl = listOf(preview, poster, image, screenshot)
            .firstOrNull { it.isNotBlank() }
            ?.normalizeUrl()
            .orEmpty(),
        subscribed = subscribed,
    )
}

private fun VideoSkipsDto?.toVideoSkipSegments(): List<VideoSkipSegment> {
    return listOfNotNull(
        this?.opening.toVideoSkipSegment(VideoSkipKind.Opening),
        this?.ending.toVideoSkipSegment(VideoSkipKind.Ending),
    ).sortedBy { it.startMs }
}

internal fun JsonElement?.toVideoSkipSegment(kind: VideoSkipKind): VideoSkipSegment? {
    val element = this ?: return null
    val startAndEndSeconds = when (element) {
        is JsonObject -> {
            val start = element["time"].positiveOrZeroLong() ?: return null
            val length = element["length"].positiveLong() ?: return null
            start to start + length
        }
        is JsonArray -> {
            val start = element.getOrNull(0).positiveOrZeroLong() ?: return null
            val end = element.getOrNull(1).positiveLong() ?: return null
            start to end
        }
        else -> return null
    }
    val (startSeconds, endSeconds) = startAndEndSeconds
    if (endSeconds <= startSeconds) return null
    return VideoSkipSegment(
        kind = kind,
        startMs = startSeconds * 1_000L,
        endMs = endSeconds * 1_000L,
    )
}

private fun JsonElement?.positiveOrZeroLong(): Long? {
    return this?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.takeIf { it >= 0L }
}

private fun JsonElement?.positiveLong(): Long? {
    return this?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.takeIf { it > 0L }
}

internal fun ProfileDto.toUserProfile(): UserProfile {
    return UserProfile(
        id = id,
        nickname = nickname,
        avatarUrl = listOf(avatars?.full, avatars?.big, avatars?.small)
            .firstOrNull { !it.isNullOrBlank() }
            .normalizeUrl(),
        about = about,
        banned = banned,
        roles = roles,
        unreadNotifications = notifications?.count ?: 0,
        unreadMessages = messages?.unreadCount ?: 0,
    )
}

internal fun UserAnimeMarkDto.toUserAnimeMark(): UserAnimeMark {
    return UserAnimeMark(
        list = UserAnimeListMark.fromId(list),
        isFavorite = isFavorite,
    )
}

internal fun WatchHistoryDto.toPlaybackProgress(): PlaybackProgress? {
    if (animeId <= 0L || date <= 0L) return null
    return PlaybackProgress(
        animeId = animeId,
        videoId = videoId.coerceAtLeast(0L),
        animeTitle = title.trim(),
        posterUrl = poster.bestPosterUrl(),
        groupKey = "",
        episode = episodeTitle.trim(),
        positionMs = endTime.coerceAtLeast(0L) * 1000L,
        durationMs = duration.coerceAtLeast(0L) * 1000L,
        updatedAtMs = date * 1000L,
    )
}

internal fun ScheduleAnimeDto.toScheduleAnime(): ScheduleAnime? {
    if (animeId <= 0L || title.isBlank()) return null
    val episodeInfo = episodes
    return ScheduleAnime(
        anime = Anime(
            id = animeId,
            title = title,
            description = description,
            posterUrl = poster.bestPosterUrl(),
            animeUrl = animeUrl,
            year = null,
            rating = null,
            views = 0,
            status = "",
            type = "",
            genres = emptyList(),
            blockedIn = emptyList(),
            episodeAired = episodeInfo?.aired ?: 0,
            episodeCount = episodeInfo?.count ?: 0,
        ),
        airedEpisodes = episodeInfo?.aired ?: 0,
        totalEpisodes = episodeInfo?.count ?: 0,
        previousEpisodeAtSeconds = episodeInfo?.previousDate ?: 0L,
        nextEpisodeAtSeconds = episodeInfo?.nextDate ?: 0L,
    )
}

internal fun CollectionDto.toAnimeCollectionSummary(): AnimeCollectionSummary {
    val animeItems = animes.map { it.toAnime() }
    return AnimeCollectionSummary(
        id = id,
        title = title,
        description = description.cleanApiText(),
        ownerName = owner?.nickname?.takeIf(String::isNotBlank)
            ?: owner?.login.orEmpty(),
        posterUrl = poster.bestPosterUrl().ifBlank { animeItems.firstOrNull()?.posterUrl.orEmpty() },
        animeCount = count.takeIf { it > 0 } ?: animeItems.size,
        views = views,
        likes = likes?.likes ?: 0L,
        dislikes = likes?.dislikes ?: 0L,
        createdAtSeconds = createdAt.takeIf { it > 0L } ?: dateCreate,
        animes = animeItems,
    )
}

internal fun CommentDto.toAnimeComment(): AnimeComment {
    val commentUser = user
    val avatar = commentUser?.avatars ?: avatars
    return AnimeComment(
        id = id,
        userId = userId.takeIf { it > 0L } ?: commentUser?.id ?: 0L,
        userName = listOf(
            commentUser?.nickname,
            commentUser?.name,
            commentUser?.userName,
            commentUser?.login,
            commentUser?.username,
            name,
            userName,
            nickname,
            login,
            username,
            userNickname,
            userLogin,
            authorName,
            author,
        ).firstNonBlank().cleanApiText(),
        avatarUrl = avatar?.full?.takeIf(String::isNotBlank)
            ?: avatar?.big?.takeIf(String::isNotBlank)
            ?: avatar?.small.orEmpty(),
        text = listOf(text, comment, body)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .cleanApiText(),
        createdAtSeconds = createdAt.takeIf { it > 0L } ?: time.takeIf { it > 0L } ?: date,
        likes = likes,
        dislikes = dislikes,
        childrenCount = childrenCount,
    )
}

internal fun JsonElement.toAnimeCommentOrNull(): AnimeComment? {
    val root = runCatching { jsonObject }.getOrNull() ?: return null
    val commentObject = root["comment"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: root
    val userObject = commentObject["user"]?.let { runCatching { it.jsonObject }.getOrNull() }
    val avatarObject = userObject?.get("avatars")?.let { runCatching { it.jsonObject }.getOrNull() }
        ?: commentObject["avatars"]?.let { runCatching { it.jsonObject }.getOrNull() }
    return AnimeComment(
        id = commentObject.longValue("id"),
        userId = commentObject.longValue("user_id").takeIf { it > 0L } ?: userObject.longValue("id"),
        userName = listOf(
            userObject.stringValue("nickname"),
            userObject.stringValue("name"),
            userObject.stringValue("user_name"),
            userObject.stringValue("login"),
            userObject.stringValue("username"),
            commentObject.stringValue("name"),
            commentObject.stringValue("user_name"),
            commentObject.stringValue("nickname"),
            commentObject.stringValue("login"),
            commentObject.stringValue("username"),
            commentObject.stringValue("user_nickname"),
            commentObject.stringValue("user_login"),
            commentObject.stringValue("author_name"),
            commentObject.stringValue("author"),
        ).firstNonBlank().cleanApiText(),
        avatarUrl = listOf(
            avatarObject.stringValue("full"),
            avatarObject.stringValue("big"),
            avatarObject.stringValue("small"),
        ).firstOrNull { it.isNotBlank() }.normalizeUrl(),
        text = listOf(
            commentObject.stringValue("text"),
            commentObject.stringValue("comment"),
            commentObject.stringValue("body"),
        ).firstOrNull { it.isNotBlank() }.orEmpty().cleanApiText(),
        createdAtSeconds = commentObject.longValue("created_at").takeIf { it > 0L }
            ?: commentObject.longValue("time").takeIf { it > 0L }
            ?: commentObject.longValue("date"),
        likes = commentObject.longValue("likes"),
        dislikes = commentObject.longValue("dislikes"),
        childrenCount = commentObject.intValue("children_count"),
    ).takeIf { it.id > 0L || it.text.isNotBlank() }
}

internal fun RatingBucketDto.toAnimeRatingBucket(): AnimeRatingBucket? {
    return AnimeRatingBucket(
        rating = rating.takeIf { it in 1..10 } ?: return null,
        count = count.coerceAtLeast(0L),
    )
}

internal fun SubscriptionDto.toVideoSubscription(): VideoSubscription? {
    val subscription = sub.toSubscriptionData() ?: return null
    return VideoSubscription(
        animeId = animeId.takeIf { it > 0L } ?: return null,
        title = title,
        posterUrl = poster.bestPosterUrl(),
        player = subscription.player,
        dubbing = subscription.dubbing,
        playerId = subscription.playerId,
        videoId = subscription.videoId,
    )
}

private data class SubscriptionData(
    val player: String,
    val playerId: Long,
    val dubbing: String,
    val videoId: Long,
)

private fun JsonElement?.toSubscriptionData(): SubscriptionData? {
    val element = this ?: return null
    return when (element) {
        is JsonObject -> element.toSubscriptionData()
        is JsonArray -> element.firstNotNullOfOrNull { it.toSubscriptionData() }
        else -> null
    }
}

private fun JsonObject.toSubscriptionData(): SubscriptionData? {
    val player = firstTextValue(
        "player",
        "player_title",
        "player_name",
        "playerName",
    )
    val dubbing = firstTextValue(
        "dubbing",
        "dubbing_title",
        "dubbing_name",
        "voice",
        "voice_title",
        "translation",
        "translation_title",
        "translation_name",
        "name",
        "title",
    )
    val playerId = firstLongValue(
        "player_id",
        "playerId",
        "player_video_id",
        "playerVideoId",
    )
    val videoId = firstLongValue(
        "video_id",
        "videoId",
        "video",
    )

    if (player.isBlank() && dubbing.isBlank() && playerId <= 0L && videoId <= 0L) return null
    return SubscriptionData(
        player = player,
        playerId = playerId,
        dubbing = dubbing,
        videoId = videoId,
    )
}

internal fun NotificationDto.toSiteNotification(): SiteNotification? {
    val notificationId = id.takeIf { it > 0L } ?: return null
    return SiteNotification(
        id = notificationId,
        title = titleHtml.cleanApiText().ifBlank { "New episode" },
        text = textHtml.cleanApiText(),
        clickUrl = clickUri.normalizeUrl(),
        type = type,
        subType = subType,
        objectId = objectId,
        dateSeconds = date,
        viewed = viewed,
    )
}

internal fun CatalogDto.toFilterCatalog(locale: Locale): FilterCatalog {
    return FilterCatalog(
        genres = genres.genres
            .filter { it.href.isNotBlank() && it.title.isNotBlank() }
            .map { FilterOption(title = it.title, value = it.href) }
            .sortedByFilterTitle(locale),
        types = types
            .mapNotNull { entry ->
                val alias = entry.type.alias.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val title = entry.type.name.takeIf { it.isNotBlank() }
                    ?: entry.type.shortname.takeIf { it.isNotBlank() }
                    ?: alias
                FilterOption(title = title, value = alias)
            }
            .sortedByFilterTitle(locale),
        studios = (studios + data.flatMap { it.studios })
            .mapNotNull { it.toFilterOption() }
            .distinctBy { it.value }
            .sortedByFilterTitle(locale),
        creators = (creators + directors + data.flatMap { it.creators })
            .mapNotNull { it.toFilterOption() }
            .distinctBy { it.value }
            .sortedByFilterTitle(locale),
    )
}

private fun List<FilterOption>.sortedByFilterTitle(locale: Locale): List<FilterOption> {
    val collator = Collator.getInstance(locale).apply {
        strength = Collator.PRIMARY
    }
    return sortedWith { first, second ->
        val titleCompare = collator.compare(first.title, second.title)
        if (titleCompare != 0) titleCompare else first.value.compareTo(second.value)
    }
}

private fun PosterDto?.bestPosterUrl(): String {
    if (this == null) return ""
    return listOf(fullsize, mega, huge, big, medium, small)
        .firstOrNull { it.isNotBlank() }
        .normalizeUrl()
}

private fun String?.normalizeUrl(): String {
    val value = this?.trim().orEmpty()
    return when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "${DEFAULT_SITE_BASE_URL.trimEnd('/')}$value"
        else -> value
    }
}

private fun String.cleanApiText(): String {
    return replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<[^>]+>"""), "")
        .replace(Regex("""\[(?:/?)[^\]]+]"""), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString("\n")
        .trim()
}

private fun Iterable<String?>.firstNonBlank(): String {
    return firstOrNull { !it.isNullOrBlank() }.orEmpty()
}

private fun JsonObject?.stringValue(name: String): String {
    return this?.get(name)?.jsonPrimitive?.contentOrNull.orEmpty()
}

private fun JsonObject?.longValue(name: String): Long {
    return stringValue(name).toLongOrNull() ?: 0L
}

private fun JsonObject.firstTextValue(vararg names: String): String {
    return names
        .asSequence()
        .map { name -> get(name).textValue() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}

private fun JsonObject.firstLongValue(vararg names: String): Long {
    return names
        .asSequence()
        .mapNotNull { name -> get(name).longTextValue() }
        .firstOrNull { it > 0L }
        ?: 0L
}

private fun JsonElement?.textValue(): String {
    val element = this ?: return ""
    return when (element) {
        is JsonObject -> element.firstTextValue("title", "name", "label", "value", "text", "slug", "key")
        is JsonArray -> element.asSequence()
            .map { it.textValue() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        else -> runCatching { element.jsonPrimitive.contentOrNull.orEmpty() }.getOrDefault("")
    }.trim()
}

private fun JsonElement?.longTextValue(): Long? {
    val element = this ?: return null
    return when (element) {
        is JsonObject -> element.firstLongValue("id", "value", "video_id", "player_id").takeIf { it > 0L }
        is JsonArray -> element.asSequence()
            .mapNotNull { it.longTextValue() }
            .firstOrNull { it > 0L }
        else -> runCatching { element.jsonPrimitive.contentOrNull?.toLongOrNull() }.getOrNull()
    }
}

private fun JsonObject?.intValue(name: String): Int {
    return stringValue(name).toIntOrNull() ?: 0
}

internal fun Long.toWholeSeconds(): Int {
    return (coerceAtLeast(0L) / 1000L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}

private fun List<VideoVariant>.sortedForUi(): List<VideoVariant> {
    return sortedWith(
        compareBy<VideoVariant> { it.groupTitle }
            .thenBy { it.episodeOrderValue() ?: Double.MAX_VALUE }
            .thenBy { it.index.takeIf { index -> index > 0 } ?: Int.MAX_VALUE }
            .thenBy { it.id },
    )
}

internal fun BrowseFilters.toApiParams(): List<Pair<String, String>> {
    return buildList {
        add("sort" to sort.apiValue)
        add("sort_forward" to sort.forward.toString())
        fromYear?.takeIf { it in 1900..2100 }?.let { add("from_year" to it.toString()) }
        toYear?.takeIf { it in 1900..2100 }?.let { add("to_year" to it.toString()) }
        minRating?.takeIf { it in 0.0..10.0 }?.let { add("min_rating" to it.toString()) }
        maxRating?.takeIf { it in 0.0..10.0 }?.let { add("max_rating" to it.toString()) }
        episodeFrom?.takeIf { it in 0..10000 }?.let { add("ep_from" to it.toString()) }
        episodeTo?.takeIf { it in 0..10000 }?.let { add("ep_to" to it.toString()) }
        statuses.forEach { add("status" to it) }
        genres.forEach { add("genres" to it) }
        excludedGenres.forEach { add("exclude_genres" to it) }
        seasons.forEach { add("season" to it) }
        types.forEach { add("types" to it) }
        studios.forEach { add("studio_ids" to it) }
        creators.forEach { add("director_ids" to it) }
        translates.forEach { add("translates" to it) }
        ageRatings.forEach { add("min_age" to it) }
    }
}

internal fun BrowseFilters.toAnimeQueryParams(
    query: String?,
    limit: Int,
    offset: Int,
    ids: Set<Long>,
): List<Pair<String, String>> {
    return buildList {
        addAll(toApiParams())
        if (query != null) add("q" to query)
        add("limit" to limit.toString())
        add("offset" to offset.coerceAtLeast(0).toString())
        ids.forEach { add("ids" to it.toString()) }
    }
}

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
        return loadAnimeWithVideos(animeId, token).toDetailsWithVideos(transport.locale)
    }

    suspend fun getVideos(animeId: Long, token: String?): List<VideoVariant> {
        return loadAnimeWithVideos(animeId, token)
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

    private suspend fun loadAnimeWithVideos(animeId: Long, token: String?): AnimeDto {
        return transport.get(
            path = "/anime/$animeId",
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
        return transport.putEmptySuccess(
            path = "/video/$videoId/subscribe",
            authToken = token,
        )
    }

    suspend fun unsubscribeVideo(videoId: Long, token: String): Boolean {
        return transport.deleteSuccess(
            path = "/video/$videoId/subscribe",
            authToken = token,
        )
    }

    suspend fun getVideoSubscriptions(userId: Long, token: String): List<VideoSubscription> {
        return transport.get<List<SubscriptionDto>>(
            path = "/users/$userId/lists/subs",
            authToken = token,
        ).mapNotNull { it.toVideoSubscription() }
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
