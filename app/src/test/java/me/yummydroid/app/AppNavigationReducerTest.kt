package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.VideoVariant

class AppNavigationReducerTest {
    @Test
    fun rootBackClearsSearchAndRequestsCatalogWithoutChangingSection() {
        val state = YummyDroidUiState(
            homeSection = BrowseSection.Catalog,
            searchQuery = "naruto",
            searchResults = LoadState.Ready(emptyList()),
            searchPaging = PagingUiState(canLoadMore = true),
        )

        val transition = backTransition(state)

        assertEquals(AppRoute.Home, transition.state.route)
        assertEquals(BrowseSection.Catalog, transition.state.homeSection)
        assertEquals("", transition.state.searchQuery)
        assertEquals(PagingUiState(canLoadMore = false), transition.state.searchPaging)
        assertTrue(transition.cancelSearchRequests)
        assertEquals(
            listOf(NavigationEffect.EnsureBrowseSection(BrowseSection.Catalog)),
            transition.effects,
        )
    }

    @Test
    fun rootBackMovesEverySecondaryOnlineSectionToCatalog() {
        listOf(
            BrowseSection.Schedule,
            BrowseSection.History,
            BrowseSection.Downloads,
        ).forEach { section ->
            val transition = backTransition(
                YummyDroidUiState(homeSection = section),
            )

            assertEquals(BrowseSection.Catalog, transition.state.homeSection)
            assertFalse(transition.cancelSearchRequests)
            assertEquals(
                listOf(NavigationEffect.EnsureBrowseSection(BrowseSection.Catalog)),
                transition.effects,
            )
        }
    }

    @Test
    fun rootBackKeepsForcedOfflineDownloadsUnchanged() {
        val state = YummyDroidUiState(
            homeSection = BrowseSection.Downloads,
            forcedOfflineMode = true,
        )

        assertEquals(NavigationTransition(state), backTransition(state))
    }

    @Test
    fun rootDetailsBackReturnsHomeAndHonorsForcedOfflineMode() {
        val transition = backTransition(
            YummyDroidUiState(
                route = AppRoute.Details(10),
                homeSection = BrowseSection.History,
                forcedOfflineMode = true,
            ),
        )

        assertEquals(AppRoute.Home, transition.state.route)
        assertEquals(BrowseSection.Downloads, transition.state.homeSection)
        assertEquals(emptyList(), transition.effects)
    }

    @Test
    fun rootPlayerBackDelegatesToOpeningItsAnime() {
        val video = video(animeId = 10)
        val state = YummyDroidUiState(
            route = AppRoute.Player(video = video, animeTitle = "Anime"),
        )

        val transition = backTransition(state)

        assertEquals(state, transition.state)
        assertEquals(listOf(NavigationEffect.OpenAnime(10)), transition.effects)
    }

    @Test
    fun backStackHomeEntryRestoresCachedCatalogWithoutReload() {
        val filters = BrowseFilters(fromYear = 2026)
        val entry = navigationEntry(
            route = AppRoute.Home,
            section = BrowseSection.Catalog,
            filters = filters,
        )
        val cachedCatalog = CatalogRouteCache(
            animes = emptyList(),
            paging = PagingUiState(canLoadMore = false),
            forcedOfflineMode = false,
        )
        val state = YummyDroidUiState(
            route = AppRoute.Details(20),
            navigationBackStack = listOf(entry),
            filters = BrowseFilters(toYear = 2000),
            featured = LoadState.Loading,
        )

        val transition = backNavigationTransition(
            state = state,
            catalogCacheForFilters = { requestedFilters ->
                cachedCatalog.takeIf { requestedFilters == filters }
            },
            detailsCacheForAnime = { null },
        )

        assertEquals(AppRoute.Home, transition.state.route)
        assertEquals(filters, transition.state.filters)
        assertEquals(PagingUiState(canLoadMore = false), transition.state.featuredPaging)
        assertTrue(transition.cancelSearchRequests)
        assertEquals(emptyList(), transition.effects)
    }

    @Test
    fun homeSearchEntryRequestsTheRestoredQuery() {
        val filters = BrowseFilters(fromYear = 2026)
        val entry = navigationEntry(
            route = AppRoute.Home,
            section = BrowseSection.Catalog,
            filters = filters,
            searchQuery = "one piece",
        )

        val transition = restoreTransition(
            state = YummyDroidUiState(route = AppRoute.Details(20)),
            entry = entry,
        )

        assertEquals("one piece", transition.state.searchQuery)
        assertIs<LoadState.Loading>(transition.state.searchResults)
        assertEquals(listOf(NavigationEffect.SearchCatalog("one piece")), transition.effects)
    }

    @Test
    fun cachedDetailsEntryRestoresRouteAndRefreshesProgress() {
        val entry = navigationEntry(
            route = AppRoute.Details(10),
            section = BrowseSection.History,
            selectedVideoGroup = "CVH|AniDUB",
        )
        val cached = detailsCache(
            selectedVideoGroup = "CVH|AniDUB",
            progress = playbackProgress(animeId = 10, groupKey = "Alloha|MiraiDUB"),
            videos = listOf(video(animeId = 10, player = "Alloha", dubbing = "MiraiDUB")),
        )

        val transition = restoreTransition(
            state = YummyDroidUiState(route = AppRoute.Details(20)),
            entry = entry,
            cachedDetails = cached,
        )

        assertEquals(AppRoute.Details(10), transition.state.route)
        assertEquals(BrowseSection.History, transition.state.homeSection)
        assertEquals("Alloha|MiraiDUB", transition.state.selectedVideoGroup)
        assertEquals(listOf(NavigationEffect.RefreshPlaybackProgress(10)), transition.effects)
    }

