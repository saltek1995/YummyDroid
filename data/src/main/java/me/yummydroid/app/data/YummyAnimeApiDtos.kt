package me.yummydroid.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
