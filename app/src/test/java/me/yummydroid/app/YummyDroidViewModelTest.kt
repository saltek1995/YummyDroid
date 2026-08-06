package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.VideoVariant

class YummyDroidViewModelTest {
    @Test
    fun homeRestorePlanUsesDownloadsWhenCurrentStateIsForcedOffline() {
        val entry = NavigationEntry(
            route = AppRoute.Home,
            homeSection = BrowseSection.Catalog,
            filters = BrowseFilters(),
            searchQuery = "anime",
            selectedVideoGroup = null,
        )

        val plan = homeRouteRestorePlan(
            entry = entry,
            currentState = YummyDroidUiState(forcedOfflineMode = true),
            cachedCatalogForEntry = null,
            preserveHomeSection = false,
        )

        assertEquals(BrowseSection.Downloads, plan.restoredHomeSection)
        assertEquals("", plan.restoredSearchQuery)
        assertFalse(plan.shouldLoadCatalog)
        assertFalse(plan.shouldSearchNow)
    }

    @Test
    fun homeRestoreUsesCachedCatalogWhenCurrentCatalogCannotBeReused() {
        val entry = NavigationEntry(
            route = AppRoute.Home,
            homeSection = BrowseSection.Catalog,
            filters = BrowseFilters(),
            searchQuery = "",
            selectedVideoGroup = "CVH|AniDUB",
        )
        val cachedCatalog = CatalogRouteCache(
            animes = listOf(anime(id = 10)),
            paging = PagingUiState(canLoadMore = false),
            forcedOfflineMode = true,
        )
        val state = YummyDroidUiState(featured = LoadState.Loading)
        val plan = homeRouteRestorePlan(
            entry = entry,
            currentState = state,
            cachedCatalogForEntry = cachedCatalog,
            preserveHomeSection = false,
        )

        val restored = state.withRestoredHomeRoute(
            entry = entry,
            remainingBackStack = emptyList(),
            plan = plan,
        )

        assertEquals(listOf(10L), restored.featured.readyListOrEmpty().map { it.id })
        assertEquals(PagingUiState(canLoadMore = false), restored.featuredPaging)
        assertEquals(true, restored.forcedOfflineMode)
        assertEquals("CVH|AniDUB", restored.selectedVideoGroup)
        assertFalse(plan.shouldLoadCatalog)
    }

    @Test
    fun homeRestoreReusesCurrentSearchOnlyWhenQueryAndFiltersMatch() {
        val filters = BrowseFilters(fromYear = 2026)
        val entry = NavigationEntry(
            route = AppRoute.Home,
            homeSection = BrowseSection.Catalog,
            filters = filters,
            searchQuery = "naruto",
            selectedVideoGroup = null,
        )
        val state = YummyDroidUiState(
            filters = filters,
            searchQuery = "naruto",
            searchResults = LoadState.Ready(listOf(anime(id = 20))),
            searchPaging = PagingUiState(canLoadMore = false),
        )

        val plan = homeRouteRestorePlan(
            entry = entry,
            currentState = state,
            cachedCatalogForEntry = null,
            preserveHomeSection = false,
        )
        val restored = state.withRestoredHomeRoute(
            entry = entry,
            remainingBackStack = emptyList(),
            plan = plan,
        )

        assertEquals(listOf(20L), restored.searchResults.readyListOrEmpty().map { it.id })
        assertEquals(PagingUiState(canLoadMore = false), restored.searchPaging)
        assertFalse(plan.shouldSearchNow)
    }

    @Test
    fun detailsRouteCacheRestoresProgressGroupWhenVideoGroupExists() {
        val cachedRoute = detailsRouteCache(
            selectedVideoGroup = "CVH|AniDUB",
            playbackProgress = playbackProgress(groupKey = "Alloha|MiraiDUB"),
            videos = listOf(
                video(player = "CVH", dubbing = "AniDUB"),
                video(player = "Alloha", dubbing = "MiraiDUB"),
            ),
        )

        val restored = YummyDroidUiState().withDetailsRouteCache(
            route = AppRoute.Details(animeId = 10),
            navigationBackStack = emptyList(),
            cachedRoute = cachedRoute,
        )

        assertEquals("Alloha|MiraiDUB", restored.selectedVideoGroup)
    }

    @Test
    fun detailsRouteCacheFallsBackToCachedGroupWhenProgressGroupIsMissing() {
        val cachedRoute = detailsRouteCache(
            selectedVideoGroup = "CVH|AniDUB",
            playbackProgress = playbackProgress(groupKey = "Alloha|MiraiDUB"),
            videos = listOf(video(player = "CVH", dubbing = "AniDUB")),
        )

        val restored = YummyDroidUiState().withDetailsRouteCache(
            route = AppRoute.Details(animeId = 10),
            navigationBackStack = emptyList(),
            cachedRoute = cachedRoute,
        )

        assertEquals("CVH|AniDUB", restored.selectedVideoGroup)
    }

    private fun detailsRouteCache(
        selectedVideoGroup: String?,
        playbackProgress: PlaybackProgress?,
        videos: List<VideoVariant>,
    ): DetailsRouteCache {
        return DetailsRouteCache(
            details = LoadState.Ready(animeDetails()),
            videos = LoadState.Ready(videos),
            detailsExtras = LoadState.Ready(AnimeDetailsExtras()),
            animeMark = LoadState.Ready(null),
            selectedVideoGroup = selectedVideoGroup,
            forcedOfflineMode = false,
            playbackProgress = playbackProgress,
            playbackHistory = playbackProgress?.let(::listOf).orEmpty(),
        )
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

    private fun animeDetails(): AnimeDetails {
        return AnimeDetails(
            id = 10,
            title = "Anime",
            otherTitles = emptyList(),
            description = "",
            posterUrl = "",
            backdropUrl = null,
            year = 2026,
            rating = null,
            views = 0,
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
            commentsCount = 0,
            listsCount = 0,
            translations = emptyList(),
            relatedAnime = emptyList(),
            screenshots = emptyList(),
            blockedIn = emptyList(),
        )
    }

    private fun video(player: String, dubbing: String): VideoVariant {
        return VideoVariant(
            id = player.hashCode().toLong(),
            animeId = 10,
            player = player,
            dubbing = dubbing,
            episode = "1",
            url = "https://example.test/$player",
            index = 1,
            durationSeconds = null,
            views = 0,
        )
    }

    private fun playbackProgress(groupKey: String): PlaybackProgress {
        return PlaybackProgress(
            animeId = 10,
            videoId = 1,
            animeTitle = "Anime",
            posterUrl = "",
            groupKey = groupKey,
            episode = "1",
            positionMs = 1000,
            durationMs = 2000,
            updatedAtMs = 3000,
        )
    }
}
