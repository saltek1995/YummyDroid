package me.yummydroid.app.data

import kotlinx.serialization.Serializable

@Serializable
data class Anime(
    val id: Long,
    val title: String,
    val description: String,
    val posterUrl: String,
    val animeUrl: String,
    val year: Int?,
    val rating: Double?,
    val views: Long,
    val status: String,
    val type: String,
    val genres: List<String>,
    val blockedIn: List<String>,
) {
    val meta: String
        get() = listOfNotNull(
            year?.takeIf { it > 0 }?.toString(),
            type.takeIf { it.isNotBlank() },
            status.takeIf { it.isNotBlank() },
        ).joinToString(" • ")
}

@Serializable
data class AnimeDetails(
    val id: Long,
    val title: String,
    val otherTitles: List<String>,
    val description: String,
    val posterUrl: String,
    val backdropUrl: String?,
    val year: Int?,
    val rating: Double?,
    val views: Long,
    val status: String,
    val type: String,
    val minAge: String,
    val genreTags: List<FilterOption>,
    val genres: List<String>,
    val episodeSummary: String,
    val episodeAired: Int,
    val episodeCount: Int,
    val nextEpisodeText: String,
    val durationSeconds: Int,
    val ratingDetails: RatingDetails,
    val studios: List<FilterOption>,
    val creators: List<FilterOption>,
    val original: String,
    val commentsCount: Long,
    val listsCount: Long,
    val translations: List<String>,
    val relatedAnime: List<RelatedAnime>,
    val screenshots: List<String>,
    val blockedIn: List<String>,
) {
    val meta: String
        get() = listOfNotNull(
            year?.takeIf { it > 0 }?.toString(),
            type.takeIf { it.isNotBlank() },
            status.takeIf { it.isNotBlank() },
            minAge.takeIf { it.isNotBlank() },
        ).joinToString(" • ")
}

@Serializable
data class RelatedAnime(
    val id: Long,
    val title: String,
    val posterUrl: String,
    val year: Int?,
    val rating: Double?,
    val type: String,
    val status: String,
    val relation: String,
    val isCurrent: Boolean,
) {
    val meta: String
        get() = listOfNotNull(
            relation.takeIf { it.isNotBlank() },
            year?.takeIf { it > 0 }?.toString(),
            type.takeIf { it.isNotBlank() },
            status.takeIf { it.isNotBlank() },
        ).joinToString(" • ")
}

data class ScheduleAnime(
    val anime: Anime,
    val airedEpisodes: Int,
    val totalEpisodes: Int,
    val previousEpisodeAtSeconds: Long,
    val nextEpisodeAtSeconds: Long,
)

data class AnimeCollectionSummary(
    val id: Long,
    val title: String,
    val description: String,
    val ownerName: String,
    val posterUrl: String,
    val animeCount: Int,
    val views: Long,
    val likes: Long,
    val dislikes: Long,
    val createdAtSeconds: Long,
    val animes: List<Anime> = emptyList(),
)

data class AnimeComment(
    val id: Long,
    val userId: Long,
    val userName: String,
    val avatarUrl: String,
    val text: String,
    val createdAtSeconds: Long,
    val likes: Long,
    val dislikes: Long,
    val childrenCount: Int,
)

data class AnimeTrailer(
    val id: Long,
    val title: String,
    val player: String,
    val dubbing: String,
    val url: String,
)

data class AnimeRatingBucket(
    val rating: Int,
    val count: Long,
)

data class AnimeRatingSummary(
    val buckets: List<AnimeRatingBucket> = emptyList(),
    val userRating: Int? = null,
) {
    val votes: Long
        get() = buckets.sumOf { it.count }

    val average: Double?
        get() {
            val total = votes.takeIf { it > 0 } ?: return null
            return buckets.sumOf { it.rating.toDouble() * it.count.toDouble() } / total.toDouble()
        }
}

data class VideoSubscription(
    val animeId: Long,
    val title: String,
    val posterUrl: String,
    val player: String,
    val dubbing: String,
)

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

@Serializable
data class VideoVariant(
    val id: Long,
    val animeId: Long,
    val player: String,
    val dubbing: String,
    val episode: String,
    val url: String,
    val index: Int,
    val durationSeconds: Int?,
    val views: Long,
    val skipSegments: List<VideoSkipSegment> = emptyList(),
    val previewUrl: String = "",
    val localPlaybackUrl: String = "",
    val localMimeType: String? = null,
    val localBytes: Long = 0L,
) {
    val groupKey: String = "$player|$dubbing"
    val groupTitle: String = listOf(player.cleanLabel("Плеер"), dubbing.cleanLabel("Озвучка"))
        .filter { it.isNotBlank() }
        .joinToString(" • ")

    val episodeTitle: String
        get() = if (episode.isBlank()) "Эпизод" else "Серия $episode"
    val isOfflineAvailable: Boolean
        get() = localPlaybackUrl.isNotBlank()
}

@Serializable
enum class VideoSkipKind(
    val title: String,
) {
    Opening("опенинг"),
    Ending("эндинг"),
}

@Serializable
data class VideoSkipSegment(
    val kind: VideoSkipKind,
    val startMs: Long,
    val endMs: Long,
) {
    val key: String = "${kind.name}:$startMs:$endMs"

    fun isActive(positionMs: Long): Boolean {
        return startMs >= 0L && endMs > startMs && positionMs in startMs until endMs
    }
}

data class ResolvedVideoStream(
    val url: String,
    val mimeType: String?,
    val headers: Map<String, String>,
    val maxVideoHeight: Int? = null,
)

data class ResolvedPlayback(
    val video: VideoVariant,
    val stream: ResolvedVideoStream,
)

@Serializable
data class RatingDetails(
    val average: Double? = null,
    val counters: Long = 0,
    val kinopoisk: Double? = null,
    val shikimori: Double? = null,
    val myAnimeList: Double? = null,
    val worldArt: Double? = null,
    val aniDub: Double? = null,
)

data class UserProfile(
    val id: Long,
    val nickname: String,
    val avatarUrl: String,
    val about: String = "",
    val banned: Boolean = false,
    val roles: List<String> = emptyList(),
    val unreadNotifications: Int = 0,
    val unreadMessages: Int = 0,
)

enum class UserAnimeListMark(
    val id: Int,
    val title: String,
) {
    Watching(0, "Смотрю"),
    Planned(1, "В планах"),
    Watched(2, "Просмотрено"),
    Dropped(3, "Брошено"),
    Postponed(5, "Отложено");

    companion object {
        fun fromId(id: Int?): UserAnimeListMark? = entries.firstOrNull { it.id == id }

        val displayOrder: List<UserAnimeListMark> = listOf(
            Watching,
            Planned,
            Watched,
            Postponed,
            Dropped,
        )
    }
}

data class UserAnimeMark(
    val list: UserAnimeListMark? = null,
    val isFavorite: Boolean = false,
)

private fun String.cleanLabel(prefix: String): String {
    return trim().removePrefix(prefix).trim()
}

