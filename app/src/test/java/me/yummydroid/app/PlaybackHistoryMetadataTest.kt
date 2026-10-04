package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.toAnimeSummary

class PlaybackHistoryMetadataTest {
    @Test
    fun finalPlayerSnapshotRetainsCachedMetadataWhileBackRestorationIsLoading() {
        val cached = progress().toAnimeSummary().copy(year = 2026, rating = 8.0)
        val state = YummyDroidUiState(
            route = AppRoute.Details(10),
            details = LoadState.Loading,
            historyAnime = LoadState.Ready(emptyList()),
        )
        val anime = state.playbackAnimeSummary(10, cached)
        val finalProgress = progress().copy(
            animeTitle = anime?.title.orEmpty(), posterUrl = anime?.posterUrl.orEmpty(), positionMs = 12_000,
        )
        val updated = state.withLocalPlaybackProgress(finalProgress, anime)

        assertEquals(cached.title, finalProgress.animeTitle)
        assertEquals(cached.posterUrl, finalProgress.posterUrl)
        assertEquals(listOf(cached), updated.historyAnime.readyListOrEmpty())
        assertEquals(LoadState.Loading, updated.details)
    }

    @Test
    fun metadataPoorSnapshotCannotReplaceExistingHistoryCard() {
        val known = progress().toAnimeSummary().copy(year = 2026)
        val state = YummyDroidUiState(
            route = AppRoute.Details(10), details = LoadState.Loading,
            historyAnime = LoadState.Ready(listOf(known)),
        )
        val empty = progress().copy(animeTitle = "", posterUrl = "")
        assertEquals(known, state.playbackAnimeSummary(10, null))
        assertEquals(listOf(known), state.withLocalPlaybackProgress(empty, null).historyAnime.readyListOrEmpty())
    }

    @Test
    fun metadataLookupNeverUsesAnotherAnime() {
        val other = progress().copy(animeId = 20).toAnimeSummary()
        val state = YummyDroidUiState(historyAnime = LoadState.Ready(listOf(other)))
        assertNull(state.playbackAnimeSummary(10, other))
    }

    private fun progress() = PlaybackProgress(
        animeId = 10, videoId = 1, animeTitle = "Anime title", posterUrl = "https://example.test/poster.jpg",
        groupKey = "voice", episode = "1", positionMs = 1_000, durationMs = 20_000, updatedAtMs = 123,
    )
}
