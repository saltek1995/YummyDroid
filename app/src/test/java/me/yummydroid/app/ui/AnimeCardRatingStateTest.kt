package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.data.Anime

class AnimeCardRatingStateTest {
    @Test
    fun knownMapScoreOverridesStalePayloadWithoutMutatingMap() {
        val ratings = linkedMapOf(10L to 9)

        assertEquals(9, animeCardUserRating(anime(10, 4), ratings))
        assertEquals(linkedMapOf(10L to 9), ratings)
    }

    @Test
    fun explicitNullSuppressesStalePayloadAndAbsentEntryUsesPayload() {
        val ratings = linkedMapOf<Long, Int?>(10L to null)

        assertEquals(null, animeCardUserRating(anime(10, 4), ratings))
        assertEquals(6, animeCardUserRating(anime(20, 6), ratings))
        assertEquals(linkedMapOf<Long, Int?>(10L to null), ratings)
    }

    @Test
    fun ownRatingResolutionDoesNotDependOnGeneralRating() {
        assertEquals(8, animeCardUserRating(anime(10, 4, generalRating = null), mapOf(10L to 8)))
        assertEquals(4, animeCardUserRating(anime(10, 4, generalRating = null), emptyMap()))
    }

    private fun anime(id: Long, userRating: Int?, generalRating: Double? = 8.0) = Anime(
        id = id, title = "Anime $id", description = "", posterUrl = "", animeUrl = "", year = 2026,
        rating = generalRating, userRating = userRating, views = 0, status = "ongoing", type = "TV",
        genres = emptyList(), blockedIn = emptyList(),
    )
}
