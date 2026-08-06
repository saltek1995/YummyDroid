package me.yummydroid.app

import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.BrowseFilters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowsePagingReducerTest {
    @Test
    fun pageRequestUsesOneOffsetAndLoadingPolicy() {
        val items = LoadState.Ready(listOf(anime(1), anime(2)))

        assertEquals(
            AnimePageRequest(
                offset = 0,
                loadingPaging = PagingUiState(canLoadMore = false),
            ),
            animePageRequest(
                items = items,
                paging = PagingUiState(canLoadMore = true),
                reset = true,
                canLoadMoreOnReset = false,
            ),
        )
        assertEquals(
            AnimePageRequest(
                offset = 2,
                loadingPaging = PagingUiState(isLoadingMore = true, canLoadMore = true),
            ),
            animePageRequest(
                items = items,
                paging = PagingUiState(canLoadMore = true),
                reset = false,
            ),
        )
        assertNull(
            animePageRequest(
                items = items,
                paging = PagingUiState(canLoadMore = false),
                reset = false,
            ),
        )
    }

    @Test
    fun catalogSuccessProducesStateAndMatchingRouteCacheTogether() {
        val filters = BrowseFilters(fromYear = 2026)
        val state = YummyDroidUiState(
            filters = filters,
            featured = LoadState.Ready(listOf(anime(1))),
        )

        val update = requireNotNull(
            reduceCatalogPageSuccess(
                state = state,
                requestedFilters = filters,
                incoming = listOf(anime(2)),
                reset = false,
                pageSize = 2,
                forcedOfflineMode = false,
            ),
        )

        assertEquals(listOf(1L, 2L), update.state.featured.readyListOrEmpty().map { it.id })
        assertEquals(update.state.featured.readyListOrEmpty(), update.cache.animes)
        assertEquals(update.state.featuredPaging, update.cache.paging)
    }

    @Test
    fun catalogResultIsIgnoredWhenRequestIdentityIsStale() {
        val requestedFilters = BrowseFilters(fromYear = 2026)
        val changedFilters = BrowseFilters(fromYear = 2025)

        assertNull(
            reduceCatalogPageSuccess(
                state = YummyDroidUiState(filters = changedFilters),
                requestedFilters = requestedFilters,
                incoming = listOf(anime(1)),
                reset = true,
                pageSize = 36,
                forcedOfflineMode = false,
            ),
        )
        assertNull(
            reduceCatalogPageSuccess(
                state = YummyDroidUiState(filters = requestedFilters, searchQuery = "query"),
                requestedFilters = requestedFilters,
                incoming = listOf(anime(1)),
                reset = true,
                pageSize = 36,
                forcedOfflineMode = false,
            ),
        )
    }

    @Test
    fun offlineCatalogFailureMovesHomeToDownloadsAndClearsSearch() {
        val filters = BrowseFilters()
        val result = reduceCatalogPageFailure(
            state = YummyDroidUiState(
                filters = filters,
                searchResults = LoadState.Ready(listOf(anime(3))),
                searchPaging = PagingUiState(canLoadMore = true),
            ),
            requestedFilters = filters,
            reset = true,
            offlineFailure = true,
            error = "offline",
        )

        assertEquals(true, result.forcedOfflineMode)
        assertEquals(BrowseSection.Downloads, result.homeSection)
        assertEquals(emptyList(), result.featured.readyListOrEmpty())
        assertEquals(emptyList(), result.searchResults.readyListOrEmpty())
        assertEquals(PagingUiState(canLoadMore = false), result.searchPaging)
    }

    @Test
    fun searchReducersIgnoreResultsFromPreviousQueryOrFilters() {
        val requestedFilters = BrowseFilters(fromYear = 2026)
        val current = YummyDroidUiState(
            filters = BrowseFilters(fromYear = 2025),
            searchQuery = "current",
            searchResults = LoadState.Ready(listOf(anime(1))),
        )

        val success = reduceSearchPageSuccess(
            state = current,
            query = "old",
            requestedFilters = requestedFilters,
            incoming = listOf(anime(2)),
            reset = true,
            pageSize = 36,
            forcedOfflineMode = false,
        )
        val failure = reduceSearchPageFailure(
            state = current,
            query = "old",
            requestedFilters = requestedFilters,
            reset = true,
            error = "stale",
        )

        assertEquals(current, success)
        assertEquals(current, failure)
    }

    private fun anime(id: Long): Anime {
        return Anime(
            id = id,
            title = "Anime $id",
            description = "",
            posterUrl = "",
            animeUrl = "",
            year = 2026,
            rating = null,
            views = 0,
            status = "",
            type = "",
            genres = emptyList(),
            blockedIn = emptyList(),
        )
    }
}