    @Test
    fun uncachedDetailsEntryStartsOneLoadingTransitionAndKeepsMatchingHistory() {
        val matching = playbackProgress(animeId = 10, groupKey = "CVH|AniDUB")
        val other = playbackProgress(animeId = 20, groupKey = "Kodik|AniLibria")
        val entry = navigationEntry(
            route = AppRoute.Details(10),
            section = BrowseSection.Schedule,
            selectedVideoGroup = "CVH|AniDUB",
        )

        val transition = restoreTransition(
            state = YummyDroidUiState(
                route = AppRoute.Details(20),
                playbackProgress = other,
                playbackHistory = listOf(matching, other),
            ),
            entry = entry,
        )

        assertEquals(AppRoute.Details(10), transition.state.route)
        assertEquals(BrowseSection.Schedule, transition.state.homeSection)
        assertEquals("CVH|AniDUB", transition.state.selectedVideoGroup)
        assertIs<LoadState.Loading>(transition.state.details)
        assertIs<LoadState.Loading>(transition.state.videos)
        assertEquals(null, transition.state.playbackProgress)
        assertEquals(listOf(matching, other), transition.state.playbackHistory)
        assertEquals(listOf(NavigationEffect.LoadAnimeDetails(10)), transition.effects)
    }

    @Test
    fun playerEntryRestoresStateBeforeStartingPlayback() {
        val route = AppRoute.Player(
            video = video(animeId = 10),
            animeTitle = "Anime",
            startPositionMs = 12_000,
            preferredQuality = PreferredQuality.P720,
        )
        val entry = navigationEntry(
            route = route,
            section = BrowseSection.History,
            selectedVideoGroup = "CVH|AniDUB",
        )

        val transition = restoreTransition(
            state = YummyDroidUiState(forcedOfflineMode = true),
            entry = entry,
        )

        assertEquals(route, transition.state.route)
        assertEquals(BrowseSection.Downloads, transition.state.homeSection)
        assertEquals("CVH|AniDUB", transition.state.selectedVideoGroup)
        assertEquals(listOf(NavigationEffect.PlayVideo(route)), transition.effects)
    }

    @Test
    fun preservedHomeSectionSuppressesAutomaticSectionLoading() {
        val entry = navigationEntry(
            route = AppRoute.Home,
            section = BrowseSection.Schedule,
        )

        val transition = restoreTransition(
            state = YummyDroidUiState(
                route = AppRoute.Details(10),
                forcedOfflineMode = true,
            ),
            entry = entry,
            preserveHomeSection = true,
        )

        assertEquals(BrowseSection.Schedule, transition.state.homeSection)
        assertTrue(transition.cancelSearchRequests)
        assertEquals(emptyList(), transition.effects)
    }

    private fun backTransition(state: YummyDroidUiState): NavigationTransition {
        return backNavigationTransition(
            state = state,
            catalogCacheForFilters = { null },
            detailsCacheForAnime = { null },
        )
    }

    private fun restoreTransition(
        state: YummyDroidUiState,
        entry: NavigationEntry,
        cachedCatalog: CatalogRouteCache? = null,
        cachedDetails: DetailsRouteCache? = null,
        preserveHomeSection: Boolean = false,
    ): NavigationTransition {
        return restoreNavigationEntryTransition(
            state = state,
            entry = entry,
            remainingBackStack = emptyList(),
            cachedCatalogForEntry = cachedCatalog,
            cachedDetailsForEntry = cachedDetails,
            preserveHomeSection = preserveHomeSection,
        )
    }

    private fun navigationEntry(
        route: AppRoute,
        section: BrowseSection,
        filters: BrowseFilters = BrowseFilters(),
        searchQuery: String = "",
        selectedVideoGroup: String? = null,
    ): NavigationEntry {
        return NavigationEntry(
            route = route,
            homeSection = section,
            filters = filters,
            searchQuery = searchQuery,
            selectedVideoGroup = selectedVideoGroup,
        )
    }

    private fun detailsCache(
        selectedVideoGroup: String?,
        progress: PlaybackProgress?,
        videos: List<VideoVariant>,
    ): DetailsRouteCache {
        return DetailsRouteCache(
            details = LoadState.Ready(animeDetails()),
            videos = LoadState.Ready(videos),
            detailsExtras = LoadState.Ready(AnimeDetailsExtras()),
            animeMark = LoadState.Ready(null),
            selectedVideoGroup = selectedVideoGroup,
            forcedOfflineMode = false,
            playbackProgress = progress,
            playbackHistory = progress?.let(::listOf).orEmpty(),
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

    private fun video(
        animeId: Long,
        player: String = "CVH",
        dubbing: String = "AniDUB",
    ): VideoVariant {
        return VideoVariant(
            id = animeId * 10,
            animeId = animeId,
            player = player,
            dubbing = dubbing,
            episode = "1",
            url = "https://example.test/$animeId/$player",
            index = 1,
            durationSeconds = null,
            views = 0,
        )
    }

    private fun playbackProgress(animeId: Long, groupKey: String): PlaybackProgress {
        return PlaybackProgress(
            animeId = animeId,
            videoId = animeId * 10,
            animeTitle = "Anime $animeId",
            posterUrl = "",
            groupKey = groupKey,
            episode = "1",
            positionMs = 1_000,
            durationMs = 2_000,
            updatedAtMs = 3_000,
        )
    }
}
