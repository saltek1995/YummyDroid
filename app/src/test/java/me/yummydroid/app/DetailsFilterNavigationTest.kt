package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.RatingDetails

class DetailsFilterNavigationTest {
    @Test
    fun detailsFilterPushesCurrentDetailsRoute() {
        val state = YummyDroidUiState(
            route = AppRoute.Details(42L),
            homeSection = BrowseSection.History,
            filters = BrowseFilters(fromYear = 2024, toYear = 2024),
            searchQuery = "old",
            selectedVideoGroup = "AniLibria",
        )

        val stack = state.navigationStackForDetailsFilter()

        assertEquals(1, stack.size)
        assertEquals(AppRoute.Details(42L), stack.last().route)
        assertEquals(BrowseSection.History, stack.last().homeSection)
        assertEquals(state.filters, stack.last().filters)
        assertEquals("old", stack.last().searchQuery)
        assertEquals("AniLibria", stack.last().selectedVideoGroup)
    }

    @Test
    fun detailsFilterCanUseReadyDetailsWhenRouteIsAlreadyHome() {
        val state = YummyDroidUiState(
            route = AppRoute.Home,
            details = LoadState.Ready(minimalDetails(77L)),
            selectedVideoGroup = "CVH",
        )

        val stack = state.navigationStackForDetailsFilter()

        assertEquals(1, stack.size)
        assertEquals(AppRoute.Details(77L), stack.last().route)
        assertEquals("CVH", stack.last().selectedVideoGroup)
    }

    @Test
    fun detailsFilterUsesExplicitSourceAnimeIdOverHomeRoute() {
        val homeEntry = NavigationEntry(
            route = AppRoute.Home,
            homeSection = BrowseSection.Catalog,
            filters = BrowseFilters(),
            searchQuery = "",
            selectedVideoGroup = null,
        )
        val state = YummyDroidUiState(
            route = AppRoute.Home,
            navigationBackStack = listOf(homeEntry),
            details = LoadState.Ready(minimalDetails(77L)),
            selectedVideoGroup = "AniLibria",
        )

        val stack = state.navigationStackForDetailsFilter(sourceAnimeId = 42L)

        assertEquals(listOf(AppRoute.Home, AppRoute.Details(42L)), stack.map { it.route })
        assertEquals("AniLibria", stack.last().selectedVideoGroup)
    }

    private fun minimalDetails(id: Long) = AnimeDetails(
        id = id,
        title = "Anime $id",
        otherTitles = emptyList(),
        description = "",
        posterUrl = "",
        backdropUrl = null,
        year = null,
        rating = null,
        views = 0L,
        status = "",
        type = "",
        minAge = "",
        genreTags = emptyList(),
        genres = emptyList(),
        episodeSummary = "",
        episodeAired = 0,
        episodeCount = 0,
        nextEpisodeText = "",
        durationSeconds = 0,
        ratingDetails = RatingDetails(),
        studios = emptyList(),
        creators = emptyList(),
        original = "",
        commentsCount = 0L,
        listsCount = 0L,
        translations = emptyList(),
        relatedAnime = emptyList(),
        screenshots = emptyList(),
        blockedIn = emptyList(),
    )
}
