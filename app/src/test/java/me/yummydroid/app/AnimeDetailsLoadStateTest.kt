package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.toAnimeSummary

class AnimeDetailsLoadStateTest {
    @Test
    fun cachedAndInFlightDetailsCannotExitOfflineModeOrRestoreRemoteErrors() {
        val online = video()
        val local = online.copy(id = 2, dubbing = "Downloaded", localPlaybackUrl = "file:///one.mp4")
        val cache = DetailsRouteCache(
            details = LoadState.Ready(details()), videos = LoadState.Ready(listOf(online, local)),
            detailsExtras = LoadState.Error("DNS error"), animeMark = LoadState.Error("DNS error"),
            selectedVideoGroup = online.groupKey, playbackProgress = null, playbackHistory = emptyList(),
        )
        val state = YummyDroidUiState(forcedOfflineMode = true).withDetailsRouteCache(AppRoute.Details(10), emptyList(), cache)
        assertTrue(state.forcedOfflineMode)
        assertEquals(local.groupKey, state.selectedVideoGroup)
        assertIs<LoadState.Ready<AnimeDetailsExtras>>(state.detailsExtras)
        assertIs<LoadState.Ready<*>>(state.animeMark)
        val loaded = state.withLoadedAnimeDetails(10, LoadedAnimeDetails(details(), listOf(online, local), false, online.groupKey))
        assertTrue(loaded.forcedOfflineMode)
        assertEquals(local.groupKey, loaded.selectedVideoGroup)
    }

    @Test
    fun localSnapshotUpdatesEachEpisodeAndReconcilesRestoredCardsWithoutChangingSelection() {
        val first = video(subscribed = true)
        val second = first.copy(id = 2, episode = "2", index = 2)
        val initial = YummyDroidUiState(
            route = AppRoute.Details(10),
            details = LoadState.Ready(details()),
            videos = LoadState.Ready(listOf(first, second)),
            selectedVideoGroup = first.groupKey,
            playbackProgress = progress(),
        )
        fun entries(vararg videos: VideoVariant) = LoadState.Ready(listOf(
            OfflineAnimeEntry(details().toAnimeSummary(), details(), videos.toList(), 1L),
        ))
        val localFirst = first.copy(localPlaybackUrl = "file:///episode1.mp4")
        val one = initial.copy(offlineEntries = entries(localFirst)).withCurrentOfflineVideos(initial)
        assertEquals(listOf(true, false), one.videos.readyListOrEmpty().map { it.isOfflineAvailable })
        val localSecond = second.copy(localPlaybackUrl = "file:///episode2.mp4")
        val two = one.copy(offlineEntries = entries(localFirst, localSecond)).withCurrentOfflineVideos(one)
        assertEquals(listOf(true, true), two.videos.readyListOrEmpty().map { it.isOfflineAvailable })
        val restored = two.copy(videos = initial.videos).withCurrentOfflineVideos(two)
        assertEquals(two.videos, restored.videos)
        assertEquals(initial.selectedVideoGroup, restored.selectedVideoGroup)
        assertEquals(initial.playbackProgress, restored.playbackProgress)
        assertEquals(true, restored.videos.readyListOrEmpty().first().subscribed)
        val deleted = restored.copy(offlineEntries = entries(localSecond)).withCurrentOfflineVideos(restored)
        assertEquals(listOf(false, true), deleted.videos.readyListOrEmpty().map { it.isOfflineAvailable })
        val cleared = deleted.copy(offlineEntries = LoadState.Ready(emptyList())).withCurrentOfflineVideos(deleted)
        assertEquals(listOf(false, false), cleared.videos.readyListOrEmpty().map { it.isOfflineAvailable })
        assertSame(cleared, cleared.withCurrentOfflineVideos(cleared))
        val loading = two.copy(offlineEntries = LoadState.Loading)
        assertSame(loading, loading.withCurrentOfflineVideos(two))
    }

    @Test
    fun successfulEmptyHistoryClearsProgressWhileFailedRefreshRetainsIt() {
        val saved = progress()
        val state = YummyDroidUiState(
            route = AppRoute.Details(10),
            details = LoadState.Ready(details()),
            playbackProgress = saved,
            playbackHistory = listOf(saved),
            playbackHistoryLoading = true,
        )
        val authoritative = state.withRefreshedPlaybackHistory(10, null, emptyList(), null, null)
        assertNull(authoritative.playbackProgress)
        assertEquals(emptyList(), authoritative.playbackHistory)
        assertEquals(false, authoritative.playbackHistoryLoading)

        val fallback = state.withRefreshedPlaybackHistory(10, null, emptyList(), null, null, retainMissingProgress = true)
        assertEquals(saved, fallback.playbackProgress)
        assertEquals(listOf(saved), fallback.playbackHistory)
        assertEquals(false, fallback.playbackHistoryLoading)
    }

    @Test
    fun delayedHistoryRefreshCannotSelectAnOlderVoiceAfterManualSelection() {
        val before = video()
        val selected = before.copy(id = 2, dubbing = "New voice")
        val state = YummyDroidUiState(
            route = AppRoute.Details(10),
            videos = LoadState.Ready(listOf(before, selected)),
            selectedVideoGroup = selected.groupKey,
        )
        val saved = progress().copy(groupKey = before.groupKey)
        val refreshed = state.withRefreshedPlaybackHistory(10, saved, listOf(saved), null, before.groupKey)
        assertEquals(selected.groupKey, refreshed.selectedVideoGroup)
        assertSame(state, state.withRefreshedPlaybackHistory(20, null, emptyList(), null, null))
    }

