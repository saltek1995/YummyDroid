package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class BrowseFiltersTest {
    @Test
    fun defaultsHaveNoActiveFilters() {
        val filters = BrowseFilters()

        assertEquals(0, filters.activeCount)
        assertEquals(AnimeStatusFilter.All, filters.status)
        assertEquals(AnimeGenreFilter.All, filters.genre)
    }

    @Test
    fun activeCountIncludesEveryFilterKindAndNonDefaultSort() {
        val filters = BrowseFilters(
            sort = AnimeSort.Title,
            fromYear = 2000,
            toYear = 2020,
            minRating = 5.0,
            maxRating = 9.0,
            episodeFrom = 1,
            episodeTo = 12,
            statuses = setOf("released"),
            genres = setOf("action"),
            excludedGenres = setOf("horror"),
            seasons = setOf("winter"),
            types = setOf("tv"),
            studios = setOf("studio"),
            creators = setOf("creator"),
            translates = setOf("dubbing"),
            ageRatings = setOf("2"),
            userMarks = setOf("0"),
            excludedUserMarks = setOf("3"),
            offlineOnly = true,
        )

        assertEquals(19, filters.activeCount)
    }
}
