package me.yummydroid.app.data

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
