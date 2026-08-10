package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnimeRatingSummaryTest {
    @Test
    fun votesAndAverageAreWeightedByBucketCounts() {
        val summary = AnimeRatingSummary(
            buckets = listOf(
                AnimeRatingBucket(rating = 10, count = 2),
                AnimeRatingBucket(rating = 4, count = 1),
            ),
        )

        assertEquals(3L, summary.votes)
        assertEquals(8.0, summary.average)
    }

    @Test
    fun averageIsAbsentWithoutPositiveVotes() {
        val summary = AnimeRatingSummary(
            buckets = listOf(AnimeRatingBucket(rating = 10, count = 0)),
        )

        assertEquals(0L, summary.votes)
        assertNull(summary.average)
    }
}
