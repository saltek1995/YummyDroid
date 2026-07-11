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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class YummyAnimeApi(
    private val client: OkHttpClient = defaultClient,
) {
    private val json = Json {
        coerceInputValues = true
        explicitNulls = false
        ignoreUnknownKeys = true
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
        return get<CatalogDto>(path = "/anime/catalog").toFilterCatalog()
    }

    suspend fun getAnime(animeId: Long, token: String? = null): AnimeDetails {
        return get<AnimeDto>(path = "/anime/$animeId", authToken = token).toDetails()
    }

    suspend fun getAnimeWithVideos(animeId: Long, token: String? = null): Pair<AnimeDetails, List<VideoVariant>> {
        val anime = get<AnimeDto>(
            path = "/anime/$animeId",
            params = listOf("need_videos" to "true"),
            authToken = token,
        )

        return anime.toDetails() to anime.videos
            .map { it.toVideoVariant(anime.animeId) }
            .sortedForUi()
    }

    suspend fun getVideos(animeId: Long): List<VideoVariant> {
        return get<List<VideoDto>>(path = "/anime/$animeId/videos")
            .map { it.toVideoVariant(animeId) }
            .sortedForUi()
    }

    suspend fun getUserListAnime(userId: Long, listId: Int, token: String): List<Anime> {
        return get<List<AnimeDto>>(
            path = "/users/$userId/lists/$listId",
            authToken = token,
        ).map { it.toAnime() }
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
            throw IOException("Не удалось войти в аккаунт")
        }
        return response.token
    }

    suspend fun refreshToken(token: String): String {
        val response: TokenResponseDto = get(
            path = "/profile/token",
            authToken = token,
        )
        return response.token.takeIf { it.isNotBlank() }
            ?: throw IOException("Не удалось обновить токен")
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

    private suspend inline fun <reified T> get(
        path: String,
        params: List<Pair<String, String>> = emptyList(),
        authToken: String? = null,
    ): T = withContext(Dispatchers.IO) {
        val urlBuilder = "$BASE_URL$path".toHttpUrl().newBuilder()
        params.forEach { (key, value) ->
            if (value.isNotBlank()) urlBuilder.addQueryParameter(key, value)
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
        val requestBody = json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE)
        val request = baseRequest("$BASE_URL$path", authToken)
            .post(requestBody)
            .build()

        execute(request)
    }

    private suspend inline fun <reified T, reified B> put(
        path: String,
        body: B,
        authToken: String? = null,
    ): T = withContext(Dispatchers.IO) {
        val requestBody = json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE)
        val request = baseRequest("$BASE_URL$path", authToken)
            .put(requestBody)
            .build()

        execute(request)
    }

    private suspend inline fun <reified T> delete(
        path: String,
        authToken: String? = null,
    ): T = withContext(Dispatchers.IO) {
        val request = baseRequest("$BASE_URL$path", authToken)
            .delete()
            .build()

        execute(request)
    }

    private fun baseRequest(url: String, authToken: String?): Request.Builder {
        return Request.Builder()
            .url(url)
            .header("Accept", "application/json, image/avif, image/webp")
            .header("Lang", "ru")
            .header("Vary", "json")
            .header("X-Application", APPLICATION_ID)
            .header("User-Agent", USER_AGENT)
            .apply {
                if (!authToken.isNullOrBlank()) {
                    header("Authorization", "Yummy $authToken")
                }
            }
    }

    private inline fun <reified T> execute(request: Request): T {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = body.apiErrorMessage() ?: "YummyAnime API вернул HTTP ${response.code}"
                if (response.code == 420) {
                    throw CaptchaRequiredException(message)
                }
                throw IOException(message)
            }

            return json.decodeFromString<ApiEnvelope<T>>(body).response
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
        const val USER_AGENT = "YummyDroid Android TV"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

class CaptchaRequiredException(message: String) : IOException(message)

@Serializable
private data class ApiEnvelope<T>(
    val response: T,
)