    @Test
    fun localProgressUpdatesCurrentEpisodeAndRetainsOtherEpisodes() {
        val previous = progress().copy(episode = "2", videoId = 2)
        val latest = progress().copy(positionMs = 12_000, updatedAtMs = 999_999)
        val state = YummyDroidUiState(
            route = AppRoute.Details(10),
            details = LoadState.Ready(details()),
            playbackProgress = previous,
            playbackHistory = listOf(previous),
            historyAnime = LoadState.Ready(emptyList()),
        )

        val updated = state.withLocalPlaybackProgress(latest, null)

        assertEquals(latest, updated.playbackProgress)
        assertEquals(setOf(previous, latest), updated.playbackHistory.toSet())
        assertEquals(listOf(10L), updated.historyAnime.readyListOrEmpty().map { it.id })
        assertEquals(listOf(previous), state.playbackHistory)
        assertEquals(updated, state.withLocalPlaybackProgress(latest, null))
    }

    @Test
    fun delayedProgressForAnotherAnimeCannotReplaceTheCurrentDetailsHistory() {
        val current = progress()
        val state = YummyDroidUiState(
            route = AppRoute.Details(10),
            details = LoadState.Ready(details()),
            playbackProgress = current,
            playbackHistory = listOf(current),
            historyAnime = LoadState.Ready(emptyList()),
        )
        val other = current.copy(animeId = 20, videoId = 200)

        val updated = state.withLocalPlaybackProgress(other, null)

        assertEquals(current, updated.playbackProgress)
        assertEquals(listOf(current), updated.playbackHistory)
        assertEquals(listOf(other), state.playbackHistoryWith(other))
        assertEquals(listOf(20L), updated.historyAnime.readyListOrEmpty().map { it.id })
        assertSame(state, state.withStoredPlaybackHistory(20, listOf(other)))
    }

    @Test
    fun progressPublicationNeverChangesTheActivePlayerState() {
        val state = YummyDroidUiState(
            route = AppRoute.Player(video(), "Anime 10"),
            details = LoadState.Ready(details()),
        )
        assertSame(state, state.withLocalPlaybackProgress(progress(), null))
        assertSame(state, state.withStoredPlaybackHistory(10, listOf(progress())))
    }

    @Test
    fun onlineSuccessPublishesLoadedDataWithoutReplacingExtrasOrMarkState() {
        val extras = LoadState.Error("keep extras")
        val mark = LoadState.Error("keep mark")
        val state = YummyDroidUiState(
            route = AppRoute.Details(10),
            detailsExtras = extras,
            animeMark = mark,
            forcedOfflineMode = false,
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
    fun restoredSelectionTakesPriorityOverOlderPlaybackProgress() {
        val oldVideo = video().copy(dubbing = "Old voice")
        val restoredVideo = video().copy(id = 2, player = "Kodik", dubbing = "Restored voice")
        val state = YummyDroidUiState(
            route = AppRoute.Details(10),
            playbackProgress = progress().copy(groupKey = oldVideo.groupKey),
        )
        val loaded = LoadedAnimeDetails(
            details = details(),
            videos = listOf(oldVideo, restoredVideo),
            offlineMode = false,
            selectedVideoGroup = restoredVideo.groupKey,
            restoredVideoGroup = restoredVideo.groupKey,
        )

        val updated = state.withLoadedAnimeDetails(animeId = 10, loaded = loaded)

        assertEquals(restoredVideo.groupKey, updated.selectedVideoGroup)
    }

    @Test
    fun playbackProgressRemainsFallbackWithoutPersistedSelection() {
        val progressVideo = video().copy(dubbing = "Previous voice")
        val defaultVideo = video().copy(id = 2, player = "Kodik", dubbing = "Default voice")
        val state = YummyDroidUiState(
            route = AppRoute.Details(10),
            playbackProgress = progress().copy(groupKey = progressVideo.groupKey),
        )
        val loaded = LoadedAnimeDetails(
            details = details(),
            videos = listOf(progressVideo, defaultVideo),
            offlineMode = false,
            selectedVideoGroup = defaultVideo.groupKey,
        )

        val updated = state.withLoadedAnimeDetails(animeId = 10, loaded = loaded)

        assertEquals(progressVideo.groupKey, updated.selectedVideoGroup)
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
    fun extrasLoadingDoesNotMixProfileSubscriptionStateIntoDetails() {
        val loaded = AnimeDetailsExtras(recommendations = emptyList())
        val state = YummyDroidUiState(
            route = AppRoute.Details(10),
            details = LoadState.Ready(details()),
            videos = LoadState.Ready(listOf(video(subscribed = true))),
            globalSubscriptions = LoadState.Ready(emptyList()),
        )

        val updated = state.withLoadedAnimeDetailsExtras(10, loaded)

        assertSame(loaded, updated.detailsExtras.readyDataOrNull())
        assertEquals(true, updated.videos.readyListOrEmpty().single().subscribed)
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
    fun genericFailurePublishesSharedErrorWithoutChangingConnectivity() {
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
        assertEquals(true, published.forcedOfflineMode)
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

    private fun video(subscribed: Boolean = false): VideoVariant {
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
            subscribed = subscribed,
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
