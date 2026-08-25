package me.yummydroid.app.data

import java.util.Locale
import kotlinx.serialization.Serializable

// AnimeCollectionSummary
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

// AnimeComment
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

// AnimeModels
@Serializable
data class Anime(
    val id: Long,
    val title: String,
    val description: String,
    val posterUrl: String,
    val animeUrl: String,
    val year: Int?,
    val rating: Double?,
    val userRating: Int? = null,
    val views: Long,
    val status: String,
    val type: String,
    val genres: List<String>,
    val blockedIn: List<String>,
    val episodeAired: Int = 0,
    val episodeCount: Int = 0,
) {
    val meta: String
        get() = animeMetadata(year?.takeIf { it > 0 }?.toString(), type, status)
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
    val userRating: Int? = null,
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
        get() = animeMetadata(year?.takeIf { it > 0 }?.toString(), type, status, minAge)
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
        get() = animeMetadata(relation, year?.takeIf { it > 0 }?.toString(), type, status)
}

@Serializable
data class ScheduleAnime(
    val anime: Anime,
    val airedEpisodes: Int,
    val totalEpisodes: Int,
    val previousEpisodeAtSeconds: Long,
    val nextEpisodeAtSeconds: Long,
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

private fun animeMetadata(vararg values: String?): String {
    return values.filterNotNull().filter(String::isNotBlank).joinToString(" \u2022 ")
}

// AnimeRatingSummary
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

// AnimeSort
enum class AnimeSort(
    val title: String,
    val apiValue: String,
    val forward: Boolean,
) {
    Rating("Rating", "rating", false),
    RatingCounters("Votes", "rating_counters", false),
    Views("Views", "views", false),
    Year("New", "year", false),
    Top("Top", "top", false),
    Title("A-Z", "title", true),
    Id("Recently added", "id", false),
    Random("Random", "random", true),
}

// AnimeStatus
private val ongoingStatusTokens = listOf(
    "\u043e\u043d\u0433\u043e",
    "ongoing",
    "\u0430\u043d\u043e\u043d\u0441",
    "\u043d\u0435 \u0432\u044b\u0448",
)

private val releasedStatusTokens = listOf(
    "\u0432\u044b\u0448\u0435\u043b",
    "\u0432\u044b\u0448\u043b\u043e",
    "\u0437\u0430\u0432\u0435\u0440\u0448",
    "released",
    "completed",
    "complete",
    "finished",
)

fun AnimeDetails.isFullyReleased(): Boolean {
    val normalizedStatus = status
        .lowercase(Locale.ROOT)
        .replace('\u0451', '\u0435')

    if (ongoingStatusTokens.any(normalizedStatus::contains)) return false
    return releasedStatusTokens.any(normalizedStatus::contains)
}

fun AnimeDetails.canShowVideoSubscriptions(): Boolean = id > 0L

// AnimeSummaryMapping
fun AnimeDetails.toAnimeSummary(): Anime {
    return Anime(
        id = id,
        title = title,
        description = description,
        posterUrl = posterUrl,
        animeUrl = "",
        year = year,
        rating = rating,
        userRating = userRating,
        views = views,
        status = status,
        type = type,
        genres = genres,
        blockedIn = blockedIn,
        episodeAired = episodeAired,
        episodeCount = episodeCount,
    )
}

fun PlaybackProgress.toAnimeSummary(): Anime {
    return Anime(
        id = animeId,
        title = animeTitle.ifBlank { "Anime #$animeId" },
        description = "",
        posterUrl = posterUrl,
        animeUrl = "",
        year = null,
        rating = null,
        views = 0L,
        status = "",
        type = "",
        genres = emptyList(),
        blockedIn = emptyList(),
    )
}

// CachedAnimeWithVideos
@Serializable
data class CachedAnimeWithVideos(
    val details: AnimeDetails,
    val videos: List<VideoVariant>,
)

// UserAnimeMark
enum class UserAnimeListMark(
    val id: Int,
) {
    Watching(0),
    Planned(1),
    Watched(2),
    Dropped(3),
    Postponed(5);

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

// UserProfile
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

// VideoAvailability
fun Iterable<VideoVariant>.availableEpisodeCount(): Int {
    return map { it.matchingEpisodeKey }
        .filter { it.isNotBlank() }
        .distinct()
        .size
}

fun Iterable<VideoVariant>.availableVoiceEpisodeCount(): Int {
    return availableEpisodeCount()
}

fun Iterable<VideoVariant>.sourceEpisodeCounts(): Map<String, Int> {
    return groupBy { it.matchingSourceKey }
        .mapValues { (_, variants) ->
            variants.availableEpisodeCount()
        }
}
