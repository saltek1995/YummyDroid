package me.yummydroid.app.data

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
    val playerId: Long = 0L,
    val videoId: Long = 0L,
)

data class SiteNotification(
    val id: Long,
    val title: String,
    val text: String,
    val clickUrl: String,
    val type: String,
    val subType: String,
    val objectId: Long,
    val dateSeconds: Long,
    val viewed: Boolean,
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
