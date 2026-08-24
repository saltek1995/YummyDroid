package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant

class AnimeDetailsLoadStateTest {
    @Test
    fun onlineSuccessPublishesLoadedDataWithoutReplacingExtrasOrMarkState() {
        val extras = LoadState.Error("keep extras")
        val mark = LoadState.Error("keep mark")
        val state = YummyDroidUiState(
            route = AppRoute.Details(10),
            detailsExtras = extras,
            animeMark = mark,
            forcedOfflineMode = true,
            playbackProgress = progress(),
            playbackHistory = listOf(progress()),
        )
        val loaded = result(offlineMode = false)

        val updated = state.withLoadedAnimeDetails(animeId = 10, loaded = loaded)

        assertEquals(loaded.details, updated.details.readyDataOrNull())
        assertEquals(loaded.videos, updated.videos.readyListOrEmpty())
        assertEquals(loaded.selectedVideoGroup, updated.selectedVideoGroup)
        assertEquals(state.playbackProgress, updated.playbackProgress)
        assertEquals(state.playbackHistory, updated.playbackHistory)
        assertSame(extras, updated.detailsExtras)
        assertSame(mark, updated.animeMark)
        assertEquals(false, updated.forcedOfflineMode)
    }

    @Test
    fun offlineSuccessClearsOnlineOnlyExtrasAndAnimeMark() {
        val state = YummyDroidUiState(
            route = AppRoute.Details(10),
            detailsExtras = LoadState.Error("old"),
            animeMark = LoadState.Error("old"),
        )

        val updated = state.withLoadedAnimeDetails(10, result(offlineMode = true))

        assertEquals(AnimeDetailsExtras(), updated.detailsExtras.readyDataOrNull())
        assertNull(updated.animeMark.readyDataOrNull())
        assertEquals(true, updated.forcedOfflineMode)
    }

    @Test
    fun staleSuccessCannotMutateAnotherRoute() {
        val state = YummyDroidUiState(route = AppRoute.Details(20))

        assertSame(state, state.withLoadedAnimeDetails(10, result()))
    }

    @Test
    fun aliasSuccessReplacesProvisionalRouteWithCanonicalAnimeId() {
        val state = YummyDroidUiState(route = AppRoute.Details(0))

        val updated = state.withLoadedAnimeDetails(0, result())

        assertEquals(AppRoute.Details(10), updated.route)
    }

    @Test
    fun extrasUseLatestGlobalServerSubscriptions() {
        val subscription = VideoSubscription(
            animeId = 10,
            title = "Anime",
            posterUrl = "poster",
            player = "CVH",
            dubbing = "Voice",
            videoId = 42,
        )
        val state = YummyDroidUiState(
            route = AppRoute.Details(10),
            globalSubscriptions = LoadState.Ready(listOf(subscription)),
        )

        val updated = state.withLoadedAnimeDetailsExtras(10, AnimeDetailsExtras())

        assertEquals(listOf(subscription), updated.detailsExtras.readyDataOrNull()?.subscriptions)
    }

    @Test
    fun offlineFailureRestoresPreviousNavigationEntryWhenAvailable() {
        val previous = navigationEntry(AppRoute.Home)
        val state = YummyDroidUiState(
            route = AppRoute.Details(10),
            navigationBackStack = listOf(previous),
        )

        val plan = animeDetailsLoadFailurePlan(
            state = state,
            animeId = 10,
            offlineUnavailable = true,
            offlineMessage = "offline",
            errorMessage = "ignored",
        )

        val restore = assertIs<AnimeDetailsLoadFailurePlan.RestorePrevious>(plan)
        assertEquals(previous, restore.entry)
        assertEquals(emptyList(), restore.remainingBackStack)
    }

    @Test
    fun rootOfflineFailurePublishesOfflineSpecificState() {
        val history = listOf(progress())
        val state = YummyDroidUiState(
            route = AppRoute.Details(10),
            forcedOfflineMode = true,
            playbackProgress = progress(),
            playbackHistory = history,
        )

        val plan = animeDetailsLoadFailurePlan(
            state = state,
            animeId = 10,
            offlineUnavailable = true,
            offlineMessage = "offline",
            errorMessage = "ignored",
        )
        val published = assertIs<AnimeDetailsLoadFailurePlan.Publish>(plan).state

        assertEquals(LoadState.Error("offline"), published.details)
        assertEquals(LoadState.Error("offline"), published.videos)
        assertEquals(AnimeDetailsExtras(), published.detailsExtras.readyDataOrNull())
        assertNull(published.animeMark.readyDataOrNull())
        assertNull(published.playbackProgress)
        assertEquals(history, published.playbackHistory)
        assertEquals(true, published.forcedOfflineMode)
    }

    @Test
    fun genericFailurePublishesSharedErrorAndLeavesOfflineMode() {
        val state = YummyDroidUiState(
            route = AppRoute.Details(10),
            forcedOfflineMode = true,
            playbackProgress = progress(),
        )

        val plan = animeDetailsLoadFailurePlan(
            state = state,
            animeId = 10,
            offlineUnavailable = false,
            offlineMessage = "ignored",
            errorMessage = "server",
        )
        val published = assertIs<AnimeDetailsLoadFailurePlan.Publish>(plan).state

        assertEquals(LoadState.Error("server"), published.details)
        assertEquals(LoadState.Error("server"), published.videos)
        assertEquals(LoadState.Error("server"), published.detailsExtras)
        assertNull(published.animeMark.readyDataOrNull())
        assertNull(published.playbackProgress)
        assertEquals(false, published.forcedOfflineMode)
    }

    @Test
    fun staleFailureIsIgnored() {
        val plan = animeDetailsLoadFailurePlan(
            state = YummyDroidUiState(route = AppRoute.Details(20)),
            animeId = 10,
            offlineUnavailable = false,
            offlineMessage = "offline",
            errorMessage = "server",
        )

        assertIs<AnimeDetailsLoadFailurePlan.Ignore>(plan)
    }

    private fun result(offlineMode: Boolean = false): LoadedAnimeDetails {
        return LoadedAnimeDetails(
            details = details(),
            videos = listOf(video()),
            offlineMode = offlineMode,
            selectedVideoGroup = "CVH|Voice",
        )
    }

    private fun navigationEntry(route: AppRoute): NavigationEntry {
        return NavigationEntry(
            route = route,
            homeSection = BrowseSection.Catalog,
            filters = BrowseFilters(),
            searchQuery = "",
            selectedVideoGroup = null,
        )
    }

    private fun details(): AnimeDetails {
        return AnimeDetails(
            id = 10,
            title = "Anime 10",
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

    private fun video(): VideoVariant {
        return VideoVariant(
            id = 1,
            animeId = 10,
            player = "CVH",
            dubbing = "Voice",
            episode = "1",
            url = "https://example.test/1",
            index = 1,
            durationSeconds = null,
            views = 0,
        )
    }

    private fun progress(): PlaybackProgress {
        return PlaybackProgress(
            animeId = 10,
            videoId = 1,
            animeTitle = "Anime 10",
            posterUrl = "",
            groupKey = "CVH|Voice",
            episode = "1",
            positionMs = 1_000,
            durationMs = 2_000,
            updatedAtMs = 3_000,
        )
    }
}