@Serializable
private data class AnimeDto(
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
private data class UserListAnimeDto(
    @SerialName("anime_id") val animeId: Long = 0,
    val user: AnimeUserDto? = null,
)

@Serializable
private data class PosterDto(
    val fullsize: String = "",
    val big: String = "",
    val medium: String = "",
    val small: String = "",
)

@Serializable
private data class GenreDto(
    val title: String = "",
    val id: Long = 0,
    val alias: String = "",
    val url: String = "",
)

@Serializable
private data class CatalogLinkDto(
    val title: String = "",
    val id: Long = 0,
    val url: String = "",
)

@Serializable
private data class NamedDto(
    val title: String? = null,
    val name: String? = null,
    val shortname: String? = null,
)

@Serializable
private data class AgeDto(
    val title: String = "",
    @SerialName("title_long") val titleLong: String = "",
)

@Serializable
private data class TranslateDto(
    val title: String = "",
)

@Serializable
private data class EpisodesDto(
    val count: Int = 0,
    val aired: Int = 0,
    @SerialName("next_date") val nextDate: Long = 0,
)

@Serializable
private data class ScreenshotDto(
    val sizes: ScreenshotSizesDto? = null,
)

@Serializable
private data class ScreenshotSizesDto(
    val full: String = "",
    val small: String = "",
)

@Serializable
private data class VideoDto(
    @SerialName("video_id") val videoId: Long = 0,
    val data: VideoDataDto = VideoDataDto(),
    val number: String = "",
    @SerialName("iframe_url") val iframeUrl: String = "",
    val index: Int = 0,
    val views: Long = 0,
    val duration: Int? = null,
    val skips: VideoSkipsDto? = null,
)

@Serializable
private data class VideoDataDto(
    val player: String = "",
    val dubbing: String = "",
)

@Serializable
private data class VideoSkipsDto(
    val opening: JsonElement? = null,
    val ending: JsonElement? = null,
)

@Serializable
private data class ViewingOrderDto(
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
private data class ViewingOrderDataDto(
    val index: Int = 0,
    val text: String = "",
)

@Serializable
private data class AnimeUserDto(
    val list: AnimeUserListWrapperDto? = null,
)

@Serializable
private data class AnimeUserListWrapperDto(
    @SerialName("is_fav") val isFavorite: Boolean = false,
    val list: AnimeUserListDto? = null,
)

@Serializable
private data class AnimeUserListDto(
    val id: Int? = null,
)

@Serializable
private data class CatalogDto(
    val genres: CatalogGenresDto = CatalogGenresDto(),
    val types: List<CatalogTypeEntryDto> = emptyList(),
)

@Serializable
private data class CatalogGenresDto(
    val genres: List<CatalogGenreDto> = emptyList(),
)

@Serializable
private data class CatalogGenreDto(
    val title: String = "",
    val href: String = "",
    val value: Long = 0,
)

@Serializable
private data class CatalogTypeEntryDto(
    val type: CatalogTypeDto = CatalogTypeDto(),
)

@Serializable
private data class CatalogTypeDto(
    val name: String = "",
    val shortname: String = "",
    val alias: String = "",
)

@Serializable
private data class LoginRequestDto(
    val login: String,
    val password: String,
    @SerialName("need_json") val needJson: Boolean,
    @SerialName("recaptcha_response") val recaptchaResponse: String? = null,
)

@Serializable
private data class LoginResponseDto(
    val success: Boolean = false,
    val token: String = "",
)

@Serializable
private data class TokenResponseDto(
    val token: String = "",
)

@Serializable
private data class ProfileDto(
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
private data class AvatarDto(
    val full: String = "",
    val big: String = "",
    val small: String = "",
)

@Serializable
private data class ProfileNotificationsDto(
    val count: Int = 0,
)

@Serializable
private data class ProfileMessagesDto(
    @SerialName("unread_count") val unreadCount: Int = 0,
)

@Serializable
private data class UserAnimeMarkDto(
    val list: Int? = null,
    @SerialName("is_favorite") val isFavorite: Boolean = false,
)

@Serializable
private data class SetAnimeListRequestDto(
    val date: Long,
    val list: Int,
)

@Serializable
private data class FavoriteRequestDto(
    val date: Long,
)

@Serializable
private data class SetVideoWatchRequestDto(
    val time: Int,
    val duration: Int,
    val date: Long,
    val times: List<Int> = emptyList(),
)

@Serializable
private data class WatchHistoryDto(
    @SerialName("anime_id") val animeId: Long = 0,
    @SerialName("video_id") val videoId: Long = 0,
    @SerialName("ep_title") val episodeTitle: String = "",
    @SerialName("end_time") val endTime: Long = 0,
    val duration: Long = 0,
    val date: Long = 0,
)

private fun AnimeDto.toAnime(): Anime {
    return Anime(
        id = animeId,
        title = title,
        description = description,
        posterUrl = poster.bestPosterUrl(),
        animeUrl = animeUrl,
        year = year.takeIf { it > 0 },
        rating = rating.ratingValue(),
        views = views,
        status = animeStatus?.title.orEmpty(),
        type = type?.name ?: type?.title ?: type?.shortname.orEmpty(),
        genres = genres.mapNotNull { it.title.takeIf(String::isNotBlank) },
        blockedIn = blockedIn.filter { it.isNotBlank() },
    )
}

private fun AnimeDto.toDetails(): AnimeDetails {
    val screenshot = randomScreenshots.firstOrNull()?.sizes?.full?.normalizeUrl()
    val genreTags = genres.mapNotNull { it.toFilterOption() }

    return AnimeDetails(
        id = animeId,
        title = title,
        otherTitles = otherTitles,
        description = description,
        posterUrl = poster.bestPosterUrl(),
        backdropUrl = screenshot,
        year = year.takeIf { it > 0 },
        rating = rating.ratingValue(),
        views = views,
        status = animeStatus?.title.orEmpty(),
        type = type?.name ?: type?.title ?: type?.shortname.orEmpty(),
        minAge = minAge?.title.orEmpty(),
        genreTags = genreTags,
        genres = genreTags.map { it.title },
        episodeSummary = episodes.toEpisodeSummary(),
        episodeAired = episodes?.aired ?: 0,
        episodeCount = episodes?.count ?: 0,
        nextEpisodeText = episodes.nextEpisodeText(),
        durationSeconds = duration,
        ratingDetails = rating.ratingDetails(),
        studios = studios.mapNotNull { it.toFilterOption() }.sortedByFilterTitle(),
        creators = creators.mapNotNull { it.toFilterOption() }.sortedByFilterTitle(),
        original = original,
        commentsCount = commentsCount,
        listsCount = listsCount,
        translations = translates.mapNotNull { it.title.takeIf(String::isNotBlank) },
        relatedAnime = viewingOrder.toRelatedAnime(animeId),
        screenshots = randomScreenshots
            .mapNotNull { it.sizes?.full?.normalizeUrl()?.takeIf(String::isNotBlank) }
            .distinct(),
        blockedIn = blockedIn.filter { it.isNotBlank() },
    )
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
        days > 0 && hours > 0 -> "${days}д. ${hours}ч."
        days > 0 -> "${days}д."
        hours > 0 -> "${hours}ч."
        else -> "меньше часа"
    }
}

private fun VideoDto.toVideoVariant(animeId: Long): VideoVariant {
    return VideoVariant(
        id = videoId,
        animeId = animeId,
        player = data.player.ifBlank { "Плеер" },
        dubbing = data.dubbing.ifBlank { "Озвучка" },
        episode = number,
        url = iframeUrl.normalizeUrl(),
        index = index,
        durationSeconds = duration,
        views = views,
        skipSegments = skips.toVideoSkipSegments(),
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

private fun ProfileDto.toUserProfile(): UserProfile {
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

private fun UserAnimeMarkDto.toUserAnimeMark(): UserAnimeMark {
    return UserAnimeMark(
        list = UserAnimeListMark.fromId(list),
        isFavorite = isFavorite,
    )
}

private fun WatchHistoryDto.toPlaybackProgress(): PlaybackProgress? {
    if (animeId <= 0L || endTime <= 0L || date <= 0L) return null
    return PlaybackProgress(
        animeId = animeId,
        videoId = videoId.coerceAtLeast(0L),
        groupKey = "",
        episode = episodeTitle.trim(),
        positionMs = endTime * 1000L,
        durationMs = duration.coerceAtLeast(0L) * 1000L,
        updatedAtMs = date * 1000L,
    )
}

private fun CatalogDto.toFilterCatalog(): FilterCatalog {
    return FilterCatalog(
        genres = genres.genres
            .filter { it.href.isNotBlank() && it.title.isNotBlank() }
            .map { FilterOption(title = it.title, value = it.href) }
            .sortedByFilterTitle(),
        types = types
            .mapNotNull { entry ->
                val alias = entry.type.alias.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val title = entry.type.name.takeIf { it.isNotBlank() }
                    ?: entry.type.shortname.takeIf { it.isNotBlank() }
                    ?: alias
                FilterOption(title = title, value = alias)
            }
            .sortedByFilterTitle(),
    )
}

private fun List<FilterOption>.sortedByFilterTitle(): List<FilterOption> {
    val collator = Collator.getInstance(Locale.forLanguageTag("ru-RU")).apply {
        strength = Collator.PRIMARY
    }
    return sortedWith { first, second ->
        val titleCompare = collator.compare(first.title, second.title)
        if (titleCompare != 0) titleCompare else first.value.compareTo(second.value)
    }
}

private fun PosterDto?.bestPosterUrl(): String {
    if (this == null) return ""
    return listOf(fullsize, big, medium, small)
        .firstOrNull { it.isNotBlank() }
        .normalizeUrl()
}

private fun EpisodesDto?.toEpisodeSummary(): String {
    if (this == null) return ""
    return when {
        aired > 0 && count > 0 -> "Вышло $aired из $count"
        aired > 0 -> "Вышло $aired"
        count > 0 -> "$count серий"
        else -> ""
    }
}

private fun String?.normalizeUrl(): String {
    val value = this?.trim().orEmpty()
    return when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "https://old.yummyani.me$value"
        else -> value
    }
}

private fun Long.toWholeSeconds(): Int {
    return (coerceAtLeast(0L) / 1000L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
}

private fun List<VideoVariant>.sortedForUi(): List<VideoVariant> {
    return sortedWith(
        compareBy<VideoVariant> { it.groupTitle }
            .thenBy { it.index }
            .thenBy { it.episode.toDoubleOrNull() ?: 0.0 },
    )
}

private fun BrowseFilters.toApiParams(): List<Pair<String, String>> {
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

