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

    @Test
    fun animeQueryParamsPreserveFiltersPaginationAndIds() {
        val filters = BrowseFilters(
            sort = AnimeSort.Title,
            genres = setOf("action"),
        )

        assertEquals(
            listOf(
                "sort" to AnimeSort.Title.apiValue,
                "sort_forward" to AnimeSort.Title.forward.toString(),
                "genres" to "action",
                "q" to "query",
                "limit" to "24",
                "offset" to "0",
                "ids" to "7",
                "ids" to "9",
            ),
            filters.toAnimeQueryParams(
                query = "query",
                limit = 24,
                offset = -5,
                ids = linkedSetOf(7L, 9L),
            ),
        )
    }

    @Test
    fun featuredAnimeQueryOmitsSearchParameter() {
        val params = BrowseFilters().toAnimeQueryParams(
            query = null,
            limit = 12,
            offset = 5,
            ids = emptySet(),
        )

        assertEquals(false, params.any { it.first == "q" })
        assertEquals("12", params.first { it.first == "limit" }.second)
        assertEquals("5", params.first { it.first == "offset" }.second)
    }
}
